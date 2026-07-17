package com.example.thresholdservice.scheduler;

import com.example.common.dto.StockAlertMessage;
import com.example.common.dto.StockPriceMessage;
import com.example.thresholdservice.client.PortfolioClient;
import com.example.thresholdservice.client.UserClient;
import com.example.thresholdservice.config.RabbitMQConfig;
import com.example.thresholdservice.dto.HoldingInfo;
import com.example.thresholdservice.dto.UserInfo;
import com.example.thresholdservice.entity.StockThreshold;
import com.example.thresholdservice.repository.StockThresholdRepository;
import com.example.thresholdservice.service.PriceCacheService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertGeneratorSchedulerTest {

    @Mock StockThresholdRepository thresholdRepository;
    @Mock PriceCacheService        priceCacheService;
    @Mock PortfolioClient          portfolioClient;
    @Mock UserClient               userClient;
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
        when(priceCacheService.getDataStatus()).thenReturn("UNAVAILABLE");

        scheduler.checkAllThresholds();

        verify(priceCacheService, never()).getCurrentQuotesMap();
        verifyNoInteractions(thresholdRepository, portfolioClient, rabbitTemplate);
    }

    @Test
    void checkAllThresholds_emptyPriceMap_skipsThresholdFetch() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(Collections.emptyMap());

        scheduler.checkAllThresholds();

        verifyNoInteractions(thresholdRepository, portfolioClient, rabbitTemplate);
    }

    @Test
    void checkAllThresholds_noThresholds_doesNotPublish() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(2050.0)); // within bounds
        when(thresholdRepository.findAll()).thenReturn(Collections.emptyList());

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    // ── Upper breach ──────────────────────────────────────────────────────────

    @Test
    void checkAllThresholds_upperBreach_publishesAlertToRabbit() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        // Current price 2200 > upperAlert 2100 → UPPER breach
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0));
        when(thresholdRepository.findAll()).thenReturn(List.of(freshThreshold()));
        when(portfolioClient.getHolding(any(), eq("RELIANCE.NS"))).thenReturn(buildHolding());
        when(userClient.getByUsername(any())).thenReturn(buildUserInfo());
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
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        // Current price 1900 < lowerAlert 1940 → LOWER breach
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(1900.0));
        when(thresholdRepository.findAll()).thenReturn(List.of(freshThreshold()));
        when(portfolioClient.getHolding(any(), eq("RELIANCE.NS"))).thenReturn(buildHolding());
        when(userClient.getByUsername(any())).thenReturn(buildUserInfo());
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
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        // 2050 is between 1940 and 2100 → no breach
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(2050.0));
        when(thresholdRepository.findAll()).thenReturn(List.of(freshThreshold()));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    // ── Cooldown ──────────────────────────────────────────────────────────────

    @Test
    void checkAllThresholds_sameBreach_insideCooldown_suppressesAlert() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0)); // upper breach
        // Last alert was UPPER 1 hour ago — still within 4-hour cooldown
        StockThreshold threshold = buildThresholdWithLastAlert("UPPER", LocalDateTime.now().minusHours(1));
        when(thresholdRepository.findAll()).thenReturn(List.of(threshold));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void checkAllThresholds_differentBreach_bypassesCooldown() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(1900.0)); // lower breach
        // Last alert was UPPER 1 hour ago — different type so cooldown doesn't apply
        StockThreshold threshold = buildThresholdWithLastAlert("UPPER", LocalDateTime.now().minusHours(1));
        when(thresholdRepository.findAll()).thenReturn(List.of(threshold));
        when(portfolioClient.getHolding(any(), eq("RELIANCE.NS"))).thenReturn(buildHolding());
        when(userClient.getByUsername(any())).thenReturn(buildUserInfo());
        when(thresholdRepository.save(any())).thenReturn(threshold);

        scheduler.checkAllThresholds();

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) any());
    }

    @Test
    void checkAllThresholds_sameBreach_afterCooldownExpiry_alertsAgain() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0)); // upper breach
        // Last alert was UPPER 5 hours ago — cooldown (4h) has expired
        StockThreshold threshold = buildThresholdWithLastAlert("UPPER", LocalDateTime.now().minusHours(5));
        when(thresholdRepository.findAll()).thenReturn(List.of(threshold));
        when(portfolioClient.getHolding(any(), eq("RELIANCE.NS"))).thenReturn(buildHolding());
        when(userClient.getByUsername(any())).thenReturn(buildUserInfo());
        when(thresholdRepository.save(any())).thenReturn(threshold);

        scheduler.checkAllThresholds();

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) any());
    }

    // ── Missing data edge cases ───────────────────────────────────────────────

    @Test
    void checkAllThresholds_noPriceForSymbol_skipsThreshold() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        // Price map has TCS but not RELIANCE.NS
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(Map.of(
                "TCS.NS", StockPriceMessage.builder().symbol("TCS.NS").price(3500.0).build()
        ));
        when(thresholdRepository.findAll()).thenReturn(List.of(freshThreshold()));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void checkAllThresholds_nullReferencePrice_skipsThreshold() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0));

        StockThreshold t = freshThreshold();
        t.setReferencePrice(null); // no reference price set
        when(thresholdRepository.findAll()).thenReturn(List.of(t));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void checkAllThresholds_noPortfolioHolding_skipsAlert() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0)); // upper breach
        when(thresholdRepository.findAll()).thenReturn(List.of(freshThreshold()));
        when(portfolioClient.getHolding(any(), eq("RELIANCE.NS")))
                .thenThrow(new RuntimeException("404 Not Found"));

        scheduler.checkAllThresholds();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void checkAllThresholds_evaluateException_logsAndContinues() {
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(priceMap(2200.0));
        when(thresholdRepository.findAll()).thenReturn(List.of(freshThreshold()));
        when(portfolioClient.getHolding(any(), any()))
                .thenThrow(new RuntimeException("portfolio-service unreachable"));

        // Should not propagate — per-threshold errors are caught individually
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> scheduler.checkAllThresholds());
        verifyNoInteractions(rabbitTemplate);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private StockThreshold freshThreshold() {
        return StockThreshold.builder()
                .id(1L).username("john_doe").symbol("RELIANCE.NS").companyName("Reliance Industries")
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

    private HoldingInfo buildHolding() {
        return HoldingInfo.builder()
                .quantity(10).buyingPrice(new BigDecimal("2000.00"))
                .build();
    }

    private UserInfo buildUserInfo() {
        return UserInfo.builder().username("john_doe").email("john@example.com").build();
    }

    private Map<String, StockPriceMessage> priceMap(double price) {
        return Map.of("RELIANCE.NS",
                StockPriceMessage.builder().symbol("RELIANCE.NS").price(price).build());
    }
}
