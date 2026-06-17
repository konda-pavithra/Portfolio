package com.example.portfolio.controller;

import com.example.portfolio.dto.ThresholdRequest;
import com.example.portfolio.dto.ThresholdResponse;
import com.example.portfolio.service.ThresholdService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ThresholdControllerTest {

    @Mock ThresholdService thresholdService;

    @InjectMocks ThresholdController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static Principal user() {
        return new UsernamePasswordAuthenticationToken("john_doe", null, Collections.emptyList());
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── PUT /api/thresholds/{symbol} ──────────────────────────────────────────

    @Test
    void setThreshold_returns200WithResponse() throws Exception {
        ThresholdRequest req = ThresholdRequest.builder()
                .upperThresholdPercent(new BigDecimal("5.0"))
                .lowerThresholdPercent(new BigDecimal("3.0"))
                .build();

        ThresholdResponse resp = ThresholdResponse.builder()
                .id(1L).symbol("RELIANCE.NS").displaySymbol("RELIANCE")
                .companyName("Reliance Industries")
                .upperThresholdPercent(new BigDecimal("5.00"))
                .lowerThresholdPercent(new BigDecimal("3.00"))
                .referencePrice(new BigDecimal("2450.00"))
                .upperAlertPrice(new BigDecimal("2572.50"))
                .lowerAlertPrice(new BigDecimal("2376.50"))
                .build();
        when(thresholdService.setThreshold(eq("RELIANCE"), any(), eq("john_doe"))).thenReturn(resp);

        mockMvc.perform(put("/api/thresholds/RELIANCE")
                        .principal(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("RELIANCE.NS"))
                .andExpect(jsonPath("$.referencePrice").value(2450.00))
                .andExpect(jsonPath("$.upperAlertPrice").value(2572.50));
    }

    @Test
    void setThreshold_symbolPassedAsPathVariable() throws Exception {
        ThresholdRequest req = ThresholdRequest.builder()
                .upperThresholdPercent(new BigDecimal("5.0"))
                .lowerThresholdPercent(new BigDecimal("3.0"))
                .build();
        when(thresholdService.setThreshold(eq("TCS"), any(), any()))
                .thenReturn(ThresholdResponse.builder().symbol("TCS.NS").build());

        mockMvc.perform(put("/api/thresholds/TCS")
                        .principal(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(thresholdService).setThreshold(eq("TCS"), any(), eq("john_doe"));
    }

    // ── GET /api/thresholds ───────────────────────────────────────────────────

    @Test
    void getAllThresholds_returns200WithList() throws Exception {
        List<ThresholdResponse> list = List.of(
                ThresholdResponse.builder().symbol("RELIANCE.NS").displaySymbol("RELIANCE")
                        .upperThresholdPercent(new BigDecimal("5.00"))
                        .lowerThresholdPercent(new BigDecimal("3.00")).build());
        when(thresholdService.getAllThresholds("john_doe")).thenReturn(list);

        mockMvc.perform(get("/api/thresholds").principal(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE.NS"))
                .andExpect(jsonPath("$[0].upperThresholdPercent").value(5.00));
    }

    @Test
    void getAllThresholds_empty_returnsEmptyArray() throws Exception {
        when(thresholdService.getAllThresholds("john_doe")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/thresholds").principal(user()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ── GET /api/thresholds/{symbol} ──────────────────────────────────────────

    @Test
    void getThreshold_returns200() throws Exception {
        ThresholdResponse resp = ThresholdResponse.builder()
                .symbol("RELIANCE.NS").displaySymbol("RELIANCE")
                .companyName("Reliance Industries")
                .upperThresholdPercent(new BigDecimal("5.00"))
                .lowerThresholdPercent(new BigDecimal("3.00"))
                .referencePrice(new BigDecimal("2450.00")).build();
        when(thresholdService.getThreshold("RELIANCE", "john_doe")).thenReturn(resp);

        mockMvc.perform(get("/api/thresholds/RELIANCE").principal(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("RELIANCE.NS"))
                .andExpect(jsonPath("$.companyName").value("Reliance Industries"));
    }

    // ── DELETE /api/thresholds/{symbol} ──────────────────────────────────────

    @Test
    void deleteThreshold_returns204() throws Exception {
        doNothing().when(thresholdService).deleteThreshold("RELIANCE", "john_doe");

        mockMvc.perform(delete("/api/thresholds/RELIANCE").principal(user()))
                .andExpect(status().isNoContent());

        verify(thresholdService).deleteThreshold("RELIANCE", "john_doe");
    }

    @Test
    void deleteThreshold_callsServiceWithCorrectArgs() throws Exception {
        doNothing().when(thresholdService).deleteThreshold("TCS", "john_doe");

        mockMvc.perform(delete("/api/thresholds/TCS").principal(user()))
                .andExpect(status().isNoContent());

        verify(thresholdService, times(1)).deleteThreshold("TCS", "john_doe");
    }
}
