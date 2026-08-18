package com.weblogs.blog.auth;

import com.weblogs.blog.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @Async
    public void sendVerificationEmail(String to, String rawToken) {
        String link = appProperties.getFrontendUrl() + "/verify-email?token=" + rawToken;
        String body = """
                Welcome to Weblogs!
                
                Please verify your email address by clicking the link below:
                
                %s
                
                This link expires in 24 hours.
                
                If you didn't create an account, you can safely ignore this email.
                """.formatted(link);

        send(to, "Verify your email address", body);
    }

    @Async
    public void sendPasswordResetEmail(String to, String rawToken) {
        String link = appProperties.getFrontendUrl() + "/reset-password?token=" + rawToken;
        String body = """
                You requested a password reset for your Weblogs account.
                
                Click the link below to reset your password:
                
                %s
                
                This link expires in 1 hour.
                
                If you didn't request this, you can safely ignore this email.
                """.formatted(link);

        send(to, "Reset your password", body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(appProperties.getMail().getFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.debug("Email sent to: {}, subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            // Don't rethrow — email failure shouldn't break the request
        }
    }
}
