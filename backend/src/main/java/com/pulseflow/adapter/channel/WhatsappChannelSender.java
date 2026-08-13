package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelException;
import com.pulseflow.domain.port.ChannelSender;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsappChannelSender implements ChannelSender {
    private static final Logger log = LoggerFactory.getLogger(WhatsappChannelSender.class);

    private final String defaultAccountSid;
    private final String defaultAuthToken;
    private final String defaultFrom;
    private volatile boolean twilioInitialized;

    public WhatsappChannelSender(
            @Value("${app.integrations.twilioSid:}") String defaultAccountSid,
            @Value("${app.integrations.twilioToken:}") String defaultAuthToken,
            @Value("${app.integrations.twilioWhatsappFrom:}") String defaultFrom) {
        this.defaultAccountSid = defaultAccountSid;
        this.defaultAuthToken = defaultAuthToken;
        this.defaultFrom = defaultFrom;
    }

    @PostConstruct
    void initTwilio() {
        if (isBlank(defaultAccountSid) || isBlank(defaultAuthToken)) {
            return;
        }
        com.twilio.Twilio.init(defaultAccountSid, defaultAuthToken);
        twilioInitialized = true;
    }

    @Override
    public String channelTypeCode() {
        return "WHATSAPP";
    }

    @Override
    public void send(DeliveryContext context) throws ChannelException {
        Map<String, Object> cfg = context.channelConfig();
        String sid = str(cfg.get("accountSid"), defaultAccountSid);
        String token = str(cfg.get("authToken"), defaultAuthToken);
        String from = str(cfg.get("whatsappFrom"), defaultFrom);
        String to = resolveDestinationPhone(context);
        if (isBlank(to)) {
            throw new IllegalStateException("Skipped: no phone number available for recipient");
        }
        if (isBlank(sid) || isBlank(token) || isBlank(from)) {
            throw new IllegalStateException("Skipped: Twilio WhatsApp is not fully configured");
        }
        if (!twilioInitialized) {
            synchronized (this) {
                if (!twilioInitialized) {
                    com.twilio.Twilio.init(sid, token);
                    twilioInitialized = true;
                }
            }
        }
        String text = context.renderedBody() != null && !context.renderedBody().isBlank()
                ? context.renderedBody()
                : context.notification().getBody();
        try {
            Message.creator(new PhoneNumber("whatsapp:" + normalizeWhatsApp(to)), new PhoneNumber("whatsapp:" + normalizeFrom(from)), text)
                    .create();
        } catch (Exception e) {
            throw new ChannelException("WhatsApp delivery failed", e);
        }
    }

    private static String normalizeWhatsApp(String to) {
        String t = to.replace("whatsapp:", "").trim();
        return t.startsWith("+") ? t : "+" + t;
    }

    private static String normalizeFrom(String from) {
        return from.replace("whatsapp:", "").trim();
    }

    private static String str(Object o, String fallback) {
        if (o == null) {
            return fallback;
        }
        String s = o.toString();
        return s.isBlank() ? fallback : s;
    }

    private static String resolveDestinationPhone(DeliveryContext context) {
        Map<String, Object> cfg = context.channelConfig();
        if (cfg != null) {
            String fromConfig = str(cfg.get("toPhoneNumber"), null);
            if (isBlank(fromConfig)) {
                fromConfig = str(cfg.get("phone"), null);
            }
            if (!isBlank(fromConfig)) {
                return fromConfig;
            }
        }

        Map<String, Object> payload = context.notification().getMetadata();
        if (payload != null) {
            String[] keys = {"toPhoneNumber", "phone", "mobile", "whatsappPhone", "userPhone"};
            for (String key : keys) {
                String value = str(payload.get(key), null);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }

        String userId = context.recipient().getUserId();
        if (!isBlank(userId) && userId.trim().startsWith("+")) {
            return userId.trim();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
