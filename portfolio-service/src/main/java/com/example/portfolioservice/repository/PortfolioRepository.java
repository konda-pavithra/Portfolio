package com.example.portfolioservice.repository;


import com.example.portfolioservice.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUsernameOrderBySymbolAsc(String username);

    Optional<Portfolio> findByUsernameAndSymbol(String username, String symbol);

    boolean existsByUsernameAndSymbol(String username, String symbol);
}
