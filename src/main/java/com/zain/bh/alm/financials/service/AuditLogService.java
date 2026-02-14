package com.zain.bh.alm.financials.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.AuditLog;
import com.zain.bh.alm.financials.entity.FinancialReport;
import com.zain.bh.alm.financials.repository.AuditLogRepository;

@Service
public class AuditLogService {

	private final AuditLogRepository auditLogRepository;

	public AuditLogService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	public void logAudit(FinancialReport asset, String previousStatus, String newStatus, String notes) {
		AuditLog auditLog = new AuditLog();
		auditLog.setAssetId(asset != null ? String.valueOf(asset.getId()) : null);
		auditLog.setSerialNumber(asset != null ? asset.getAssetSerialNumber() : null);
		auditLog.setPreviousStatus(previousStatus);
		auditLog.setNewStatus(newStatus);
		auditLog.setChangeDate(LocalDateTime.now());
		auditLog.setNodeType(asset != null ? asset.getNodeType() : null);
		auditLog.setNotes(notes);
		auditLogRepository.save(auditLog);
	}

	public void logUnmappedAudit(String serialNumber, String nodeType, String notes) {
		AuditLog auditLog = new AuditLog();
		auditLog.setAssetId(null);
		auditLog.setSerialNumber(serialNumber);
		auditLog.setPreviousStatus("UNKNOWN");
		auditLog.setNewStatus("UNMAPPED");
		auditLog.setChangeDate(LocalDateTime.now());
		auditLog.setNodeType(nodeType);
		auditLog.setNotes(notes);
		auditLogRepository.save(auditLog);
	}
}
