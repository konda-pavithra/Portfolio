package com.example.portfolio.controller;

import com.example.portfolio.dto.StockQuote;
import com.example.portfolio.dto.StockTickerResponse;
import com.example.portfolio.service.StockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StockControllerTest {

    @Mock StockService stockService;

    @InjectMocks StockController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── GET /api/stocks/quotes ────────────────────────────────────────────────

    @Test
    void getQuotes_returns200WithSnapshot() throws Exception {
        StockTickerResponse resp = StockTickerResponse.builder()
                .stocks(List.of(
                        StockQuote.builder().symbol("RELIANCE.NS").displaySymbol("RELIANCE")
                                .companyName("Reliance Industries").price(2450.0)
                                .marketState("REGULAR").gainDay(true).build()
                ))
                .count(1)
                .marketOpen(true)
                .fetchedAt(LocalDateTime.now())
                .dataStatus("LIVE")
                .message("Market is open — live prices")
                .build();
        when(stockService.getCurrentTickerResponse()).thenReturn(resp);

        mockMvc.perform(get("/api/stocks/quotes"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dataStatus").value("LIVE"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.marketOpen").value(true))
                .andExpect(jsonPath("$.stocks[0].symbol").value("RELIANCE.NS"))
                .andExpect(jsonPath("$.stocks[0].price").value(2450.0));
    }

    @Test
    void getQuotes_unavailable_returns200WithUnavailableStatus() throws Exception {
        StockTickerResponse resp = StockTickerResponse.builder()
                .stocks(Collections.emptyList())
                .count(0)
                .marketOpen(false)
                .fetchedAt(LocalDateTime.now())
                .dataStatus("UNAVAILABLE")
                .message("Stock data is being loaded. Please retry in a few seconds.")
                .build();
        when(stockService.getCurrentTickerResponse()).thenReturn(resp);

        mockMvc.perform(get("/api/stocks/quotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.stocks").isEmpty());
    }

    @Test
    void getQuotes_returnsJsonContentType() throws Exception {
        when(stockService.getCurrentTickerResponse()).thenReturn(
                StockTickerResponse.builder()
                        .stocks(Collections.emptyList()).count(0)
                        .dataStatus("UNAVAILABLE").fetchedAt(LocalDateTime.now()).build());

        mockMvc.perform(get("/api/stocks/quotes"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void getQuotes_multipleStocks_returnsAllInList() throws Exception {
        StockTickerResponse resp = StockTickerResponse.builder()
                .stocks(List.of(
                        StockQuote.builder().symbol("RELIANCE.NS").price(2450.0).build(),
                        StockQuote.builder().symbol("TCS.NS").price(3500.0).build()
                ))
                .count(2).dataStatus("LIVE").fetchedAt(LocalDateTime.now()).build();
        when(stockService.getCurrentTickerResponse()).thenReturn(resp);

        mockMvc.perform(get("/api/stocks/quotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.stocks.length()").value(2));
    }
}
