package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;

import java.util.List;

public interface TailorAssignmentService {
    Invoice assignTailorToInvoice(String invoiceId, String tailorId);

    Invoice unassignTailorFromInvoice(String invoiceId);

    List<Invoice> getInvoicesForShop();

    List<Invoice> getInvoicesAssignedToTailor(String tailorId);
}
