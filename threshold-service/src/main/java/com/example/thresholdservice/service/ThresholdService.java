package com.example.thresholdservice.service;

import com.example.common.constants.NseStocks;
import com.example.common.dto.StockPriceMessage;
import com.example.thresholdservice.dto.ThresholdRequest;
import com.example.thresholdservice.dto.ThresholdResponse;
import com.example.thresholdservice.entity.StockThreshold;
import com.example.thresholdservice.repository.StockThresholdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
public class ThresholdService {

    private static final Logger logger = LoggerFactory.getLogger(ThresholdService.class);

    private final StockThresholdRepository thresholdRepository;
    private final PriceCacheService        priceCacheService;

    public ThresholdService(StockThresholdRepository thresholdRepository,
                            PriceCacheService priceCacheService) {
        this.thresholdRepository = thresholdRepository;
        this.priceCacheService   = priceCacheService;
    }


    @Transactional
    public ThresholdResponse setThreshold(String rawSymbol, ThresholdRequest request, String username) {
        String symbol = resolveAndValidateSymbol(rawSymbol);
        validatePercentages(request.getUpperThresholdPercent(),
                            request.getLowerThresholdPercent(), symbol);

        BigDecimal refPx  = snapshotReferencePrice(symbol);

        Optional<StockThreshold> existing = thresholdRepository.findByUsernameAndSymbol(username, symbol);

        StockThreshold threshold;

        if (existing.isEmpty()) {
            threshold = StockThreshold.builder()
                    .username(username)
                    .symbol(symbol)
                    .companyName(NseStocks.DISPLAY_NAMES.getOrDefault(symbol, symbol.replace(".NS", "")))
                    .upperThresholdPercent(request.getUpperThresholdPercent().setScale(2, RoundingMode.HALF_UP))
                    .lowerThresholdPercent(request.getLowerThresholdPercent().setScale(2, RoundingMode.HALF_UP))
                    .referencePrice(refPx)
                    .build();

            logger.info("User '{}' — creating threshold for '{}': upper={}%, lower={}%, refPrice={}",
                    username, symbol,
                    request.getUpperThresholdPercent(), request.getLowerThresholdPercent(), refPx);

        } else {
            threshold = existing.get();

            logger.info("User '{}' — updating threshold for '{}': "
                            + "upper {}% → {}%, lower {}% → {}%, refPrice {} → {}",
                    username, symbol,
                    threshold.getUpperThresholdPercent(), request.getUpperThresholdPercent(),
                    threshold.getLowerThresholdPercent(), request.getLowerThresholdPercent(),
                    threshold.getReferencePrice(), refPx);

            threshold.setUpperThresholdPercent(request.getUpperThresholdPercent().setScale(2, RoundingMode.HALF_UP));
            threshold.setLowerThresholdPercent(request.getLowerThresholdPercent().setScale(2, RoundingMode.HALF_UP));
            threshold.setReferencePrice(refPx);
        }

        StockThreshold saved = thresholdRepository.save(threshold);
        logger.info("User '{}' — threshold {} for '{}' — upperAlert={}, lowerAlert={}",
                username, existing.isEmpty() ? "created" : "updated", symbol,
                computeUpperAlert(refPx, saved.getUpperThresholdPercent()),
                computeLowerAlert(refPx, saved.getLowerThresholdPercent()));

        return toResponse(saved);
    }


    @Transactional(readOnly = true)
    public List<ThresholdResponse> getAllThresholds(String username) {
        logger.info("User '{}' — fetching all thresholds", username);

        List<ThresholdResponse> list = thresholdRepository.findByUsernameOrderBySymbolAsc(username)
                .stream()
                .map(this::toResponse)
                .toList();

        logger.info("User '{}' — {} threshold(s) returned", username, list.size());
        return list;
    }


    @Transactional(readOnly = true)
    public ThresholdResponse getThreshold(String rawSymbol, String username) {
        String symbol = resolveAndValidateSymbol(rawSymbol);

        logger.info("User '{}' — fetching threshold for '{}'", username, symbol);

        StockThreshold threshold = thresholdRepository.findByUsernameAndSymbol(username, symbol)
                .orElseThrow(() -> {
                    logger.warn("User '{}' — no threshold set for '{}'", username, symbol);
                    return new IllegalArgumentException(
                            "No threshold set for " + symbol.replace(".NS", "")
                            + ". Use PUT /api/thresholds/" + symbol.replace(".NS", "")
                            + " to create one.");
                });

        logger.info("User '{}' — threshold for '{}': upper={}%, lower={}%",
                username, symbol,
                threshold.getUpperThresholdPercent(), threshold.getLowerThresholdPercent());
        return toResponse(threshold);
    }


