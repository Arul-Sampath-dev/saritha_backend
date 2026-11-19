package com.saritha.fashions.modules.invoice.service;

import com.saritha.fashions.modules.invoice.model.InvoiceCustomer;
import com.saritha.fashions.modules.invoice.model.InvoiceItem;
import com.saritha.fashions.modules.invoice.model.InvoiceRecord;
import com.saritha.fashions.modules.invoice.model.InvoiceStage;
import com.saritha.fashions.modules.invoice.payload.InvoiceResponse;
import com.saritha.fashions.modules.invoice.payload.InvoiceSummaryResponse;
import com.saritha.fashions.modules.invoice.repository.InvoiceRecordRepositoryCustom.ReceiptInfo;

import java.util.List;
import java.util.Objects;

/**
 * Maps invoices to API responses (shared by the read and write services).
 */
public final class InvoiceResponseMapper {

    public static final String PDF_URL_TEMPLATE = "/v1/api/invoices/%s/pdf";

    private InvoiceResponseMapper() {
    }

    /**
     * Full invoice.
     *
     * @param customer      may be null (e.g. old invoice whose customer was deleted)
     * @param notifications only on creation; null otherwise
     */
    public static InvoiceResponse toResponse(InvoiceRecord invoice, InvoiceCustomer customer, boolean newCustomer,
                                             InvoiceResponse.NotificationStatus notifications) {
        return toResponse(invoice, customer, newCustomer, notifications, (ReceiptInfo) null);
    }

    /** @param receipt the delivery receipt when the invoice is delivered (looked up by the caller), else null */
    public static InvoiceResponse toResponse(InvoiceRecord invoice, InvoiceCustomer customer, boolean newCustomer,
                                             InvoiceResponse.NotificationStatus notifications, ReceiptInfo receipt) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceId(invoice.getInvoiceId())
                .shopId(invoice.getShopId())
                .customer(customer == null ? null : InvoiceResponse.CustomerSummary.builder()
                        .id(customer.getId())
                        .customerId(customer.getCustomerId())
                        .name(customer.getName())
                        .phone(customer.getPhone())
                        .email(customer.getEmail())
                        .newCustomer(newCustomer)
                        .build())
                .customerState(invoice.getCustomerState())
                .items(invoice.getItems())
                .totalAmount(invoice.getTotalAmount())
                .discount(invoice.getDiscount())
                .taxableAmount(invoice.getTaxableAmount())
                .cgstRate(invoice.getCgstRate())
                .sgstRate(invoice.getSgstRate())
                .igstRate(invoice.getIgstRate())
                .cgstAmount(invoice.getCgstAmount())
                .sgstAmount(invoice.getSgstAmount())
                .igstAmount(invoice.getIgstAmount())
                .totalAmountWithGST(invoice.getTotalAmountWithGST())
                .advancePaid(invoice.getAdvancePaid())
                .balanceDue(invoice.getBalanceDue())
                .gstin(invoice.getGstin())
                .sacCode(invoice.getSacCode())
                .hsnCode(invoice.getHsnCode())
                .paymentMethod(invoice.getPaymentMethod())
                .paymentStatus(invoice.getPaymentStatus())
                .orderStatus(invoice.getOrderStatus())
                .paymentHistories(invoice.getPaymentHistories())
                .invoiceDate(invoice.getInvoiceDate())
                .orderDate(invoice.getOrderDate())
                .expectedDeliveryDate(invoice.getExpectedDeliveryDate())
                .notes(invoice.getNotes())
                .receiptId(invoice.getReceiptId())
                .receiptNo(receipt == null ? null : receipt.receiptNo())
                .deliveredAt(receipt == null ? null : receipt.deliveredAt())
                .stage(InvoiceStage.of(invoice.getOrderStatus(), invoice.getItems()).name())
                .itemStatusCounts(InvoiceStage.statusCounts(invoice.getItems()))
                .pdfUrl(String.format(PDF_URL_TEMPLATE, invoice.getInvoiceId()))
                .notifications(notifications)
                .build();
    }

    /** One row of an invoice list. */
    public static InvoiceSummaryResponse toSummary(InvoiceRecord invoice, InvoiceCustomer customer) {
        return toSummary(invoice, customer, (ReceiptInfo) null);
    }

    /** @param receipt the delivery receipt when the invoice is delivered, else null */
    public static InvoiceSummaryResponse toSummary(InvoiceRecord invoice, InvoiceCustomer customer, ReceiptInfo receipt) {
        List<InvoiceItem> items = invoice.getItems() == null ? List.of() : invoice.getItems();
        return InvoiceSummaryResponse.builder()
                .id(invoice.getId())
                .invoiceId(invoice.getInvoiceId())
                .invoiceDate(invoice.getInvoiceDate())
                .expectedDeliveryDate(invoice.getExpectedDeliveryDate())
                .customerId(customer == null ? null : customer.getCustomerId())
                .customerName(customer == null ? null : customer.getName())
                .customerPhone(customer == null ? null : customer.getPhone())
                .itemCount(items.size())
                .itemNames(items.stream().map(InvoiceItem::getItemName).filter(Objects::nonNull).toList())
                .completedItemCount((int) items.stream().filter(item -> "ORDER_COMPLETED".equals(item.getItemOrderStatus())).count())
                .totalAmountWithGST(invoice.getTotalAmountWithGST())
                .advancePaid(invoice.getAdvancePaid())
                .balanceDue(invoice.getBalanceDue())
                .paymentStatus(invoice.getPaymentStatus())
                .orderStatus(invoice.getOrderStatus())
                .pdfUrl(String.format(PDF_URL_TEMPLATE, invoice.getInvoiceId()))
                .stage(InvoiceStage.of(invoice.getOrderStatus(), items).name())
                .itemStatusCounts(InvoiceStage.statusCounts(items))
                .receiptNo(receipt == null ? null : receipt.receiptNo())
                .deliveredAt(receipt == null ? null : receipt.deliveredAt())
                .build();
    }
}
