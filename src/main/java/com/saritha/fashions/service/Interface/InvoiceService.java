package com.saritha.fashions.service.Interface;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.payload.invoice.InvoiceAssignmentRequestDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.invoice.InvoiceResponseDTO;
import com.saritha.fashions.payload.invoice.UpdateItemsRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface InvoiceService {

    Invoice updateInvoiceItems(UpdateItemsRequest request) throws IOException;

    Invoice generateInvoice(Invoice receipt);

    Optional<Invoice> getInvoice(String id);
    Invoice getInvoiceWithAssignedItems(String invoiceId);

    boolean updatePaymentStatus(String id, String status);

    List<Invoice> getInvoicesByShop(String shopId);

    InvoiceResponse getAllInvoiceFromParticularShop(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    InvoiceResponse getAllInvoiceFromParticularShopForTailorMaster(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    InvoiceResponse getAllInvoiceFromParticularShopInSpecialCategory(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    ResponseEntity<byte[]> createInvoiceWithImages(Invoice invoice, List<MultipartFile> itemImages, int num) throws Exception;

    Receipt generateReceiptByInvoice(Invoice invoiceFromFrontend);

    InvoiceResponseDTO assignTailorToInvoice(InvoiceAssignmentRequestDTO requestDTO);
    InvoiceResponseDTO unassignTailorFromInvoice(String invoiceId);

    Invoice updatePaymentAmountInInvoiceAndReceipt(String id, String paymentAmount, String paymentMethod, String discount);

    InvoiceResponseDTO assignShopAssistantToInvoice(InvoiceAssignmentRequestDTO requestDTO);

    ResponseEntity<byte[]> downloadInvoiceByCustomer(String invoiceId);

    InvoiceResponse getAllInvoiceFromParticularShopWithDetails(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
