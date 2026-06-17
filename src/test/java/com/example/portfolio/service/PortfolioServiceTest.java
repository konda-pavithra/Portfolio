package com.example.portfolio.service;

import com.example.portfolio.dto.*;
import com.example.portfolio.entity.Portfolio;
import com.example.portfolio.entity.User;
import com.example.portfolio.exception.StockAlreadyInPortfolioException;
import com.example.portfolio.repository.PortfolioRepository;
import com.example.portfolio.repository.UserRepository;
import com.example.portfolio.util.ExcelParser;
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
    @Mock UserRepository      userRepository;
    @Mock ExcelParser         excelParser;
    @Mock StockService        stockService;

    @InjectMocks PortfolioService portfolioService;

    private User            user;
    private AddStockRequest addReq;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("john_doe").email("john@example.com").build();

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
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.existsByUserAndSymbol(eq(user), eq("RELIANCE.NS"))).thenReturn(false);

        Portfolio saved = Portfolio.builder().id(1L).user(user)
                .symbol("RELIANCE.NS").companyName("Reliance Industries")
                .quantity(10).buyingPrice(new BigDecimal("2450.00")).build();
        when(portfolioRepository.save(any())).thenReturn(saved);

        PortfolioResponse resp = portfolioService.addSingleStock(addReq, "john_doe");

        assertEquals("RELIANCE.NS", resp.getSymbol());
        assertEquals("RELIANCE", resp.getDisplaySymbol());
        assertEquals(10, resp.getQuantity());
    }

    @Test
    void addSingleStock_acceptsNsQualifiedSymbol() {
        addReq.setSymbol("RELIANCE.NS");
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.existsByUserAndSymbol(any(), any())).thenReturn(false);
        Portfolio saved = Portfolio.builder().id(1L).symbol("RELIANCE.NS")
                .companyName("Reliance Industries").quantity(10)
                .buyingPrice(new BigDecimal("2450.00")).build();
        when(portfolioRepository.save(any())).thenReturn(saved);

        PortfolioResponse resp = portfolioService.addSingleStock(addReq, "john_doe");
        assertEquals("RELIANCE.NS", resp.getSymbol());
    }

    @Test
    void addSingleStock_acceptsCompanyName() {
        addReq.setSymbol("Reliance Industries");
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.existsByUserAndSymbol(any(), any())).thenReturn(false);
        Portfolio saved = Portfolio.builder().id(1L).symbol("RELIANCE.NS")
                .companyName("Reliance Industries").quantity(10)
                .buyingPrice(new BigDecimal("2450.00")).build();
        when(portfolioRepository.save(any())).thenReturn(saved);

        PortfolioResponse resp = portfolioService.addSingleStock(addReq, "john_doe");
        assertEquals("RELIANCE.NS", resp.getSymbol());
    }

    @Test
    void addSingleStock_invalidSymbol_throwsIllegalArgument() {
        addReq.setSymbol("FAKECORP");
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, "john_doe"));
    }

    @Test
    void addSingleStock_zeroQuantity_throwsIllegalArgument() {
        addReq.setQuantity(0);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, "john_doe"));
    }

    @Test
    void addSingleStock_negativeQuantity_throwsIllegalArgument() {
        addReq.setQuantity(-5);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, "john_doe"));
    }

    @Test
    void addSingleStock_nullQuantity_throwsIllegalArgument() {
        addReq.setQuantity(null);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, "john_doe"));
    }

    @Test
    void addSingleStock_zeroBuyingPrice_throwsIllegalArgument() {
        addReq.setBuyingPrice(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, "john_doe"));
    }

    @Test
    void addSingleStock_nullBuyingPrice_throwsIllegalArgument() {
        addReq.setBuyingPrice(null);
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.addSingleStock(addReq, "john_doe"));
    }

    @Test
    void addSingleStock_alreadyInPortfolio_throwsConflictWithExistingHolding() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.existsByUserAndSymbol(eq(user), eq("RELIANCE.NS"))).thenReturn(true);

        Portfolio existing = Portfolio.builder().id(1L).symbol("RELIANCE.NS")
                .companyName("Reliance Industries").quantity(5)
                .buyingPrice(new BigDecimal("2300.00")).build();
        when(portfolioRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(existing));

        StockAlreadyInPortfolioException ex = assertThrows(
                StockAlreadyInPortfolioException.class,
                () -> portfolioService.addSingleStock(addReq, "john_doe"));

        assertNotNull(ex.getExistingHolding());
        assertEquals("RELIANCE.NS", ex.getExistingHolding().getSymbol());
    }

    // ── updateHolding ─────────────────────────────────────────────────────────

    @Test
    void updateHolding_success_updatesQtyAndPrice() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));

        Portfolio existing = Portfolio.builder().id(1L).user(user)
                .symbol("RELIANCE.NS").companyName("Reliance Industries")
                .quantity(5).buyingPrice(new BigDecimal("2300.00")).build();
        when(portfolioRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(existing));
        when(portfolioRepository.save(any())).thenReturn(existing);

        portfolioService.updateHolding("RELIANCE", addReq, "john_doe");

        verify(portfolioRepository).save(argThat(p ->
                p.getQuantity() == 10 &&
                p.getBuyingPrice().compareTo(new BigDecimal("2450.00")) == 0));
    }

    @Test
    void updateHolding_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.updateHolding("FAKECORP", addReq, "john_doe"));
    }

    @Test
    void updateHolding_notInPortfolio_throwsIllegalArgument() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.updateHolding("RELIANCE", addReq, "john_doe"));
    }

    // ── removeHolding ─────────────────────────────────────────────────────────

    @Test
    void removeHolding_success_callsDelete() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        Portfolio existing = Portfolio.builder().id(1L).user(user).symbol("RELIANCE.NS").build();
        when(portfolioRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.of(existing));

        portfolioService.removeHolding("RELIANCE", "john_doe");

        verify(portfolioRepository).delete(existing);
    }

    @Test
    void removeHolding_notInPortfolio_throwsIllegalArgument() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.removeHolding("RELIANCE", "john_doe"));
    }

    @Test
    void removeHolding_invalidSymbol_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> portfolioService.removeHolding("FAKECORP", "john_doe"));
    }

    // ── getUserPortfolio ──────────────────────────────────────────────────────

    @Test
    void getUserPortfolio_returnsAllHoldings() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(List.of(
                Portfolio.builder().id(1L).symbol("RELIANCE.NS").companyName("Reliance Industries")
                        .quantity(10).buyingPrice(new BigDecimal("2450.00")).build()
        ));

        List<PortfolioResponse> result = portfolioService.getUserPortfolio("john_doe");

        assertEquals(1, result.size());
        assertEquals("RELIANCE.NS", result.get(0).getSymbol());
    }

    @Test
    void getUserPortfolio_empty_returnsEmptyList() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());

        assertTrue(portfolioService.getUserPortfolio("john_doe").isEmpty());
    }

    // ── getPortfolioValuation ─────────────────────────────────────────────────

    @Test
    void getPortfolioValuation_withLivePrice_computesPnL() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(List.of(
                Portfolio.builder().symbol("RELIANCE.NS").companyName("Reliance Industries")
                        .quantity(10).buyingPrice(new BigDecimal("2000.00")).build()
        ));
        when(stockService.getCurrentQuotesMap()).thenReturn(Map.of(
                "RELIANCE.NS", StockQuote.builder().symbol("RELIANCE.NS").price(2500.0).marketState("REGULAR").build()
        ));
        when(stockService.getDataStatus()).thenReturn("LIVE");

        PortfolioValuationResponse resp = portfolioService.getPortfolioValuation("john_doe");

        assertEquals(1, resp.getTotalHoldings());
        assertEquals(0, new BigDecimal("20000.00").compareTo(resp.getTotalInvestment()));
        assertEquals(0, new BigDecimal("25000.00").compareTo(resp.getTotalCurrentValue()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(resp.getTotalProfitLoss()));
        assertEquals(25.0, resp.getTotalPLPercent());
        assertEquals("LIVE", resp.getDataStatus());
    }

    @Test
    void getPortfolioValuation_noPriceAvailable_currentValueIsZero() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(List.of(
                Portfolio.builder().symbol("RELIANCE.NS").companyName("Reliance Industries")
                        .quantity(10).buyingPrice(new BigDecimal("2000.00")).build()
        ));
        when(stockService.getCurrentQuotesMap()).thenReturn(Collections.emptyMap());
        when(stockService.getDataStatus()).thenReturn("UNAVAILABLE");

        PortfolioValuationResponse resp = portfolioService.getPortfolioValuation("john_doe");

        assertEquals(0, BigDecimal.ZERO.compareTo(resp.getTotalCurrentValue()));
    }

    @Test
    void getPortfolioValuation_emptyPortfolio_returnsZeroTotals() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());
        when(stockService.getCurrentQuotesMap()).thenReturn(Collections.emptyMap());
        when(stockService.getDataStatus()).thenReturn("UNAVAILABLE");

        PortfolioValuationResponse resp = portfolioService.getPortfolioValuation("john_doe");

        assertEquals(0, resp.getTotalHoldings());
        assertEquals(0.0, resp.getTotalPLPercent());
    }

    // ── previewUpload ─────────────────────────────────────────────────────────

    @Test
    void previewUpload_newStock_classifiedAsNew() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());

        PortfolioEntry entry = PortfolioEntry.builder()
                .symbol("RELIANCE").quantity(10).buyingPrice(new BigDecimal("2450.00")).build();
        when(excelParser.parse(any())).thenReturn(new ExcelParser.ParseResult(List.of(entry), List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "p.xlsx", "application/octet-stream", new byte[1]);
        PortfolioUploadPreview preview = portfolioService.previewUpload(file, "john_doe");

        assertEquals(1, preview.getNewStocks().size());
        assertTrue(preview.getStocksToUpdate().isEmpty());
        assertFalse(preview.isRequiresConfirmation());
    }

    @Test
    void previewUpload_existingStock_classifiedAsUpdate() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));

        Portfolio existingReliance = Portfolio.builder().symbol("RELIANCE.NS").quantity(5)
                .buyingPrice(new BigDecimal("2300.00")).build();
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(List.of(existingReliance));

        PortfolioEntry entry = PortfolioEntry.builder()
                .symbol("RELIANCE").quantity(10).buyingPrice(new BigDecimal("2450.00")).build();
        when(excelParser.parse(any())).thenReturn(new ExcelParser.ParseResult(List.of(entry), List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "p.xlsx", "application/octet-stream", new byte[1]);
        PortfolioUploadPreview preview = portfolioService.previewUpload(file, "john_doe");

        assertTrue(preview.getNewStocks().isEmpty());
        assertEquals(1, preview.getStocksToUpdate().size());
        assertTrue(preview.isRequiresConfirmation());
    }

    @Test
    void previewUpload_invalidSymbol_classifiedAsInvalid() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());

        PortfolioEntry bad = PortfolioEntry.builder()
                .symbol("FAKECORP").quantity(5).buyingPrice(new BigDecimal("100.00")).build();
        when(excelParser.parse(any())).thenReturn(new ExcelParser.ParseResult(List.of(bad), List.of()));

        MockMultipartFile file = new MockMultipartFile("file", "p.xlsx", "application/octet-stream", new byte[1]);
        PortfolioUploadPreview preview = portfolioService.previewUpload(file, "john_doe");

        assertEquals(1, preview.getInvalidSymbols().size());
        assertEquals("FAKECORP", preview.getInvalidSymbols().get(0));
    }

    // ── confirmUpload ─────────────────────────────────────────────────────────

    @Test
    void confirmUpload_addsAndUpdatesHoldings() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.existsByUserAndSymbol(eq(user), eq("RELIANCE.NS"))).thenReturn(false);
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());

        Portfolio existingTcs = Portfolio.builder().symbol("TCS.NS").quantity(3)
                .buyingPrice(new BigDecimal("3200.00")).build();
        when(portfolioRepository.findByUserAndSymbol(eq(user), eq("TCS.NS")))
                .thenReturn(Optional.of(existingTcs));

        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(List.of(PortfolioEntry.builder().symbol("RELIANCE").quantity(10)
                .buyingPrice(new BigDecimal("2450.00")).build()));
        req.setToUpdate(List.of(PortfolioEntry.builder().symbol("TCS").quantity(5)
                .buyingPrice(new BigDecimal("3500.00")).build()));

        PortfolioConfirmResponse resp = portfolioService.confirmUpload(req, "john_doe");

        assertEquals(1, resp.getAddedCount());
        assertEquals(1, resp.getUpdatedCount());
        assertEquals(0, resp.getSkippedCount());
    }

    @Test
    void confirmUpload_invalidSymbolInToAdd_isSkipped() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());

        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(List.of(PortfolioEntry.builder().symbol("FAKECORP").quantity(5)
                .buyingPrice(new BigDecimal("100.00")).build()));
        req.setToUpdate(Collections.emptyList());

        PortfolioConfirmResponse resp = portfolioService.confirmUpload(req, "john_doe");

        assertEquals(0, resp.getAddedCount());
        assertEquals(1, resp.getSkippedCount());
    }

    @Test
    void confirmUpload_duplicateInToAdd_isSkipped() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.existsByUserAndSymbol(eq(user), eq("RELIANCE.NS"))).thenReturn(true);
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());

        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(List.of(PortfolioEntry.builder().symbol("RELIANCE").quantity(5)
                .buyingPrice(new BigDecimal("2300.00")).build()));
        req.setToUpdate(Collections.emptyList());

        PortfolioConfirmResponse resp = portfolioService.confirmUpload(req, "john_doe");

        assertEquals(0, resp.getAddedCount());
        assertEquals(1, resp.getSkippedCount());
    }

    @Test
    void confirmUpload_notFoundInToUpdate_isSkipped() {
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(portfolioRepository.findByUserAndSymbol(eq(user), eq("RELIANCE.NS")))
                .thenReturn(Optional.empty());
        when(portfolioRepository.findByUserOrderBySymbolAsc(user)).thenReturn(Collections.emptyList());

        PortfolioConfirmRequest req = new PortfolioConfirmRequest();
        req.setToAdd(Collections.emptyList());
        req.setToUpdate(List.of(PortfolioEntry.builder().symbol("RELIANCE").quantity(10)
                .buyingPrice(new BigDecimal("2450.00")).build()));

        PortfolioConfirmResponse resp = portfolioService.confirmUpload(req, "john_doe");

        assertEquals(0, resp.getUpdatedCount());
        assertEquals(1, resp.getSkippedCount());
    }
}
