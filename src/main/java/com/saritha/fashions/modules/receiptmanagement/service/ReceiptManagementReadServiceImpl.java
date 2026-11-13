package com.saritha.fashions.modules.receiptmanagement.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.modules.receiptmanagement.model.ReceiptRecord;
import com.saritha.fashions.modules.receiptmanagement.payload.ReceiptResponse;
import com.saritha.fashions.modules.receiptmanagement.repository.ReceiptRecordRepository;
import com.saritha.fashions.modules.receiptmanagement.repository.ReceiptRecordRepositoryCustom.CustomerSummary;
import com.saritha.fashions.modules.receiptmanagement.repository.ReceiptRecordRepositoryCustom.PageResult;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reading receipts. Replaces GET /v1/api/receipt/{id}, /shop/{shopId} and /all-receipt.
 *
 * Fixes vs the old reads: shop owners only see their own shop's receipts (old: any
 * logged-in user could read any shop's receipts by number or shop id); the platform owner
 * sees all shops. Missing receipts are a 400 "Receipt not found" (old: empty 404).
 *
 * The PDF reuses the existing template pipeline (ReceiptServiceImpl.getReceiptPdfMethod),
 * which works on the old Receipt model, so the receipt is read through the old repository.
 */
@Service
public class ReceiptManagementReadServiceImpl implements ReceiptManagementReadService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String PDF_URL = "/v1/api/receipt-management/receipts/%s/pdf";

    private final ReceiptActorResolver actorResolver;
    private final ReceiptRecordRepository repository;
    private final ReceiptRepository legacyReceiptRepository;
    private final ReceiptService legacyReceiptService;

    public ReceiptManagementReadServiceImpl(ReceiptActorResolver actorResolver,
                                            ReceiptRecordRepository repository,
                                            ReceiptRepository legacyReceiptRepository,
                                            ReceiptService legacyReceiptService) {
        this.actorResolver = actorResolver;
        this.repository = repository;
        this.legacyReceiptRepository = legacyReceiptRepository;
        this.legacyReceiptService = legacyReceiptService;
    }

    @Override
    public PaginatedResponse<ReceiptResponse> getReceipts(String shopId, int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new APIException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new APIException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        ReceiptActor actor = actorResolver.requireReader();
        String scope = actor.isPlatformOwner()
                ? (shopId == null || shopId.isBlank() ? null : shopId.trim())
                : actor.requireShopId(); // shop owners: always their own shop (shopId param ignored)

        PageResult page = repository.findReceipts(scope, pageNumber, pageSize);
        List<ReceiptResponse> content = toResponses(page.receipts());
        int totalPages = (int) Math.ceil((double) page.total() / pageSize);
        return new PaginatedResponse<>(content, pageNumber, pageSize, page.total(), totalPages,
                pageNumber >= totalPages - 1);
    }

    @Override
    public ReceiptResponse getReceipt(String receiptNo) {
        ReceiptActor actor = actorResolver.requireReader();
        ReceiptRecord receipt = repository.findReceipt(receiptNo, actor.isPlatformOwner() ? null : actor.requireShopId())
                .orElseThrow(() -> new APIException("Receipt not found"));
        return toResponse(receipt, null);
    }

    @Override
    public ReceiptPdf getReceiptPdf(String receiptNo) {
        ReceiptActor actor = actorResolver.requireReader();
        Receipt receipt = legacyReceiptRepository.findByReceiptNo(receiptNo)
                .filter(found -> actor.isPlatformOwner() || actor.requireShopId().equals(found.getShopId()))
                .orElseThrow(() -> new APIException("Receipt not found"));
        ResponseEntity<byte[]> pdf;
        try {
            pdf = legacyReceiptService.getReceiptPdfMethod(receipt);
        } catch (Exception exception) {
            throw new APIException("Could not generate the receipt PDF, please try again");
        }
        if (pdf == null || pdf.getBody() == null || pdf.getBody().length == 0) {
            throw new APIException("Could not generate the receipt PDF, please try again");
        }
        return new ReceiptPdf(receiptNo + ".pdf", pdf.getBody());
    }

    // ------------------------------------------------------------------ mapping (also used by the write side)

    ReceiptResponse toResponse(ReceiptRecord receipt, CustomerSummary knownCustomer) {
        ReceiptResponse response = toResponses(List.of(receipt)).get(0);
        if (knownCustomer != null) {
            response.setCustomerName(knownCustomer.name());
            response.setCustomerPhone(knownCustomer.phone());
        }
        return response;
    }

    private List<ReceiptResponse> toResponses(List<ReceiptRecord> receipts) {
        Map<String, String> invoiceNumbers = repository.findInvoiceNumbers(
                receipts.stream().map(ReceiptRecord::getInvoiceId).toList());
        Map<String, CustomerSummary> customers = repository.findCustomersOfReceipts(
                receipts.stream().map(ReceiptRecord::getId).toList());

        List<ReceiptResponse> responses = new ArrayList<>();
        for (ReceiptRecord receipt : receipts) {
            CustomerSummary customer = customers.get(receipt.getId());
            responses.add(ReceiptResponse.builder()
                    .id(receipt.getId())
                    .receiptNo(receipt.getReceiptNo())
                    .invoiceId(invoiceNumbers.getOrDefault(receipt.getInvoiceId(), receipt.getInvoiceId()))
                    .shopId(receipt.getShopId())
                    .customerName(customer == null ? null : customer.name())
                    .customerPhone(customer == null ? null : customer.phone())
                    .receiptDate(receipt.getReceiptDate())
                    .items(receipt.getItems())
                    .totalAmount(receipt.getTotalAmount())
                    .discount(receipt.getDiscount())
                    .taxableAmount(receipt.getTaxableAmount())
                    .cgstAmount(receipt.getCgstAmount())
                    .sgstAmount(receipt.getSgstAmount())
                    .igstAmount(receipt.getIgstAmount())
                    .totalAmountWithGST(receipt.getTotalAmountWithGST())
                    .amountPaid(receipt.getAdvancePaid())
                    .paymentStatus(receipt.getPaymentStatus())
                    .paymentMethod(receipt.getPaymentMethod())
                    .orderStatus(receipt.getOrderStatus())
                    .gstin(receipt.getGstin())
                    .pdfUrl(String.format(PDF_URL, receipt.getReceiptNo()))
                    .build());
        }
        return responses;
    }
}
