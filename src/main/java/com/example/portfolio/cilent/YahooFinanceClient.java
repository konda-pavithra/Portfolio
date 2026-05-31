package com.example.portfolio.cilent;


import com.example.portfolio.cilent.dto.YahooFinanceQuoteResponse;
import com.example.portfolio.cilent.dto.YahooFinanceQuoteResponse.YahooQuote;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Low-level HTTP client for the Yahoo Finance v7 quote API.
 *
 * Session management (2025+ Yahoo Finance requirement):
 *   Step 1 — GET consent URL  → obtains session cookie
 *   Step 2 — GET crumb URL    → obtains crumb token (requires cookie)
 *   Step 3 — GET quote URL    → append crumb as query param + send cookie
 *
 * If Yahoo Finance responds with 401/403 the client refreshes its session
 * automatically and retries the request exactly once.
 */
@Component
public class YahooFinanceClient {


}
