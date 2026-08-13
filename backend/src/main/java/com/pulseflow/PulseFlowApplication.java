package com.pulseflow;

import com.pulseflow.config.KeycloakBootstrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(KeycloakBootstrapProperties.class)
public class PulseFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(PulseFlowApplication.class, args);
    }
}
