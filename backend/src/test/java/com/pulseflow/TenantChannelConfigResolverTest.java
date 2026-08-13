package com.pulseflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.pulseflow.adapter.channel.TenantChannelConfigResolver;
import com.pulseflow.domain.entity.ChannelConfiguration;
import com.pulseflow.repository.ChannelConfigurationRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TenantChannelConfigResolverTest {
    @Test
    void shouldMapEmailChannelToSmtpConfigurationType() {
        ChannelConfigurationRepository repository = Mockito.mock(ChannelConfigurationRepository.class);
        TenantChannelConfigResolver resolver = new TenantChannelConfigResolver(repository);

        UUID appId = UUID.randomUUID();
        ChannelConfiguration cfg = new ChannelConfiguration();
        cfg.setConfigJson(Map.of("host", "smtp.example.com"));

        when(repository.findFirstByTenantIdAndApp_IdAndChannelTypeAndIsActiveTrueOrderByCreatedAtDesc(
                        "default", appId, "smtp"))
                .thenReturn(Optional.of(cfg));

        Map<String, Object> out = resolver.resolve("default", appId, "EMAIL", null);
        assertEquals("smtp.example.com", out.get("host"));
    }
}
