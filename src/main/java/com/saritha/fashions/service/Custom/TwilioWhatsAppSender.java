package com.saritha.fashions.service.Custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TwilioWhatsAppSender {

    @Value("${twillo.website.link}")
    private String websiteLink;

    @Value("${twilio.accountSid}")
    private String ACCOUNT_SID;

    @Value("${twilio.authToken}")
    private String AUTH_TOKEN ;

    @Value("${twilio.whatsappFrom}")
    private String TWILIO_WHATSAPP_NUMBER;

    public void sendWhatsAppMessage(String recipientNumber, String messageType, String invoiceId) {
        try {
            // Verify credentials are loaded
            if (ACCOUNT_SID == null || AUTH_TOKEN == null) {
                throw new IllegalStateException("Twilio credentials not configured properly");
            }

            // Initialize Twilio client
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);

            Map<String, String> contentVariables = new HashMap<>();

            if (messageType.equals("Invoice")) {
                contentVariables.put("1", messageType);
                contentVariables.put("2", websiteLink + "/download-invoice/"+ invoiceId);
            } else {
                contentVariables.put("1", messageType);
                contentVariables.put("2", websiteLink + "/download-receipt/"+ invoiceId);
            }


            String whatappMessage = new ObjectMapper().writeValueAsString(contentVariables);

            // Create and send message
            Message message = Message.creator(
                    new PhoneNumber("whatsapp:" + recipientNumber),
                    new PhoneNumber("whatsapp:" + TWILIO_WHATSAPP_NUMBER),
                            "" // Body is left empty, as the content variables are used
                    ).setContentSid("HXaf344c13c6065746cbd7eee0725808df")
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

    // Helper method for order placement notifications
    public void sendOrderConfirmation(String customerNumber, String orderId, String invoiceUrl) {
        String message = "Thank you for your order! 🎉\n"
                + "Your order ID: " + orderId + "\n"
                + "Download your invoice here: " + invoiceUrl + "\n"
                + "We appreciate your decision for choosing us!";

        System.out.println("Sending order confirmation to: " + customerNumber);
        sendWhatsAppMessage(customerNumber, message, orderId);
    }
}