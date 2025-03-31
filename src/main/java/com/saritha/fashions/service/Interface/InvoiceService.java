package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface InvoiceService {
    Invoice generateInvoice(Invoice receipt);

    Optional<Invoice> getInvoice(String id);

    boolean updatePaymentStatus(String id, String status);

    List<Invoice> getInvoicesByShop(String shopId);

    Page<Invoice> getAllInvoiceFromParticularShop(int page, int size);
}
