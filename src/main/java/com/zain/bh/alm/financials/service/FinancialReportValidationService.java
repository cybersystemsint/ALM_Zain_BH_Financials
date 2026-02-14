package com.zain.bh.alm.financials.service;

import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.FinancialReport;

@Service
public class FinancialReportValidationService {

	public String validateUploadRecord(FinancialReport report, String identifier, int recordNumber,
			Set<String> identifiersInRequest) {
		if (identifier == null || identifier.trim().isEmpty()) {
			return "Record " + recordNumber + ": Invalid or missing identifier";
		}

		if (!identifiersInRequest.add(identifier)) {
			return "Record " + recordNumber + ": Duplicate identifier in upload batch: " + identifier;
		}

		String[] mandatoryFields = { "NodeType", report.getNodeType(), "AssetName", report.getAssetName(), "AssetType",
				report.getAssetType(), "InstallationDate", String.valueOf(report.getInstallationDate()), "InitialCost",
				String.valueOf(report.getInitialCost()), "SalvageValue", String.valueOf(report.getSalvageValue()),
				"PONumber", report.getPoNumber(), "PODate", String.valueOf(report.getPoDate()), "FA_CATEGORY",
				report.getAssetCategory(), "L1", report.getL1(), "L2", report.getL2(), "L3", report.getL3(), "L4",
				report.getL4(), "AccumulatedDepreciationCode", report.getAccumulatedDepreciationCode(),
				"DepreciationCode", report.getDepreciationCode(), "UsefulLife(Months)",
				String.valueOf(report.getUsefulLifeMonths()), "VENDOR_NAME", report.getVendorName(), "VENDOR_NUMBER",
				report.getVendorNumber(), "PROJECT_NUMBER", report.getProjectNumber(), "DateOfService",
				String.valueOf(report.getDateOfService()), "PoLineNumber", report.getPoLineNumber(), "CostCenterData",
				report.getCostCenterData() };

		for (int j = 0; j < mandatoryFields.length; j += 2) {
			String fieldName = mandatoryFields[j];
			String fieldValue = mandatoryFields[j + 1];
			if (fieldValue == null || fieldValue.trim().isEmpty() || fieldValue.equals("null")) {
				return "Record " + recordNumber + ": Missing or invalid mandatory field: " + fieldName;
			}
		}

		return null;
	}

	public boolean hasChanges(FinancialReport existing, FinancialReport incoming) {
		return !(Objects.equals(existing.getNodeType(), incoming.getNodeType())
				&& Objects.equals(existing.getAssetName(), incoming.getAssetName())
				&& Objects.equals(existing.getAssetSerialNumber(), incoming.getAssetSerialNumber())
				&& Objects.equals(existing.getAssetType(), incoming.getAssetType())
				&& Objects.equals(existing.getAssetCategory(), incoming.getAssetCategory())
				&& Objects.equals(existing.getModel(), incoming.getModel())
				&& Objects.equals(existing.getPartNumber(), incoming.getPartNumber())
				&& Objects.equals(existing.getInstallationDate(), incoming.getInstallationDate())
				&& Objects.equals(existing.getInitialCost(), incoming.getInitialCost())
				&& Objects.equals(existing.getSalvageValue(), incoming.getSalvageValue())
				&& Objects.equals(existing.getPoNumber(), incoming.getPoNumber())
				&& Objects.equals(existing.getPoDate(), incoming.getPoDate())
				&& Objects.equals(existing.getFaCategory(), incoming.getFaCategory())
				&& Objects.equals(existing.getL1(), incoming.getL1())
				&& Objects.equals(existing.getL2(), incoming.getL2())
				&& Objects.equals(existing.getL3(), incoming.getL3())
				&& Objects.equals(existing.getL4(), incoming.getL4())
				&& Objects.equals(existing.getAccumulatedDepreciationCode(), incoming.getAccumulatedDepreciationCode())
				&& Objects.equals(existing.getDepreciationCode(), incoming.getDepreciationCode())
				&& Objects.equals(existing.getUsefulLifeMonths(), incoming.getUsefulLifeMonths())
				&& Objects.equals(existing.getVendorName(), incoming.getVendorName())
				&& Objects.equals(existing.getVendorNumber(), incoming.getVendorNumber())
				&& Objects.equals(existing.getProjectNumber(), incoming.getProjectNumber())
				&& Objects.equals(existing.getDateOfService(), incoming.getDateOfService())
				&& Objects.equals(existing.getOldFarCategory(), incoming.getOldFarCategory())
				&& Objects.equals(existing.getCostCenterData(), incoming.getCostCenterData())
				&& Objects.equals(existing.getAdjustment(), incoming.getAdjustment())
				&& Objects.equals(existing.getTaskId(), incoming.getTaskId())
				&& Objects.equals(existing.getPoLineNumber(), incoming.getPoLineNumber())
				&& Objects.equals(existing.getMonthlyDepreciationAmount(), incoming.getMonthlyDepreciationAmount())
				&& Objects.equals(existing.getAccumulatedDepreciation(), incoming.getAccumulatedDepreciation())
				&& Objects.equals(existing.getNetCost(), incoming.getNetCost())
				&& Objects.equals(existing.getDescription(), incoming.getDescription())
				&& Objects.equals(existing.getOracleAssetId(), incoming.getOracleAssetId())
				&& Objects.equals(existing.getTechnologySupported(), incoming.getTechnologySupported())
				&& Objects.equals(existing.getNepAssetId(), incoming.getNepAssetId())
				&& Objects.equals(existing.getDeleted(), incoming.getDeleted())
				&& Objects.equals(existing.getWriteOffDate(), incoming.getWriteOffDate())
				&& Objects.equals(existing.getTag(), incoming.getTag())
				&& Objects.equals(existing.getHostSerialNumber(), incoming.getHostSerialNumber())
				&& Objects.equals(existing.getReleaseNumber(), incoming.getReleaseNumber())
				&& Objects.equals(existing.getSpectrumLicenseDate(), incoming.getSpectrumLicenseDate())
				&& Objects.equals(existing.getItemBarCode(), incoming.getItemBarCode())
				&& Objects.equals(existing.getRfid(), incoming.getRfid())
				&& Objects.equals(existing.getInvoiceNumber(), incoming.getInvoiceNumber()));
	}
}
