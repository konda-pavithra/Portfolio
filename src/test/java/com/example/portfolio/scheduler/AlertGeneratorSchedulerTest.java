package com.example.portfolio.scheduler;

import com.example.portfolio.config.RabbitMQConfig;
import com.example.portfolio.dto.StockAlertMessage;
import com.example.portfolio.dto.StockQuote;
import com.example.portfolio.entity.Portfolio;
import com.example.portfolio.entity.StockThreshold;
import com.example.portfolio.entity.User;
import com.example.portfolio.repository.PortfolioRepository;
import com.example.portfolio.repository.StockThresholdRepository;
import com.example.portfolio.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertGeneratorSchedulerTest {

    @Mock StockThresholdRepository thresholdRepository;
    @Mock PortfolioRepository      portfolioRepository;
    @Mock StockService             stockService;
    @Mock RabbitTemplate           rabbitTemplate;

    @InjectMocks AlertGeneratorScheduler scheduler;

    // refPrice = 2000, upper = 5% → alertAt 2100, lower = 3% → alertAt 1940
    private static final BigDecimal REF_PRICE      = new BigDecimal("2000.00");
    private static final BigDecimal UPPER_PERCENT  = new BigDecimal("5.00");
    private static final BigDecimal LOWER_PERCENT  = new BigDecimal("3.00");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "cooldownHours", 4);
    }

    // ── Early exit conditions ─────────────────────────────────────────────────

    @Test
    void checkAllThresholds_notLiveData_skipsEverything() {
        when(stockService.getDataStatus()).thenReturn("UNAVAILABLE");

        scheduler.checkAllThresholds();

        verify(stockService, never()).getCurrentQuotesMap();
        verifyNoInteractions(thresholdRepository, portfolioRepository, rabbitTemplate);
    }

    @Test
    void checkAllThresholds_emptyPriceMap_skipsThresholdFetch() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        when(stockService.getCurrentQuotesMap()).thenReturn(Collections.emptyMap());

        scheduler.checkAllThresholds();

        verifyNoInteractions(thresholdRepository, portfolioRepository, rabbitTemplate);
    }

    @Test
    void checkAllThresholds_noThresholdsWithPortfolioHolding_doesNotPublish() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(2050.0)); // within bounds
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(Collections.emptyList());

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    // ── Upper breach ──────────────────────────────────────────────────────────

    @Test
    void checkAllThresholds_upperBreach_publishesAlertToRabbit() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        // Current price 2200 > upperAlert 2100 → UPPER breach
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0));
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(freshThreshold()));
        when(portfolioRepository.findByUserAndSymbol(any(), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(buildPortfolio()));
        when(thresholdRepository.save(any())).thenReturn(freshThreshold());

        scheduler.checkAllThresholds();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.ALERT_EXCHANGE),
                eq(RabbitMQConfig.ALERT_ROUTING_KEY),
                (Object) argThat(msg -> "UPPER".equals(((StockAlertMessage) msg).getAlertType())));
    }

    // ── Lower breach ──────────────────────────────────────────────────────────

    @Test
    void checkAllThresholds_lowerBreach_publishesAlertToRabbit() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        // Current price 1900 < lowerAlert 1940 → LOWER breach
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(1900.0));
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(freshThreshold()));
        when(portfolioRepository.findByUserAndSymbol(any(), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(buildPortfolio()));
        when(thresholdRepository.save(any())).thenReturn(freshThreshold());

        scheduler.checkAllThresholds();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.ALERT_EXCHANGE),
                eq(RabbitMQConfig.ALERT_ROUTING_KEY),
                (Object) argThat(msg -> "LOWER".equals(((StockAlertMessage) msg).getAlertType())));
    }

    // ── Within bounds — no alert ──────────────────────────────────────────────

    @Test
    void checkAllThresholds_priceWithinBounds_noAlertPublished() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        // 2050 is between 1940 and 2100 → no breach
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(2050.0));
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(freshThreshold()));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    // ── Cooldown ──────────────────────────────────────────────────────────────

    @Test
    void checkAllThresholds_sameBreach_insideCooldown_suppressesAlert() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0)); // upper breach
        // Last alert was UPPER 1 hour ago — still within 4-hour cooldown
        StockThreshold threshold = buildThresholdWithLastAlert("UPPER", LocalDateTime.now().minusHours(1));
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(threshold));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void checkAllThresholds_differentBreach_bypassesCooldown() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(1900.0)); // lower breach
        // Last alert was UPPER 1 hour ago — different type so cooldown doesn't apply
        StockThreshold threshold = buildThresholdWithLastAlert("UPPER", LocalDateTime.now().minusHours(1));
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(threshold));
        when(portfolioRepository.findByUserAndSymbol(any(), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(buildPortfolio()));
        when(thresholdRepository.save(any())).thenReturn(threshold);

        scheduler.checkAllThresholds();

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) any());
    }

    @Test
    void checkAllThresholds_sameBreach_afterCooldownExpiry_alertsAgain() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0)); // upper breach
        // Last alert was UPPER 5 hours ago — cooldown (4h) has expired
        StockThreshold threshold = buildThresholdWithLastAlert("UPPER", LocalDateTime.now().minusHours(5));
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(threshold));
        when(portfolioRepository.findByUserAndSymbol(any(), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(buildPortfolio()));
        when(thresholdRepository.save(any())).thenReturn(threshold);

        scheduler.checkAllThresholds();

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) any());
    }

    // ── Missing data edge cases ───────────────────────────────────────────────

    @Test
    void checkAllThresholds_noPriceForSymbol_skipsThreshold() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        // Price map has TCS but not RELIANCE.NS
        when(stockService.getCurrentQuotesMap()).thenReturn(Map.of(
                "TCS.NS", StockQuote.builder().symbol("TCS.NS").price(3500.0).build()
        ));
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(freshThreshold()));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void checkAllThresholds_nullReferencePrice_skipsThreshold() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0));

        StockThreshold t = freshThreshold();
        t.setReferencePrice(null); // no reference price set
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(t));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void checkAllThresholds_noPortfolioHolding_skipsAlert() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0)); // upper breach
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(freshThreshold()));
        when(portfolioRepository.findByUserAndSymbol(any(), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void checkAllThresholds_evaluateException_logsAndContinues() {
        when(stockService.getDataStatus()).thenReturn("LIVE");
        when(stockService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0));
        when(thresholdRepository.findAllWithPortfolioHolding()).thenReturn(List.of(freshThreshold()));
        when(portfolioRepository.findByUserAndSymbol(any(), any()))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Should not propagate — per-threshold errors are caught individually
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> scheduler.checkAllThresholds());
        verifyNoInteractions(rabbitTemplate);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StockThreshold freshThreshold() {
        User user = User.builder().id(1L).username("john_doe").email("john@example.com").build();
        return StockThreshold.builder()
                .id(1L).user(user).symbol("RELIANCE.NS").companyName("Reliance Industries")
                .upperThresholdPercent(UPPER_PERCENT)
                .lowerThresholdPercent(LOWER_PERCENT)
                .referencePrice(REF_PRICE)
                .build();
    }

    private StockThreshold buildThresholdWithLastAlert(String alertType, LocalDateTime sentAt) {
        StockThreshold t = freshThreshold();
        t.setLastAlertType(alertType);
        t.setLastAlertSentAt(sentAt);
        return t;
    }

    private Portfolio buildPortfolio() {
        return Portfolio.builder()
                .id(1L).symbol("RELIANCE.NS").companyName("Reliance Industries")
                .quantity(10).buyingPrice(new BigDecimal("2000.00"))
                .build();
    }

    private Map<String, StockQuote> priceMap(double price) {
        return Map.of("RELIANCE.NS",
                StockQuote.builder().symbol("RELIANCE.NS").price(price).build());
    }
}
