package com.saritha.fashions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sendinblue.ApiClient;
import sendinblue.auth.ApiKeyAuth;
import sibApi.TransactionalEmailsApi;
import sibModel.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailServiceForForgotPassword {

    @Value("${brevo.api.key}")
    private String apiKey;

    public void sendEmail(String toEmail, String toName, Integer otp) throws Exception {
        // Initialize the Brevo API Client
        ApiClient defaultClient = new ApiClient();

        // Configure API key authorization
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
        apiKeyAuth.setApiKey(apiKey);

        // Create an instance of TransactionalEmailsApi
        TransactionalEmailsApi apiInstance = new TransactionalEmailsApi(defaultClient);

        // Sender information
        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail("fp@sarithasdesignerstudio.com");
        sender.setName("sarithasdesignerstudio");

        // ReplyTo Email Set
        SendSmtpEmailReplyTo replyTo = new SendSmtpEmailReplyTo();
        replyTo.setEmail("fp@sarithasdesignerstudio.com");
        replyTo.setName("sarithasdesignerstudio");

        // Recipient information
        SendSmtpEmailTo recipient = new SendSmtpEmailTo();
        recipient.setEmail(toEmail);
        recipient.setName(toName);
        List<SendSmtpEmailTo> toList = new ArrayList<>();
        toList.add(recipient);

        // Set template parameters
        Map<String, Object> params = new HashMap<>();
        params.put("USER_NAME", toName);
        params.put("OTP_CODE", otp);
        params.put("EMAIL", toEmail);

        // Create the transactional email object
        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();
        sendSmtpEmail.setSender(sender);
        sendSmtpEmail.setTo(toList);
        sendSmtpEmail.setTemplateId(1L);
        sendSmtpEmail.setParams(params);
        sendSmtpEmail.setReplyTo(replyTo);

        try {
            // Send the email
            CreateSmtpEmail response = apiInstance.sendTransacEmail(sendSmtpEmail);
            System.out.println("Email sent successfully: " + response.toString());
        } catch (Exception e) {
            System.err.println("Exception while sending email: " + e.getMessage() + "toMail" + toEmail);
            throw e;
        }
    }
}

