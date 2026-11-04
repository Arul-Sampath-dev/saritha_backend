package com.saritha.fashions.service.Custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saritha.fashions.service.CustomerDocumentTokenService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

@Service
public class TwilioWhatsAppSender {

    private static final Logger logger = LoggerFactory.getLogger(TwilioWhatsAppSender.class);

    @Autowired
    private CustomerDocumentTokenService customerDocumentTokenService;

    @Value("${twillo.website.link}")
    private String websiteLink;

    @Value("${twilio.accountSid}")
    private String ACCOUNT_SID;

    @Value("${twilio.authToken}")
    private String AUTH_TOKEN ;

    @Value("${twilio.whatsappFrom}")
    private String TWILIO_WHATSAPP_NUMBER;

    @Value("${customer.document-link.debug-output:false}")
    private boolean debugCustomerDocumentLinks;

    @Value("${customer.document-link.debug-file:customer-document-links.debug.txt}")
    private String customerDocumentLinkDebugFile;

    public void sendWhatsAppMessage(String recipientNumber, String messageType, String invoiceId, WhatsAppVariable template) {
        try {
            // Verify credentials are loaded
            if (ACCOUNT_SID == null || AUTH_TOKEN == null) {
                throw new IllegalStateException("Twilio credentials not configured properly");
            }

            // Initialize Twilio client
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);

            Map<String, String> contentVariables = new HashMap<>();

            if (messageType.equals("Invoice")) {
                String token = customerDocumentTokenService.createToken("invoice", template.getDocumentId());
                contentVariables.put("1", template.getCustomerName());
                contentVariables.put("2", template.getDocumentId());
                contentVariables.put("3", template.getAmount());
                String downloadUrl = websiteLink + "/download-invoice/" + token;
                contentVariables.put("4", downloadUrl);
                logLinkForLocalTesting("invoice", downloadUrl);
            } else {
                String token = customerDocumentTokenService.createToken("receipt", template.getDocumentId());
                contentVariables.put("1", template.getCustomerName());
                contentVariables.put("2", template.getDocumentId());
                contentVariables.put("3", template.getAmount());
                String downloadUrl = websiteLink + "/download-receipt/" + token;
                contentVariables.put("4", downloadUrl);
                logLinkForLocalTesting("receipt", downloadUrl);
            }

            String whatappMessage = new ObjectMapper().writeValueAsString(contentVariables);

            // Create and send message
            Message message = Message.creator(
                    new PhoneNumber("whatsapp:" + recipientNumber),
                    new PhoneNumber("whatsapp:" + TWILIO_WHATSAPP_NUMBER),
                            "" // Body is left empty, as the content variables are used
                    ).setContentSid("HXe6f4ff67cb16b5e9de82d3f5c0a5296e")
                    .setContentVariables(whatappMessage)
                    .create();

            // Log success information
            System.out.println("WhatsApp message sent successfully!");
            System.out.println("Message SID: " + message.getSid());
            System.out.println("Recipient: " + recipientNumber);
            System.out.println("Status: " + message.getStatus());
            System.out.println("The body of the message is: \n" + message.getBody());

        } catch (Exception e) {
            System.err.println("Error sending WhatsApp message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logLinkForLocalTesting(String documentType, String url) {
        if (!debugCustomerDocumentLinks) {
            return;
        }
        logger.info("DEBUG customer {} download URL: {}", documentType, url);
        try {
            Files.writeString(Path.of(customerDocumentLinkDebugFile), url + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.info("Customer download URL appended to {}", customerDocumentLinkDebugFile);
        } catch (IOException e) {
            logger.error("Could not append customer download URL to debug file {}", customerDocumentLinkDebugFile, e);
        }
    }

    // Helper method for order placement notifications
    public void sendOrderConfirmation(String customerNumber, String orderId, String invoiceUrl) {
        String message = "Thank you for your order! 🎉\n"
                + "Your order ID: " + orderId + "\n"
                + "Download your invoice here: " + invoiceUrl + "\n"
                + "We appreciate your decision for choosing us!";

        System.out.println("Sending order confirmation to: " + customerNumber);
//        sendWhatsAppMessage(customerNumber, message, orderId);
    }
}
