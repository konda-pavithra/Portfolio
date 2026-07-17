package com.example.thresholdservice.consumer;

import com.example.common.dto.StockPriceMessage;
import com.example.thresholdservice.store.LivePriceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

// Consumes stock.prices (produced by stock-service) into this service's own LivePriceStore —
// used both to snapshot referencePrice when a threshold is created and to evaluate breaches.
@Component
public class StockPriceKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(StockPriceKafkaConsumer.class);

    private final LivePriceStore livePriceStore;

    public StockPriceKafkaConsumer(LivePriceStore livePriceStore) {
        this.livePriceStore = livePriceStore;
    }

    @KafkaListener(
        topics           = "${kafka.topic.stock-prices}",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumePriceBatch(List<StockPriceMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        messages.forEach(livePriceStore::update);

        logger.info("KafkaConsumer — LivePriceStore updated: {} symbol(s), store size={}",
                messages.size(), livePriceStore.size());
    }
}
