package com.zain.bh.alm.financials.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zain.bh.alm.financials.entity.AuditLog;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.entity.WriteOffReport;
import com.zain.bh.alm.financials.repository.AuditLogRepository;
import com.zain.bh.alm.financials.repository.WriteOffReportRepository;

@Service
public class WriteOffReportService {

	private static final Logger LOGGER = LoggerFactory.getLogger(WriteOffReportService.class);

	private final WriteOffReportRepository writeOffReportRepository;
	private final AuditLogRepository auditLogRepository;

	public WriteOffReportService(WriteOffReportRepository writeOffReportRepository,
			AuditLogRepository auditLogRepository) {
		this.writeOffReportRepository = writeOffReportRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	public WriteOffReport save(WriteOffReport report) {
		validateWriteOffReport(report);
		Optional<WriteOffReport> existing = writeOffReportRepository.findBySerialNumber(report.getSerialNumber());
		if (existing.isPresent()) {
			LOGGER.warn("Write-off already exists for serial number: {}", report.getSerialNumber());
			throw new IllegalStateException(
					"Write-off report already exists for serial number: " + report.getSerialNumber());
		}
		WriteOffReport savedReport = writeOffReportRepository.save(report);
		logAudit(savedReport, "CREATED", "Write-off report created");
		LOGGER.info("Saved write-off report for serial number: {}", savedReport.getSerialNumber());
		return savedReport;
	}

	@Transactional
	public List<WriteOffReport> saveAll(List<WriteOffReport> reports) {
		reports.forEach(this::validateWriteOffReport);
		List<String> serialNumbers = reports.stream().map(WriteOffReport::getSerialNumber).collect(Collectors.toList());
		Optional<WriteOffReport> existing = writeOffReportRepository.findBySerialNumber(String.valueOf(serialNumbers));
		if (!existing.isEmpty()) {
			String duplicates = existing.stream().map(WriteOffReport::getSerialNumber)
					.collect(Collectors.joining(", "));
			LOGGER.warn("Duplicate write-offs detected: {}", duplicates);
			throw new IllegalStateException("Duplicate write-off reports found for serial numbers: " + duplicates);
		}
		List<WriteOffReport> savedReports = writeOffReportRepository.saveAll(reports);
		savedReports.forEach(report -> logAudit(report, "CREATED", "Bulk write-off report created"));
		LOGGER.info("Saved {} write-off reports in bulk", savedReports.size());
		return savedReports;
	}

	@Transactional
	public WriteOffReport moveAssetToWriteOff(FinancialReport financialReport, String approvedBy) {
		Optional<WriteOffReport> existing = writeOffReportRepository
				.findBySerialNumber(financialReport.getAssetSerialNumber());
		if (existing.isPresent()) {
			LOGGER.warn("Asset {} already exists in write-off report", financialReport.getAssetSerialNumber());
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
		WriteOffReport savedReport = writeOffReportRepository.save(writeOff);
		logAudit(savedReport, "MOVED", "Asset moved to write-off from financial report by " + approvedBy);
		LOGGER.info("Moved asset {} to write-off report", financialReport.getAssetSerialNumber());
		return savedReport;
	}

	@Transactional(readOnly = true)
	public Optional<WriteOffReport> findBySerialNumber(String serialNumber) {
		return writeOffReportRepository.findBySerialNumber(serialNumber);
	}

	@Transactional(readOnly = true)
	public Optional<WriteOffReport> findByAssetId(String assetId) {
		return writeOffReportRepository.findByAssetId(assetId);
	}

	@Transactional
	public void delete(WriteOffReport report) {
		writeOffReportRepository.delete(report);
		logAudit(report, "DELETED", "Write-off report deleted");
		LOGGER.info("Deleted write-off report for serial number: {}", report.getSerialNumber());
	}

	@Transactional(readOnly = true)
	public List<WriteOffReport> findAll() {
		return writeOffReportRepository.findAll();
	}

	@Transactional(readOnly = true)
	public Page<WriteOffReport> findAll(Pageable pageable) {
		return writeOffReportRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Page<WriteOffReport> findWithFilters(String serialNumber, String rfid, String tag, String assetType,
			String assetId, String neType, String statusFlag, Date startDate, Date endDate, Pageable pageable) {
		return writeOffReportRepository.findWithFilters(serialNumber, rfid, tag, assetType, assetId, neType, statusFlag,
				startDate, endDate, pageable);
	}

	private void validateWriteOffReport(WriteOffReport report) {
		if (report.getSerialNumber() == null || report.getSerialNumber().trim().isEmpty()) {
			throw new IllegalArgumentException("Serial number cannot be null or empty");
		}
		if (report.getStatusFlag() == null) {
			report.setStatusFlag("Pending");
		}
		if (report.getInsertDate() == null) {
			report.setInsertDate(new Timestamp(System.currentTimeMillis()));
		}
	}

	public Page<WriteOffReport> search(String query, Pageable pageable) {
		return writeOffReportRepository.search(query, pageable);
	}

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
