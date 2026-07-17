package com.example.thresholdservice.store;

import com.example.common.dto.StockPriceMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class LivePriceStore {

    private static final Logger logger = LoggerFactory.getLogger(LivePriceStore.class);


    private final ConcurrentHashMap<String, StockPriceMessage> store = new ConcurrentHashMap<>();


    public void update(StockPriceMessage message) {
        store.put(message.getSymbol(), message);
        logger.trace("LivePriceStore updated: {} = ₹{}", message.getSymbol(), message.getPrice());
    }


    public Optional<StockPriceMessage> get(String symbol) {
        return Optional.ofNullable(store.get(symbol));
    }

    public Map<String, StockPriceMessage> getAll() {
        return Collections.unmodifiableMap(store);
    }

    public boolean hasData() {
        return !store.isEmpty();
    }

    public int size() {
        return store.size();
    }
}
