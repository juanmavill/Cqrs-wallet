package com.wallet.command.messaging;

import com.wallet.command.event.BalanceUpdatedEvent;
import com.wallet.command.exception.BrokerUnavailableException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public EventPublisher(RabbitTemplate rabbitTemplate,
                          @Value("${wallet.exchange}") String exchange,
                          @Value("${wallet.routing-key.balance-updated}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(BalanceUpdatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.debug("Published BalanceUpdatedEvent for account {}", event.getAccountId());
        } catch (AmqpException ex) {
            log.error("RabbitMQ unavailable while publishing event for {}: {}",
                    event.getAccountId(), ex.getMessage());
            throw new BrokerUnavailableException("Failed to publish event to RabbitMQ", ex);
        }
    }
}
