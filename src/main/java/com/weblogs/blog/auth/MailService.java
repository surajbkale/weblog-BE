package com.weblogs.blog.auth;

import com.weblogs.blog.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final AppProperties  appProperties;

    // ── Verification ──────────────────────────────────────────────────────────

    @Async
    public void sendVerificationEmail(String to, String rawToken) {
        String link = appProperties.getFrontendUrl() + "/verify-email?token=" + rawToken;

        String subject = "Verify your email — Weblogs";

        String html = emailTemplate(
                "Confirm your email address",
                "Thanks for signing up! Click the button below to verify your email address and activate your account.",
                link,
                "Verify Email Address",
                "This link expires in <strong>24 hours</strong>. If you didn't create a Weblogs account, you can safely ignore this email."
        );

        String text = """
                Welcome to Weblogs!

                Please verify your email address by visiting the link below:

                %s

                This link expires in 24 hours.
                If you didn't create an account, you can safely ignore this email.
                """.formatted(link);

        send(to, subject, html, text);
    }

    // ── Password reset ────────────────────────────────────────────────────────

    @Async
    public void sendPasswordResetEmail(String to, String rawToken) {
        String link = appProperties.getFrontendUrl() + "/reset-password?token=" + rawToken;

        String subject = "Reset your password — Weblogs";

        String html = emailTemplate(
                "Reset your password",
                "We received a request to reset the password for your Weblogs account. Click the button below to choose a new password.",
                link,
                "Reset Password",
                "This link expires in <strong>1 hour</strong>. If you didn't request a password reset, you can safely ignore this email — your password won't change."
        );

        String text = """
                You requested a password reset for your Weblogs account.

                Click the link below to reset your password:

                %s

                This link expires in 1 hour.
                If you didn't request this, you can safely ignore this email.
                """.formatted(link);

        send(to, subject, html, text);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sends a multi-part email (HTML + plain-text fallback).
     * Using {@link MimeMessageHelper} instead of {@link org.springframework.mail.SimpleMailMessage}
     * lets us attach both a rich HTML body and a plain-text alternative in a single MIME message.
     * Mail clients that can't render HTML (or respect user preferences) fall back to the text part.
     */
    private void send(String to, String subject, String html, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true enables both HTML + text parts in one message
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appProperties.getMail().getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            // setText(html, text, isHtml) — Spring sets HTML as primary, text as alternative
            helper.setText(text, html);
            mailSender.send(message);
            log.debug("Email sent to: {}, subject: {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to: {}, subject: {}", to, subject, e);
            // Don't rethrow — email failure shouldn't break the request flow
        }
    }

    /**
     * Builds a self-contained branded HTML email.
     *
     * <p>All CSS is inlined — external stylesheets are stripped by most mail clients
     * (Gmail, Outlook, Apple Mail). The layout uses a simple single-column table so
     * it renders correctly in clients that don't support modern CSS (Outlook 2016+ uses
     * Word's rendering engine which ignores flexbox/grid).
     *
     * @param heading     large title text at the top of the card
     * @param body        descriptive sentence(s) below the heading
     * @param actionUrl   the CTA button link
     * @param actionLabel the CTA button label
     * @param footer      small-print text below the button (use &lt;strong&gt; for emphasis)
     */
    private static String emailTemplate(String heading, String body,
                                        String actionUrl, String actionLabel,
                                        String footer) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" role="presentation"
                         style="background-color:#f4f4f5;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <!-- Card -->
                        <table width="560" cellpadding="0" cellspacing="0" role="presentation"
                               style="max-width:560px;width:100%%;background:#ffffff;border-radius:12px;
                                      box-shadow:0 1px 3px rgba(0,0,0,.08);overflow:hidden;">

                          <!-- Brand header -->
                          <tr>
                            <td style="background:linear-gradient(135deg,#3b82f6 0%%,#6366f1 100%%);
                                        padding:28px 40px;text-align:center;">
                              <span style="font-size:22px;font-weight:700;color:#ffffff;
                                           letter-spacing:-0.5px;">✍ Weblogs</span>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:40px 40px 0;">
                              <h1 style="margin:0 0 16px;font-size:24px;font-weight:700;
                                          color:#111827;line-height:1.3;">%s</h1>
                              <p style="margin:0 0 28px;font-size:16px;color:#4b5563;
                                         line-height:1.6;">%s</p>

                              <!-- CTA button -->
                              <table cellpadding="0" cellspacing="0" role="presentation">
                                <tr>
                                  <td style="border-radius:8px;background:linear-gradient(135deg,#3b82f6 0%%,#6366f1 100%%);">
                                    <a href="%s"
                                       style="display:inline-block;padding:14px 32px;
                                              font-size:15px;font-weight:600;color:#ffffff;
                                              text-decoration:none;border-radius:8px;
                                              letter-spacing:0.2px;">%s</a>
                                  </td>
                                </tr>
                              </table>

                              <!-- Fallback URL -->
                              <p style="margin:20px 0 0;font-size:13px;color:#9ca3af;">
                                If the button doesn't work, copy and paste this link into your browser:<br>
                                <a href="%s" style="color:#6366f1;word-break:break-all;">%s</a>
                              </p>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="padding:28px 40px 32px;">
                              <hr style="border:none;border-top:1px solid #e5e7eb;margin:0 0 20px;">
                              <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">%s</p>
                            </td>
                          </tr>

                        </table>

                        <!-- Legal footer -->
                        <p style="margin:20px 0 0;font-size:12px;color:#9ca3af;text-align:center;">
                          &copy; 2025 Weblogs. All rights reserved.
                        </p>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(heading, heading, body, actionUrl, actionLabel,
                              actionUrl, actionUrl, footer);
    }
}
