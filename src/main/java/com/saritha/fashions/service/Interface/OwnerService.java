package com.saritha.fashions.service.Interface;

import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.owner.UserDtoForOwner;
import com.saritha.fashions.payload.receipt.ReceiptDTO;
import com.saritha.fashions.payload.receipt.ReceiptResponse;
import com.saritha.fashions.payload.shop.PettyCashDTO;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public interface OwnerService {
    ReceiptResponse getAllReceiptByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId);

    byte[] downloadReceipt(ReceiptDTO receiptDTO) throws IOException;

    ReceiptDTO getReceiptByReceiptId(String shopiId, String receiptId);

    InvoiceResponse getAllInvoiceByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId);

    InvoiceDTO getInvoiceByInvoiceId(String shopId, String invoiceId);

    byte[] downloadInvoice(InvoiceDTO receiptDTO);

    PaginatedResponse<UserDtoForOwner> getAllEmployeeByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId);

    List<UserDtoForOwner> getAllEmployeeListByShopId(String shopId);

    UserDtoForOwner getUserByUserId(String shopId, String userId);

    PaginatedResponse<PettyCashDTO> getAllPettyCashByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId);

    PaginatedResponse<PettyCashDTO> searchPettyCashByAmount(String shopId, String term, Integer pageNumber, Integer pageSize);

    PaginatedResponse<InvoiceDTO> getALlPendingOrdersByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId);

    PaginatedResponse<InvoiceDTO> getALlPendingOrdersByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String searchTerm);

    PaginatedResponse<InvoiceDTO> searchPendingOrdersByName(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String term);

    PaginatedResponse<InvoiceDTO> getAllCompletedOrdersByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId, Date startDate, Date endDate, String term);

    List<InvoiceDTO> searchCompletedOrdersByName(String shopId, String term);
}
