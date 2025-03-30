package com.saritha.fashions.service;

import com.saritha.fashions.model.Invoice;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppService {

    @Value("${twilio.accountSid}")
    private String accountSid;

    @Value("${twilio.authToken}")
    private String authToken;

    @Value("${twilio.whatsappFrom}")
    private String whatsappFrom;

    public void sendInvoice(Invoice invoice) {
        Twilio.init(accountSid, authToken);

        String messageBody = "Hello " + invoice.getCustomer().getName() + ", your invoice is ready. Total: " + invoice.getTotalAmount() + ". View here: " + invoice.getInvoiceUrl();

        Message.creator(
                new com.twilio.type.PhoneNumber("whatsapp:" + invoice.getCustomer().getPhone()),
                new com.twilio.type.PhoneNumber("whatsapp:" + whatsappFrom),
                messageBody
        ).create();
    }
}

