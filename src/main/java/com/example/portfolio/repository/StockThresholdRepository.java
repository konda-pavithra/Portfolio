package com.example.portfolio.repository;


import com.example.portfolio.entity.StockThreshold;
import com.example.portfolio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link StockThreshold} — one row per (user, symbol) pair.
 */
public interface StockThresholdRepository extends JpaRepository<StockThreshold, Long> {


    List<StockThreshold> findByUserOrderBySymbolAsc(User user);

    Optional<StockThreshold> findByUserAndSymbol(User user, String symbol);

    /** Fast existence check used before insert to decide create vs. update. */
    boolean existsByUserAndSymbol(User user, String symbol);


    @Query("""
           SELECT t FROM StockThreshold t
           JOIN FETCH t.user
           WHERE EXISTS (
               SELECT p FROM Portfolio p
               WHERE p.user = t.user AND p.symbol = t.symbol
           )
           """)
    List<StockThreshold> findAllWithPortfolioHolding();
}
