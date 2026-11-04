package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.payload.receipt.ReceiptResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.net.CacheRequest;
import java.util.List;
import java.util.Optional;

public interface ReceiptService {
    Invoice generateReceipt(Invoice invoice);

    Optional<Receipt> getReceipt(String id);

    boolean updatePaymentStatus(String id, String status);

    List<Receipt> getReceiptByShop(String shopId);

    Page<Receipt> getAllReceiptFromAllShop(int page, int size);
    ReceiptResponse getAllReceiptFromAllShops(int page, int size, String sortBy, String sortOrder);

    ResponseEntity<byte[]> generateReceiptAndInvoice(Invoice invoice, int i);

    ResponseEntity<byte[]> generateReceiptDocument(Invoice invoice, int i);

    ResponseEntity<byte[]> getInvoicePdfMethod(Invoice invoice);

    ResponseEntity<byte[]> getReceiptPdfMethod(Receipt receipt) throws IOException;

    ResponseEntity<byte[]> downloadInvoiceByCustomerPdf(String token);

    ResponseEntity<byte[]> downloadReceiptByCustomerPdf(String token) throws IOException;

//    ResponseEntity<byte[]> generateReceiptAndInvoice(Invoice invoice, List<MultipartFile> itemImages, int i);
}
