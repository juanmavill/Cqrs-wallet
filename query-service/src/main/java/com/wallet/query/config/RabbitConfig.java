package com.wallet.query.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${wallet.exchange}")
    private String exchangeName;

    @Value("${wallet.queue.balance-updated}")
    private String queueName;

    @Value("${wallet.routing-key.balance-updated}")
    private String routingKey;

    @Value("${wallet.exchange.dlx}")
    private String dlxName;

    @Value("${wallet.queue.balance-updated.dlq}")
    private String dlqName;

    @Value("${wallet.routing-key.balance-updated.dlq}")
    private String dlqRoutingKey;

    @Bean
    public DirectExchange walletExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange walletDlxExchange() {
        return new DirectExchange(dlxName, true, false);
    }

    @Bean
    public Queue balanceUpdatedQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue balanceUpdatedDlq() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding balanceUpdatedBinding(Queue balanceUpdatedQueue, DirectExchange walletExchange) {
        return BindingBuilder.bind(balanceUpdatedQueue).to(walletExchange).with(routingKey);
    }

    @Bean
    public Binding balanceUpdatedDlqBinding(Queue balanceUpdatedDlq, DirectExchange walletDlxExchange) {
        return BindingBuilder.bind(balanceUpdatedDlq).to(walletDlxExchange).with(dlqRoutingKey);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
