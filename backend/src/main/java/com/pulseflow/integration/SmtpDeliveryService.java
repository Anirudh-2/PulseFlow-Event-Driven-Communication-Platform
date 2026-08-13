package com.pulseflow.integration;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpDeliveryService {

    public void sendHtml(Map<String, Object> smtpConfig, String toEmail, String subject, String htmlBody) {
        String host = stringVal(smtpConfig.get("host"));
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("SMTP config missing host");
        }
        String from = firstNonBlank(stringVal(smtpConfig.get("from_email")), stringVal(smtpConfig.get("from")));
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("SMTP config missing from_email");
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        int port = Optional.ofNullable(smtpConfig.get("port"))
                .map(v -> Integer.parseInt(v.toString()))
                .orElseThrow(() -> new IllegalStateException("SMTP channel config missing required field: port"));
        sender.setPort(port);
        sender.setUsername(stringVal(smtpConfig.get("username")));
        sender.setPassword(stringVal(smtpConfig.get("password")));
        boolean useTls = Optional.ofNullable(smtpConfig.get("useTls"))
                .or(() -> Optional.ofNullable(smtpConfig.get("startTls")))
                .map(v -> Boolean.parseBoolean(v.toString()))
                .orElseThrow(
                        () -> new IllegalStateException("SMTP channel config missing required field: useTls"));

        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(!isBlank(sender.getUsername())));
        props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
        sender.setJavaMailProperties(props);

        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            sender.send(message);
        } catch (Exception ex) {
            throw new IllegalStateException("SMTP delivery failed: " + ex.getMessage(), ex);
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) {
            return a;
        }
        return b;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
