package com.zain.bh.alm.financials.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.service.FinancialReportService;

import java.math.BigDecimal;

@Component
public class DepreciationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DepreciationScheduler.class);
    private static final int PAGE_SIZE = 100;
    private static final String SYSTEM_USER = "system";

    private final FinancialReportService financialReportService;

    public DepreciationScheduler(FinancialReportService financialReportService) {
        this.financialReportService = financialReportService;
    }

    @Scheduled(cron = "${depreciation.scheduler.cron:0 1 0 1 * ?}", zone = "Africa/Nairobi")
    public void calculateMonthlyDepreciation() {
        LOGGER.info("Starting monthly depreciation calculation");
        int totalProcessed = 0;
        int totalFailed = 0;

        try {
            Page<FinancialReport> reportPage;
            int pageNumber = 0;

            do {
                reportPage = financialReportService.findByStatusFlagNotAndNetCostGreaterThan(
                        "DECOMMISSIONED", BigDecimal.ZERO, PageRequest.of(pageNumber++, PAGE_SIZE));

                for (FinancialReport report : reportPage.getContent()) {
                    int result = processDepreciation(report);
                    if (result == 1) {
                        totalProcessed++;
                    } else {
                        totalFailed++;
                    }
                }
            } while (!reportPage.isLast());

            LOGGER.info("Monthly depreciation completed. Processed: {}, Failed: {}", totalProcessed, totalFailed);
        } catch (Exception e) {
            LOGGER.error("Error during monthly depreciation calculation: {}", e.getMessage());
        }
    }

    private int processDepreciation(FinancialReport report) {
        try {
            financialReportService.calculateDepreciation(
                    report.getAssetSerialNumber(),
                    report.getAdjustment(),
                    SYSTEM_USER);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to calculate depreciation for asset {}: {}",
                    report.getAssetSerialNumber(), e.getMessage());
            return 0;
        }
    }
}
