package com.example.portfolio.consumer;

import com.example.portfolio.dto.StockPriceMessage;
import com.example.portfolio.event.StockPricesUpdatedEvent;
import com.example.portfolio.store.LivePriceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

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

        //  Update LivePriceStore
        messages.forEach(livePriceStore::update);

        logger.info("KafkaConsumer — LivePriceStore updated: {} symbol(s), store size={}",
                messages.size(), livePriceStore.size());

        //  Fire ONE application event for the whole batch
        //    PortfolioRealtimeService will push SSE events to all connected clients.
        eventPublisher.publishEvent(new StockPricesUpdatedEvent(this, messages));

        logger.debug("KafkaConsumer — StockPricesUpdatedEvent published");
    }
}
