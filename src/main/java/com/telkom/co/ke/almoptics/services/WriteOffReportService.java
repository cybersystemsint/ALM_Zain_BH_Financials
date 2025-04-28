package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.WriteOffReport;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.models.AuditLog;
import com.telkom.co.ke.almoptics.repository.AuditLogRepository;
import com.telkom.co.ke.almoptics.repository.FinancialReportRepo;
import com.telkom.co.ke.almoptics.repository.WriteOffReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WriteOffReportService {

    private static final Logger logger = LoggerFactory.getLogger(WriteOffReportService.class);

    @Autowired
    private WriteOffReportRepository repository;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Save a single WriteOffReport with validation and audit logging
     */
    @Transactional
    public WriteOffReport save(WriteOffReport report) {
        validateWriteOffReport(report);

        // Check for existing write-off to avoid duplicates
        Optional<WriteOffReport> existing = repository.findBySerialNumber(report.getSerialNumber());
        if (existing.isPresent()) {
            logger.warn("Write-off already exists for serial number: {}", report.getSerialNumber());
            throw new IllegalStateException("Write-off report already exists for serial number: " + report.getSerialNumber());
        }

        WriteOffReport savedReport = repository.save(report);
        logAudit(savedReport, "CREATED", "Write-off report created");
        logger.info("Saved write-off report for serial number: {}", savedReport.getSerialNumber());
        return savedReport;
    }

    /**
     * Save a list of WriteOffReports in bulk
     */
    @Transactional
    public List<WriteOffReport> saveAll(List<WriteOffReport> reports) {
        reports.forEach(this::validateWriteOffReport);

        // Check for duplicates in the batch
        List<String> serialNumbers = reports.stream()
                .map(WriteOffReport::getSerialNumber)
                .collect(Collectors.toList());
        Optional<WriteOffReport> existing = repository.findBySerialNumber(String.valueOf(serialNumbers));
        if (!existing.isEmpty()) {
            String duplicates = existing.stream()
                    .map(WriteOffReport::getSerialNumber)
                    .collect(Collectors.joining(", "));
            logger.warn("Duplicate write-offs detected: {}", duplicates);
            throw new IllegalStateException("Duplicate write-off reports found for serial numbers: " + duplicates);
        }

        List<WriteOffReport> savedReports = repository.saveAll(reports);
        savedReports.forEach(report -> logAudit(report, "CREATED", "Bulk write-off report created"));
        logger.info("Saved {} write-off reports in bulk", savedReports.size());
        return savedReports;
    }

    /**
     * Move an asset from Financial Report to WriteOffReport (called by approval workflow)
     */
    @Transactional
    public WriteOffReport moveAssetToWriteOff(tb_FinancialReport financialReport, String approvedBy) {
        Optional<WriteOffReport> existing = repository.findBySerialNumber(financialReport.getAssetSerialNumber());
        if (existing.isPresent()) {
            logger.warn("Asset {} already exists in write-off report", financialReport.getAssetSerialNumber());
            return existing.get();
        }

        WriteOffReport writeOff = new WriteOffReport();
        writeOff.setSerialNumber(financialReport.getAssetSerialNumber());
        writeOff.setAssetId(String.valueOf(financialReport.getId()));
        writeOff.setAssetType(financialReport.getNodeType());
        writeOff.setStatusFlag("Approved");
        writeOff.setWriteOffDate(new Timestamp(System.currentTimeMillis()));
        writeOff.setInsertedBy(approvedBy);
        writeOff.setInsertDate(new Timestamp(System.currentTimeMillis()));


        WriteOffReport savedReport = repository.save(writeOff);
        logAudit(savedReport, "MOVED", "Asset moved to write-off from financial report by " + approvedBy);
        logger.info("Moved asset {} to write-off report", financialReport.getAssetSerialNumber());
        return savedReport;
    }

    /**
     * Find WriteOffReport by serial number
     */
    @Transactional(readOnly = true)
    public Optional<WriteOffReport> findBySerialNumber(String serialNumber) {
        return repository.findBySerialNumber(serialNumber);
    }

    /**
     * Find WriteOffReport by asset ID
     */
    @Transactional(readOnly = true)
    public Optional<WriteOffReport> findByAssetId(String assetId) {
        return repository.findByAssetId(assetId);
    }

    /**
     * Delete a WriteOffReport with audit logging
     */
    @Transactional
    public void delete(WriteOffReport report) {
        repository.delete(report);
        logAudit(report, "DELETED", "Write-off report deleted");
        logger.info("Deleted write-off report for serial number: {}", report.getSerialNumber());
    }

    /**
     * Find all WriteOffReports
     */
    @Transactional(readOnly = true)
    public List<WriteOffReport> findAll() {
        return repository.findAll();
    }

    /**
     * Find all WriteOffReports with pagination
     */
    @Transactional(readOnly = true)
    public Page<WriteOffReport> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /**
     * Find WriteOffReports with filters and pagination
     */
    @Transactional(readOnly = true)
    public Page<WriteOffReport> findWithFilters(
            String serialNumber,
            String rfid,
            String tag,
            String assetType,
            String assetId,
            String neType,
            String statusFlag,
            Date startDate,
            Date endDate,
            Pageable pageable) {
        return repository.findWithFilters(
                serialNumber,
                rfid,
                tag,
                assetType,
                assetId,
                neType,
                statusFlag,
                startDate,
                endDate,
                pageable);
    }

    /**
     * Validate WriteOffReport before saving
     */
    private void validateWriteOffReport(WriteOffReport report) {
        if (report.getSerialNumber() == null || report.getSerialNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Serial number cannot be null or empty");
        }
        if (report.getStatusFlag() == null) {
            report.setStatusFlag("Pending"); // Default status if not set
        }
        if (report.getInsertDate() == null) {
            report.setInsertDate(new Timestamp(System.currentTimeMillis()));
        }
    }

    /**
     * search a WriteOffReport
     */
    public Page<WriteOffReport> search(String query, Pageable pageable) {
        return repository.search(query, pageable);
    }


    /**
     * Log audit entry for write-off actions
     */
    private void logAudit(WriteOffReport report, String newStatus, String notes) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAssetId(report.getAssetId());
        auditLog.setSerialNumber(report.getSerialNumber());
        auditLog.setPreviousStatus(report.getStatusFlag());
        auditLog.setNewStatus(newStatus);
        auditLog.setChangeDate(LocalDateTime.now());
        auditLog.setNodeType(report.getAssetType());
        auditLog.setNotes(notes);
        auditLogRepository.save(auditLog);
    }
}