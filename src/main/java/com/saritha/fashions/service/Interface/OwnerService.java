package com.saritha.fashions.service.Interface;

import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.receipt.ReceiptDTO;

import java.io.IOException;
import java.util.List;

public interface OwnerService {
    List<ReceiptDTO> getAllReceiptByShopId(String shopId);

    byte[] downloadReceipt(ReceiptDTO receiptDTO) throws IOException;

    ReceiptDTO getReceiptByReceiptId(String shopiId, String receiptId);

    InvoiceResponse getAllInvoiceByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId);

    InvoiceDTO getInvoiceByInvoiceId(String shopId, String invoiceId);

    byte[] downloadInvoice(InvoiceDTO receiptDTO);
}
