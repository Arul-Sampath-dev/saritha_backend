package com.saritha.fashions.modules.core.documentlink;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds the public download links that are sent to customers (WhatsApp / email).
 *
 * The document id is encrypted into an opaque, tamper-evident token by DocumentTokenService
 * (module-owned; same token format as the legacy CustomerDocumentTokenService), so customers
 * cannot guess other documents' links.
 *
 * The links point to the frontend routes /download-invoice/{token}, /download-receipt/{token} and
 * /download-payment-receipt/{token}, which download the PDF from CustomerDocumentController
 * (GET /v1/api/public/documents/{invoices|receipts|payment-receipts}/{token}). Links sent before the
 * refactoring keep working because the token format and secret are unchanged.
 */
@Service
public class CustomerDocumentLinkService {

    private final DocumentTokenService tokenService;
    private final String websiteLink;

    public CustomerDocumentLinkService(DocumentTokenService tokenService,
                                       @Value("${twillo.website.link}") String websiteLink) {
        this.tokenService = tokenService;
        this.websiteLink = websiteLink.trim().replaceAll("/+$", "");
    }

    public String invoiceDownloadLink(String invoiceId) {
        return websiteLink + "/download-invoice/" + tokenService.createToken(DocumentKind.INVOICE, invoiceId);
    }

    public String receiptDownloadLink(String receiptId) {
        return websiteLink + "/download-receipt/" + tokenService.createToken(DocumentKind.RECEIPT, receiptId);
    }

    /** Frontend page /download-payment-receipt/{token} → GET /v1/api/public/documents/payment-receipts/{token}. */
    public String paymentReceiptDownloadLink(String paymentReceiptNo) {
        return websiteLink + "/download-payment-receipt/" + tokenService.createToken(DocumentKind.PAYMENT_RECEIPT, paymentReceiptNo);
    }
}
