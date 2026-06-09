package com.example.portfolio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "stock_thresholds")
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


    @Column(nullable = false, length = 20)
    private String symbol;


    @Column(nullable = false, name = "company_name")
    private String companyName;

    @Column(nullable = false, precision = 8, scale = 2, name = "upper_threshold_percent")
    private BigDecimal upperThresholdPercent;

    @Column(nullable = false, precision = 8, scale = 2, name = "lower_threshold_percent")
    private BigDecimal lowerThresholdPercent;

    @Column(precision = 12, scale = 2, name = "reference_price")
    private BigDecimal referencePrice;


    @Column(name = "last_alert_type", length = 10)
    private String lastAlertType;


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
