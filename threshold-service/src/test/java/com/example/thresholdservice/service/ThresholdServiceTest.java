package com.example.thresholdservice.service;

import com.example.common.dto.StockPriceMessage;
import com.example.thresholdservice.dto.ThresholdRequest;
import com.example.thresholdservice.dto.ThresholdResponse;
import com.example.thresholdservice.entity.StockThreshold;
import com.example.thresholdservice.repository.StockThresholdRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThresholdServiceTest {

    @Mock StockThresholdRepository thresholdRepository;
    @Mock PriceCacheService        priceCacheService;

    @InjectMocks ThresholdService thresholdService;

    private static final String USERNAME = "john_doe";
    private ThresholdRequest req;

    @BeforeEach
    void setUp() {
        req = ThresholdRequest.builder()
                .upperThresholdPercent(new BigDecimal("5.0"))
                .lowerThresholdPercent(new BigDecimal("3.0"))
                .build();
    }

    // ── setThreshold ──────────────────────────────────────────────────────────

    @Test
    void setThreshold_createNew_withLivePrice() {
        when(thresholdRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(Map.of(
                "RELIANCE.NS", StockPriceMessage.builder().symbol("RELIANCE.NS").price(2450.0).build()
        ));

        StockThreshold saved = StockThreshold.builder()
                .id(1L).username(USERNAME).symbol("RELIANCE.NS").companyName("Reliance Industries")
                .upperThresholdPercent(new BigDecimal("5.00"))
                .lowerThresholdPercent(new BigDecimal("3.00"))
                .referencePrice(new BigDecimal("2450.00")).build();
        when(thresholdRepository.save(any())).thenReturn(saved);

        ThresholdResponse resp = thresholdService.setThreshold("RELIANCE", req, USERNAME);

        assertNotNull(resp);
        assertEquals("RELIANCE.NS", resp.getSymbol());
        assertEquals(new BigDecimal("2450.00"), resp.getReferencePrice());
        // upperAlert = 2450 × 1.05 = 2572.50
        assertEquals(0, new BigDecimal("2572.50").compareTo(resp.getUpperAlertPrice()));
        // lowerAlert = 2450 × 0.97 = 2376.50
        assertEquals(0, new BigDecimal("2376.50").compareTo(resp.getLowerAlertPrice()));
    }

    @Test
    void setThreshold_updateExisting_overwritesPercentages() {
        StockThreshold existing = StockThreshold.builder()
                .id(1L).username(USERNAME).symbol("RELIANCE.NS").companyName("Reliance Industries")
                .upperThresholdPercent(new BigDecimal("3.00"))
                .lowerThresholdPercent(new BigDecimal("2.00"))
                .referencePrice(new BigDecimal("2300.00")).build();
        when(thresholdRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(existing));
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(Map.of(
                "RELIANCE.NS", StockPriceMessage.builder().symbol("RELIANCE.NS").price(2450.0).build()
        ));
        when(thresholdRepository.save(any())).thenReturn(existing);

        thresholdService.setThreshold("RELIANCE", req, USERNAME);

        verify(thresholdRepository).save(argThat(t ->
                t.getUpperThresholdPercent().compareTo(new BigDecimal("5.00")) == 0 &&
                t.getLowerThresholdPercent().compareTo(new BigDecimal("3.00")) == 0));
    }

    @Test
    void setThreshold_noLivePrice_referencePriceStoredAsNull() {
        when(thresholdRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(Collections.emptyMap());

        StockThreshold saved = StockThreshold.builder()
                .id(1L).symbol("RELIANCE.NS").companyName("Reliance Industries")
                .upperThresholdPercent(new BigDecimal("5.00"))
                .lowerThresholdPercent(new BigDecimal("3.00"))
                .referencePrice(null).build();
        when(thresholdRepository.save(any())).thenReturn(saved);

        ThresholdResponse resp = thresholdService.setThreshold("RELIANCE", req, USERNAME);

        assertNull(resp.getReferencePrice());
        assertNull(resp.getUpperAlertPrice());
        assertNull(resp.getLowerAlertPrice());
    }

    @Test
    void setThreshold_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.setThreshold("FAKECORP", req, USERNAME));
    }

    @Test
    void setThreshold_zeroUpperPercent_throwsIllegalArgument() {
        req.setUpperThresholdPercent(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.setThreshold("RELIANCE", req, USERNAME));
    }

    @Test
    void setThreshold_nullLowerPercent_throwsIllegalArgument() {
        req.setLowerThresholdPercent(null);
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.setThreshold("RELIANCE", req, USERNAME));
    }

    @Test
    void setThreshold_negativeLowerPercent_throwsIllegalArgument() {
        req.setLowerThresholdPercent(new BigDecimal("-1.0"));
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.setThreshold("RELIANCE", req, USERNAME));
    }

    // ── getAllThresholds ───────────────────────────────────────────────────────

    @Test
    void getAllThresholds_returnsMappedList() {
        when(thresholdRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(List.of(
                StockThreshold.builder().symbol("RELIANCE.NS").companyName("Reliance Industries")
                        .upperThresholdPercent(new BigDecimal("5.00"))
                        .lowerThresholdPercent(new BigDecimal("3.00"))
                        .referencePrice(new BigDecimal("2450.00")).build()
        ));

        List<ThresholdResponse> result = thresholdService.getAllThresholds(USERNAME);

        assertEquals(1, result.size());
        assertEquals("RELIANCE.NS", result.get(0).getSymbol());
    }

    @Test
    void getAllThresholds_empty_returnsEmptyList() {
        when(thresholdRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());

        assertTrue(thresholdService.getAllThresholds(USERNAME).isEmpty());
    }

    // ── getThreshold ──────────────────────────────────────────────────────────

    @Test
    void getThreshold_found_returnsResponse() {
        when(thresholdRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS"))).thenReturn(
                Optional.of(StockThreshold.builder().symbol("RELIANCE.NS")
                        .companyName("Reliance Industries")
                        .upperThresholdPercent(new BigDecimal("5.00"))
                        .lowerThresholdPercent(new BigDecimal("3.00"))
                        .referencePrice(new BigDecimal("2450.00")).build()));

        ThresholdResponse resp = thresholdService.getThreshold("RELIANCE", USERNAME);

        assertEquals("RELIANCE.NS", resp.getSymbol());
    }

    @Test
    void getThreshold_notFound_throwsIllegalArgument() {
        when(thresholdRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.getThreshold("RELIANCE", USERNAME));
    }

    @Test
    void getThreshold_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.getThreshold("FAKECORP", USERNAME));
    }

    // ── deleteThreshold ───────────────────────────────────────────────────────

    @Test
    void deleteThreshold_found_callsRepositoryDelete() {
        StockThreshold threshold = StockThreshold.builder().id(1L).username(USERNAME).symbol("RELIANCE.NS").build();
        when(thresholdRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(threshold));

        thresholdService.deleteThreshold("RELIANCE", USERNAME);

        verify(thresholdRepository).delete(threshold);
    }

    @Test
    void deleteThreshold_notFound_throwsIllegalArgument() {
        when(thresholdRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.deleteThreshold("RELIANCE", USERNAME));
    }

    @Test
    void deleteThreshold_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.deleteThreshold("FAKECORP", USERNAME));
    }
}
