package com.telkom.co.ke.almoptics;

import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DepreciationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DepreciationScheduler.class);

    @Autowired
    private FinancialReportService financialReportService;

    /**
     * Scheduled task to calculate depreciation for all active financial reports.
     * Runs on the 1st day of each month at 00:01 AM in Africa/Nairobi timezone.
     */
    @Scheduled(cron = "${depreciation.scheduler.cron:0 1 0 1 * ?}", zone = "Africa/Nairobi")
    public void calculateMonthlyDepreciation() {
        logger.info("Starting monthly depreciation calculation for all assets");

        try {
            // Process assets in batches
            int pageSize = 100;
            int pageNumber = 0;
            Page<tb_FinancialReport> reportPage;
            int totalProcessed = 0;
            int totalFailed = 0;

            do {
                Pageable pageable = PageRequest.of(pageNumber, pageSize);
                reportPage = financialReportService.findByStatusFlagNotAndNetCostGreaterThan(
                        "DECOMMISSIONED", BigDecimal.ZERO, pageable);
                List<tb_FinancialReport> reports = reportPage.getContent();

                for (tb_FinancialReport report : reports) {
                    try {
                        // Skip assets that are decommissioned or have zero net cost
                        if (report.getWriteOffDate() != null ||
                                report.getNetCost().compareTo(BigDecimal.ZERO) <= 0) {
                            logger.debug("Skipping asset {}: already decommissioned or net cost is zero",
                                    report.getAssetSerialNumber());
                            continue;
                        }

                        financialReportService.calculateDepreciation(
                                report.getAssetSerialNumber(),
                                report.getAdjustment(), // Use existing adjustment
                                "system"
                        );
                        totalProcessed++;
                        logger.debug("Depreciation calculated for asset: {}", report.getAssetSerialNumber());
                    } catch (Exception e) {
                        totalFailed++;
                        logger.error("Failed to calculate depreciation for asset {}: {}",
                                report.getAssetSerialNumber(), e.getMessage());
                    }
                }

                pageNumber++;
            } while (reportPage.hasNext());

            logger.info("Monthly depreciation calculation completed. Processed: {}, Failed: {}",
                    totalProcessed, totalFailed);
        } catch (Exception e) {
            logger.error("Unexpected error during monthly depreciation calculation", e);
        }
    }
}
