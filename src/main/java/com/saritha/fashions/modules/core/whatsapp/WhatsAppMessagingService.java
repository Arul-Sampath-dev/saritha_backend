package com.saritha.fashions.modules.core.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends WhatsApp messages through Twilio using approved content templates.
 *
 * Differences from the old service.Custom.TwilioWhatsAppSender:
 * - one Twilio client per service, instead of Twilio.init(...) on every message
 *   (Twilio.init changes global state and is not safe from multiple threads);
 * - failures are thrown as WhatsAppDeliveryException instead of being printed and
 *   swallowed, so the caller decides what to do (log, retry, ...);
 * - the template (content SID) is configurable;
 * - logging uses SLF4J and never logs the download link (it grants document access).
 */
@Service
public class WhatsAppMessagingService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessagingService.class);

    /** Same template the old sender used; override with twilio.template.document-sid. */
    private static final String DEFAULT_DOCUMENT_TEMPLATE_SID = "HXe6f4ff67cb16b5e9de82d3f5c0a5296e";

    private final TwilioRestClient twilioClient;
    private final String fromNumber;
    private final String documentTemplateSid;
    private final ObjectMapper objectMapper;

    public WhatsAppMessagingService(@Value("${twilio.accountSid}") String accountSid,
                                    @Value("${twilio.authToken}") String authToken,
                                    @Value("${twilio.whatsappFrom}") String fromNumber,
                                    @Value("${twilio.template.document-sid:" + DEFAULT_DOCUMENT_TEMPLATE_SID + "}")
                                    String documentTemplateSid,
                                    ObjectMapper objectMapper) {
        if (isBlank(accountSid) || isBlank(authToken) || isBlank(fromNumber)) {
            throw new IllegalStateException("Twilio credentials (twilio.accountSid / authToken / whatsappFrom) are not configured");
        }
        this.twilioClient = new TwilioRestClient.Builder(accountSid, authToken).build();
        this.fromNumber = fromNumber.trim();
        this.documentTemplateSid = documentTemplateSid;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends the "document ready" template (invoice / receipt) to a customer.
     *
     * @return the Twilio message SID
     * @throws WhatsAppDeliveryException if the phone is invalid or Twilio rejects the message
     */
    public String sendDocumentMessage(WhatsAppDocumentMessage message) {
        String recipient = toWhatsAppNumber(message.recipientPhone());

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("1", nullToEmpty(message.customerName()));
        variables.put("2", nullToEmpty(message.documentId()));
        variables.put("3", nullToEmpty(message.amount()));
        variables.put("4", nullToEmpty(message.downloadUrl()));

        try {
            Message sent = Message.creator(
                            new PhoneNumber("whatsapp:" + recipient),
                            new PhoneNumber("whatsapp:" + fromNumber),
                            "") // body comes from the content template
                    .setContentSid(documentTemplateSid)
                    .setContentVariables(objectMapper.writeValueAsString(variables))
                    .create(twilioClient);

            log.info("WhatsApp document message queued: sid={}, document={}, status={}",
                    sent.getSid(), message.documentId(), sent.getStatus());
            return sent.getSid();
        } catch (JsonProcessingException exception) {
            throw new WhatsAppDeliveryException("Could not build WhatsApp template variables", exception);
        } catch (ApiException exception) {
            // Twilio's own error: code + docs link explain the exact reason
            // (e.g. 63007 sender not enabled, 21211 invalid "To", 63016 outside session, ...).
            throw new WhatsAppDeliveryException(String.format(
                    "Twilio rejected WhatsApp message for document %s: HTTP %s, Twilio code %s, %s (more info: %s)",
                    message.documentId(), exception.getStatusCode(), exception.getCode(),
                    exception.getMessage(), exception.getMoreInfo()), exception);
        } catch (RuntimeException exception) {
            throw new WhatsAppDeliveryException(
                    "Could not reach Twilio for document " + message.documentId() + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Normalises a phone number to E.164. A bare 10-digit number is treated as Indian (+91).
     */
    static String toWhatsAppNumber(String phone) {
        if (isBlank(phone)) {
            throw new WhatsAppDeliveryException("Recipient phone number is missing");
        }
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        throw new WhatsAppDeliveryException("Unsupported phone number format for WhatsApp");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
