package com.example.portfolio.exception;

import com.example.portfolio.dto.ErrorResponse;
import com.example.portfolio.dto.PortfolioResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest      request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void handleInvalidEmail_returns400WithMessage() {
        var ex = new InvalidEmailException("Invalid email format: 'bad'");
        ResponseEntity<ErrorResponse> resp = handler.handleInvalidEmail(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(400, resp.getBody().getStatus());
        assertEquals("Invalid email format: 'bad'", resp.getBody().getMessage());
        assertEquals("/api/test", resp.getBody().getPath());
    }

    @Test
    void handleInvalidPassword_returns400() {
        var ex = new InvalidPasswordException("Password too weak");
        ResponseEntity<ErrorResponse> resp = handler.handleInvalidPassword(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Password too weak", resp.getBody().getMessage());
    }

    @Test
    void handleUserAlreadyExists_returns409() {
        var ex = new UserAlreadyExistsException("Username already taken");
        ResponseEntity<ErrorResponse> resp = handler.handleUserAlreadyExists(ex, request);

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(409, resp.getBody().getStatus());
    }

    @Test
    void handleInvalidCredentials_returns401() {
        var ex = new InvalidCredentialsException("Invalid username or password");
        ResponseEntity<ErrorResponse> resp = handler.handleInvalidCredentials(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals(401, resp.getBody().getStatus());
    }

    @Test
    void handleStockConflict_returns409WithExistingHolding() {
        PortfolioResponse existing = PortfolioResponse.builder()
                .id(1L).symbol("RELIANCE.NS").displaySymbol("RELIANCE")
                .quantity(5).buyingPrice(new BigDecimal("2300.00"))
                .totalInvestment(new BigDecimal("11500.00")).build();
        var ex = new StockAlreadyInPortfolioException("RELIANCE is already in your portfolio", existing);

        ResponseEntity<Map<String, Object>> resp = handler.handleStockConflict(ex, request);

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        assertEquals(409, resp.getBody().get("status"));
        assertNotNull(resp.getBody().get("existingHolding"));
        assertEquals(existing, resp.getBody().get("existingHolding"));
    }

    @Test
    void handleInvalidFile_returns400() {
        var ex = new InvalidFileException("Only .xls and .xlsx files are accepted");
        ResponseEntity<ErrorResponse> resp = handler.handleInvalidFile(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Only .xls and .xlsx files are accepted", resp.getBody().getMessage());
    }

    @Test
    void handleIllegalArgument_returns400() {
        var ex = new IllegalArgumentException("Quantity must be a positive integer");
        ResponseEntity<ErrorResponse> resp = handler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("Quantity must be a positive integer", resp.getBody().getMessage());
    }

    @Test
    void handleGeneral_returns500WithGenericMessage() {
        var ex = new RuntimeException("Some unexpected database error");
        ResponseEntity<ErrorResponse> resp = handler.handleGeneral(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals(500, resp.getBody().getStatus());
        assertEquals("An unexpected error occurred", resp.getBody().getMessage());
    }

    @Test
    void errorResponse_alwaysContainsTimestamp() {
        var ex = new IllegalArgumentException("bad input");
        ResponseEntity<ErrorResponse> resp = handler.handleIllegalArgument(ex, request);

        assertNotNull(resp.getBody().getTimestamp());
    }

    @Test
    void errorResponse_errorFieldMatchesStatusReasonPhrase() {
        var ex = new InvalidEmailException("bad email");
        ResponseEntity<ErrorResponse> resp = handler.handleInvalidEmail(ex, request);

        assertEquals("Bad Request", resp.getBody().getError());
    }

    @Test
    void handleStockConflict_responseBodyContainsAllRequiredFields() {
        PortfolioResponse existing = PortfolioResponse.builder().id(1L).symbol("TCS.NS").build();
        var ex = new StockAlreadyInPortfolioException("TCS conflict", existing);

        ResponseEntity<Map<String, Object>> resp = handler.handleStockConflict(ex, request);
        Map<String, Object> body = resp.getBody();

        assertTrue(body.containsKey("timestamp"));
        assertTrue(body.containsKey("status"));
        assertTrue(body.containsKey("error"));
        assertTrue(body.containsKey("message"));
        assertTrue(body.containsKey("path"));
        assertTrue(body.containsKey("existingHolding"));
    }
}
