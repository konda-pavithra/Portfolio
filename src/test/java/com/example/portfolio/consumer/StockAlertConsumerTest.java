package com.example.portfolio.consumer;

import com.example.portfolio.dto.StockAlertMessage;
import com.example.portfolio.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAlertConsumerTest {

    @Mock EmailService emailService;

    @InjectMocks StockAlertConsumer consumer;

    @Test
    void handleAlert_success_callsEmailServiceAndDoesNotThrow() throws Exception {
        StockAlertMessage msg = buildMessage("UPPER");

        assertDoesNotThrow(() -> consumer.handleAlert(msg));

        verify(emailService).sendThresholdAlert(msg);
    }

    @Test
    void handleAlert_emailThrows_rethrowsForRetry() throws Exception {
        StockAlertMessage msg = buildMessage("LOWER");
        doThrow(new RuntimeException("SMTP connection refused")).when(emailService).sendThresholdAlert(msg);

        // Re-thrown so RabbitMQ retry interceptor can retry → DLQ
        assertThrows(RuntimeException.class, () -> consumer.handleAlert(msg));
        verify(emailService).sendThresholdAlert(msg);
    }

    @Test
    void handleAlert_upperBreach_processedWithoutError() throws Exception {
        StockAlertMessage msg = buildMessage("UPPER");
        consumer.handleAlert(msg);
        verify(emailService, times(1)).sendThresholdAlert(msg);
    }

    @Test
    void handleAlert_lowerBreach_processedWithoutError() throws Exception {
        StockAlertMessage msg = buildMessage("LOWER");
        consumer.handleAlert(msg);
        verify(emailService, times(1)).sendThresholdAlert(msg);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private StockAlertMessage buildMessage(String alertType) {
        return StockAlertMessage.builder()
                .username("john_doe")
                .userEmail("john@example.com")
                .symbol("RELIANCE.NS")
                .displaySymbol("RELIANCE")
                .companyName("Reliance Industries")
                .alertType(alertType)
                .thresholdPercent(new BigDecimal("5.00"))
                .referencePrice(new BigDecimal("2000.00"))
                .alertPrice(new BigDecimal("2100.00"))
                .currentPrice(new BigDecimal("2150.00"))
                .quantity(10)
                .buyingPrice(new BigDecimal("2000.00"))
                .investmentValue(new BigDecimal("20000.00"))
                .currentValue(new BigDecimal("21500.00"))
                .profitLoss(new BigDecimal("1500.00"))
                .plPercent(7.5)
                .gain(true)
                .build();
    }
}
