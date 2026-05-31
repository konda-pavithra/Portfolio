package com.example.portfolio.repository;


import com.example.portfolio.entity.Portfolio;
import com.example.portfolio.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {


    List<Portfolio> findByUserOrderBySymbolAsc(User user);


    Optional<Portfolio> findByUserAndSymbol(User user, String symbol);


    boolean existsByUserAndSymbol(User user, String symbol);
}
