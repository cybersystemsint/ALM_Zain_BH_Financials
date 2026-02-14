package com.zain.bh.alm.financials.service;

import org.springframework.stereotype.Service;

@Service
public class DepreciationCalculationService {

    public double calculateTotalDepreciation(double initialCost, double salvageValue, int usefulLife, int yearDiff) {
        if (usefulLife == 0) {
            return 0.0;
        }
        double annualDepreciation = (initialCost - salvageValue) / usefulLife;
        return annualDepreciation * yearDiff;
    }

    public double calculateReducingBalanceDepreciation(double initialCost, double salvageValue, int usefulLife) {
        double depreciationRate = 0.25;
        double accumulatedDepreciation = 0.0;
        
        for (int year = 1; year <= usefulLife; year++) {
            double depreciation = (initialCost - accumulatedDepreciation) * depreciationRate;
            accumulatedDepreciation += depreciation;
        }
        return accumulatedDepreciation;
    }
}
