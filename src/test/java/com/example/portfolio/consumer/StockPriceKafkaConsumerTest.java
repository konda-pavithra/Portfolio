package com.example.portfolio.consumer;

import com.example.portfolio.dto.StockPriceMessage;
import com.example.portfolio.event.StockPricesUpdatedEvent;
import com.example.portfolio.store.LivePriceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockPriceKafkaConsumerTest {

    @Mock LivePriceStore            livePriceStore;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks StockPriceKafkaConsumer consumer;

    @Test
    void consumePriceBatch_nullList_doesNothing() {
        consumer.consumePriceBatch(null);

        verifyNoInteractions(livePriceStore, eventPublisher);
    }

    @Test
    void consumePriceBatch_emptyList_doesNothing() {
        consumer.consumePriceBatch(Collections.emptyList());

        verifyNoInteractions(livePriceStore, eventPublisher);
    }

    @Test
    void consumePriceBatch_validBatch_updatesStoreAndPublishesEvent() {
        StockPriceMessage msg1 = StockPriceMessage.builder().symbol("RELIANCE.NS").price(2450.0).build();
        StockPriceMessage msg2 = StockPriceMessage.builder().symbol("TCS.NS").price(3500.0).build();

        consumer.consumePriceBatch(List.of(msg1, msg2));

        verify(livePriceStore).update(msg1);
        verify(livePriceStore).update(msg2);
        verify(eventPublisher).publishEvent(any(StockPricesUpdatedEvent.class));
    }

    @Test
    void consumePriceBatch_singleMessage_updatesStoreAndPublishesOneEvent() {
        StockPriceMessage msg = StockPriceMessage.builder().symbol("RELIANCE.NS").price(2450.0).build();

        consumer.consumePriceBatch(List.of(msg));

        verify(livePriceStore, times(1)).update(msg);
        verify(eventPublisher, times(1)).publishEvent(any(StockPricesUpdatedEvent.class));
    }
}
