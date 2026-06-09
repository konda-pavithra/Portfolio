package com.example.portfolio.scheduler;

import com.example.portfolio.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StockPriceScheduler {

    private static final Logger logger = LoggerFactory.getLogger(StockPriceScheduler.class);

    private final StockService stockService;

    public StockPriceScheduler(StockService stockService) {
        this.stockService = stockService;
    }

    @Scheduled(
        fixedDelayString   = "${stock.refresh.interval-ms:30000}",
        initialDelayString = "${stock.refresh.initial-delay-ms:5000}"
    )
    public void refreshStockPrices() {
        logger.info("Scheduler — triggering Nifty 50 price refresh");
        stockService.refreshQuotes();
        logger.info("Scheduler — price refresh cycle complete");
    }
}
