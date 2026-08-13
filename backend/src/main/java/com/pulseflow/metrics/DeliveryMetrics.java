package com.pulseflow.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMetrics {
    private final MeterRegistry meterRegistry;

    public DeliveryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void success(String channel) {
        counter("pulseflow.delivery.success", channel).increment();
    }

    public void failure(String channel) {
        counter("pulseflow.delivery.failure", channel).increment();
    }

    public void skipped(String channel) {
        counter("pulseflow.delivery.skipped", channel).increment();
    }

    public void retry(String channel) {
        counter("pulseflow.delivery.retry", channel).increment();
    }

    public void deadLettered(String channel) {
        counter("pulseflow.delivery.dead_lettered", channel).increment();
    }

    private Counter counter(String name, String channel) {
        return Counter.builder(name)
                .tag("channel", channel == null ? "UNKNOWN" : channel)
                .register(meterRegistry);
    }
}
