package com.telkom.co.ke.almoptics.services;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.repository.FinancialReportRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
public class DepreciationService {
    private static final Logger logger = LoggerFactory.getLogger(DepreciationService.class);
    @Autowired
    private FinancialReportService financialReportService;
    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Scheduled(cron = "0 0 0 L * ?") // Runs at midnight (00:00:00) on the last day of every month
    @Transactional
    public void calculateMonthlyDepreciation() {
        logger.info("Starting monthly depreciation calculation for all assets");
        try {
            int totalUpdated = 0;
            // Step 1: Reset MonthlyDepreciationAmount and AccumulatedDepreciation to NULL for valid assets
            int resetMonthly = financialReportRepo.resetMonthlyDepreciationAmount();
            int resetAccumulated = financialReportRepo.resetAccumulatedDepreciation();
            int resetNetcost = financialReportRepo.resetNetCost();
            logger.info("Reset {} records for MonthlyDepreciationAmount, {} for AccumulatedDepreciation, and {} for NetCost", resetMonthly, resetAccumulated, resetNetcost);

            // Step 2: Update MonthlyDepreciationAmount for valid assets
            int updatedMonthly = financialReportRepo.updateMonthlyDepreciation();
            totalUpdated += updatedMonthly;
            logger.info("Updated MonthlyDepreciationAmount for {} assets", updatedMonthly);

            // Step 3: Update AccumulatedDepreciation for valid assets
            int updatedAccumulated = financialReportRepo.updateAccumulatedDepreciation();
            totalUpdated += updatedAccumulated;
            logger.info("Updated AccumulatedDepreciation for {} assets", updatedAccumulated);

            // Step 4: Adjust NetCost and AccumulatedDepreciation where necessary
            int adjusted = financialReportRepo.adjustNetCostAndAccumulatedDepreciation();
            logger.info("Adjusted NetCost and AccumulatedDepreciation for {} assets", adjusted);

            // Step 5: Update NetCost for valid assets (batch update)
            int updateNetCost = financialReportRepo.updateNetCost();
            logger.info("Updated NetCost {} assets", updateNetCost);

            // Step 6: Update RetirementDate for valid assets (batch update)
            int updatedRetirement = financialReportRepo.updateRetirementDate();
            logger.info("Updated RetirementDate for {} assets", updatedRetirement);


            logger.info("Monthly depreciation calculation completed. Total updated: {}", totalUpdated);
        } catch (Exception e) {
            logger.error("Unexpected error during monthly depreciation calculation", e);
        }
    }
}