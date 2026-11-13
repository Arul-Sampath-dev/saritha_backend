package com.saritha.fashions.modules.invoicemanagement.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.invoicemanagement.model.InvoiceCustomer;
import com.saritha.fashions.modules.invoicemanagement.model.InvoiceRecord;
import com.saritha.fashions.modules.invoicemanagement.payload.InvoiceResponse;
import com.saritha.fashions.modules.invoicemanagement.repository.InvoiceRecordRepository;
import com.saritha.fashions.modules.invoicemanagement.repository.InvoiceRecordRepositoryCustom.InvoiceFilter;
import com.saritha.fashions.modules.invoicemanagement.repository.InvoiceRecordRepositoryCustom.InvoicePage;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.service.Interface.ReceiptService;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Read-side implementation for invoice management. Replaces the old invoice list / search /
 * detail screens (GET /v1/api/invoices/..., /v1/api/shopOwner/invoices..., special-category
 * and pending/delivered lists) with ONE filtered, paginated list plus a detail endpoint.
 *
 * Access: a SHOP_OWNER reads their own shop; the platform OWNER passes shopId. An invoice of
 * another shop returns the same "Invoice not found" as a missing one, so invoice numbers of
 * other shops are not revealed.
 *
 * PDF generation reuses the existing Word-template → PDF pipeline in ReceiptServiceImpl
 * (Receipt_SF.docx + the docx-to-PDF Cloud Run service), so invoices look exactly the
 * same as before. That pipeline works on the old Invoice model, so the invoice is read
 * through the old InvoiceRepository (same collection, same data).
 */
@Service
public class InvoiceManagementReadServiceImpl implements InvoiceManagementReadService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> PAYMENT_STATUSES = List.of(
            InvoiceAmountCalculator.STATUS_DUES, InvoiceAmountCalculator.STATUS_PARTIAL, InvoiceAmountCalculator.STATUS_PAID);

    private final InvoiceRecordRepository invoiceRecordRepository;
    private final InvoiceRepository legacyInvoiceRepository;
    private final ReceiptService receiptService;
    private final AuthUtil authUtil;

    public InvoiceManagementReadServiceImpl(InvoiceRecordRepository invoiceRecordRepository,
                                            InvoiceRepository legacyInvoiceRepository,
                                            ReceiptService receiptService,
                                            AuthUtil authUtil) {
        this.invoiceRecordRepository = invoiceRecordRepository;
        this.legacyInvoiceRepository = legacyInvoiceRepository;
        this.receiptService = receiptService;
        this.authUtil = authUtil;
    }

    @Override
    public PaginatedResponse<?> listInvoices(String shopId, LocalDate from, LocalDate to, String status,
                                             String paymentStatus, String search, boolean special, boolean details,
                                             String sort, int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new APIException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new APIException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new APIException("to must not be before from");
        }
        Boolean delivered = switch (status == null ? "" : status.trim().toLowerCase(Locale.ROOT)) {
            case "", "all" -> null;
            case "pending" -> false;
            case "delivered" -> true;
            default -> throw new APIException("status must be pending, delivered or all");
        };
        boolean oldestFirst = switch (sort == null ? "newest" : sort.trim().toLowerCase(Locale.ROOT)) {
            case "newest" -> false;
            case "oldest" -> true;
            default -> throw new APIException("sort must be newest or oldest");
        };
        String payment = null;
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            payment = PAYMENT_STATUSES.stream().filter(value -> value.equalsIgnoreCase(paymentStatus.trim()))
                    .findFirst()
                    .orElseThrow(() -> new APIException("paymentStatus must be Dues, Partial or Paid"));
        }
        String text = blankToNull(search);
        if (text != null && text.length() > 100) {
            throw new APIException("search must be at most 100 characters");
        }

        String scope = resolveShop(shopId);
        InvoiceFilter filter = new InvoiceFilter(
                from == null ? null : Date.from(from.atStartOfDay(IST).toInstant()),
                to == null ? null : Date.from(to.plusDays(1).atStartOfDay(IST).toInstant()),
                delivered, payment, text, special, oldestFirst);
        InvoicePage page = invoiceRecordRepository.findInvoices(scope, filter, pageNumber, pageSize);

        List<?> content = page.invoices().stream()
                .map(invoice -> {
                    InvoiceCustomer customer = page.customersByInvoiceDbId().get(invoice.getId());
                    return details
                            ? InvoiceResponseMapper.toResponse(invoice, customer, false, null)
                            : InvoiceResponseMapper.toSummary(invoice, customer);
                })
                .toList();
        int totalPages = (int) Math.ceil((double) page.total() / pageSize);
        return new PaginatedResponse<>(content, pageNumber, pageSize, page.total(), totalPages,
                pageNumber >= totalPages - 1);
    }

    @Override
    public InvoiceResponse getInvoice(String invoiceId, String shopId) {
        String scope = resolveShop(shopId);
        // Loaded through the module repository: the customer DBRef is resolved (one extra query).
        InvoiceRecord invoice = invoiceRecordRepository.findByInvoiceIdAndShopId(invoiceId, scope)
                .orElseThrow(() -> new APIException("Invoice not found"));
        return InvoiceResponseMapper.toResponse(invoice, invoice.getCustomer(), false, null);
    }

    @Override
    public InvoicePdf getInvoicePdf(String invoiceId, String shopId) {
        String scope = resolveShop(shopId);
        Invoice invoice = legacyInvoiceRepository.findByInvoiceId(invoiceId)
                .filter(found -> scope.equals(found.getShopId()))
                .orElseThrow(() -> new APIException("Invoice not found"));

        ResponseEntity<byte[]> pdf = receiptService.getInvoicePdfMethod(invoice);
        if (pdf == null || !pdf.getStatusCode().is2xxSuccessful() || pdf.getBody() == null || pdf.getBody().length == 0) {
            throw new APIException("Could not generate the invoice PDF, please try again");
        }
        return new InvoicePdf(invoiceId + ".pdf", pdf.getBody());
    }

    /** Shop owner: their own shop. Platform owner: the requested shop (must exist). */
    private String resolveShop(String requestedShopId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Login required");
        }
        User user = authUtil.loggedInUser();
        if (hasRole(user, AppRole.SHOP_OWNER)) {
            return invoiceRecordRepository.findShopProfileByEmployeeId(user.getUserId())
                    .orElseThrow(() -> new APIException("User is not assigned to any shop"))
                    .shopId();
        }
        if (hasRole(user, AppRole.OWNER)) {
            String shopId = blankToNull(requestedShopId);
            if (shopId == null) {
                throw new APIException("shopId is required");
            }
            if (!invoiceRecordRepository.shopExists(shopId)) {
                throw new APIException("Shop Not Found");
            }
            return shopId;
        }
        throw new AccessDeniedException("Only shop owners and the platform owner can view invoices");
    }

    private static boolean hasRole(User user, AppRole role) {
        return user.getRoles() != null && user.getRoles().stream()
                .filter(Objects::nonNull).anyMatch(r -> r.getRoleName() == role);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
