package com.saritha.fashions.service;

import com.saritha.fashions.model.Receipt;
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

    public void sendInvoice(Receipt receipt) {
        Twilio.init(accountSid, authToken);

        String messageBody = "Hello " + receipt.getCustomer().getName() + ", your receipt is ready. Total: " + receipt.getTotalAmount() + ". View here: " + receipt.getReceiptUrl();

        Message.creator(
                new com.twilio.type.PhoneNumber("whatsapp:" + receipt.getCustomer().getPhone()),
                new com.twilio.type.PhoneNumber("whatsapp:" + whatsappFrom),
                messageBody
        ).create();
    }
}

