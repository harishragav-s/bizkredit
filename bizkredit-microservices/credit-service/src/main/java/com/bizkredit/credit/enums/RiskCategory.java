package com.bizkredit.credit.enums;

// Risk classification of the borrower based on financial analysis
public enum RiskCategory {
    LOW,        // Strong financials, low default risk
    MEDIUM,     // Moderate financials, manageable risk
    HIGH,       // Weak financials, high default risk
    WATCHLIST   // Under special monitoring
}
