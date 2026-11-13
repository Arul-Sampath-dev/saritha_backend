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
 * The links point to the existing frontend routes /download-invoice/{token} and
 * /download-receipt/{token}, which call the legacy PublicController
 * (/v1/api/public/download-invoice|receipt/{token}). That controller still decrypts with the
 * legacy service and generates the PDF with the legacy pipeline; it moves when the PDF pipeline
 * is ported. Both services must keep the same format and secret until then.
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
}
