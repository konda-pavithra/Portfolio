package com.example.stockservice.client;

import com.example.stockservice.client.dto.YahooChartResponse;
import com.example.stockservice.client.dto.YahooChartResponse.Meta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class YahooChartClient {

    private static final String BASE_URL = "https://query2.finance.yahoo.com/v8/finance/chart/";

    // 10 parallel threads — fast enough for 50 symbols without hammering Yahoo
    private static final ExecutorService POOL = Executors.newFixedThreadPool(10);

    private final RestTemplate restTemplate;

    public YahooChartClient(@Qualifier("stockRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }


    public List<Meta> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyList();

        List<CompletableFuture<Meta>> futures = symbols.stream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> fetchOne(symbol), POOL))
                .toList();

        List<Meta> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        log.info("Yahoo chart API: fetched {}/{} quotes successfully", results.size(), symbols.size());
        return results;
    }


    private Meta fetchOne(String symbol) {
        String encodedSymbol = UriUtils.encodePathSegment(symbol, StandardCharsets.UTF_8);
        String url = BASE_URL + encodedSymbol + "?interval=1d&range=1d";
        try {
            ResponseEntity<YahooChartResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers()),
                    YahooChartResponse.class);

            YahooChartResponse body = response.getBody();
            if (body == null
                    || body.getChart() == null
                    || body.getChart().getResult() == null
                    || body.getChart().getResult().isEmpty()) {
                log.warn("Empty response for {}", symbol);
                return null;
            }

            Meta meta = body.getChart().getResult().get(0).getMeta();
            if (meta == null || meta.getRegularMarketPrice() == null) {
                log.warn("No price data for {}", symbol);
                return null;
            }

            log.debug("{} → ₹{}", symbol, meta.getRegularMarketPrice());
            return meta;

        } catch (Exception ex) {
            log.warn("Failed to fetch {}: {}", symbol, ex.getMessage());
            return null;
        }
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36");
        h.set(HttpHeaders.ACCEPT, "application/json");
        h.set("Referer", "https://finance.yahoo.com");
        return h;
    }
}
