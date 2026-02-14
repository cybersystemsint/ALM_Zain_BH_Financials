package com.zain.bh.alm.financials.service;

import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.WFFinancialApprovalRequest;
import com.zain.bh.alm.financials.entity.WriteOffReport;
import com.zain.bh.alm.financials.repository.WFFinancialApprovalRequestRepository;

@Service
public class WriteOffImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WriteOffImportService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final FinancialReportService financialReportService;
    private final WriteOffReportService writeOffReportService;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final WFFinancialApprovalRequestRepository approvalWorkflowRepository;

    public WriteOffImportService(FinancialReportService financialReportService,
                                  WriteOffReportService writeOffReportService,
                                  ApprovalWorkflowService approvalWorkflowService,
                                  WFFinancialApprovalRequestRepository approvalWorkflowRepository) {
        this.financialReportService = financialReportService;
        this.writeOffReportService = writeOffReportService;
        this.approvalWorkflowService = approvalWorkflowService;
        this.approvalWorkflowRepository = approvalWorkflowRepository;
    }

    @Transactional
    public Map<String, Object> importFromCsv(MultipartFile file, char separator, boolean ignoreHeader, String username) throws Exception {
        List<Map<String, String>> recordsToProcess = new ArrayList<>();
        List<String> invalidRecords = new ArrayList<>();

        CSVFormat csvFormat = CSVFormat.DEFAULT.withDelimiter(separator).withFirstRecordAsHeader().withTrim();
        if (!ignoreHeader) {
            csvFormat = csvFormat.withSkipHeaderRecord(false);
        }

        try (CSVParser parser = new CSVParser(new InputStreamReader(file.getInputStream()), csvFormat)) {
            Map<String, String> columnMapping = buildColumnMapping(parser.getHeaderNames());

            if (!columnMapping.containsKey("serialNumber") || !columnMapping.containsKey("assetId")) {
                throw new IllegalArgumentException("CSV file must include serialNumber and assetId columns.");
            }

            int recordCount = 0;
            for (CSVRecord record : parser) {
                recordCount++;
                String serialNumber = record.get(columnMapping.getOrDefault("serialNumber", "Serial Number"));
                String assetId = record.get(columnMapping.getOrDefault("assetId", "Asset ID"));
                String writeOffDateStr = record.get(columnMapping.getOrDefault("writeOffDate", "Write-Off Date"));

                if (serialNumber == null || serialNumber.trim().isEmpty() || assetId == null || assetId.trim().isEmpty()) {
                    invalidRecords.add(String.format("Row %d: Missing serialNumber or assetId", recordCount));
                    continue;
                }

                Optional<FinancialReport> financialReportOpt = financialReportService.findBySerialNumber(serialNumber)
                        .or(() -> financialReportService.findByAssetName(assetId));
                if (!financialReportOpt.isPresent()) {
                    invalidRecords.add(String.format("Row %d: No Financial Report found for serialNumber=%s or assetId=%s",
                            recordCount, serialNumber, assetId));
                    continue;
                }

                Optional<WriteOffReport> existingWriteOff = writeOffReportService.findBySerialNumber(serialNumber);
                if (existingWriteOff.isPresent()) {
                    invalidRecords.add(String.format("Row %d: Write-off already exists for serialNumber=%s", recordCount, serialNumber));
                    continue;
                }

                if (writeOffDateStr != null && !writeOffDateStr.trim().isEmpty()) {
                    try {
                        DATE_FORMAT.parse(writeOffDateStr);
                    } catch (java.text.ParseException e) {
                        invalidRecords.add(String.format("Row %d: Invalid writeOffDate format: %s (expected yyyy-MM-dd)",
                                recordCount, writeOffDateStr));
                        continue;
                    }
                }

                Map<String, String> recordData = new HashMap<>();
                recordData.put("serialNumber", serialNumber);
                recordData.put("assetId", assetId);
                recordData.put("writeOffDate", writeOffDateStr);
                recordsToProcess.add(recordData);
            }

            if (!invalidRecords.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("message", "Some assets could not be processed. Please verify and try again:\n" + String.join("\n", invalidRecords));
                errorResponse.put("errors", invalidRecords);
                return errorResponse;
            }

            if (recordsToProcess.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("message", "No valid records found in the CSV file.");
                return errorResponse;
            }

            return processWriteOffRecords(recordsToProcess, username);
        }
    }

    private Map<String, String> buildColumnMapping(List<String> headers) {
        Map<String, String> columnMapping = new HashMap<>();
        for (String header : headers) {
            String normHeader = header.toLowerCase().replaceAll("[^a-zA-Z0-9]", "");
            if (normHeader.contains("serialnumber")) {
                columnMapping.put("serialNumber", header);
            } else if (normHeader.contains("assetid")) {
                columnMapping.put("assetId", header);
            } else if (normHeader.contains("writeoffdate")) {
                columnMapping.put("writeOffDate", header);
            }
        }
        return columnMapping;
    }

    private Map<String, Object> processWriteOffRecords(List<Map<String, String>> recordsToProcess, String username) {
        List<WriteOffReport> writeOffs = new ArrayList<>();
        List<WFFinancialApprovalRequest> workflows = new ArrayList<>();

        for (Map<String, String> recordData : recordsToProcess) {
            String serialNumber = recordData.get("serialNumber");
            String assetId = recordData.get("assetId");
            String writeOffDateStr = recordData.get("writeOffDate");

            Optional<FinancialReport> financialReportOpt = financialReportService.findBySerialNumber(serialNumber)
                    .or(() -> financialReportService.findByAssetName(assetId));
            FinancialReport financialReport = financialReportOpt.get();

            financialReport.setFinancialApprovalStatus("Pending L1 Approval");
            financialReport.setChangedBy(username);
            financialReport.setChangeDate(new java.util.Date());
            financialReportService.save(financialReport);

            WriteOffReport writeOff = new WriteOffReport();
            writeOff.setSerialNumber(financialReport.getAssetSerialNumber());
            writeOff.setAssetId(assetId);
            writeOff.setAssetType(financialReport.getNodeType());
            writeOff.setStatusFlag(financialReport.getStatusFlag());
            if (writeOffDateStr != null && !writeOffDateStr.trim().isEmpty()) {
                try {
                    writeOff.setWriteOffDate(DATE_FORMAT.parse(writeOffDateStr));
                } catch (java.text.ParseException e) {
                    LOGGER.error("Failed to parse writeOffDate for serialNumber={}: {}", serialNumber, writeOffDateStr);
                    throw new RuntimeException("Invalid writeOffDate format: " + writeOffDateStr);
                }
            } else {
                writeOff.setWriteOffDate(new java.util.Date());
            }
            writeOff.setInsertedBy(username);

            try {
                WriteOffReport savedWriteOff = writeOffReportService.save(writeOff);
                writeOffs.add(savedWriteOff);
            } catch (Exception e) {
                LOGGER.error("Failed to save WriteOffReport for serialNumber={}: {}", serialNumber, e.getMessage());
                throw new RuntimeException("Failed to save write-off report: " + e.getMessage(), e);
            }

            WFFinancialApprovalRequest workflow = new WFFinancialApprovalRequest();
            workflow.setAssetId(String.valueOf(financialReport.getAssetName()));
            workflow.setObjectType("FinancialReport");
            workflow.setOriginalStatus(financialReport.getFinancialApprovalStatus());
            workflow.setUpdatedStatus("Pending L1 Approval");
            workflow.setComments("Write-off pending L1 approval");
            workflow.setInsertedBy(username);
            Integer maxProcessId = approvalWorkflowRepository.findMaxProcessId();
            workflow.setProcessId(maxProcessId != null ? maxProcessId + 1 : 1);
            workflows.add(workflow);
        }

        if (!workflows.isEmpty()) {
            try {
                approvalWorkflowService.saveAllEntities(workflows);
            } catch (Exception e) {
                LOGGER.error("Failed to save workflows: {}", e.getMessage());
                throw new RuntimeException("Failed to save approval workflows: " + e.getMessage(), e);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", String.format("Successfully imported %d write-off records. They are now pending approval.", writeOffs.size()));
        response.put("recordsImported", writeOffs.size());
        response.put("status", "success");
        return response;
    }
}
