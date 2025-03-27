package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;

import java.util.List;
import java.util.Optional;

public interface InvoiceService {
    Invoice generateInvoice(Invoice invoice);

    Optional<Invoice> getInvoice(String id);

    boolean updatePaymentStatus(String id, String status);

    List<Invoice> getInvoicesByShop(String shopId);
}
