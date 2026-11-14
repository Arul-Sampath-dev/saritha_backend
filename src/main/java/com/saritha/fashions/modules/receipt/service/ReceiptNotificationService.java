package com.saritha.fashions.modules.receipt.service;

import com.saritha.fashions.modules.core.async.NotificationDispatcher;
import com.saritha.fashions.modules.core.documentlink.CustomerDocumentLinkService;
import com.saritha.fashions.modules.core.mailing.BrevoMailService;
import com.saritha.fashions.modules.core.mailing.MailMessage;
import com.saritha.fashions.modules.core.whatsapp.WhatsAppDocumentMessage;
import com.saritha.fashions.modules.core.whatsapp.WhatsAppMessagingService;
import com.saritha.fashions.modules.receipt.payload.ReceiptResponse;
import com.saritha.fashions.util.TaxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends the "receipt ready" message (WhatsApp, and email when known) with a secure
 * download link. New: the old flow never sent receipts to customers, although the
 * WhatsApp template and the public /download-receipt link already supported it.
 *
 * Runs in the background after the receipt is saved; failures are logged only.
 */
@Service
public class ReceiptNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptNotificationService.class);

    static final String QUEUED = "QUEUED";
    static final String SKIPPED = "SKIPPED";

    private final WhatsAppMessagingService whatsAppService;
    private final BrevoMailService mailService;
    private final CustomerDocumentLinkService linkService;
    private final NotificationDispatcher dispatcher;

    public ReceiptNotificationService(WhatsAppMessagingService whatsAppService, BrevoMailService mailService,
                                      CustomerDocumentLinkService linkService, NotificationDispatcher dispatcher) {
        this.whatsAppService = whatsAppService;
        this.mailService = mailService;
        this.linkService = linkService;
        this.dispatcher = dispatcher;
    }

    public ReceiptResponse.Notifications notifyReceiptCreated(String receiptNo, String shopName, String customerName,
                                                              String customerPhone, String customerEmail, double amountPaid) {
        boolean sendWhatsApp = customerPhone != null && !customerPhone.isBlank();
        boolean sendEmail = customerEmail != null && !customerEmail.isBlank();
        if (sendWhatsApp || sendEmail) {
            dispatcher.dispatch("receipt " + receiptNo + " notifications", () -> {
                String link = linkService.receiptDownloadLink(receiptNo);
                if (sendWhatsApp) {
                    try {
                        whatsAppService.sendDocumentMessage(new WhatsAppDocumentMessage(
                                customerPhone, customerName, receiptNo, TaxUtils.formatAmount(amountPaid), link));
                    } catch (Exception exception) {
                        log.error("WhatsApp receipt notification failed for {}: {}", receiptNo, exception.getMessage(), exception);
                    }
                }
                if (sendEmail) {
                    try {
                        mailService.send(new MailMessage(customerEmail, customerName,
                                "Your receipt " + receiptNo + " from " + shopName,
                                emailHtml(receiptNo, shopName, customerName, amountPaid, link)));
                    } catch (Exception exception) {
                        log.error("Email receipt notification failed for {}: {}", receiptNo, exception.getMessage(), exception);
                    }
                }
            });
        }
        return new ReceiptResponse.Notifications(sendWhatsApp ? QUEUED : SKIPPED, sendEmail ? QUEUED : SKIPPED);
    }

    private static String emailHtml(String receiptNo, String shopName, String customerName, double amountPaid, String link) {
        String safeLink = HtmlUtils.htmlEscape(link);
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#222;max-width:560px\">"
                + "<p>Dear " + HtmlUtils.htmlEscape(customerName == null ? "" : customerName) + ",</p>"
                + "<p>Thank you for shopping with <strong>" + HtmlUtils.htmlEscape(shopName == null ? "" : shopName)
                + "</strong>. Your order has been delivered and fully paid.</p>"
                + "<p>Receipt number: <strong>" + HtmlUtils.htmlEscape(receiptNo) + "</strong><br>"
                + "Amount paid: <strong>&#8377;" + TaxUtils.formatAmount(amountPaid) + "</strong></p>"
                + "<p style=\"margin:24px 0\"><a href=\"" + safeLink + "\" style=\"background:#7b2c5b;color:#fff;"
                + "padding:10px 18px;border-radius:4px;text-decoration:none\">Download receipt</a></p>"
                + "<p style=\"font-size:12px;color:#666\">If the button does not work, open this link: " + safeLink + "</p>"
                + "</div>";
    }
}
