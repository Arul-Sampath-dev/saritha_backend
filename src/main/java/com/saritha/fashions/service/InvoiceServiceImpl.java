package com.saritha.fashions.service;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.service.Interface.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InvoiceServiceImpl implements InvoiceService {


    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private WhatsAppService whatsAppService;


    @Override
    public boolean generateInvoice(Invoice invoice) {
        try {
            invoiceRepository.save(invoice);
//            whatsAppService.sendInvoice(invoice); // Send WhatsApp message
            return true;
        }catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Optional<Invoice> getInvoice(String id) {
        return invoiceRepository.findById(id);
    }

    @Override
    public boolean updatePaymentStatus(String id, String status) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        if (invoice.isPresent()) {
            Invoice inv = invoice.get();
            inv.setStatus(status);
            try{
                invoiceRepository.save(inv);
                return true;
            }catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }else {
            return false;
        }
    }

    @Override
    public List<Invoice> getInvoicesByShop(String shopId) {
        return invoiceRepository.findByShopId(shopId);
    }
}
