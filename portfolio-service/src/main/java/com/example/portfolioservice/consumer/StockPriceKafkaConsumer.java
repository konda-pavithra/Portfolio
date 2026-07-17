package com.example.portfolioservice.consumer;

import com.example.common.dto.StockPriceMessage;
import com.example.portfolioservice.event.StockPricesUpdatedEvent;
import com.example.portfolioservice.store.LivePriceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

// Consumes stock.prices (produced by stock-service) into this service's own LivePriceStore,
// then fires a local event so PortfolioRealtimeService can push SSE updates.
@Component
public class StockPriceKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(StockPriceKafkaConsumer.class);

    private final LivePriceStore livePriceStore;
    private final ApplicationEventPublisher eventPublisher;

    public StockPriceKafkaConsumer(LivePriceStore livePriceStore,
                                   ApplicationEventPublisher eventPublisher) {
        this.livePriceStore  = livePriceStore;
        this.eventPublisher  = eventPublisher;
    }


    @KafkaListener(
        topics           = "${kafka.topic.stock-prices}",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumePriceBatch(List<StockPriceMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        logger.debug("KafkaConsumer — received batch of {} price message(s)", messages.size());

        messages.forEach(livePriceStore::update);

        logger.info("KafkaConsumer — LivePriceStore updated: {} symbol(s), store size={}",
                messages.size(), livePriceStore.size());

        eventPublisher.publishEvent(new StockPricesUpdatedEvent(this, messages));

        logger.debug("KafkaConsumer — StockPricesUpdatedEvent published");
    }
}
