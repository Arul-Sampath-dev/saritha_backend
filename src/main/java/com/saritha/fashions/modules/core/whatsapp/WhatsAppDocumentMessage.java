package com.saritha.fashions.modules.core.whatsapp;

/**
 * Data for the approved Twilio WhatsApp "document ready" template.
 * The template has 4 variables:
 * {{1}} customer name, {{2}} document id, {{3}} amount, {{4}} download link.
 *
 * @param recipientPhone customer phone; 10-digit Indian numbers get +91 added
 */
public record WhatsAppDocumentMessage(
        String recipientPhone,
        String customerName,
        String documentId,
        String amount,
        String downloadUrl) {
}
