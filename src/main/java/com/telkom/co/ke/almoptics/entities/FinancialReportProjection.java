package com.telkom.co.ke.almoptics.entities;

import java.math.BigDecimal;

public interface FinancialReportProjection {
    BigDecimal getInitialCost();
    BigDecimal getSalvageValue();
    String getDateOfService();
    Integer getUsefulLifeMonths();
    BigDecimal getMonthlyDepreciationAmount();
}