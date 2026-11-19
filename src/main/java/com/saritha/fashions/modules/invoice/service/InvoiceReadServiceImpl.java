package com.saritha.fashions.modules.invoice.service;

import com.saritha.fashions.modules.invoice.repository.InvoiceRecordRepositoryCustom.SortField;
import com.saritha.fashions.modules.invoice.model.InvoiceStage;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.model.User;
import com.saritha.fashions.modules.core.pdf.SalesDocumentPdfService;
import com.saritha.fashions.modules.invoice.model.InvoiceCustomer;
import com.saritha.fashions.modules.invoice.model.InvoiceRecord;
import com.saritha.fashions.modules.invoice.payload.InvoiceResponse;
import com.saritha.fashions.modules.invoice.repository.InvoiceRecordRepository;
import com.saritha.fashions.modules.invoice.repository.InvoiceRecordRepositoryCustom.InvoiceFilter;
import com.saritha.fashions.modules.invoice.repository.InvoiceRecordRepositoryCustom.InvoicePage;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.util.AuthUtil;
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
 * PDFs come from the module-owned SalesDocumentPdfService (same Word template and
 * docx-to-PDF Cloud Run service as before, so invoices look the same).
 */
@Service
public class InvoiceReadServiceImpl implements InvoiceReadService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> PAYMENT_STATUSES = List.of(
            InvoiceAmountCalculator.STATUS_DUES, InvoiceAmountCalculator.STATUS_PARTIAL, InvoiceAmountCalculator.STATUS_PAID);

    private final InvoiceRecordRepository invoiceRecordRepository;
    private final SalesDocumentPdfService pdfService;
    private final AuthUtil authUtil;

    public InvoiceReadServiceImpl(InvoiceRecordRepository invoiceRecordRepository,
                                            SalesDocumentPdfService pdfService,
                                            AuthUtil authUtil) {
        this.invoiceRecordRepository = invoiceRecordRepository;
        this.pdfService = pdfService;
        this.authUtil = authUtil;
    }

    @Override
    public PaginatedResponse<?> listInvoices(String shopId, LocalDate from, LocalDate to, String status,
                                             String paymentStatus, String search, boolean special, boolean details,
                                             String sort, String stage, String itemStatus, int pageNumber, int pageSize) {
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
        String sortKey = sort == null || sort.isBlank() ? "newest" : sort.trim().toLowerCase(Locale.ROOT);
        boolean ascending = switch (sortKey) {
            case "newest", "duelatest" -> false;
            case "oldest", "duesoonest" -> true;
            default -> throw new APIException("sort must be newest, oldest, dueSoonest or dueLatest");
        };
        SortField sortField = sortKey.startsWith("due") ? SortField.DUE_DATE : SortField.INVOICE_DATE;
        InvoiceStage stageFilter = blankToNull(stage) == null ? null : InvoiceStage.parse(stage)
                .orElseThrow(() -> new APIException("stage must be NEW, IN_PRODUCTION, READY or DELIVERED"));
        String itemStatusFilter = null;
        if (blankToNull(itemStatus) != null) {
            itemStatusFilter = InvoiceStage.ITEM_STATUSES.stream()
                    .filter(value -> value.equalsIgnoreCase(itemStatus.trim())).findFirst()
                    .orElseThrow(() -> new APIException("itemStatus must be one of " + InvoiceStage.ITEM_STATUSES));
        }
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
                delivered, payment, text, special, ascending, stageFilter, itemStatusFilter, sortField);
        InvoicePage page = invoiceRecordRepository.findInvoices(scope, filter, pageNumber, pageSize);

        List<?> content = page.invoices().stream()
                .map(invoice -> {
                    InvoiceCustomer customer = page.customersByInvoiceDbId().get(invoice.getId());
                    var receipt = page.receiptByInvoiceDbId().get(invoice.getId());
                    return details
                            ? InvoiceResponseMapper.toResponse(invoice, customer, false, null, receipt)
                            : InvoiceResponseMapper.toSummary(invoice, customer, receipt);
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
        return InvoiceResponseMapper.toResponse(invoice, invoice.getCustomer(), false, null,
                invoiceRecordRepository.findReceipt(invoice.getReceiptId()).orElse(null));
    }

    @Override
    public InvoicePdf getInvoicePdf(String invoiceId, String shopId) {
        String scope = resolveShop(shopId);
        return new InvoicePdf(invoiceId + ".pdf", pdfService.invoicePdf(invoiceId, scope));
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
