package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

public interface ReceiptService {
    Invoice generateReceipt(Invoice invoice);

    Optional<Receipt> getReceipt(String id);

    boolean updatePaymentStatus(String id, String status);

    List<Receipt> getReceiptByShop(String shopId);

    Page<Receipt> getAllReceiptFromAllShop(int page, int size);

//    ResponseEntity<byte[]> generateReceiptDocument(Invoice receipt, int i);

    ResponseEntity<byte[]> generateReceiptAndInvoice(Invoice invoice, int i);

    ResponseEntity<byte[]> generateReceiptDocument(Invoice invoice, int i);
}
