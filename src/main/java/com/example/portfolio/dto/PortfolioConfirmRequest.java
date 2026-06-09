package com.example.portfolio.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PortfolioConfirmRequest {

    private List<PortfolioEntry> toAdd = new ArrayList<>();

    private List<PortfolioEntry> toUpdate = new ArrayList<>();
}
