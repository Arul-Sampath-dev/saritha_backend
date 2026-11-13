package com.saritha.fashions.modules.core.mailing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sendinblue.ApiClient;
import sendinblue.ApiException;
import sendinblue.auth.ApiKeyAuth;
import sibApi.TransactionalEmailsApi;
import sibModel.CreateSmtpEmail;
import sibModel.SendSmtpEmail;
import sibModel.SendSmtpEmailReplyTo;
import sibModel.SendSmtpEmailSender;
import sibModel.SendSmtpEmailTo;

import java.util.List;

/**
 * Sends transactional emails through Brevo (Sendinblue).
 *
 * Unlike the old BrevoEmailServiceForForgotPassword, which is tied to one template,
 * this sends any MailMessage with inline HTML. The API client is created once, and the
 * sender is configurable:
 *   brevo.sender.email (default: fp@sarithasdesignerstudio.com, the existing verified sender)
 *   brevo.sender.name  (default: sarithasdesignerstudio)
 */
@Service
public class BrevoMailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoMailService.class);

    private final TransactionalEmailsApi emailsApi;
    private final String senderEmail;
    private final String senderName;

    public BrevoMailService(@Value("${brevo.api.key}") String apiKey,
                            @Value("${brevo.sender.email:fp@sarithasdesignerstudio.com}") String senderEmail,
                            @Value("${brevo.sender.name:sarithasdesignerstudio}") String senderName) {
        ApiClient apiClient = new ApiClient();
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) apiClient.getAuthentication("api-key");
        apiKeyAuth.setApiKey(apiKey);

        this.emailsApi = new TransactionalEmailsApi(apiClient);
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    /**
     * Sends an email built from a template stored in Brevo (subject and HTML live in Brevo).
     *
     * @param templateId Brevo template id
     * @param params     template parameters, used in the template as {{ params.NAME }}
     * @return Brevo message id
     * @throws MailDeliveryException if Brevo rejects the request
     */
    public String sendTemplate(String toEmail, String toName, long templateId, java.util.Map<String, Object> params) {
        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail(senderEmail);
        sender.setName(senderName);

        SendSmtpEmailReplyTo replyTo = new SendSmtpEmailReplyTo();
        replyTo.setEmail(senderEmail);
        replyTo.setName(senderName);

        SendSmtpEmailTo recipient = new SendSmtpEmailTo();
        recipient.setEmail(toEmail);
        if (toName != null && !toName.isBlank()) {
            recipient.setName(toName);
        }

        SendSmtpEmail email = new SendSmtpEmail();
        email.setSender(sender);
        email.setReplyTo(replyTo);
        email.setTo(List.of(recipient));
        email.setTemplateId(templateId);
        email.setParams(params);

        try {
            CreateSmtpEmail response = emailsApi.sendTransacEmail(email);
            log.info("Template email {} queued with Brevo: messageId={}", templateId, response.getMessageId());
            return response.getMessageId();
        } catch (ApiException exception) {
            throw new MailDeliveryException(String.format(
                    "Brevo rejected template email %d: HTTP %d, response %s",
                    templateId, exception.getCode(), exception.getResponseBody()), exception);
        } catch (Exception exception) {
            throw new MailDeliveryException(
                    "Could not reach Brevo for template email " + templateId + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Sends the email.
     *
     * @return Brevo message id
     * @throws MailDeliveryException if Brevo rejects the request
     */
    public String send(MailMessage message) {
        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail(senderEmail);
        sender.setName(senderName);

        SendSmtpEmailReplyTo replyTo = new SendSmtpEmailReplyTo();
        replyTo.setEmail(senderEmail);
        replyTo.setName(senderName);

        SendSmtpEmailTo recipient = new SendSmtpEmailTo();
        recipient.setEmail(message.toEmail());
        if (message.toName() != null && !message.toName().isBlank()) {
            recipient.setName(message.toName());
        }

        SendSmtpEmail email = new SendSmtpEmail();
        email.setSender(sender);
        email.setReplyTo(replyTo);
        email.setTo(List.of(recipient));
        email.setSubject(message.subject());
        email.setHtmlContent(message.htmlContent());

        try {
            CreateSmtpEmail response = emailsApi.sendTransacEmail(email);
            log.info("Email queued with Brevo: messageId={}, subject={}", response.getMessageId(), message.subject());
            return response.getMessageId();
        } catch (ApiException exception) {
            // Brevo's own error: HTTP code + JSON body ({"code": "...", "message": "..."}) explain the reason
            // (e.g. 401 unauthorized / unrecognised IP, 400 invalid sender or recipient).
            throw new MailDeliveryException(String.format(
                    "Brevo rejected email '%s': HTTP %d, response %s",
                    message.subject(), exception.getCode(), exception.getResponseBody()), exception);
        } catch (Exception exception) {
            throw new MailDeliveryException(
                    "Could not reach Brevo for email '" + message.subject() + "': " + exception.getMessage(), exception);
        }
    }
}
