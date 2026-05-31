package com.telkom.co.ke.almoptics.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.telkom.co.ke.almoptics.entities.ConsolidatedFinancialReport;

public interface ConsolidatedFinancialReportRepository
        extends JpaRepository<ConsolidatedFinancialReport, Long>,
                JpaSpecificationExecutor<ConsolidatedFinancialReport> {

    /** Grand totals – no filter, fast aggregate on the view. */
    @Query("""
            SELECT
                SUM(r.initialCost)              AS totalCost,
                SUM(r.accumulatedDepreciation)  AS totalDepreciation,
                SUM(r.initialCost)
                    - SUM(r.accumulatedDepreciation) AS totalNBV
            FROM ConsolidatedFinancialReport r
            """)
    SummaryTotalsProjection calculateTotalSummary();

    interface SummaryTotalsProjection {
        java.math.BigDecimal getTotalCost();
        java.math.BigDecimal getTotalNBV();
        java.math.BigDecimal getTotalDepreciation();
    }
}