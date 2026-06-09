package com.example.portfolio.consumer;

import com.example.portfolio.service.EmailService;
import com.example.portfolio.config.RabbitMQConfig;
import com.example.portfolio.dto.StockAlertMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockAlertConsumer {

    private static final Logger logger = LoggerFactory.getLogger(StockAlertConsumer.class);

    private final EmailService emailService;

    public StockAlertConsumer(EmailService emailService) {
        this.emailService = emailService;
    }


    @RabbitListener(
        queues             = RabbitMQConfig.ALERT_QUEUE,
        containerFactory   = "rabbitListenerContainerFactory"
    )
    public void handleAlert(StockAlertMessage message) throws Exception {

        logger.info("Received alert message — user='{}', symbol='{}', type={}, currentPrice=₹{}",
                message.getUsername(),
                message.getDisplaySymbol(),
                message.getAlertType(),
                message.getCurrentPrice());

        try {
            emailService.sendThresholdAlert(message);

            logger.info("Alert email sent successfully → '{}' for '{}' ({} breach, P&L: {}₹{})",
                    message.getUserEmail(),
                    message.getDisplaySymbol(),
                    message.getAlertType(),
                    message.isGain() ? "+" : "-",
                    message.getProfitLoss().abs());

        } catch (Exception ex) {
            logger.error("Failed to send alert email to '{}' for '{}' (type={}): {}",
                    message.getUserEmail(),
                    message.getDisplaySymbol(),
                    message.getAlertType(),
                    ex.getMessage(), ex);

            // Re-throw so the container's retry interceptor handles retries → DLQ
            throw ex;
        }
    }
}
