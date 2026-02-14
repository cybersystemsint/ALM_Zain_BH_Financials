package com.zain.bh.alm.financials.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.repository.FinancialReportRepository;
import com.zain.bh.alm.financials.service.ApprovalWorkflowService;
import com.zain.bh.alm.financials.service.FinancialReportService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class FinancialReportServiceImpl implements FinancialReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialReportServiceImpl.class);

    @Autowired
    private FinancialReportRepository FinancialReportRepository;

    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    @Transactional
    public FinancialReport calculateDepreciation(String serialNumber, BigDecimal adjustment, String username) {
        LOGGER.info("Calculating depreciation for asset serial number: {}", serialNumber);

        Optional<FinancialReport> reportOpt = FinancialReportRepository.findByAssetSerialNumber(serialNumber);
        if (!reportOpt.isPresent()) {
            LOGGER.error("Financial report not found for serial number: {}", serialNumber);
            throw new IllegalArgumentException("Financial report not found for serial number: " + serialNumber);
        }

        FinancialReport report = reportOpt.get();

        // Validate mandatory fields
        if (report.getInitialCost() == null || report.getInitialCost().compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.error("Initial cost is missing or invalid for serial number: {}", serialNumber);
            throw new IllegalArgumentException("Initial cost is mandatory and must be greater than zero");
        }
        if (report.getDateOfService() == null || report.getDateOfService().trim().isEmpty()) {
            LOGGER.error("Date of service is missing for serial number: {}", serialNumber);
            throw new IllegalArgumentException("Date of service is mandatory");
        }
        if (report.getUsefulLifeMonths() == null || report.getUsefulLifeMonths() <= 0) {
            LOGGER.error("Useful life months is missing or invalid for serial number: {}", serialNumber);
            throw new IllegalArgumentException("Useful life months is mandatory and must be greater than zero");
        }

        // Parse dates
        LocalDate dateOfService;
        LocalDate installationDate = null;
        try {
            dateOfService = LocalDate.parse(report.getDateOfService(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            if (report.getInstallationDate() != null && !report.getInstallationDate().trim().isEmpty()) {
                installationDate = LocalDate.parse(report.getInstallationDate(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                if (dateOfService.isBefore(installationDate)) {
                    LOGGER.error("Date of service is before installation date for serial number: {}", serialNumber);
                    throw new IllegalArgumentException("Date of service cannot be before installation date");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Invalid date format for serial number: {}", serialNumber, e);
            throw new IllegalArgumentException("Invalid date format for dateOfService or installationDate");
        }

        // Set default salvage value if null
        if (report.getSalvageValue() == null) {
            report.setSalvageValue(BigDecimal.ZERO);
        }

        // Validate adjustment
        if (adjustment == null) {
            adjustment = BigDecimal.ZERO;
        } else if (adjustment.abs().compareTo(report.getInitialCost()) >= 0) {
            LOGGER.error("Adjustment value {} exceeds initial cost {} for serial number: {}",
                    adjustment, report.getInitialCost(), serialNumber);
            throw new IllegalArgumentException("Adjustment value must be less than initial cost");
        }

        // Calculate Monthly Depreciation (MD = (IC - Salvage Value) / L)
        BigDecimal monthlyDepreciation = report.getInitialCost()
                .subtract(report.getSalvageValue())
                .divide(new BigDecimal(report.getUsefulLifeMonths()), 3, RoundingMode.HALF_UP);
        report.setMonthlyDepreciationAmount(monthlyDepreciation);

        // Calculate Number of Months Utilised (NoMU = Current Date - D)
        LocalDate currentDate = LocalDate.now();
        // D = 1st of the next calendar month from Date of Service
        LocalDate d = dateOfService.withDayOfMonth(1).plusMonths(1);
        long numberOfMonthsUtilised = YearMonth.from(d).until(YearMonth.from(currentDate),
                java.time.temporal.ChronoUnit.MONTHS);
        if (numberOfMonthsUtilised < 0) {
            numberOfMonthsUtilised = 0; // Asset not yet in service
        }

        // Adjust NoMU if asset is decommissioned
        if (report.getWriteOffDate() != null) {
            LocalDate writeOffDate = report.getWriteOffDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            LocalDate lastDayOfWriteOffMonth = writeOffDate.withDayOfMonth(
                    writeOffDate.lengthOfMonth());
            numberOfMonthsUtilised = Math.min(numberOfMonthsUtilised,
                    YearMonth.from(d).until(YearMonth.from(lastDayOfWriteOffMonth),
                            java.time.temporal.ChronoUnit.MONTHS));
        }

        // Calculate Accumulated Depreciation (AD = MD * NoMU + ADJ)
        BigDecimal accumulatedDepreciation = monthlyDepreciation
                .multiply(new BigDecimal(numberOfMonthsUtilised))
                .add(adjustment)
                .setScale(3, RoundingMode.HALF_UP);

        // Ensure AD does not exceed (IC - Salvage Value)
        BigDecimal maxAccumulatedDepreciation = report.getInitialCost()
                .subtract(report.getSalvageValue()).setScale(3, RoundingMode.HALF_UP);
        accumulatedDepreciation = accumulatedDepreciation.min(maxAccumulatedDepreciation);

        // Check if AD + ADJ <= IC
        if (accumulatedDepreciation.add(adjustment).compareTo(report.getInitialCost()) > 0) {
            LOGGER.error("Accumulated depreciation + adjustment exceeds initial cost for serial number: {}", serialNumber);
            throw new IllegalArgumentException("Accumulated depreciation plus adjustment cannot exceed initial cost");
        }

        report.setAccumulatedDepreciation(accumulatedDepreciation);
        report.setAdjustment(adjustment);

        // Calculate Net Cost (NC = IC - AD)
        BigDecimal netCost = report.getInitialCost()
                .subtract(accumulatedDepreciation)
                .setScale(3, RoundingMode.HALF_UP);

        // Ensure NC is not less than Salvage Value
        netCost = netCost.max(report.getSalvageValue());

        // Stop calculations if NC = 0
        if (netCost.compareTo(BigDecimal.ZERO) <= 0) {
            netCost = report.getSalvageValue();
            accumulatedDepreciation = report.getInitialCost().subtract(report.getSalvageValue());
            report.setAccumulatedDepreciation(accumulatedDepreciation);
        }

        report.setNetCost(netCost);

        // Calculate Date of Asset Retirement (DoAR = (D + L) - 1 day)
        LocalDate dateOfRetirement = d.plusMonths(report.getUsefulLifeMonths()).minusDays(1);
        if (report.getWriteOffDate() != null) {
            LocalDate writeOffDate = report.getWriteOffDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            dateOfRetirement = writeOffDate.withDayOfMonth(writeOffDate.lengthOfMonth());
        }
        report.setRetirementDate(Date.from(dateOfRetirement.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));

        // Save original state for approval workflow
        try {
            Map<String, Object> originalState = new HashMap<>();
            originalState.put("initialCost", report.getInitialCost());
            originalState.put("salvageValue", report.getSalvageValue());
            originalState.put("usefulLifeMonths", report.getUsefulLifeMonths());
            originalState.put("dateOfService", report.getDateOfService());
            originalState.put("monthlyDepreciationAmount", report.getMonthlyDepreciationAmount());
            originalState.put("accumulatedDepreciation", report.getAccumulatedDepreciation());
            originalState.put("netCost", report.getNetCost());
            originalState.put("adjustment", report.getAdjustment());
            originalState.put("retirementDate", report.getRetirementDate());
            report.setOriginalState(objectMapper.writeValueAsString(originalState));
        } catch (Exception e) {
            LOGGER.error("Failed to serialize original state for serial number: {}", serialNumber, e);
            throw new RuntimeException("Failed to serialize original state", e);
        }

        // Update metadata
        report.setChangedBy(username);
        report.setChangeDate(new Date());
        report.setFinancialApprovalStatus("Pending L1 Approval");

        // Save the report
        FinancialReport savedReport = FinancialReportRepository.save(report);

        // Trigger approval workflow
        approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(),"Pending Modification");

        LOGGER.info("Depreciation calculated and saved for serial number: {}", serialNumber);
        return savedReport;
    }

    @Override
    public Page<FinancialReport> findByStatusFlagNotAndNetCostGreaterThan(String statusFlag, BigDecimal netCost, Pageable pageable) {
        return FinancialReportRepository.findByStatusFlagNotAndNetCostGreaterThan(statusFlag, netCost, pageable);
    }

    @Override
    public List<FinancialReport> findAll() {
        return FinancialReportRepository.findAll();
    }

    @Override
    public Page<FinancialReport> findAll(Pageable pageable) {
        return FinancialReportRepository.findAll(pageable);
    }

    @Override
    public Page<FinancialReport> findBySearchTerm(String search, Pageable pageable) {
        return FinancialReportRepository.findBySearchTerm(search, pageable);
    }

    @Override
    public FinancialReport save(FinancialReport report) {
        return FinancialReportRepository.save(report);
    }

    @Override
    public Optional<FinancialReport> findBySerialNumber(String serialNumber) {
        return FinancialReportRepository.findByAssetSerialNumber(serialNumber);
    }

    @Override
    public List<FinancialReport> findAllBySerialNumber(String serialNumber) {
        return FinancialReportRepository.findAllByAssetSerialNumber(serialNumber);
    }

    @Override
    public Optional<FinancialReport> findByAssetName(String assetName) {
        return FinancialReportRepository.findByAssetName(assetName);
    }

    @Override
    public List<FinancialReport> findAllByAssetName(String assetName) {
        return FinancialReportRepository.findAllByAssetName(assetName);
    }

    @Override
    public Optional<FinancialReport> findById(Integer financialReportId) {
        return FinancialReportRepository.findById(financialReportId.longValue());
    }

    @Override
    public void delete(int recordNo) {
        FinancialReportRepository.deleteById((long) recordNo);
    }

    @Override
    public BigDecimal getTotalCost() {
        return FinancialReportRepository.findTotalCost().orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getTotalNBV() {
        return FinancialReportRepository.findTotalNBV().orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getTotalDepreciation() {
        return FinancialReportRepository.findTotalDepreciation().orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getFilteredCost(String search, String lastMonthDate) {
        return FinancialReportRepository.findFilteredCost(search, lastMonthDate).orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getFilteredNBV(String search, String lastMonthDate) {
        return FinancialReportRepository.findFilteredNBV(search, lastMonthDate).orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getFilteredDepreciation(String search, String lastMonthDate) {
        return FinancialReportRepository.findFilteredDepreciation(search, lastMonthDate).orElse(BigDecimal.ZERO);
    }


    @Override
    public Page<FinancialReport> findByAssetNameOrSerialNumber(String query, Pageable pageable) {
        return FinancialReportRepository.findByAssetNameOrAssetSerialNumber(query, pageable);
    }

    @Override
    public Page<FinancialReport> findAll(Specification<FinancialReport> spec, Pageable pageable) {
        return FinancialReportRepository.findAll(spec, pageable);
    }
}
