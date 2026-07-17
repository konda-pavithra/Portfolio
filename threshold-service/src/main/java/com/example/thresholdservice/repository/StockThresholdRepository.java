package com.example.thresholdservice.repository;


import com.example.thresholdservice.entity.StockThreshold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link StockThreshold} — one row per (username, symbol) pair.
 */
public interface StockThresholdRepository extends JpaRepository<StockThreshold, Long> {

    List<StockThreshold> findByUsernameOrderBySymbolAsc(String username);

    Optional<StockThreshold> findByUsernameAndSymbol(String username, String symbol);

    /** Fast existence check used before insert to decide create vs. update. */
    boolean existsByUsernameAndSymbol(String username, String symbol);
}
