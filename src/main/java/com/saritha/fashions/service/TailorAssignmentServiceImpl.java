package com.saritha.fashions.service;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TailorAssignmentServiceImpl implements TailorAssignmentService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Override
    public Invoice assignTailorToInvoice(String invoiceId, String tailorId) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findById(invoiceId);
        if (optionalInvoice.isPresent()) {
            Invoice invoice = optionalInvoice.get();
            invoice.setAssignedTailorId(tailorId);
            invoice.setAssignmentStatus("ASSIGNED");
            return invoiceRepository.save(invoice);
        } else {
            throw new RuntimeException("Invoice not found with ID: " + invoiceId);
        }
    }

    @Override
    public Invoice unassignTailorFromInvoice(String invoiceId) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findById(invoiceId);
        if (optionalInvoice.isPresent()) {
            Invoice invoice = optionalInvoice.get();
            invoice.setAssignedTailorId(null);
            invoice.setAssignmentStatus("UNASSIGNED");
            return invoiceRepository.save(invoice);
        } else {
            throw new RuntimeException("Invoice not found with ID: " + invoiceId);
        }
    }

    @Override
    public List<Invoice> getInvoicesForShop(String shopId) {
        return invoiceRepository.findByShopId(shopId);
    }

    @Override
    public List<Invoice> getInvoicesAssignedToTailor(String tailorId) {
        return invoiceRepository.findByAssignedTailorId(tailorId);
    }
}

