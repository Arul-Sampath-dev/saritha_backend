package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Receipt;

import java.util.List;
import java.util.Optional;

public interface InvoiceService {
    Receipt generateInvoice(Receipt receipt);

    Optional<Receipt> getInvoice(String id);

    boolean updatePaymentStatus(String id, String status);

    List<Receipt> getInvoicesByShop(String shopId);
}
