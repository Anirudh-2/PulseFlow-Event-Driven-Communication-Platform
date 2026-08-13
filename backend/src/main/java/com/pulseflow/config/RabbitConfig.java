package com.pulseflow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;

@Configuration
public class RabbitConfig {
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }

    @Bean(name = "rabbitHandlerMessageConverter")
    MappingJackson2MessageConverter rabbitHandlerMessageConverter() {
        return new MappingJackson2MessageConverter();
    }

    @Bean
    DirectExchange notifExchange(@Value("${app.messaging.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName);
    }

    @Bean
    Queue eventsDlq(@Value("${app.messaging.dlq}") String dlqName) {
        return new Queue(dlqName, true);
    }

    @Bean
    Queue eventsQueue(
            @Value("${app.messaging.queue}") String queueName,
            @Value("${app.messaging.dlq}") String dlqName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", dlqName)
                .build();
    }

    @Bean
    Binding eventsBinding(
            Queue eventsQueue,
            DirectExchange notifExchange,
            @Value("${app.messaging.routing-key}") String routingKey) {
        return BindingBuilder.bind(eventsQueue).to(notifExchange).with(routingKey);
    }

    @Bean
    Queue deliveryDlq(@Value("${app.messaging.delivery-dlq}") String deliveryDlqName) {
        return new Queue(deliveryDlqName, true);
    }

    @Bean
    Queue deliveryQueue(
            @Value("${app.messaging.delivery-queue}") String deliveryQueueName,
            @Value("${app.messaging.delivery-dlq}") String deliveryDlqName) {
        return QueueBuilder.durable(deliveryQueueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", deliveryDlqName)
                .build();
    }

    @Bean
    Binding deliveryBinding(
            Queue deliveryQueue,
            DirectExchange notifExchange,
            @Value("${app.messaging.delivery-routing-key}") String deliveryRoutingKey) {
        return BindingBuilder.bind(deliveryQueue).to(notifExchange).with(deliveryRoutingKey);
    }

    @Bean
    Queue deliveryRetry5sQueue(
            @Value("${app.messaging.delivery-retry-5s-queue}") String queueName,
            @Value("${app.messaging.exchange}") String exchange,
            @Value("${app.messaging.delivery-routing-key}") String routingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", 5000)
                .withArgument("x-dead-letter-exchange", exchange)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build();
    }

    @Bean
    Queue deliveryRetry30sQueue(
            @Value("${app.messaging.delivery-retry-30s-queue}") String queueName,
            @Value("${app.messaging.exchange}") String exchange,
            @Value("${app.messaging.delivery-routing-key}") String routingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", 30000)
                .withArgument("x-dead-letter-exchange", exchange)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build();
    }

    @Bean
    Queue deliveryRetry5mQueue(
            @Value("${app.messaging.delivery-retry-5m-queue}") String queueName,
            @Value("${app.messaging.exchange}") String exchange,
            @Value("${app.messaging.delivery-routing-key}") String routingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", 300000)
                .withArgument("x-dead-letter-exchange", exchange)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build();
    }

    @Bean
    Binding deliveryRetry5sBinding(
            Queue deliveryRetry5sQueue,
            DirectExchange notifExchange,
            @Value("${app.messaging.delivery-retry-5s-routing-key}") String routingKey) {
        return BindingBuilder.bind(deliveryRetry5sQueue).to(notifExchange).with(routingKey);
    }

    @Bean
    Binding deliveryRetry30sBinding(
            Queue deliveryRetry30sQueue,
            DirectExchange notifExchange,
            @Value("${app.messaging.delivery-retry-30s-routing-key}") String routingKey) {
        return BindingBuilder.bind(deliveryRetry30sQueue).to(notifExchange).with(routingKey);
    }

    @Bean
    Binding deliveryRetry5mBinding(
            Queue deliveryRetry5mQueue,
            DirectExchange notifExchange,
            @Value("${app.messaging.delivery-retry-5m-routing-key}") String routingKey) {
        return BindingBuilder.bind(deliveryRetry5mQueue).to(notifExchange).with(routingKey);
    }
}
