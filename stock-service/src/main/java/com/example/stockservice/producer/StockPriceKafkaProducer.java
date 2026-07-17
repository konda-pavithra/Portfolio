package com.example.stockservice.producer;

import com.example.common.dto.StockPriceMessage;
import com.example.common.dto.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Publishes Nifty 50 price updates to the stock.prices topic — called by StockService#refreshQuotes()
// after each successful Yahoo Finance fetch. Consumed independently by portfolio-service and threshold-service.
@Component
public class StockPriceKafkaProducer {

    private static final Logger logger = LoggerFactory.getLogger(StockPriceKafkaProducer.class);

    private final KafkaTemplate<String, StockPriceMessage> kafkaTemplate;

    @Value("${kafka.topic.stock-prices}")
    private String topic;

    public StockPriceKafkaProducer(KafkaTemplate<String, StockPriceMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Converts each {@link StockQuote} to a {@link StockPriceMessage} and publishes
     * it to the Kafka topic asynchronously.  Returns immediately; delivery callbacks
     * are logged for observability.
     *
     * @param quotes fresh quotes from Yahoo Finance (never null, may be empty)
     */
    public void publishAll(List<StockQuote> quotes) {
        if (quotes.isEmpty()) {
            logger.debug("KafkaProducer — no quotes to publish");
            return;
        }

        logger.debug("KafkaProducer — publishing {} price message(s) to topic '{}'",
                quotes.size(), topic);

        for (StockQuote quote : quotes) {
            StockPriceMessage message = toMessage(quote);
            CompletableFuture<SendResult<String, StockPriceMessage>> future =
                    kafkaTemplate.send(topic, message.getSymbol(), message);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.warn("KafkaProducer — failed to publish '{}': {}",
                            message.getSymbol(), ex.getMessage());
                } else {
                    logger.trace("KafkaProducer — published '{}' to partition {} @ offset {}",
                            message.getSymbol(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });
        }
    }


    private StockPriceMessage toMessage(StockQuote q) {
        return StockPriceMessage.builder()
                .symbol(q.getSymbol())
                .displaySymbol(q.getDisplaySymbol())
                .companyName(q.getCompanyName())
                .price(q.getPrice())
                .change(q.getChange())
                .changePercent(q.getChangePercent())
                .open(q.getOpen())
                .high(q.getHigh())
                .low(q.getLow())
                .previousClose(q.getPreviousClose())
                .volume(q.getVolume())
                .currency(q.getCurrency())
                .marketState(q.getMarketState())
                .publishedAt(LocalDateTime.now())
                .build();
    }
}
