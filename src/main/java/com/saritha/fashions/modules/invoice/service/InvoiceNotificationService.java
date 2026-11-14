package com.saritha.fashions.modules.invoice.service;

import com.saritha.fashions.modules.core.async.NotificationDispatcher;
import com.saritha.fashions.modules.core.documentlink.CustomerDocumentLinkService;
import com.saritha.fashions.modules.core.mailing.BrevoMailService;
import com.saritha.fashions.modules.core.mailing.MailMessage;
import com.saritha.fashions.modules.core.whatsapp.WhatsAppDocumentMessage;
import com.saritha.fashions.modules.core.whatsapp.WhatsAppMessagingService;
import com.saritha.fashions.modules.invoice.payload.InvoiceResponse.NotificationStatus;
import com.saritha.fashions.util.TaxUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends "invoice created" notifications to the customer (WhatsApp, and email when the
 * customer has an email address).
 *
 * Called only AFTER the invoice is saved. Messages are sent on the background
 * notification executor and every failure is caught and logged, so a Twilio / Brevo
 * outage can never fail invoice creation or cause a duplicate invoice on retry.
 * (The old flow sent WhatsApp synchronously inside the same try block, so a Twilio
 * error made a saved invoice look failed.)
 */
@Service
public class InvoiceNotificationService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNotificationService.class);

    public static final String QUEUED = "QUEUED";
    public static final String SKIPPED = "SKIPPED";

    private final WhatsAppMessagingService whatsAppService;
    private final BrevoMailService mailService;
    private final CustomerDocumentLinkService documentLinkService;
    private final NotificationDispatcher notificationDispatcher;

    public InvoiceNotificationService(WhatsAppMessagingService whatsAppService,
                                      BrevoMailService mailService,
                                      CustomerDocumentLinkService documentLinkService,
                                      NotificationDispatcher notificationDispatcher) {
        this.whatsAppService = whatsAppService;
        this.mailService = mailService;
        this.documentLinkService = documentLinkService;
        this.notificationDispatcher = notificationDispatcher;
    }

    /**
     * Queues the notifications and returns immediately.
     *
     * @return what was queued (QUEUED or SKIPPED per channel)
     */
    public NotificationStatus notifyInvoiceCreated(InvoiceCreatedEvent event) {
        boolean sendWhatsApp = event.customerPhone() != null && !event.customerPhone().isBlank();
        boolean sendEmail = event.customerEmail() != null && !event.customerEmail().isBlank();

        if (sendWhatsApp || sendEmail) {
            try {
                notificationDispatcher.dispatch("invoice " + event.invoiceId() + " notifications",
                        () -> deliver(event, sendWhatsApp, sendEmail));
            } catch (Exception exception) {
                log.error("Could not queue notifications for invoice {}", event.invoiceId(), exception);
                return new NotificationStatus(SKIPPED, SKIPPED);
            }
        }
        return new NotificationStatus(sendWhatsApp ? QUEUED : SKIPPED, sendEmail ? QUEUED : SKIPPED);
    }

    private void deliver(InvoiceCreatedEvent event, boolean sendWhatsApp, boolean sendEmail) {
        String downloadLink;
        try {
            downloadLink = documentLinkService.invoiceDownloadLink(event.invoiceId());
        } catch (Exception exception) {
            log.error("Could not build download link for invoice {}; notifications not sent", event.invoiceId(), exception);
            return;
        }

        if (sendWhatsApp) {
            try {
                whatsAppService.sendDocumentMessage(new WhatsAppDocumentMessage(
                        event.customerPhone(),
                        event.customerName(),
                        event.invoiceId(),
                        TaxUtils.formatAmount(event.advancePaid()),
                        downloadLink));
            } catch (Exception exception) {
                // Message includes Twilio's error code; the exception adds the full cause.
                log.error("WhatsApp notification failed for invoice {}: {}", event.invoiceId(), exception.getMessage(), exception);
            }
        }

        if (sendEmail) {
            try {
                mailService.send(new MailMessage(
                        event.customerEmail(),
                        event.customerName(),
                        "Your invoice " + event.invoiceId() + " from " + event.shopName(),
                        buildEmailHtml(event, downloadLink)));
            } catch (Exception exception) {
                // Message includes Brevo's HTTP code and response body; the exception adds the full cause.
                log.error("Email notification failed for invoice {}: {}", event.invoiceId(), exception.getMessage(), exception);
            }
        }
    }

    /** Simple, inline-styled HTML (works in most mail clients). All values are escaped. */
    private static String buildEmailHtml(InvoiceCreatedEvent event, String downloadLink) {
        String name = HtmlUtils.htmlEscape(nullToEmpty(event.customerName()));
        String shop = HtmlUtils.htmlEscape(nullToEmpty(event.shopName()));
        String invoiceId = HtmlUtils.htmlEscape(event.invoiceId());
        String link = HtmlUtils.htmlEscape(downloadLink);
        String delivery = event.expectedDeliveryDate() == null || event.expectedDeliveryDate().isBlank()
                ? "" : row("Expected delivery", HtmlUtils.htmlEscape(event.expectedDeliveryDate()));

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#222;max-width:560px\">"
                + "<p>Dear " + name + ",</p>"
                + "<p>Thank you for your order with <strong>" + shop + "</strong>. Here are your invoice details:</p>"
                + "<table style=\"border-collapse:collapse;width:100%\">"
                + row("Invoice number", invoiceId)
                + row("Total (incl. GST)", "&#8377;" + TaxUtils.formatAmount(event.totalAmountWithGST()))
                + row("Advance paid", "&#8377;" + TaxUtils.formatAmount(event.advancePaid()))
                + row("Balance due", "&#8377;" + TaxUtils.formatAmount(event.balanceDue()))
                + delivery
                + "</table>"
                + "<p style=\"margin:24px 0\"><a href=\"" + link + "\" "
                + "style=\"background:#7b2c5b;color:#fff;padding:10px 18px;border-radius:4px;text-decoration:none\">"
                + "Download invoice</a></p>"
                + "<p style=\"font-size:12px;color:#666\">If the button does not work, open this link: " + link + "</p>"
                + "</div>";
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:6px 0;color:#666\">" + label + "</td>"
                + "<td style=\"padding:6px 0;text-align:right\"><strong>" + value + "</strong></td></tr>";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Everything the notifications need, captured after the invoice is saved.
     */
    public record InvoiceCreatedEvent(
            String invoiceId,
            String shopName,
            String customerName,
            String customerPhone,
            String customerEmail,
            double totalAmountWithGST,
            double advancePaid,
            double balanceDue,
            String expectedDeliveryDate) {
    }
}
