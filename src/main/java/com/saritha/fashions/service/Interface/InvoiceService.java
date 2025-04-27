package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.UpdateItemsRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface InvoiceService {

    Invoice updateInvoiceItems(UpdateItemsRequest request);

    Invoice generateInvoice(Invoice receipt);

    Optional<Invoice> getInvoice(String id);

    boolean updatePaymentStatus(String id, String status);

    List<Invoice> getInvoicesByShop(String shopId);

    InvoiceResponse getAllInvoiceFromParticularShop(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    ResponseEntity<byte[]> createInvoiceWithImages(Invoice invoice, List<MultipartFile> itemImages, int num) throws Exception;

    Receipt generateReceiptByInvoice(Invoice invoiceFromFrontend);
}
