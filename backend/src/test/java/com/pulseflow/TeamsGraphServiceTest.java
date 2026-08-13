package com.pulseflow;

import com.pulseflow.integration.TeamsGraphService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class TeamsGraphServiceTest {

    @Test
    void givenBlankClientId_whenSendMessage_thenSkipsGracefully() {
        var service = new TeamsGraphService("", "secret", "tenantId", "botUserId");
        assertThatCode(() -> service.sendMessage("user@company.com", "Hello"))
                .doesNotThrowAnyException();
    }

    @Test
    void givenBlankUserTarget_whenSendMessage_thenSkipsGracefully() {
        var service = new TeamsGraphService("client-id", "secret", "tenantId", "botUserId");
        assertThatCode(() -> service.sendMessage("", "Hello"))
                .doesNotThrowAnyException();
    }

    @Test
    void givenNullUserTarget_whenSendMessage_thenSkipsGracefully() {
        var service = new TeamsGraphService("client-id", "secret", "tenantId", "botUserId");
        assertThatCode(() -> service.sendMessage(null, "Hello"))
                .doesNotThrowAnyException();
    }
}
