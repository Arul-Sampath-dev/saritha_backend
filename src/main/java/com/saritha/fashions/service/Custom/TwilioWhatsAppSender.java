package com.saritha.fashions.service.Custom;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioWhatsAppSender {

    @Value("${twilio.accountSid}")
    private String ACCOUNT_SID;

    @Value("${twilio.authToken}")
    private String AUTH_TOKEN ;

    @Value("${twilio.whatsappFrom}")
    private String TWILIO_WHATSAPP_NUMBER;

    public void sendWhatsAppMessage(String recipientNumber, String messageBody) {
        try {
            // Verify credentials are loaded
            if (ACCOUNT_SID == null || AUTH_TOKEN == null) {
                throw new IllegalStateException("Twilio credentials not configured properly");
            }

            // Initialize Twilio client
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);

            // Create and send message
            Message message = Message.creator(
                    new PhoneNumber("whatsapp:" + recipientNumber),
                    new PhoneNumber("whatsapp:" + TWILIO_WHATSAPP_NUMBER),
                    messageBody
            ).create();

            // Log success information
            System.out.println("WhatsApp message sent successfully!");
            System.out.println("Message SID: " + message.getSid());
            System.out.println("Recipient: " + recipientNumber);
            System.out.println("Status: " + message.getStatus());

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
        sendWhatsAppMessage(customerNumber, message);
    }
}