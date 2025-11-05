package com.saritha.fashions.service.Interface;

import java.util.Map;

public interface DeletedInvoiceService {

    public Map<String, Object> getDeletedInvoices(
            int pageNumber, int pageSize,
            String invoiceId, String customerName, String paymentStatus,
            String fromDate, String toDate
    );
}
