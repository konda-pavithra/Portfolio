package com.example.portfolio.service;

import com.example.portfolio.cilent.YahooChartClient;
import com.example.portfolio.cilent.dto.YahooChartResponse.Meta;
import com.example.portfolio.dto.StockTickerResponse;
import com.example.portfolio.producer.StockPriceKafkaProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock YahooChartClient      yahooChartClient;
    @Mock StockPriceKafkaProducer kafkaProducer;

    @InjectMocks StockService stockService;

    // ── getCurrentTickerResponse — before any refresh ─────────────────────────

    @Test
    void getCurrentTickerResponse_emptyCache_returnsUnavailableStatus() {
        StockTickerResponse resp = stockService.getCurrentTickerResponse();

        assertEquals("UNAVAILABLE", resp.getDataStatus());
        assertEquals(0, resp.getCount());
        assertTrue(resp.getStocks().isEmpty());
        assertNotNull(resp.getMessage());
    }

    @Test
    void getDataStatus_initiallyUnavailable() {
        assertEquals("UNAVAILABLE", stockService.getDataStatus());
    }

    @Test
    void getCurrentQuotesMap_initiallyEmpty() {
        assertTrue(stockService.getCurrentQuotesMap().isEmpty());
    }

    // ── refreshQuotes ─────────────────────────────────────────────────────────

    @Test
    void refreshQuotes_success_populatesCacheAndPublishesToKafka() {
        Meta meta = buildMeta("RELIANCE.NS", "Reliance Industries", 2450.0);
        when(yahooChartClient.fetchQuotes(any())).thenReturn(List.of(meta));

        stockService.refreshQuotes();

        assertEquals("LIVE", stockService.getDataStatus());
        assertEquals(1, stockService.getCurrentQuotesMap().size());
        assertTrue(stockService.getCurrentQuotesMap().containsKey("RELIANCE.NS"));
        verify(kafkaProducer).publishAll(anyList());
    }

    @Test
    void refreshQuotes_emptyResult_doesNotChangeCache() {
        when(yahooChartClient.fetchQuotes(any())).thenReturn(Collections.emptyList());

        stockService.refreshQuotes();

        // Cache stays UNAVAILABLE and empty
        assertEquals("UNAVAILABLE", stockService.getDataStatus());
        verify(kafkaProducer, never()).publishAll(any());
    }

    @Test
    void refreshQuotes_yahooThrowsException_cacheUnchanged() {
        when(yahooChartClient.fetchQuotes(any())).thenThrow(new RuntimeException("Yahoo down"));

        // Should not propagate — cache stays unchanged
        assertDoesNotThrow(() -> stockService.refreshQuotes());
        assertEquals("UNAVAILABLE", stockService.getDataStatus());
        verify(kafkaProducer, never()).publishAll(any());
    }

    @Test
    void getCurrentTickerResponse_afterRefresh_returnsLiveData() {
        Meta meta = buildMeta("RELIANCE.NS", "Reliance Industries", 2450.0);
        when(yahooChartClient.fetchQuotes(any())).thenReturn(List.of(meta));
        stockService.refreshQuotes();

        StockTickerResponse resp = stockService.getCurrentTickerResponse();

        assertEquals("LIVE", resp.getDataStatus());
        assertEquals(1, resp.getCount());
        assertFalse(resp.getStocks().isEmpty());
    }

    @Test
    void refreshQuotes_nullSymbol_defaultsToUnknown() {
        Meta meta = buildMeta(null, "Unknown Corp", 100.0);
        when(yahooChartClient.fetchQuotes(any())).thenReturn(List.of(meta));

        assertDoesNotThrow(() -> stockService.refreshQuotes());
        // UNKNOWN key should be present (or the cache has 1 entry)
        assertEquals(1, stockService.getCurrentQuotesMap().size());
    }

    @Test
    void refreshQuotes_positiveChange_gainDayTrue() {
        Meta meta = buildMeta("RELIANCE.NS", "Reliance Industries", 2450.0);
        meta.setRegularMarketChange(10.0); // positive change
        when(yahooChartClient.fetchQuotes(any())).thenReturn(List.of(meta));

        stockService.refreshQuotes();

        assertTrue(stockService.getCurrentQuotesMap().get("RELIANCE.NS").isGainDay());
    }

    @Test
    void refreshQuotes_negativeChange_gainDayFalse() {
        Meta meta = buildMeta("RELIANCE.NS", "Reliance Industries", 2450.0);
        meta.setRegularMarketChange(-10.0); // negative change
        when(yahooChartClient.fetchQuotes(any())).thenReturn(List.of(meta));

        stockService.refreshQuotes();

        assertFalse(stockService.getCurrentQuotesMap().get("RELIANCE.NS").isGainDay());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Meta buildMeta(String symbol, String longName, double price) {
        Meta m = new Meta();
        m.setSymbol(symbol);
        m.setLongName(longName);
        m.setRegularMarketPrice(price);
        m.setRegularMarketChange(0.0);
        m.setRegularMarketChangePercent(0.0);
        m.setCurrency("INR");
        m.setMarketState("REGULAR");
        return m;
    }
}
