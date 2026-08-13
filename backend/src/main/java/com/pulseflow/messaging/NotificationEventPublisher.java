package com.pulseflow.messaging;

import com.pulseflow.dto.CreateEventRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public NotificationEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.exchange}") String exchange,
            @Value("${app.messaging.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(CreateEventRequest request) {
        rabbitTemplate.convertAndSend(exchange, routingKey, request);
    }
}
