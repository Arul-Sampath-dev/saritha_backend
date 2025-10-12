package com.saritha.fashions.service.Interface.ShopOwner;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.payload.invoice.InvoiceDTO;

public interface InvoiceAPIService {

    Invoice deleteInvoiceByInvoiceId(String shopId, String invoiceId);

    InvoiceDTO findByInvoiceId(String id);
}
