package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.repository.FinancialReportRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DepreciationService {

    private static final Logger logger = LoggerFactory.getLogger(DepreciationService.class);

    @Autowired
    private FinancialReportService financialReportService;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Transactional
    public void calculateMonthlyDepreciation() {
        logger.info("Starting monthly depreciation calculation for all assets");

        try {
            int pageSize = 100;
            int pageNumber = 0;
            Page<tb_FinancialReport> reportPage;
            int totalProcessed = 0;
            int totalFailed = 0;
            List<tb_FinancialReport> batchToSave = new ArrayList<>();

            do {
                Pageable pageable = PageRequest.of(pageNumber, pageSize);
                reportPage = financialReportService.findByStatusFlagNotAndNetCostGreaterThan(
                        "DECOMMISSIONED", BigDecimal.ZERO, pageable);
                List<tb_FinancialReport> reports = reportPage.getContent();

                for (tb_FinancialReport report : reports) {
                    try {
                        String assetSerialNumber = report.getAssetSerialNumber();
                        String assetName = report.getAssetName();

                        // Skip if both assetSerialNumber and assetName are null
                        if (assetSerialNumber == null && assetName == null) {
                            logger.warn("Skipping asset with null serial number and name: {}", report.getId());
                            continue;
                        }

                        // Skip assets that are decommissioned or have zero net cost
                        if (report.getWriteOffDate() != null ||
                                report.getNetCost().compareTo(BigDecimal.ZERO) <= 0) {
                            logger.debug("Skipping asset {}: already decommissioned or net cost is zero",
                                    assetSerialNumber != null ? assetSerialNumber : assetName);
                            continue;
                        }

                        // Skip if usefulLifeMonths is invalid
                        if (report.getUsefulLifeMonths() == null || report.getUsefulLifeMonths() <= 0) {
                            logger.warn("Skipping asset {}: useful life months is missing or invalid", assetSerialNumber != null ? assetSerialNumber : assetName);
                            continue;
                        }

                        // Calculate depreciation directly on the fetched report
                        BigDecimal monthlyDep = financialReportService.computeMonthlyDepreciation(report);
                        if (monthlyDep != null) {
                            report.setMonthlyDepreciationAmount(monthlyDep);
                            BigDecimal newAccumulated = report.getAccumulatedDepreciation().add(monthlyDep);
                            report.setAccumulatedDepreciation(newAccumulated);
                            BigDecimal newNetCost = report.getNetCost().subtract(monthlyDep);
                            report.setNetCost(newNetCost);
                            batchToSave.add(report);
                            totalProcessed++;
                            logger.debug("Depreciation calculated for asset: {}", assetSerialNumber != null ? assetSerialNumber : assetName);
                        } else {
                            logger.warn("Skipping asset {}: could not compute monthly depreciation", assetSerialNumber != null ? assetSerialNumber : assetName);
                        }
                    } catch (Exception e) {
                        totalFailed++;
                        logger.error("Failed to calculate depreciation for asset {}: {}",
                                report.getAssetSerialNumber() != null ? report.getAssetSerialNumber() : report.getAssetName(),
                                e.getMessage());
                    }
                }

                // Batch save after processing page
                if (!batchToSave.isEmpty()) {
                    financialReportRepo.saveAll(batchToSave);
                    batchToSave.clear();
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