package com.saritha.fashions.modules.core.mailing;

/**
 * A transactional email with inline HTML content.
 *
 * @param toEmail     recipient email address
 * @param toName      recipient display name (optional)
 * @param subject     email subject
 * @param htmlContent email body as HTML (values must already be HTML-escaped)
 */
public record MailMessage(String toEmail, String toName, String subject, String htmlContent) {
}
