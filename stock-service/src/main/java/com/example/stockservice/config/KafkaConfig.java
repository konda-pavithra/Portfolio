package com.example.stockservice.config;

import com.example.common.dto.StockPriceMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.topic.stock-prices}")
    private String stockPricesTopic;

    @Value("${kafka.topic.partitions:3}")
    private int partitions;

    @Value("${kafka.topic.replication-factor:1}")
    private short replicationFactor;

    @Bean
    public org.apache.kafka.clients.admin.NewTopic stockPricesTopic() {
        return TopicBuilder.name(stockPricesTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public ProducerFactory<String, StockPriceMessage> stockPriceProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Suppress __TypeId__ headers — consumers use a fixed target type
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        // Producer acks = 1: leader acknowledgment only (sufficient for internal telemetry)
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, StockPriceMessage> kafkaTemplate(
            ProducerFactory<String, StockPriceMessage> stockPriceProducerFactory) {
        return new KafkaTemplate<>(stockPriceProducerFactory);
    }
}
