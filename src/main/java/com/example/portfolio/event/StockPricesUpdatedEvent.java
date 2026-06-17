package com.example.portfolio.event;

import com.example.portfolio.dto.StockPriceMessage;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class StockPricesUpdatedEvent extends ApplicationEvent {

    private final List<StockPriceMessage> updatedQuotes;

    public StockPricesUpdatedEvent(Object source, List<StockPriceMessage> updatedQuotes) {
        super(source);
        this.updatedQuotes = List.copyOf(updatedQuotes); // defensive copy
    }

    /** The batch of price messages that triggered this event. */
    public List<StockPriceMessage> getUpdatedQuotes() {
        return updatedQuotes;
    }
}
