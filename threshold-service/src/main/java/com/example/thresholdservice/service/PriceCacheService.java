package com.example.thresholdservice.service;

import com.example.common.dto.StockPriceMessage;
import com.example.thresholdservice.store.LivePriceStore;
import org.springframework.stereotype.Service;

import java.util.Map;

// Thin wrapper around LivePriceStore — this service's local read model of stock.prices,
// fed by StockPriceKafkaConsumer. Replaces the direct in-JVM call to stock-service's
// StockService that the monolith used.
@Service
public class PriceCacheService {

    private final LivePriceStore livePriceStore;

    public PriceCacheService(LivePriceStore livePriceStore) {
        this.livePriceStore = livePriceStore;
    }

    public Map<String, StockPriceMessage> getCurrentQuotesMap() {
        return livePriceStore.getAll();
    }

    /** "LIVE" once at least one Kafka price batch has been consumed, "UNAVAILABLE" until then. */
    public String getDataStatus() {
        return livePriceStore.hasData() ? "LIVE" : "UNAVAILABLE";
    }
}
