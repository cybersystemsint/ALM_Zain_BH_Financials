package com.zain.bh.alm.financials.service;

import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.WriteOffReport;

@Service
public class FinancialReportExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FinancialReportExportService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final FinancialReportService financialReportService;
    private final WriteOffReportService writeOffReportService;

    public FinancialReportExportService(FinancialReportService financialReportService,
                                         WriteOffReportService writeOffReportService) {
        this.financialReportService = financialReportService;
        this.writeOffReportService = writeOffReportService;
    }

    public byte[] exportFinancialReportsToCsv() throws Exception {
        LOGGER.info("Starting financial reports CSV export");
        List<FinancialReport> reports = financialReportService.findAll();
        LOGGER.info("Exporting {} financial reports to CSV", reports.size());
        StringWriter writer = new StringWriter();
        try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                .withHeader("Asset Name", "Serial Number", "TAG", "Oracle Asset ID", "Asset Type",
                        "Node Type", "Installation Date", "Initial Cost", "Salvage Value",
                        "PO Number", "PO Date", "FA Category", "L1", "L2", "L3", "L4",
                        "Accumulated Depreciation Code", "Depreciation Code", "Useful Life (Months)",
                        "Vendor Name", "Vendor Number", "Project Number", "Date Of Service",
                        "Old FA Category", "Cost Center", "Adjustment", "Task ID",
                        "PO Line Number", "Monthly Depreciation Amount", "Accumulated Depreciation",
                        "Status Flag", "Net Cost"))) {

            for (FinancialReport report : reports) {
                csvPrinter.printRecord(
                        report.getAssetName(),
                        report.getAssetSerialNumber(),
                        report.getTag(),
                        report.getOracleAssetId(),
                        report.getAssetType(),
                        report.getNodeType(),
                        report.getInstallationDate() != null ? DATE_FORMAT.format(report.getInstallationDate()) : "",
                        report.getInitialCost(),
                        report.getSalvageValue(),
                        report.getPoNumber(),
                        report.getPoDate() != null ? DATE_FORMAT.format(report.getPoDate()) : "",
                        report.getFaCategory(),
                        report.getL1(),
                        report.getL2(),
                        report.getL3(),
                        report.getL4(),
                        report.getAccumulatedDepreciationCode(),
                        report.getDepreciationCode(),
                        report.getUsefulLifeMonths(),
                        report.getVendorName(),
                        report.getVendorNumber(),
                        report.getProjectNumber(),
                        report.getDateOfService() != null ? DATE_FORMAT.format(report.getDateOfService()) : "",
                        report.getOldFarCategory(),
                        report.getCostCenterData(),
                        report.getAdjustment(),
                        report.getTaskId(),
                        report.getPoLineNumber(),
                        report.getMonthlyDepreciationAmount(),
                        report.getAccumulatedDepreciation(),
                        report.getStatusFlag(),
                        report.getNetCost()
                );
            }
            csvPrinter.flush();
        }
        LOGGER.info("Financial reports CSV export completed");
        return writer.toString().getBytes();
    }

    public Object exportWriteOffReports(int page, int size, String sort, String format) throws Exception {
        LOGGER.info("Exporting write-off reports: format={}, page={}, size={}", format, page, size);
        if ("json".equalsIgnoreCase(format)) {
            return exportWriteOffReportsAsJson(page, size, sort);
        } else {
            return exportWriteOffReportsToCsv();
        }
    }

    private Map<String, Object> exportWriteOffReportsAsJson(int page, int size, String sort) {
        Sort sortObj = Sort.unsorted();
        if (sort != null && !sort.isEmpty()) {
            String[] sortParts = sort.split(",");
            String field = sortParts[0];
            Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;
            sortObj = Sort.by(direction, field);
        }

        Pageable pageable = PageRequest.of(page, size, sortObj);
        Page<WriteOffReport> reportPage = writeOffReportService.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", reportPage.getContent());
        response.put("totalElements", reportPage.getTotalElements());
        response.put("totalPages", reportPage.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        return response;
    }

    private byte[] exportWriteOffReportsToCsv() throws Exception {
        LOGGER.info("Starting write-off reports CSV export");
        List<WriteOffReport> reports = writeOffReportService.findAll();
        LOGGER.info("Exporting {} write-off reports to CSV", reports.size());
        StringWriter writer = new StringWriter();
        try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                .withHeader("Serial Number", "RFID", "TAG", "Asset Type", "Asset ID",
                        "NE Type", "Write-Off Date", "Status Flag", "Inserted By"))) {

            for (WriteOffReport report : reports) {
                csvPrinter.printRecord(
                        report.getSerialNumber(),
                        report.getRfid(),
                        report.getTag(),
                        report.getAssetType(),
                        report.getAssetId(),
                        report.getNeType(),
                        report.getWriteOffDate() != null ? DATE_FORMAT.format(report.getWriteOffDate()) : "",
                        report.getStatusFlag(),
                        report.getInsertedBy()
                );
            }
            csvPrinter.flush();
        }
        LOGGER.info("Write-off reports CSV export completed");
        return writer.toString().getBytes();
    }
}