    @Transactional
    public void deleteThreshold(String rawSymbol, String username) {
        String symbol = resolveAndValidateSymbol(rawSymbol);

        logger.info("User '{}' — removing threshold for '{}'", username, symbol);

        StockThreshold threshold = thresholdRepository.findByUsernameAndSymbol(username, symbol)
                .orElseThrow(() -> {
                    logger.warn("User '{}' — delete failed: no threshold set for '{}'", username, symbol);
                    return new IllegalArgumentException(
                            "No threshold set for " + symbol.replace(".NS", "") + ".");
                });

        thresholdRepository.delete(threshold);
        logger.info("User '{}' — threshold for '{}' removed successfully", username, symbol);
    }


    private BigDecimal snapshotReferencePrice(String symbol) {
        Map<String, StockPriceMessage> quotes = priceCacheService.getCurrentQuotesMap();
        StockPriceMessage quote = quotes.get(symbol);
        if (quote == null || quote.getPrice() == 0.0) {
            logger.warn("No live market price available for '{}' — referencePrice stored as null", symbol);
            return null;
        }
        return BigDecimal.valueOf(quote.getPrice()).setScale(2, RoundingMode.HALF_UP);
    }


    private ThresholdResponse toResponse(StockThreshold t) {
        BigDecimal ref   = t.getReferencePrice();
        BigDecimal upper = t.getUpperThresholdPercent();
        BigDecimal lower = t.getLowerThresholdPercent();

        return ThresholdResponse.builder()
                .id(t.getId())
                .symbol(t.getSymbol())
                .displaySymbol(t.getSymbol().replace(".NS", ""))
                .companyName(t.getCompanyName())
                .upperThresholdPercent(upper)
                .lowerThresholdPercent(lower)
                .referencePrice(ref)
                .upperAlertPrice(computeUpperAlert(ref, upper))
                .lowerAlertPrice(computeLowerAlert(ref, lower))
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }


    private BigDecimal computeUpperAlert(BigDecimal referencePrice, BigDecimal upperPct) {
        if (referencePrice == null) return null;
        // factor = 1 + upper / 100
        BigDecimal factor = BigDecimal.ONE.add(
                upperPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return referencePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }


    private BigDecimal computeLowerAlert(BigDecimal referencePrice, BigDecimal lowerPct) {
        if (referencePrice == null) return null;
        // factor = 1 - lower / 100
        BigDecimal factor = BigDecimal.ONE.subtract(
                lowerPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return referencePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }


    private String normalizeSymbol(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        String upper   = trimmed.toUpperCase();

        // Already qualified
        if (upper.endsWith(".NS")) {
            return NseStocks.SYMBOLS.contains(upper) ? upper : null;
        }

        // Bare ticker — try appending .NS
        String withNs = upper + ".NS";
        if (NseStocks.SYMBOLS.contains(withNs)) return withNs;

        // Display name lookup (case-insensitive)
        for (Map.Entry<String, String> entry : NseStocks.DISPLAY_NAMES.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(trimmed)) return entry.getKey();
        }
        return null;
    }

    private String resolveAndValidateSymbol(String input) {
        String symbol = normalizeSymbol(input);
        if (symbol == null) {
            throw new IllegalArgumentException(
                    "'" + input + "' is not a valid Nifty 50 stock. "
                    + "Use GET /api/portfolio/stocks to see the full list.");
        }
        return symbol;
    }

    private void validatePercentages(BigDecimal upper, BigDecimal lower, String symbol) {
        String display = symbol != null ? symbol.replace(".NS", "") : "";
        if (upper == null || upper.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "upperThresholdPercent must be a positive number for " + display
                    + " (e.g. 5.0 means 5 %).");
        }
        if (lower == null || lower.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "lowerThresholdPercent must be a positive number for " + display
                    + " (e.g. 3.0 means 3 %).");
        }
    }
}
