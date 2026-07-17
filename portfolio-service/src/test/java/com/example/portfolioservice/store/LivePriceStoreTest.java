package com.example.portfolioservice.store;

import com.example.common.dto.StockPriceMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LivePriceStoreTest {

    private LivePriceStore store;

    @BeforeEach
    void setUp() {
        store = new LivePriceStore();
    }

    @Test
    void hasData_emptyStore_returnsFalse() {
        assertFalse(store.hasData());
    }

    @Test
    void size_emptyStore_returnsZero() {
        assertEquals(0, store.size());
    }

    @Test
    void get_unknownSymbol_returnsEmpty() {
        assertTrue(store.get("RELIANCE.NS").isEmpty());
    }

    @Test
    void update_thenGet_returnsStoredMessage() {
        StockPriceMessage msg = StockPriceMessage.builder()
                .symbol("RELIANCE.NS").price(2450.50).build();

        store.update(msg);

        Optional<StockPriceMessage> result = store.get("RELIANCE.NS");
        assertTrue(result.isPresent());
        assertEquals(2450.50, result.get().getPrice());
    }

    @Test
    void update_sameSymbolTwice_overwritesPreviousValue() {
        store.update(StockPriceMessage.builder().symbol("TCS.NS").price(3000.0).build());
        store.update(StockPriceMessage.builder().symbol("TCS.NS").price(3200.0).build());

        assertEquals(3200.0, store.get("TCS.NS").get().getPrice());
    }

    @Test
    void size_afterMultipleUpdates_returnsCorrectCount() {
        store.update(StockPriceMessage.builder().symbol("RELIANCE.NS").price(2450.0).build());
        store.update(StockPriceMessage.builder().symbol("TCS.NS").price(3000.0).build());
        assertEquals(2, store.size());
    }

    @Test
    void hasData_afterUpdate_returnsTrue() {
        store.update(StockPriceMessage.builder().symbol("RELIANCE.NS").price(2450.0).build());
        assertTrue(store.hasData());
    }

    @Test
    void getAll_returnsAllStoredSymbols() {
        store.update(StockPriceMessage.builder().symbol("RELIANCE.NS").price(2450.0).build());
        store.update(StockPriceMessage.builder().symbol("TCS.NS").price(3000.0).build());

        Map<String, StockPriceMessage> all = store.getAll();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("RELIANCE.NS"));
        assertTrue(all.containsKey("TCS.NS"));
    }

    @Test
    void getAll_returnsUnmodifiableMap() {
        store.update(StockPriceMessage.builder().symbol("RELIANCE.NS").price(2450.0).build());
        Map<String, StockPriceMessage> all = store.getAll();

        assertThrows(UnsupportedOperationException.class, () -> all.put("FAKE", null));
    }
}
