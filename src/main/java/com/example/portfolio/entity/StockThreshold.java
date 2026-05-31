package com.example.portfolio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(
    name = "stock_thresholds",
    uniqueConstraints = @UniqueConstraint(
        name  = "uq_threshold_user_symbol",
        columnNames = {"user_id", "symbol"}
    )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Canonical Yahoo Finance symbol, e.g. "RELIANCE.NS". */
    @Column(nullable = false, length = 20)
    private String symbol;

    /** Human-readable company name, e.g. "Reliance Industries Limited". */
    @Column(nullable = false, name = "company_name")
    private String companyName;

    /**
     * Upper price threshold as a positive percentage.
     * E.g. 5.00 means "alert when price is 5 % above the reference price".
     */
    @Column(nullable = false, precision = 8, scale = 2, name = "upper_threshold_percent")
    private BigDecimal upperThresholdPercent;

    /**
     * Lower price threshold as a positive percentage.
     * E.g. 3.00 means "alert when price is 3 % below the reference price".
     */
    @Column(nullable = false, precision = 8, scale = 2, name = "lower_threshold_percent")
    private BigDecimal lowerThresholdPercent;

    /**
     * Market price at the moment this threshold was last saved.
     * Alert prices are computed relative to this value.
     * {@code null} when the stock ticker cache had no data at save time.
     */
    @Column(precision = 12, scale = 2, name = "reference_price")
    private BigDecimal referencePrice;

    // ── Alert deduplication ──────────────────────────────────────────────────

    /**
     * Type of the most recent alert sent for this threshold ("UPPER" or "LOWER").
     * {@code null} if no alert has been sent yet.
     * Used together with {@link #lastAlertSentAt} to enforce the cooldown window.
     */
    @Column(name = "last_alert_type", length = 10)
    private String lastAlertType;

    /**
     * Timestamp of the most recent alert that was published to RabbitMQ.
     * {@code null} if no alert has been sent yet.
     */
    @Column(name = "last_alert_sent_at")
    private LocalDateTime lastAlertSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
