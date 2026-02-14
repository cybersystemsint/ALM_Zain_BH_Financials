package com.zain.bh.alm.financials.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zain.bh.alm.financials.entity.FinancialReport;

@Service
public class FinancialReportStateService {

    public void saveOriginalState(FinancialReport report, ObjectMapper objectMapper) throws Exception {
        Map<String, Object> originalState = new HashMap<>();
        originalState.put("siteId", report.getSiteId());
        originalState.put("zone", report.getZone());
        originalState.put("nodeType", report.getNodeType());
        originalState.put("assetName", report.getAssetName());
        originalState.put("assetType", report.getAssetType());
        originalState.put("assetCategory", report.getAssetCategory());
        originalState.put("model", report.getModel());
        originalState.put("partNumber", report.getPartNumber());
        originalState.put("assetSerialNumber", report.getAssetSerialNumber());
        originalState.put("installationDate", report.getInstallationDate());
        originalState.put("initialCost", report.getInitialCost());
        originalState.put("monthlyDepreciationAmount", report.getMonthlyDepreciationAmount());
        originalState.put("accumulatedDepreciation", report.getAccumulatedDepreciation());
        originalState.put("netCost", report.getNetCost());
        originalState.put("salvageValue", report.getSalvageValue());
        originalState.put("poNumber", report.getPoNumber());
        originalState.put("poDate", report.getPoDate());
        originalState.put("faCategory", report.getFaCategory());
        originalState.put("l1", report.getL1());
        originalState.put("l2", report.getL2());
        originalState.put("l3", report.getL3());
        originalState.put("l4", report.getL4());
        originalState.put("accumulatedDepreciationCode", report.getAccumulatedDepreciationCode());
        originalState.put("depreciationCode", report.getDepreciationCode());
        originalState.put("usefulLifeMonths", report.getUsefulLifeMonths());
        originalState.put("vendorName", report.getVendorName());
        originalState.put("vendorNumber", report.getVendorNumber());
        originalState.put("projectNumber", report.getProjectNumber());
        originalState.put("description", report.getDescription());
        originalState.put("oracleAssetId", report.getOracleAssetId());
        originalState.put("dateOfService", report.getDateOfService());
        originalState.put("technologySupported", report.getTechnologySupported());
        originalState.put("oldFarCategory", report.getOldFarCategory());
        originalState.put("costCenterData", report.getCostCenterData());
        originalState.put("nepAssetId", report.getNepAssetId());
        originalState.put("deleted", report.getDeleted());
        originalState.put("adjustment", report.getAdjustment());
        originalState.put("writeOffDate", report.getWriteOffDate() != null ? report.getWriteOffDate().toString() : null);
        originalState.put("tag", report.getTag());
        originalState.put("hostSerialNumber", report.getHostSerialNumber());
        originalState.put("taskId", report.getTaskId());
        originalState.put("poLineNumber", report.getPoLineNumber());
        originalState.put("releaseNumber", report.getReleaseNumber());
        originalState.put("spectrumLicenseDate", report.getSpectrumLicenseDate() != null ? report.getSpectrumLicenseDate().toString() : null);
        originalState.put("itemBarCode", report.getItemBarCode());
        originalState.put("rfid", report.getRfid());
        originalState.put("invoiceNumber", report.getInvoiceNumber());
        report.setOriginalState(objectMapper.writeValueAsString(originalState));
    }

