package com.pulseflow.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeliveryJobPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final String retry5sRoutingKey;
    private final String retry30sRoutingKey;
    private final String retry5mRoutingKey;

    public DeliveryJobPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.exchange}") String exchange,
            @Value("${app.messaging.delivery-routing-key:pulseflow.delivery.job}") String routingKey,
            @Value("${app.messaging.delivery-retry-5s-routing-key:pulseflow.delivery.retry.5s}") String retry5sRoutingKey,
            @Value("${app.messaging.delivery-retry-30s-routing-key:pulseflow.delivery.retry.30s}") String retry30sRoutingKey,
            @Value("${app.messaging.delivery-retry-5m-routing-key:pulseflow.delivery.retry.5m}") String retry5mRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.retry5sRoutingKey = retry5sRoutingKey;
        this.retry30sRoutingKey = retry30sRoutingKey;
        this.retry5mRoutingKey = retry5mRoutingKey;
    }

    public void publish(DeliveryJobMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

    public void publishRetry5s(DeliveryJobMessage message) {
        rabbitTemplate.convertAndSend(exchange, retry5sRoutingKey, message);
    }

    public void publishRetry30s(DeliveryJobMessage message) {
        rabbitTemplate.convertAndSend(exchange, retry30sRoutingKey, message);
    }

    public void publishRetry5m(DeliveryJobMessage message) {
        rabbitTemplate.convertAndSend(exchange, retry5mRoutingKey, message);
    }
}
