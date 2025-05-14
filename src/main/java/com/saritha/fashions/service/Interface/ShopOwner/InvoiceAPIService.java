package com.saritha.fashions.service.Interface.ShopOwner;

import com.saritha.fashions.model.Invoice;

public interface InvoiceAPIService {

    Invoice deleteInvoiceByInvoiceId(String shopId, String invoiceId);
}
