package com.example.portfolio.controller;

import com.example.portfolio.dto.PortfolioRealtimeResponse;
import com.example.portfolio.service.PortfolioRealtimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PortfolioStreamControllerTest {

    @Mock PortfolioRealtimeService realtimeService;

    @InjectMocks PortfolioStreamController controller;

    private MockMvc mockMvc;

    private static Principal user() {
        return new UsernamePasswordAuthenticationToken("john_doe", null, Collections.emptyList());
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "emitterTimeoutMs", 300_000L);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── GET /api/portfolio/stream ─────────────────────────────────────────────

    @Test
    void streamPortfolio_callsRegisterWithUsernameAndTimeout() throws Exception {
        SseEmitter emitter = new SseEmitter(300_000L);
        when(realtimeService.activeConnectionCount()).thenReturn(0);
        when(realtimeService.register(eq("john_doe"), eq(300_000L))).thenReturn(emitter);

        mockMvc.perform(get("/api/portfolio/stream")
                        .principal(user())
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE));

        verify(realtimeService).register("john_doe", 300_000L);
    }

    @Test
    void streamPortfolio_registersNewEmitterPerRequest() throws Exception {
        SseEmitter emitter1 = new SseEmitter(300_000L);
        SseEmitter emitter2 = new SseEmitter(300_000L);
        when(realtimeService.activeConnectionCount()).thenReturn(0, 1);
        when(realtimeService.register(eq("john_doe"), eq(300_000L)))
                .thenReturn(emitter1, emitter2);

        // Two separate SSE connect requests
        mockMvc.perform(get("/api/portfolio/stream")
                .principal(user()).accept(MediaType.TEXT_EVENT_STREAM_VALUE));
        mockMvc.perform(get("/api/portfolio/stream")
                .principal(user()).accept(MediaType.TEXT_EVENT_STREAM_VALUE));

        verify(realtimeService, times(2)).register("john_doe", 300_000L);
    }

    // ── GET /api/portfolio/stream/snapshot ────────────────────────────────────

    @Test
    void getSnapshot_returns200WithValuation() throws Exception {
        PortfolioRealtimeResponse snapshot = PortfolioRealtimeResponse.builder()
                .totalHoldings(2)
                .totalInvestment(new BigDecimal("50000.00"))
                .totalCurrentValue(new BigDecimal("55000.00"))
                .totalProfitLoss(new BigDecimal("5000.00"))
                .totalPLPercent(10.0)
                .holdingsAboveUpperThreshold(1)
                .holdingsBelowLowerThreshold(0)
                .holdingsWithinBounds(1)
                .holdingsWithoutThreshold(0)
                .dataStatus("LIVE")
                .valuedAt(LocalDateTime.now())
                .holdings(Collections.emptyList())
                .build();
        when(realtimeService.computeValuation("john_doe")).thenReturn(snapshot);

        mockMvc.perform(get("/api/portfolio/stream/snapshot")
                        .principal(user())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalHoldings").value(2))
                .andExpect(jsonPath("$.totalPLPercent").value(10.0))
                .andExpect(jsonPath("$.dataStatus").value("LIVE"))
                .andExpect(jsonPath("$.holdingsAboveUpperThreshold").value(1));
    }

    @Test
    void getSnapshot_emptyPortfolio_returnsZeroTotals() throws Exception {
        PortfolioRealtimeResponse snapshot = PortfolioRealtimeResponse.builder()
                .totalHoldings(0)
                .totalInvestment(BigDecimal.ZERO)
                .totalCurrentValue(BigDecimal.ZERO)
                .totalProfitLoss(BigDecimal.ZERO)
                .totalPLPercent(0.0)
                .dataStatus("UNAVAILABLE")
                .valuedAt(LocalDateTime.now())
                .holdings(Collections.emptyList())
                .build();
        when(realtimeService.computeValuation("john_doe")).thenReturn(snapshot);

        mockMvc.perform(get("/api/portfolio/stream/snapshot")
                        .principal(user())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHoldings").value(0))
                .andExpect(jsonPath("$.dataStatus").value("UNAVAILABLE"));
    }

    @Test
    void getSnapshot_delegatesToRealtimeServiceWithUsername() throws Exception {
        when(realtimeService.computeValuation("john_doe"))
                .thenReturn(PortfolioRealtimeResponse.builder()
                        .holdings(Collections.emptyList())
                        .totalHoldings(0).dataStatus("LIVE")
                        .valuedAt(LocalDateTime.now()).build());

        mockMvc.perform(get("/api/portfolio/stream/snapshot")
                .principal(user()));

        verify(realtimeService, times(1)).computeValuation("john_doe");
    }
}
