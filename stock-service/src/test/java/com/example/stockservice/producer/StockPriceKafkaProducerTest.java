package com.example.stockservice.producer;

import com.example.common.dto.StockPriceMessage;
import com.example.common.dto.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockPriceKafkaProducerTest {

    @Mock KafkaTemplate<String, StockPriceMessage> kafkaTemplate;

    @InjectMocks StockPriceKafkaProducer producer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "topic", "stock.prices");
    }

    // ── publishAll ────────────────────────────────────────────────────────────

    @Test
    void publishAll_emptyList_doesNotSendAnything() {
        producer.publishAll(Collections.emptyList());

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publishAll_withQuotes_sendsOneMessagePerQuote() {
        StockQuote q1 = buildQuote("RELIANCE.NS", 2450.0);
        StockQuote q2 = buildQuote("TCS.NS", 3500.0);

        when(kafkaTemplate.send(anyString(), anyString(), any(StockPriceMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        producer.publishAll(List.of(q1, q2));

        verify(kafkaTemplate, times(2)).send(eq("stock.prices"), anyString(), any(StockPriceMessage.class));
    }

    @Test
    void publishAll_sendsWithSymbolAsKey() {
        StockQuote quote = buildQuote("RELIANCE.NS", 2450.0);
        when(kafkaTemplate.send(anyString(), anyString(), any(StockPriceMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        producer.publishAll(List.of(quote));

        verify(kafkaTemplate).send(eq("stock.prices"), eq("RELIANCE.NS"), any(StockPriceMessage.class));
    }

    @Test
    void publishAll_deliveryFailure_doesNotPropagateException() {
        StockQuote quote = buildQuote("RELIANCE.NS", 2450.0);
        CompletableFuture<SendResult<String, StockPriceMessage>> failed =
                CompletableFuture.failedFuture(new RuntimeException("Kafka broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), any(StockPriceMessage.class)))
                .thenReturn(failed);

        // Must not throw — failures are logged at WARN level
        producer.publishAll(List.of(quote));
    }

    @Test
    void publishAll_messageContainsCorrectFields() {
        StockQuote quote = buildQuote("TCS.NS", 3500.0);
        quote.setDisplaySymbol("TCS");
        quote.setCompanyName("Tata Consultancy Services");
        quote.setMarketState("REGULAR");

        when(kafkaTemplate.send(anyString(), anyString(), any(StockPriceMessage.class)))
                .thenAnswer(inv -> {
                    StockPriceMessage msg = inv.getArgument(2);
                    assert "TCS.NS".equals(msg.getSymbol());
                    assert "TCS".equals(msg.getDisplaySymbol());
                    assert 3500.0 == msg.getPrice();
                    return CompletableFuture.completedFuture(mock(SendResult.class));
                });

        producer.publishAll(List.of(quote));

        verify(kafkaTemplate).send(anyString(), anyString(), any(StockPriceMessage.class));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private StockQuote buildQuote(String symbol, double price) {
        return StockQuote.builder()
                .symbol(symbol)
                .displaySymbol(symbol.replace(".NS", ""))
                .price(price)
                .change(0.0)
                .changePercent(0.0)
                .marketState("REGULAR")
                .currency("INR")
                .build();
    }
}
