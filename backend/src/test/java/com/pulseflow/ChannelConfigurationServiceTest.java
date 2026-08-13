package com.pulseflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pulseflow.domain.entity.ChannelConfiguration;
import com.pulseflow.domain.entity.IntegrationSource;
import com.pulseflow.repository.ChannelConfigurationRepository;
import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.service.ChannelConfigurationService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChannelConfigurationServiceTest {
    @Test
    void shouldCreateChannelConfigurationUsingLowercaseChannelType() {
        ChannelConfigurationRepository repository = Mockito.mock(ChannelConfigurationRepository.class);
        IntegrationSourceRepository integrationSourceRepository = Mockito.mock(IntegrationSourceRepository.class);
        ChannelConfigurationService service = new ChannelConfigurationService(repository, integrationSourceRepository);

        UUID appId = UUID.randomUUID();
        var app = new IntegrationSource();
        app.setId(appId);
        app.setTenantId("default");
        when(integrationSourceRepository.findById(appId)).thenReturn(Optional.of(app));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create("default", appId, "SMTP", Map.of("host", "smtp.local"), true);

        assertEquals("smtp", created.getChannelType());
        assertEquals("default", created.getTenantId());
    }

    @Test
    void shouldRejectUnknownChannelType() {
        ChannelConfigurationRepository repository = Mockito.mock(ChannelConfigurationRepository.class);
        IntegrationSourceRepository integrationSourceRepository = Mockito.mock(IntegrationSourceRepository.class);
        ChannelConfigurationService service = new ChannelConfigurationService(repository, integrationSourceRepository);

        UUID appId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                service.create("default", appId, "email", Map.of(), true));
    }

    @Test
    void shouldFailWebhookTestWhenUrlMissing() {
        ChannelConfigurationRepository repository = Mockito.mock(ChannelConfigurationRepository.class);
        IntegrationSourceRepository integrationSourceRepository = Mockito.mock(IntegrationSourceRepository.class);
        ChannelConfigurationService service = new ChannelConfigurationService(repository, integrationSourceRepository);

        var cfg = new ChannelConfiguration();
        cfg.setChannelType("webhook");
        cfg.setConfigJson(Map.of());
        var result = service.testConnection(cfg);

        assertEquals(false, result.get("ok"));
    }
}
