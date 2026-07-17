package com.example.portfolioservice.controller;

import com.example.portfolioservice.service.PortfolioService;
import com.example.portfolioservice.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Manages the authenticated user's Nifty 50 portfolio.
// Upload flow is two-step: POST /upload returns a preview, then POST /confirm applies it.
@Tag(name = "Portfolio", description = "Manage your Nifty 50 stock holdings")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioController.class);

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }


    @Operation(summary = "List all tradeable Nifty 50 stocks")
    @GetMapping("/stocks")
    public ResponseEntity<List<NseStockInfo>> getNiftyStockList(Authentication authentication) {
        logger.info("GET /api/portfolio/stocks — user '{}'", authentication.getName());
        List<NseStockInfo> stocks = portfolioService.getNiftyStockList();
        logger.debug("GET /api/portfolio/stocks — returning {} stocks", stocks.size());
        return ResponseEntity.ok(stocks);
    }


    @Operation(summary = "Add a stock to your portfolio")
    @ApiResponse(responseCode = "409", description = "Stock already in portfolio — existing holding included in response body")
    @PostMapping("/add")
    public ResponseEntity<PortfolioResponse> addStock(
            @RequestBody AddStockRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("POST /api/portfolio/add — user '{}': symbol='{}', qty={}, price={}",
                username, request.getSymbol(), request.getQuantity(), request.getBuyingPrice());

        PortfolioResponse response = portfolioService.addSingleStock(request, username);

        logger.info("POST /api/portfolio/add — user '{}': '{}' added successfully",
                username, response.getSymbol());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @Operation(summary = "Update quantity or buying price of an existing holding")
    @PutMapping("/{symbol}")
    public ResponseEntity<PortfolioResponse> updateHolding(
            @Parameter(description = "Stock ticker or company name", example = "RELIANCE")
            @PathVariable String symbol,
            @RequestBody AddStockRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("PUT /api/portfolio/{} — user '{}': qty={}, price={}",
                symbol, username, request.getQuantity(), request.getBuyingPrice());

        PortfolioResponse response = portfolioService.updateHolding(symbol, request, username);

        logger.info("PUT /api/portfolio/{} — user '{}': updated successfully", symbol, username);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Remove a stock from your portfolio")
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeHolding(
            @Parameter(description = "Stock ticker or company name", example = "INFY")
            @PathVariable String symbol,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("DELETE /api/portfolio/{} — user '{}'", symbol, username);

        portfolioService.removeHolding(symbol, username);

        logger.info("DELETE /api/portfolio/{} — user '{}': removed successfully", symbol, username);
        return ResponseEntity.noContent().build();
    }


    @Operation(summary = "Get full portfolio valuation with live P&L")
    @GetMapping("/valuation")
    public ResponseEntity<PortfolioValuationResponse> getValuation(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/portfolio/valuation — user '{}'", username);

        PortfolioValuationResponse valuation = portfolioService.getPortfolioValuation(username);

        logger.info("GET /api/portfolio/valuation — user '{}': {} holdings, invested=₹{}, current=₹{}, P&L=₹{} ({}%), dataStatus={}",
                username,
                valuation.getTotalHoldings(),
                valuation.getTotalInvestment(),
                valuation.getTotalCurrentValue(),
                valuation.getTotalProfitLoss(),
                valuation.getTotalPLPercent(),
                valuation.getDataStatus());

        return ResponseEntity.ok(valuation);
    }


    // Step 1 of 2 — parse the Excel file and return a dry-run preview without saving anything.
    // Expected columns: Symbol, Quantity, Buying Price.
    @Operation(summary = "Parse an Excel file and preview what will change")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PortfolioUploadPreview> uploadPortfolio(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("POST /api/portfolio/upload — user '{}', file '{}' ({} bytes)",
                username, file.getOriginalFilename(), file.getSize());

        PortfolioUploadPreview preview = portfolioService.previewUpload(file, username);

        logger.info("POST /api/portfolio/upload — preview sent to user '{}': {} new, {} updates, {} invalid",
                username,
                preview.getNewStocks().size(),
                preview.getStocksToUpdate().size(),
                preview.getInvalidSymbols().size());

        return ResponseEntity.ok(preview);
    }


    // Step 2 of 2 — user reviewed the preview, now apply it.
    @Operation(summary = "Apply the changes from a previous upload preview")
    @PostMapping("/confirm")
    public ResponseEntity<PortfolioConfirmResponse> confirmPortfolio(
            @RequestBody PortfolioConfirmRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        logger.info("POST /api/portfolio/confirm — user '{}': {} to add, {} to update",
                username, request.getToAdd().size(), request.getToUpdate().size());

        PortfolioConfirmResponse response = portfolioService.confirmUpload(request, username);

        logger.info("POST /api/portfolio/confirm — user '{}': {} added, {} updated, {} skipped",
                username, response.getAddedCount(), response.getUpdatedCount(), response.getSkippedCount());

        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Get all your current holdings")
    @GetMapping
    public ResponseEntity<List<PortfolioResponse>> getPortfolio(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/portfolio — user '{}'", username);
        List<PortfolioResponse> portfolio = portfolioService.getUserPortfolio(username);
        logger.info("GET /api/portfolio — returning {} holding(s) for user '{}'",
                portfolio.size(), username);
        return ResponseEntity.ok(portfolio);
    }
}
