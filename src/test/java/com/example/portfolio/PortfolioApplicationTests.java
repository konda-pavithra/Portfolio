package com.example.portfolio;

import com.example.portfolio.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@SpringBootTest
class PortfolioApplicationTests {

    /**
     * Replaces the production RabbitMQ listener container factory with one that
     * has autoStartup=false so the test context doesn't try to open a real
     * AMQP connection during startup.
     */
    @TestConfiguration
    static class TestRabbitConfig {

        @Bean
        @Primary
        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
                ConnectionFactory connectionFactory,
                JacksonJsonMessageConverter jsonMessageConverter,
                RetryOperationsInterceptor retryInterceptor) {

            SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
            factory.setConnectionFactory(connectionFactory);
            factory.setMessageConverter(jsonMessageConverter);
            factory.setAdviceChain(retryInterceptor);
            factory.setAutoStartup(false);
            return factory;
        }
    }

    @Test
    void contextLoads() {
    }
}
