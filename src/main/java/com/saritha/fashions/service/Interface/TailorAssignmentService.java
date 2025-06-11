package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.invoice.InvoiceResponse;

import java.util.List;

public interface TailorAssignmentService {
    Invoice assignTailorToInvoice(String invoiceId, String tailorId);

    Invoice unassignTailorFromInvoice(String invoiceId);

    List<Invoice> getInvoicesForShop();

    InvoiceResponse getInvoicesAssignedToTailor(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    List<User> fetchAllTailorFromParticularShop();

    List<User> fetchAllShopAssistantFromParticularShop();
}
