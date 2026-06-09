package com.example.portfolio.config;

import com.example.portfolio.dto.StockPriceMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

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

    // Producer
    @Bean
    public ProducerFactory<String, StockPriceMessage> stockPriceProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Suppress __TypeId__ headers — consumer uses a fixed target type
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

    // Consumer
    @Bean
    public ConsumerFactory<String, StockPriceMessage> stockPriceConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Fixed target type — independent of any producer-set type headers
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, StockPriceMessage.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.portfolio.dto");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockPriceMessage>
            batchKafkaListenerContainerFactory(
                ConsumerFactory<String, StockPriceMessage> stockPriceConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, StockPriceMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stockPriceConsumerFactory);
        factory.setBatchListener(true);   // ← enables List<StockPriceMessage> signature
        return factory;
    }
}
