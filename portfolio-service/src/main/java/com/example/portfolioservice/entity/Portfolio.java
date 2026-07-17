package com.example.portfolioservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning user, identified by JWT subject — no FK join to a users table,
    // since users live in a different service/schema now.
    @Column(name = "username", nullable = false)
    private String username;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false)
    private Integer quantity;


    @Column(name = "buying_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyingPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
