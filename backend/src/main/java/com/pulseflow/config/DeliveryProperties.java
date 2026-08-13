package com.pulseflow.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.delivery")
public class DeliveryProperties {
    private int maxAttempts = 4;
    private String defaultChannel = "WEBSOCKET";
    private String defaultLocale = "en";
    private RetryDelays retry = new RetryDelays();

    @Getter
    @Setter
    public static class RetryDelays {
        private int delay1Seconds = 5;
        private int delay2Seconds = 30;
        private int delay3Seconds = 300;
    }
}
