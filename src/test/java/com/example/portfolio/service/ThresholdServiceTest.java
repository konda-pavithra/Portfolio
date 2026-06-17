package com.example.portfolio.service;

import com.example.portfolio.dto.StockQuote;
import com.example.portfolio.dto.ThresholdRequest;
import com.example.portfolio.dto.ThresholdResponse;
import com.example.portfolio.entity.StockThreshold;
import com.example.portfolio.entity.User;
import com.example.portfolio.repository.StockThresholdRepository;
import com.example.portfolio.repository.UserRepository;
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
    @Mock UserRepository           userRepository;
    @Mock StockService             stockService;

    @InjectMocks ThresholdService thresholdService;

    private User             user;
    private ThresholdRequest req;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("john_doe").build();

        req = ThresholdRequest.builder()
                .upperThresholdPercent(new BigDecimal("5.0"))
                .lowerThresholdPercent(new BigDecimal("3.0"))
                .build();
    }

    // ── setThreshold ──────────────────────────────────────────────────────────

    @Test
    void setThreshold_createNew_withLivePrice() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(thresholdRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());
        when(stockService.getCurrentQuotesMap()).thenReturn(Map.of(
                "RELIANCE.NS", StockQuote.builder().symbol("RELIANCE.NS").price(2450.0).build()
        ));

        StockThreshold saved = StockThreshold.builder()
                .id(1L).user(user).symbol("RELIANCE.NS").companyName("Reliance Industries")
                .upperThresholdPercent(new BigDecimal("5.00"))
                .lowerThresholdPercent(new BigDecimal("3.00"))
                .referencePrice(new BigDecimal("2450.00")).build();
        when(thresholdRepository.save(any())).thenReturn(saved);

        ThresholdResponse resp = thresholdService.setThreshold("RELIANCE", req, "john_doe");

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
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));

        StockThreshold existing = StockThreshold.builder()
                .id(1L).user(user).symbol("RELIANCE.NS").companyName("Reliance Industries")
                .upperThresholdPercent(new BigDecimal("3.00"))
                .lowerThresholdPercent(new BigDecimal("2.00"))
                .referencePrice(new BigDecimal("2300.00")).build();
        when(thresholdRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(existing));
        when(stockService.getCurrentQuotesMap()).thenReturn(Map.of(
                "RELIANCE.NS", StockQuote.builder().symbol("RELIANCE.NS").price(2450.0).build()
        ));
        when(thresholdRepository.save(any())).thenReturn(existing);

        thresholdService.setThreshold("RELIANCE", req, "john_doe");

        verify(thresholdRepository).save(argThat(t ->
                t.getUpperThresholdPercent().compareTo(new BigDecimal("5.00")) == 0 &&
                t.getLowerThresholdPercent().compareTo(new BigDecimal("3.00")) == 0));
    }

    @Test
    void setThreshold_noLivePrice_referencePriceStoredAsNull() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(thresholdRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());
        when(stockService.getCurrentQuotesMap()).thenReturn(Collections.emptyMap());

        StockThreshold saved = StockThreshold.builder()
                .id(1L).symbol("RELIANCE.NS").companyName("Reliance Industries")
                .upperThresholdPercent(new BigDecimal("5.00"))
                .lowerThresholdPercent(new BigDecimal("3.00"))
                .referencePrice(null).build();
        when(thresholdRepository.save(any())).thenReturn(saved);

        ThresholdResponse resp = thresholdService.setThreshold("RELIANCE", req, "john_doe");

        assertNull(resp.getReferencePrice());
        assertNull(resp.getUpperAlertPrice());
        assertNull(resp.getLowerAlertPrice());
    }

    @Test
    void setThreshold_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.setThreshold("FAKECORP", req, "john_doe"));
    }

    @Test
    void setThreshold_zeroUpperPercent_throwsIllegalArgument() {
        req.setUpperThresholdPercent(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.setThreshold("RELIANCE", req, "john_doe"));
    }

    @Test
    void setThreshold_nullLowerPercent_throwsIllegalArgument() {
        req.setLowerThresholdPercent(null);
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.setThreshold("RELIANCE", req, "john_doe"));
    }

    @Test
    void setThreshold_negativeLowerPercent_throwsIllegalArgument() {
        req.setLowerThresholdPercent(new BigDecimal("-1.0"));
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.setThreshold("RELIANCE", req, "john_doe"));
    }

    // ── getAllThresholds ───────────────────────────────────────────────────────

    @Test
    void getAllThresholds_returnsMappedList() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(thresholdRepository.findByUserOrderBySymbolAsc(user)).thenReturn(List.of(
                StockThreshold.builder().symbol("RELIANCE.NS").companyName("Reliance Industries")
                        .upperThresholdPercent(new BigDecimal("5.00"))
                        .lowerThresholdPercent(new BigDecimal("3.00"))
                        .referencePrice(new BigDecimal("2450.00")).build()
        ));

        List<ThresholdResponse> result = thresholdService.getAllThresholds("john_doe");

        assertEquals(1, result.size());
        assertEquals("RELIANCE.NS", result.get(0).getSymbol());
    }

    @Test
    void getAllThresholds_empty_returnsEmptyList() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(thresholdRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());

        assertTrue(thresholdService.getAllThresholds("john_doe").isEmpty());
    }

    // ── getThreshold ──────────────────────────────────────────────────────────

    @Test
    void getThreshold_found_returnsResponse() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(thresholdRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS"))).thenReturn(
                Optional.of(StockThreshold.builder().symbol("RELIANCE.NS")
                        .companyName("Reliance Industries")
                        .upperThresholdPercent(new BigDecimal("5.00"))
                        .lowerThresholdPercent(new BigDecimal("3.00"))
                        .referencePrice(new BigDecimal("2450.00")).build()));

        ThresholdResponse resp = thresholdService.getThreshold("RELIANCE", "john_doe");

        assertEquals("RELIANCE.NS", resp.getSymbol());
    }

    @Test
    void getThreshold_notFound_throwsIllegalArgument() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(thresholdRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.getThreshold("RELIANCE", "john_doe"));
    }

    @Test
    void getThreshold_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.getThreshold("FAKECORP", "john_doe"));
    }

    // ── deleteThreshold ───────────────────────────────────────────────────────

    @Test
    void deleteThreshold_found_callsRepositoryDelete() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        StockThreshold threshold = StockThreshold.builder().id(1L).user(user).symbol("RELIANCE.NS").build();
        when(thresholdRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(threshold));

        thresholdService.deleteThreshold("RELIANCE", "john_doe");

        verify(thresholdRepository).delete(threshold);
    }

    @Test
    void deleteThreshold_notFound_throwsIllegalArgument() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(thresholdRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.deleteThreshold("RELIANCE", "john_doe"));
    }

    @Test
    void deleteThreshold_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> thresholdService.deleteThreshold("FAKECORP", "john_doe"));
    }
}