    public void updateReportFields(FinancialReport existing, FinancialReport updated) {
        if (updated.getSiteId() != null) existing.setSiteId(updated.getSiteId());
        if (updated.getZone() != null) existing.setZone(updated.getZone());
        if (updated.getNodeType() != null) existing.setNodeType(updated.getNodeType());
        if (updated.getAssetName() != null) existing.setAssetName(updated.getAssetName());
        if (updated.getAssetType() != null) existing.setAssetType(updated.getAssetType());
        if (updated.getAssetCategory() != null) existing.setAssetCategory(updated.getAssetCategory());
        if (updated.getModel() != null) existing.setModel(updated.getModel());
        if (updated.getPartNumber() != null) existing.setPartNumber(updated.getPartNumber());
        if (updated.getAssetSerialNumber() != null) existing.setAssetSerialNumber(updated.getAssetSerialNumber());
        if (updated.getInstallationDate() != null) existing.setInstallationDate(updated.getInstallationDate());
        if (updated.getInitialCost() != null) existing.setInitialCost(updated.getInitialCost());
        if (updated.getMonthlyDepreciationAmount() != null) existing.setMonthlyDepreciationAmount(updated.getMonthlyDepreciationAmount());
        if (updated.getAccumulatedDepreciation() != null) existing.setAccumulatedDepreciation(updated.getAccumulatedDepreciation());
        if (updated.getNetCost() != null) existing.setNetCost(updated.getNetCost());
        if (updated.getSalvageValue() != null) existing.setSalvageValue(updated.getSalvageValue());
        if (updated.getPoNumber() != null) existing.setPoNumber(updated.getPoNumber());
        if (updated.getPoDate() != null) existing.setPoDate(updated.getPoDate());
        if (updated.getFaCategory() != null) existing.setFaCategory(updated.getFaCategory());
        if (updated.getL1() != null) existing.setL1(updated.getL1());
        if (updated.getL2() != null) existing.setL2(updated.getL2());
        if (updated.getL3() != null) existing.setL3(updated.getL3());
        if (updated.getL4() != null) existing.setL4(updated.getL4());
        if (updated.getAccumulatedDepreciationCode() != null) existing.setAccumulatedDepreciationCode(updated.getAccumulatedDepreciationCode());
        if (updated.getDepreciationCode() != null) existing.setDepreciationCode(updated.getDepreciationCode());
        if (updated.getUsefulLifeMonths() != null) existing.setUsefulLifeMonths(updated.getUsefulLifeMonths());
        if (updated.getVendorName() != null) existing.setVendorName(updated.getVendorName());
        if (updated.getVendorNumber() != null) existing.setVendorNumber(updated.getVendorNumber());
        if (updated.getProjectNumber() != null) existing.setProjectNumber(updated.getProjectNumber());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getOracleAssetId() != null) existing.setOracleAssetId(updated.getOracleAssetId());
        if (updated.getDateOfService() != null) existing.setDateOfService(updated.getDateOfService());
        if (updated.getTechnologySupported() != null) existing.setTechnologySupported(updated.getTechnologySupported());
        if (updated.getOldFarCategory() != null) existing.setOldFarCategory(updated.getOldFarCategory());
        if (updated.getCostCenterData() != null) existing.setCostCenterData(updated.getCostCenterData());
        if (updated.getNepAssetId() != null) existing.setNepAssetId(updated.getNepAssetId());
        if (updated.getDeleted() != null) existing.setDeleted(updated.getDeleted());
        if (updated.getAdjustment() != null) existing.setAdjustment(updated.getAdjustment());
        if (updated.getWriteOffDate() != null) existing.setWriteOffDate(updated.getWriteOffDate());
        if (updated.getTag() != null) existing.setTag(updated.getTag());
        if (updated.getHostSerialNumber() != null) existing.setHostSerialNumber(updated.getHostSerialNumber());
        if (updated.getTaskId() != null) existing.setTaskId(updated.getTaskId());
        if (updated.getPoLineNumber() != null) existing.setPoLineNumber(updated.getPoLineNumber());
        if (updated.getReleaseNumber() != null) existing.setReleaseNumber(updated.getReleaseNumber());
        if (updated.getSpectrumLicenseDate() != null) existing.setSpectrumLicenseDate(updated.getSpectrumLicenseDate());
        if (updated.getItemBarCode() != null) existing.setItemBarCode(updated.getItemBarCode());
        if (updated.getRfid() != null) existing.setRfid(updated.getRfid());
        if (updated.getInvoiceNumber() != null) existing.setInvoiceNumber(updated.getInvoiceNumber());
    }
}
