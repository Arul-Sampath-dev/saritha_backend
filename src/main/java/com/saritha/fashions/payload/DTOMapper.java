package com.saritha.fashions.payload;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;

public class DTOMapper {

    public static InvoiceDTO mapInvoiceToDTO(Invoice invoice) {
        InvoiceDTO dto = new InvoiceDTO();
        dto.setId(invoice.getId());
        dto.setInvoiceId(invoice.getInvoiceId());
        dto.setShopId(invoice.getShopId());
        dto.setItems(invoice.getItems());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setAdvancePaid(invoice.getAdvancePaid());
        dto.setBalanceDue(invoice.getBalanceDue());
        dto.setPaymentMethod(invoice.getPaymentMethod());
        dto.setPaymentStatus(invoice.getPaymentStatus());
        dto.setOrderStatus(invoice.getOrderStatus());
        dto.setExpectedDeliveryDate(invoice.getExpectedDeliveryDate());
        dto.setPaymentCompletedDate(invoice.getPaymentCompletedDate());
        dto.setOrderDate(invoice.getOrderDate());
        dto.setInvoiceDate(invoice.getInvoiceDate());
        dto.setNotes(invoice.getNotes());

        if (invoice.getCustomer() != null)
            dto.setCustomerName(invoice.getCustomer().getName());

        if (invoice.getReceiptId() != null)
            dto.setReceiptId(invoice.getId());

        return dto;
    }

    public static ReceiptDTO mapReceiptToDTO(Receipt receipt) {
        ReceiptDTO dto = new ReceiptDTO();
        dto.setId(receipt.getId());
        dto.setReceiptNo(receipt.getReceiptNo());
        dto.setShopId(receipt.getShopId());
        dto.setItems(receipt.getItems());
        dto.setTotalAmount(receipt.getTotalAmount());
        dto.setAdvancePaid(receipt.getAdvancePaid());
        dto.setBalanceDue(receipt.getBalanceDue());
        dto.setPaymentMethod(receipt.getPaymentMethod());
        dto.setPaymentStatus(receipt.getPaymentStatus());
        dto.setOrderStatus(receipt.getOrderStatus());
        dto.setExpectedDeliveryDate(receipt.getExpectedDeliveryDate());
        dto.setPaymentCompletedDate(receipt.getPaymentCompletedDate());
        dto.setReceiptDate(receipt.getReceiptDate());
        dto.setOrderDate(receipt.getOrderDate());
        dto.setReceiptNotes(receipt.getReceiptNotes());
        dto.setReceiptUrl(receipt.getReceiptUrl());

        if (receipt.getCustomer() != null)
            dto.setCustomerName(receipt.getCustomer().getName());

        if (receipt.getInvoiceId() != null)
            dto.setInvoiceId(receipt.getInvoiceId());

        return dto;
    }
}

