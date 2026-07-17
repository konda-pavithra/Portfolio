package com.example.thresholdservice.config;

import org.springframework.amqp.core.*;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class RabbitMQConfig {

    // ── Queue / Exchange / Routing-key names (public for use in other beans) ──

    public static final String ALERT_EXCHANGE    = "stock.alerts.exchange";
    public static final String ALERT_QUEUE       = "stock.alerts.queue";
    public static final String ALERT_ROUTING_KEY = "stock.alert";

    public static final String DLX = "stock.alerts.dlx";
    public static final String DLQ = "stock.alerts.dlq";


    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(ALERT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange alertExchange() {
        return new DirectExchange(ALERT_EXCHANGE, true, false);
    }

    @Bean
    public Queue alertQueue() {
        return QueueBuilder.durable(ALERT_QUEUE)
                .withArgument("x-dead-letter-exchange",    DLX)
                .withArgument("x-dead-letter-routing-key", ALERT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding alertBinding(Queue alertQueue, DirectExchange alertExchange) {
        return BindingBuilder.bind(alertQueue)
                .to(alertExchange)
                .with(ALERT_ROUTING_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         JacksonJsonMessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }


   @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        RejectAndDontRequeueRecoverer amqpRecoverer = new RejectAndDontRequeueRecoverer();
        org.springframework.retry.interceptor.MethodInvocationRecoverer<?> recoverer =
                (args, cause) -> {
                    amqpRecoverer.recover(null, cause);
                    return null;
                };
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1_000, 2.0, 4_000)
                .recoverer(recoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter jsonMessageConverter,
            RetryOperationsInterceptor retryInterceptor) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(retryInterceptor);
        return factory;
    }
}
