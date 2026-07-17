package com.example.portfolioservice.controller;

import com.example.portfolioservice.dto.*;
import com.example.portfolioservice.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
class PortfolioControllerTest {

    @Mock PortfolioService portfolioService;

    @InjectMocks PortfolioController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Inject Authentication via request principal (works with PrincipalMethodArgumentResolver)
    private static Principal mockPrincipal() {
        return new UsernamePasswordAuthenticationToken("john_doe", null, Collections.emptyList());
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── GET /api/portfolio/stocks ─────────────────────────────────────────────

    @Test
    void getNiftyStockList_returns200WithList() throws Exception {
        List<NseStockInfo> stocks = List.of(
                NseStockInfo.builder().symbol("RELIANCE.NS").displaySymbol("RELIANCE")
                        .companyName("Reliance Industries").build());
        when(portfolioService.getNiftyStockList()).thenReturn(stocks);

        mockMvc.perform(get("/api/portfolio/stocks")
                        .principal(mockPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE.NS"))
                .andExpect(jsonPath("$[0].displaySymbol").value("RELIANCE"));
    }

    @Test
    void getNiftyStockList_emptyList_returns200() throws Exception {
        when(portfolioService.getNiftyStockList()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/portfolio/stocks").principal(mockPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ── POST /api/portfolio/add ───────────────────────────────────────────────

    @Test
    void addStock_success_returns201() throws Exception {
        AddStockRequest req = new AddStockRequest();
        req.setSymbol("RELIANCE");
        req.setQuantity(10);
        req.setBuyingPrice(new BigDecimal("2450.00"));

        PortfolioResponse resp = PortfolioResponse.builder()
                .id(1L).symbol("RELIANCE.NS").displaySymbol("RELIANCE")
                .companyName("Reliance Industries")
                .quantity(10).buyingPrice(new BigDecimal("2450.00"))
                .totalInvestment(new BigDecimal("24500.00")).build();
        when(portfolioService.addSingleStock(any(), eq("john_doe"))).thenReturn(resp);

        mockMvc.perform(post("/api/portfolio/add")
                        .principal(mockPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("RELIANCE.NS"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    // ── PUT /api/portfolio/{symbol} ───────────────────────────────────────────

    @Test
    void updateHolding_returns200() throws Exception {
        AddStockRequest req = new AddStockRequest();
        req.setSymbol("RELIANCE");
        req.setQuantity(15);
        req.setBuyingPrice(new BigDecimal("2500.00"));

        PortfolioResponse resp = PortfolioResponse.builder()
                .id(1L).symbol("RELIANCE.NS").quantity(15)
                .buyingPrice(new BigDecimal("2500.00")).build();
        when(portfolioService.updateHolding(eq("RELIANCE"), any(), eq("john_doe"))).thenReturn(resp);

        mockMvc.perform(put("/api/portfolio/RELIANCE")
                        .principal(mockPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(15));
    }

    // ── DELETE /api/portfolio/{symbol} ────────────────────────────────────────

    @Test
    void removeHolding_returns204() throws Exception {
        doNothing().when(portfolioService).removeHolding("RELIANCE", "john_doe");

        mockMvc.perform(delete("/api/portfolio/RELIANCE").principal(mockPrincipal()))
                .andExpect(status().isNoContent());

        verify(portfolioService).removeHolding("RELIANCE", "john_doe");
    }

    // ── GET /api/portfolio/valuation ──────────────────────────────────────────

    @Test
    void getValuation_returns200WithValuationResponse() throws Exception {
        PortfolioValuationResponse resp = PortfolioValuationResponse.builder()
                .totalHoldings(2)
                .totalInvestment(new BigDecimal("50000.00"))
                .totalCurrentValue(new BigDecimal("55000.00"))
                .totalProfitLoss(new BigDecimal("5000.00"))
                .totalPLPercent(10.0)
                .dataStatus("LIVE")
                .holdings(Collections.emptyList())
                .build();
        when(portfolioService.getPortfolioValuation("john_doe")).thenReturn(resp);

        mockMvc.perform(get("/api/portfolio/valuation").principal(mockPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHoldings").value(2))
                .andExpect(jsonPath("$.dataStatus").value("LIVE"))
                .andExpect(jsonPath("$.totalPLPercent").value(10.0));
    }

    // ── POST /api/portfolio/upload ────────────────────────────────────────────

    @Test
    void uploadPortfolio_returns200WithPreview() throws Exception {
        PortfolioUploadPreview preview = PortfolioUploadPreview.builder()
                .newStocks(List.of(PortfolioEntry.builder().symbol("RELIANCE.NS").build()))
                .stocksToUpdate(Collections.emptyList())
                .invalidSymbols(Collections.emptyList())
                .parseErrors(Collections.emptyList())
                .userMessage("1 new stock will be added.")
                .requiresConfirmation(false)
                .build();
        when(portfolioService.previewUpload(any(), eq("john_doe"))).thenReturn(preview);

        MockMultipartFile file = new MockMultipartFile(
                "file", "portfolio.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy content".getBytes());

        mockMvc.perform(multipart("/api/portfolio/upload")
                        .file(file)
                        .principal(mockPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresConfirmation").value(false))
                .andExpect(jsonPath("$.newStocks").isArray());
    }

    // ── POST /api/portfolio/confirm ───────────────────────────────────────────

    @Test
    void confirmPortfolio_returns200WithResult() throws Exception {
        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(List.of(PortfolioEntry.builder().symbol("RELIANCE.NS").build()));
        req.setToUpdate(Collections.emptyList());

        PortfolioConfirmResponse resp = PortfolioConfirmResponse.builder()
                .addedCount(1).updatedCount(0).skippedCount(0)
                .message("1 stock added.")
                .portfolio(Collections.emptyList())
                .build();
        when(portfolioService.confirmUpload(any(), eq("john_doe"))).thenReturn(resp);

        mockMvc.perform(post("/api/portfolio/confirm")
                        .principal(mockPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(0));
    }

    // ── GET /api/portfolio ────────────────────────────────────────────────────

    @Test
    void getPortfolio_returns200WithHoldings() throws Exception {
        List<PortfolioResponse> holdings = List.of(
                PortfolioResponse.builder().id(1L).symbol("RELIANCE.NS").displaySymbol("RELIANCE")
                        .companyName("Reliance Industries")
                        .quantity(10).buyingPrice(new BigDecimal("2450.00")).build());
        when(portfolioService.getUserPortfolio("john_doe")).thenReturn(holdings);

        mockMvc.perform(get("/api/portfolio").principal(mockPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE.NS"))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    void getPortfolio_emptyPortfolio_returnsEmptyArray() throws Exception {
        when(portfolioService.getUserPortfolio("john_doe")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/portfolio").principal(mockPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
