package com.example.portfolioservice.service;

import com.example.common.dto.StockPriceMessage;
import com.example.portfolioservice.dto.*;
import com.example.portfolioservice.entity.Portfolio;
import com.example.portfolioservice.exception.StockAlreadyInPortfolioException;
import com.example.portfolioservice.repository.PortfolioRepository;
import com.example.portfolioservice.util.ExcelParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock PortfolioRepository portfolioRepository;
    @Mock ExcelParser         excelParser;
    @Mock PriceCacheService   priceCacheService;

    @InjectMocks PortfolioService portfolioService;

    private static final String USERNAME = "john_doe";
    private AddStockRequest addReq;

    @BeforeEach
    void setUp() {
        addReq = new AddStockRequest();
        addReq.setSymbol("RELIANCE");
        addReq.setQuantity(10);
        addReq.setBuyingPrice(new BigDecimal("2450.00"));
    }

    // ── getNiftyStockList ─────────────────────────────────────────────────────

    @Test
    void getNiftyStockList_returnsSortedList() {
        List<NseStockInfo> stocks = portfolioService.getNiftyStockList();

        assertFalse(stocks.isEmpty());
        for (int i = 0; i < stocks.size() - 1; i++) {
            assertTrue(stocks.get(i).getCompanyName()
                    .compareTo(stocks.get(i + 1).getCompanyName()) <= 0,
                    "List should be sorted by company name");
        }
    }

    @Test
    void getNiftyStockList_eachEntryHasSymbolAndDisplaySymbol() {
        portfolioService.getNiftyStockList().forEach(s -> {
            assertNotNull(s.getSymbol());
            assertNotNull(s.getDisplaySymbol());
            assertFalse(s.getDisplaySymbol().endsWith(".NS"));
        });
    }

    // ── addSingleStock ────────────────────────────────────────────────────────

    @Test
    void addSingleStock_success_returnsResponse() {
        when(portfolioRepository.existsByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS"))).thenReturn(false);

        Portfolio saved = Portfolio.builder().id(1L).username(USERNAME)
                .symbol("RELIANCE.NS").companyName("Reliance Industries")
                .quantity(10).buyingPrice(new BigDecimal("2450.00")).build();
        when(portfolioRepository.save(any())).thenReturn(saved);

        PortfolioResponse resp = portfolioService.addSingleStock(addReq, USERNAME);

        assertEquals("RELIANCE.NS", resp.getSymbol());
        assertEquals("RELIANCE", resp.getDisplaySymbol());
        assertEquals(10, resp.getQuantity());
    }

    @Test
    void addSingleStock_acceptsNsQualifiedSymbol() {
        addReq.setSymbol("RELIANCE.NS");
        when(portfolioRepository.existsByUsernameAndSymbol(any(), any())).thenReturn(false);
        Portfolio saved = Portfolio.builder().id(1L).symbol("RELIANCE.NS")
                .companyName("Reliance Industries").quantity(10)
                .buyingPrice(new BigDecimal("2450.00")).build();
        when(portfolioRepository.save(any())).thenReturn(saved);

        PortfolioResponse resp = portfolioService.addSingleStock(addReq, USERNAME);
        assertEquals("RELIANCE.NS", resp.getSymbol());
    }

    @Test
    void addSingleStock_acceptsCompanyName() {
        addReq.setSymbol("Reliance Industries");
        when(portfolioRepository.existsByUsernameAndSymbol(any(), any())).thenReturn(false);
        Portfolio saved = Portfolio.builder().id(1L).symbol("RELIANCE.NS")
                .companyName("Reliance Industries").quantity(10)
                .buyingPrice(new BigDecimal("2450.00")).build();
        when(portfolioRepository.save(any())).thenReturn(saved);

        PortfolioResponse resp = portfolioService.addSingleStock(addReq, USERNAME);
        assertEquals("RELIANCE.NS", resp.getSymbol());
    }

    @Test
    void addSingleStock_invalidSymbol_throwsIllegalArgument() {
        addReq.setSymbol("FAKECORP");
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, USERNAME));
    }

    @Test
    void addSingleStock_zeroQuantity_throwsIllegalArgument() {
        addReq.setQuantity(0);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, USERNAME));
    }

    @Test
    void addSingleStock_negativeQuantity_throwsIllegalArgument() {
        addReq.setQuantity(-5);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, USERNAME));
    }

    @Test
    void addSingleStock_nullQuantity_throwsIllegalArgument() {
        addReq.setQuantity(null);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, USERNAME));
    }

    @Test
    void addSingleStock_zeroBuyingPrice_throwsIllegalArgument() {
        addReq.setBuyingPrice(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, USERNAME));
    }

    @Test
    void addSingleStock_nullBuyingPrice_throwsIllegalArgument() {
        addReq.setBuyingPrice(null);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, USERNAME));
    }

    @Test
    void addSingleStock_alreadyInPortfolio_throwsConflictWithExistingHolding() {
        when(portfolioRepository.existsByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS"))).thenReturn(true);

        Portfolio existing = Portfolio.builder().id(1L).symbol("RELIANCE.NS")
                .companyName("Reliance Industries").quantity(5)
                .buyingPrice(new BigDecimal("2300.00")).build();
        when(portfolioRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(existing));

        StockAlreadyInPortfolioException ex = assertThrows(
                StockAlreadyInPortfolioException.class,
                () -> portfolioService.addSingleStock(addReq, USERNAME));

        assertNotNull(ex.getExistingHolding());
        assertEquals("RELIANCE.NS", ex.getExistingHolding().getSymbol());
    }

    // ── updateHolding ─────────────────────────────────────────────────────────

    @Test
    void updateHolding_success_updatesQtyAndPrice() {
        Portfolio existing = Portfolio.builder().id(1L).username(USERNAME)
                .symbol("RELIANCE.NS").companyName("Reliance Industries")
                .quantity(5).buyingPrice(new BigDecimal("2300.00")).build();
        when(portfolioRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any())).thenReturn(existing);

        portfolioService.updateHolding("RELIANCE", addReq, USERNAME);

        verify(portfolioRepository).save(argThat(p ->
                p.getQuantity() == 10 &&
                p.getBuyingPrice().compareTo(new BigDecimal("2450.00")) == 0));
    }

    @Test
    void updateHolding_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.updateHolding("FAKECORP", addReq, USERNAME));
    }

    @Test
    void updateHolding_notInPortfolio_throwsIllegalArgument() {
        when(portfolioRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.updateHolding("RELIANCE", addReq, USERNAME));
    }

    // ── removeHolding ─────────────────────────────────────────────────────────

    @Test
    void removeHolding_success_callsDelete() {
        Portfolio existing = Portfolio.builder().id(1L).username(USERNAME).symbol("RELIANCE.NS").build();
        when(portfolioRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(existing));

        portfolioService.removeHolding("RELIANCE", USERNAME);

        verify(portfolioRepository).delete(existing);
    }

    @Test
    void removeHolding_notInPortfolio_throwsIllegalArgument() {
        when(portfolioRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.removeHolding("RELIANCE", USERNAME));
    }

    @Test
    void removeHolding_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.removeHolding("FAKECORP", USERNAME));
    }

    // ── getUserPortfolio ──────────────────────────────────────────────────────

    @Test
    void getUserPortfolio_returnsAllHoldings() {
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(List.of(
                Portfolio.builder().id(1L).symbol("RELIANCE.NS").companyName("Reliance Industries")
                        .quantity(10).buyingPrice(new BigDecimal("2450.00")).build()
        ));

        List<PortfolioResponse> result = portfolioService.getUserPortfolio(USERNAME);

        assertEquals(1, result.size());
        assertEquals("RELIANCE.NS", result.get(0).getSymbol());
    }

    @Test
    void getUserPortfolio_empty_returnsEmptyList() {
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());

        assertTrue(portfolioService.getUserPortfolio(USERNAME).isEmpty());
    }

    // ── getPortfolioValuation ─────────────────────────────────────────────────

    @Test
    void getPortfolioValuation_withLivePrice_computesPnL() {
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(List.of(
                Portfolio.builder().symbol("RELIANCE.NS").companyName("Reliance Industries")
                        .quantity(10).buyingPrice(new BigDecimal("2000.00")).build()
        ));
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(Map.of(
                "RELIANCE.NS", StockPriceMessage.builder().symbol("RELIANCE.NS").price(2500.0).marketState("REGULAR").build()
        ));
        when(priceCacheService.getDataStatus()).thenReturn("LIVE");

        PortfolioValuationResponse resp = portfolioService.getPortfolioValuation(USERNAME);

        assertEquals(1, resp.getTotalHoldings());
        assertEquals(0, new BigDecimal("20000.00").compareTo(resp.getTotalInvestment()));
        assertEquals(0, new BigDecimal("25000.00").compareTo(resp.getTotalCurrentValue()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(resp.getTotalProfitLoss()));
        assertEquals(25.0, resp.getTotalPLPercent());
        assertEquals("LIVE", resp.getDataStatus());
    }

    @Test
    void getPortfolioValuation_noPriceAvailable_currentValueIsZero() {
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(List.of(
                Portfolio.builder().symbol("RELIANCE.NS").companyName("Reliance Industries")
                        .quantity(10).buyingPrice(new BigDecimal("2000.00")).build()
        ));
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(Collections.emptyMap());
        when(priceCacheService.getDataStatus()).thenReturn("UNAVAILABLE");

        PortfolioValuationResponse resp = portfolioService.getPortfolioValuation(USERNAME);

        assertEquals(0, BigDecimal.ZERO.compareTo(resp.getTotalCurrentValue()));
    }

    @Test
    void getPortfolioValuation_emptyPortfolio_returnsZeroTotals() {
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());
        when(priceCacheService.getCurrentQuotesMap()).thenReturn(Collections.emptyMap());
        when(priceCacheService.getDataStatus()).thenReturn("UNAVAILABLE");

        PortfolioValuationResponse resp = portfolioService.getPortfolioValuation(USERNAME);

        assertEquals(0, resp.getTotalHoldings());
        assertEquals(0.0, resp.getTotalPLPercent());
    }

    // ── previewUpload ─────────────────────────────────────────────────────────

    @Test
    void previewUpload_newStock_classifiedAsNew() {
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());

        PortfolioEntry entry = PortfolioEntry.builder()
                .symbol("RELIANCE").quantity(10).buyingPrice(new BigDecimal("2450.00")).build();
        when(excelParser.parse(any())).thenReturn(new ExcelParser.ParseResult(List.of(entry), List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "p.xlsx", "application/octet-stream", new byte[1]);
        PortfolioUploadPreview preview = portfolioService.previewUpload(file, USERNAME);

        assertEquals(1, preview.getNewStocks().size());
        assertTrue(preview.getStocksToUpdate().isEmpty());
        assertFalse(preview.isRequiresConfirmation());
    }

    @Test
    void previewUpload_existingStock_classifiedAsUpdate() {
        Portfolio existingReliance = Portfolio.builder().symbol("RELIANCE.NS").quantity(5)
                .buyingPrice(new BigDecimal("2300.00")).build();
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(List.of(existingReliance));

        PortfolioEntry entry = PortfolioEntry.builder()
                .symbol("RELIANCE").quantity(10).buyingPrice(new BigDecimal("2450.00")).build();
        when(excelParser.parse(any())).thenReturn(new ExcelParser.ParseResult(List.of(entry), List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "p.xlsx", "application/octet-stream", new byte[1]);
        PortfolioUploadPreview preview = portfolioService.previewUpload(file, USERNAME);

        assertTrue(preview.getNewStocks().isEmpty());
        assertEquals(1, preview.getStocksToUpdate().size());
        assertTrue(preview.isRequiresConfirmation());
    }

    @Test
    void previewUpload_invalidSymbol_classifiedAsInvalid() {
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());

        PortfolioEntry bad = PortfolioEntry.builder()
                .symbol("FAKECORP").quantity(5).buyingPrice(new BigDecimal("100.00")).build();
        when(excelParser.parse(any())).thenReturn(new ExcelParser.ParseResult(List.of(bad), List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "p.xlsx", "application/octet-stream", new byte[1]);
        PortfolioUploadPreview preview = portfolioService.previewUpload(file, USERNAME);

        assertEquals(1, preview.getInvalidSymbols().size());
        assertEquals("FAKECORP", preview.getInvalidSymbols().get(0));
    }

    // ── confirmUpload ─────────────────────────────────────────────────────────

    @Test
    void confirmUpload_addsAndUpdatesHoldings() {
        when(portfolioRepository.existsByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS"))).thenReturn(false);
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());

        Portfolio existingTcs = Portfolio.builder().symbol("TCS.NS").quantity(3)
                .buyingPrice(new BigDecimal("3200.00")).build();
        when(portfolioRepository.findByUsernameAndSymbol(eq(USERNAME), eq("TCS.NS")))
                .thenReturn(Optional.of(existingTcs));

        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(List.of(PortfolioEntry.builder().symbol("RELIANCE").quantity(10)
                .buyingPrice(new BigDecimal("2450.00")).build()));
        req.setToUpdate(List.of(PortfolioEntry.builder().symbol("TCS").quantity(5)
                .buyingPrice(new BigDecimal("3500.00")).build()));

        PortfolioConfirmResponse resp = portfolioService.confirmUpload(req, USERNAME);

        assertEquals(1, resp.getAddedCount());
        assertEquals(1, resp.getUpdatedCount());
        assertEquals(0, resp.getSkippedCount());
    }

    @Test
    void confirmUpload_invalidSymbolInToAdd_isSkipped() {
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());

        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(List.of(PortfolioEntry.builder().symbol("FAKECORP").quantity(5)
                .buyingPrice(new BigDecimal("100.00")).build()));
        req.setToUpdate(Collections.emptyList());

        PortfolioConfirmResponse resp = portfolioService.confirmUpload(req, USERNAME);

        assertEquals(0, resp.getAddedCount());
        assertEquals(1, resp.getSkippedCount());
    }

    @Test
    void confirmUpload_duplicateInToAdd_isSkipped() {
        when(portfolioRepository.existsByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS"))).thenReturn(true);
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());

        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(List.of(PortfolioEntry.builder().symbol("RELIANCE").quantity(5)
                .buyingPrice(new BigDecimal("2300.00")).build()));
        req.setToUpdate(Collections.emptyList());

        PortfolioConfirmResponse resp = portfolioService.confirmUpload(req, USERNAME);

        assertEquals(0, resp.getAddedCount());
        assertEquals(1, resp.getSkippedCount());
    }

    @Test
    void confirmUpload_notFoundInToUpdate_isSkipped() {
        when(portfolioRepository.findByUsernameAndSymbol(eq(USERNAME), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());
        when(portfolioRepository.findByUsernameOrderBySymbolAsc(USERNAME)).thenReturn(Collections.emptyList());

        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(Collections.emptyList());
        req.setToUpdate(List.of(PortfolioEntry.builder().symbol("RELIANCE").quantity(10)
                .buyingPrice(new BigDecimal("2450.00")).build()));

        PortfolioConfirmResponse resp = portfolioService.confirmUpload(req, USERNAME);

        assertEquals(0, resp.getUpdatedCount());
        assertEquals(1, resp.getSkippedCount());
    }
}
