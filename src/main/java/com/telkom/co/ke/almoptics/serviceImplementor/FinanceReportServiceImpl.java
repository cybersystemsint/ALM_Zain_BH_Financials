package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.FinancialReportProjection;
import com.telkom.co.ke.almoptics.entities.tb_Asset_Depreciation;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.repository.FinancialReportRepo;
import com.telkom.co.ke.almoptics.services.ApprovalWorkflowService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import com.telkom.co.ke.almoptics.services.tb_Asset_DepreciationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class FinanceReportServiceImpl implements FinancialReportService {

    private static final Logger logger = LoggerFactory.getLogger(FinanceReportServiceImpl.class);

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private ApprovalWorkflowService approvalWorkflowService;

    @Autowired
    private tb_Asset_DepreciationService depreciationService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final int MAX_PAGE_NUMBER = 100;

    // Modified method in FinanceReportServiceImpl.java
    @Override
    @Transactional
    public tb_FinancialReport calculateDepreciation(String serialNumber, BigDecimal adjustment, String username) {
        logger.info("Calculating depreciation for asset serial number: {}", serialNumber);

        Optional<tb_FinancialReport> reportOpt = financialReportRepo.findByAssetSerialNumber(serialNumber);
        if (!reportOpt.isPresent()) {
            logger.error("Financial report not found for serial number: {}", serialNumber);
            throw new IllegalArgumentException("Financial report not found for serial number: " + serialNumber);
        }

        tb_FinancialReport report = reportOpt.get();

        // Validate mandatory fields
        if (report.getInitialCost() == null || report.getInitialCost().compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("Initial cost is missing or invalid for serial number: {}", serialNumber);
            throw new IllegalArgumentException("Initial cost is mandatory and must be greater than zero");
        }
        if (report.getDateOfService() == null || report.getDateOfService().trim().isEmpty()) {
            logger.error("Date of service is missing for serial number: {}", serialNumber);
            throw new IllegalArgumentException("Date of service is mandatory");
        }
        if (report.getUsefulLifeMonths() == null || report.getUsefulLifeMonths() <= 0) {
            logger.error("Useful life months is missing or invalid for serial number: {}", serialNumber);
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
                    logger.error("Date of service is before installation date for serial number: {}", serialNumber);
                    throw new IllegalArgumentException("Date of service cannot be before installation date");
                }
            }
        } catch (Exception e) {
            logger.error("Invalid date format for serial number: {}", serialNumber, e);
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
            logger.error("Adjustment value {} exceeds initial cost {} for serial number: {}",
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

        // Calculate x = MD * NoMU
        BigDecimal x = monthlyDepreciation.multiply(new BigDecimal(numberOfMonthsUtilised));

        // Calculate Accumulated Depreciation (AD = x + ADJ)
        BigDecimal accumulatedDepreciation = x.add(adjustment).setScale(3, RoundingMode.HALF_UP);

        // Enforce x + p <= A
        if (accumulatedDepreciation.compareTo(report.getInitialCost()) > 0) {
            logger.error("x + p exceeds initial cost for serial number: {}", serialNumber);
            throw new IllegalArgumentException("x + p cannot exceed initial cost");
        }

        // Ensure AD does not exceed (IC - Salvage Value)
        BigDecimal maxAccumulatedDepreciation = report.getInitialCost()
                .subtract(report.getSalvageValue()).setScale(3, RoundingMode.HALF_UP);
        accumulatedDepreciation = accumulatedDepreciation.min(maxAccumulatedDepreciation);

        report.setAccumulatedDepreciation(accumulatedDepreciation);
        report.setAdjustment(adjustment);

        // Calculate Net Cost (NC = IC - AD)
        BigDecimal netCost = report.getInitialCost()
                .subtract(accumulatedDepreciation)
                .setScale(3, RoundingMode.HALF_UP);

        // Ensure NC is not less than Salvage Value
        netCost = netCost.max(report.getSalvageValue());

        // Stop calculations if NC <= 0 or reaches salvage value
        if (netCost.compareTo(BigDecimal.ZERO) <= 0 || netCost.compareTo(report.getSalvageValue()) <= 0) {
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
            logger.error("Failed to serialize original state for serial number: {}", serialNumber, e);
            throw new RuntimeException("Failed to serialize original state", e);
        }

        // Update metadata
        report.setChangedBy(username);
        report.setChangeDate(new Date());
        report.setFinancialApprovalStatus("Pending L1 Approval");

        // Save the report
        tb_FinancialReport savedReport = financialReportRepo.save(report);

        // Trigger approval workflow
        approvalWorkflowService.createApprovalWorkflow(savedReport, savedReport.getNodeType(),"Pending Modification", username);

        logger.info("Depreciation calculated and saved for serial number: {}", serialNumber);
        return savedReport;
    }
    // Modified method in FinanceReportServiceImpl.java
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> calculateDepreciationForMonth(String date, String search, Pageable pageable) {
        try {
            // Parse the input date
            SimpleDateFormat inputFormat = new SimpleDateFormat("d MMM yyyy");
            Date inputDate = inputFormat.parse(date);

            // Convert to LocalDate and get end of month
            LocalDate localInputDate = inputDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate endOfMonthLocal = YearMonth.from(localInputDate).atEndOfMonth();
            Date endOfMonth = Date.from(endOfMonthLocal.atStartOfDay(ZoneId.systemDefault()).toInstant());

            // Fetch paginated subset with filters applied (adjusted query to use dateOfService instead of insertDate)
            Page<tb_FinancialReport> reports = financialReportRepo.findByDateOfServiceBefore(endOfMonth, search, pageable);

            // Check if the requested page is beyond the available data
            if (pageable.getPageNumber() > reports.getTotalPages() || (pageable.getPageNumber() == reports.getTotalPages() && reports.getTotalElements() == 0)) {
                Map<String, Object> result = new HashMap<>();
                result.put("filteredCost", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                result.put("filteredDepreciation", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                result.put("filteredNBV", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                result.put("totalCost", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                result.put("totalDepreciation", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                result.put("totalNBV", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                result.put("totalPages", reports.getTotalPages());
                result.put("totalItems", reports.getTotalElements());
                result.put("currentPage", pageable.getPageNumber());
                result.put("first", pageable.getPageNumber() == 0);
                result.put("last", true);
                result.put("size", pageable.getPageSize());
                result.put("sort", pageable.getSort().toString());
                result.put("records", Collections.emptyList());
                return result;
            }

            List<tb_FinancialReport> filteredReports = reports.getContent();

            // Fetch totals from database
            BigDecimal totalCost = financialReportRepo.findTotalCostByDateOfServiceBefore(endOfMonth, search);
            BigDecimal totalDepreciation = financialReportRepo.findTotalDepreciationByDateOfServiceBefore(endOfMonth, search);
            BigDecimal totalNBV = financialReportRepo.findTotalNBVByDateOfServiceBefore(endOfMonth, search);

            // Recalculate depreciation for paginated filtered reports using the new formula
            for (tb_FinancialReport report : filteredReports) {
                if (report.getDateOfService() != null && report.getInitialCost() != null && report.getUsefulLifeMonths() != null) {
                    try {
                        LocalDate serviceDate = LocalDate.parse(report.getDateOfService(), DateTimeFormatter.ISO_LOCAL_DATE);
                        if (serviceDate.isBefore(endOfMonthLocal.plusDays(1)) && report.getUsefulLifeMonths() > 0) {
                            long monthsActive = ChronoUnit.MONTHS.between(serviceDate, endOfMonthLocal);
                            if (monthsActive > 0) {
                                BigDecimal salvageValue = report.getSalvageValue() != null ? report.getSalvageValue() : BigDecimal.ZERO;
                                BigDecimal monthlyDepreciation = report.getInitialCost()
                                        .subtract(salvageValue)
                                        .divide(BigDecimal.valueOf(report.getUsefulLifeMonths()), 3, RoundingMode.HALF_UP);

                                BigDecimal x = monthlyDepreciation.multiply(BigDecimal.valueOf(monthsActive));
                                BigDecimal adjustment = report.getAdjustment() != null ? report.getAdjustment() : BigDecimal.ZERO;
                                BigDecimal accumulatedDepreciation = x.add(adjustment).setScale(3, RoundingMode.HALF_UP);

                                // Enforce x + p <= A
                                if (accumulatedDepreciation.compareTo(report.getInitialCost()) > 0) {
                                    accumulatedDepreciation = report.getInitialCost(); // Cap at A if violation (or throw, but cap for reports)
                                }

                                // Cap at max (A - SV)
                                BigDecimal maxAD = report.getInitialCost().subtract(salvageValue);
                                accumulatedDepreciation = accumulatedDepreciation.min(maxAD);

                                BigDecimal netCost = report.getInitialCost().subtract(accumulatedDepreciation).setScale(3, RoundingMode.HALF_UP);
                                netCost = netCost.max(salvageValue);

                                // Stop if NBV <= 0 or = SV
                                if (netCost.compareTo(BigDecimal.ZERO) <= 0 || netCost.equals(salvageValue)) {
                                    netCost = salvageValue;
                                    accumulatedDepreciation = report.getInitialCost().subtract(salvageValue);
                                }

                                report.setAccumulatedDepreciation(accumulatedDepreciation);
                                report.setNetCost(netCost);
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error recalculating depreciation for report with serial number {}: {}",
                                report.getAssetSerialNumber(), e.getMessage());
                    }
                }
            }

            // Calculate filtered values based on paginated data
            BigDecimal filteredCost = filteredReports.stream()
                    .map(tb_FinancialReport::getInitialCost)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(3, RoundingMode.HALF_UP);

            BigDecimal filteredDepreciation = filteredReports.stream()
                    .map(tb_FinancialReport::getAccumulatedDepreciation)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(3, RoundingMode.HALF_UP);

            BigDecimal filteredNBV = filteredCost.subtract(filteredDepreciation)
                    .setScale(3, RoundingMode.HALF_UP);

            Map<String, Object> result = new HashMap<>();
            result.put("filteredCost", filteredCost);
            result.put("filteredDepreciation", filteredDepreciation);
            result.put("filteredNBV", filteredNBV);
            result.put("totalCost", totalCost);
            result.put("totalDepreciation", totalDepreciation);
            result.put("totalNBV", totalNBV);
            result.put("totalPages", reports.getTotalPages());
            result.put("totalItems", reports.getTotalElements());
            result.put("currentPage", pageable.getPageNumber());
            result.put("first", pageable.getPageNumber() == 0);
            result.put("last", pageable.getPageNumber() == reports.getTotalPages() - 1);
            result.put("size", pageable.getPageSize());
            result.put("sort", pageable.getSort().toString());
            result.put("records", filteredReports);

            return result;
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid date format for date: " + date + ". Expected format: d MMM yyyy (e.g., 5 Jun 2025)", e);
        }
    }
    @Override
    public Page<tb_FinancialReport> findByStatusFlagNotAndNetCostGreaterThan(String statusFlag, BigDecimal netCost, Pageable pageable) {
        return financialReportRepo.findByStatusFlagNotAndNetCostGreaterThan(statusFlag, netCost, pageable);
    }

    @Override
    public List<tb_FinancialReport> findAll() {
        return financialReportRepo.findAll();
    }

    @Override
    public Page<tb_FinancialReport> findAll(Pageable pageable) {
        return financialReportRepo.findAll(pageable);
    }

    @Override
    public Page<tb_FinancialReport> findBySearchTerm(String search, Pageable pageable) {
        return financialReportRepo.findBySearchTerm(search, pageable);
    }

    @Override
    public tb_FinancialReport save(tb_FinancialReport report) {
        return financialReportRepo.save(report);
    }

    @Override
    public Optional<tb_FinancialReport> findBySerialNumber(String serialNumber) {
        return financialReportRepo.findByAssetSerialNumber(serialNumber);
    }

    @Override
    public List<tb_FinancialReport> findAllBySerialNumber(String serialNumber) {
        return financialReportRepo.findAllByAssetSerialNumber(serialNumber);
    }

    @Override
    public Optional<tb_FinancialReport> findByAssetName(String assetName) {
        return financialReportRepo.findByAssetName(assetName);
    }

    @Override
    public List<tb_FinancialReport> findAllByAssetName(String assetName) {
        return financialReportRepo.findAllByAssetName(assetName);
    }

    @Override
    public Optional<tb_FinancialReport> findById(Integer financialReportId) {
        return financialReportRepo.findById(financialReportId.longValue());
    }

    @Override
    public void delete(int recordNo) {
        financialReportRepo.deleteById((long) recordNo);
    }

    @Override
    public BigDecimal getTotalCost() {
        return financialReportRepo.findTotalCost().orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getTotalNBV() {
        return financialReportRepo.findTotalNBV().orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getTotalDepreciation() {
        return financialReportRepo.findTotalDepreciation().orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getFilteredCost(String search, String lastMonthDate) {
        return financialReportRepo.findFilteredCost(search, lastMonthDate).orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getFilteredNBV(String search, String lastMonthDate) {
        return financialReportRepo.findFilteredNBV(search, lastMonthDate).orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getFilteredDepreciation(String search, String lastMonthDate) {
        return financialReportRepo.findFilteredDepreciation(search, lastMonthDate).orElse(BigDecimal.ZERO);
    }

    @Override
    public Page<tb_FinancialReport> findByAssetNameOrSerialNumber(String query, Pageable pageable) {
        return financialReportRepo.findByAssetNameOrAssetSerialNumber(query, pageable);
    }

    @Override
    public Page<tb_FinancialReport> findAll(Specification<tb_FinancialReport> spec, Pageable pageable) {
        return financialReportRepo.findAll(spec, pageable);
    }

    private BigDecimal calculateDepreciation(FinancialReportProjection p, Date endOfMonth) {
        if (p.getDateOfService() == null || p.getUsefulLifeMonths() == null) return BigDecimal.ZERO;

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date serviceDate = dateFormat.parse(p.getDateOfService());
            if (serviceDate.after(endOfMonth)) return BigDecimal.ZERO;

            long monthsActive = ChronoUnit.MONTHS.between(
                    serviceDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                    endOfMonth.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            );

            if (monthsActive <= 0) return BigDecimal.ZERO;

            BigDecimal monthlyDep = p.getMonthlyDepreciationAmount();
            if (monthlyDep == null) {
                monthlyDep = p.getInitialCost()
                        .subtract(p.getSalvageValue() != null ? p.getSalvageValue() : BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(p.getUsefulLifeMonths()), 3, RoundingMode.HALF_UP);
            }

            return monthlyDep
                    .multiply(BigDecimal.valueOf(Math.min(monthsActive, p.getUsefulLifeMonths().longValue())))
                    .setScale(3, RoundingMode.HALF_UP);
        } catch (ParseException e) {
            logger.warn("Invalid date format for DateOfService");
            return BigDecimal.ZERO;
        }
    }


    @Override
    public BigDecimal computeMonthlyDepreciation(tb_FinancialReport report) {
        BigDecimal salvageValue = report.getSalvageValue() != null ? report.getSalvageValue() : BigDecimal.ZERO;
        return report.getInitialCost()
                .subtract(salvageValue)
                .divide(new BigDecimal(report.getUsefulLifeMonths()), 3, RoundingMode.HALF_UP);
    }

    // Add to FinancialReportService
    @Transactional(readOnly = true)
    public List<tb_FinancialReport> findAllByAssetNameInOrAssetSerialNumberIn(List<String> ids) {
        return financialReportRepo.findByAssetNameInOrAssetSerialNumberIn(ids);
    }


}