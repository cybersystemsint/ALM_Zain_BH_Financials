package com.zain.bh.alm.financials.service;

import org.springframework.stereotype.Service;

@Service
public class AssetValidationService {

    public boolean isValidCost(double cost) {
        return cost >= 1;
    }

    public boolean isValidDate(String date) {
        return date != null && !date.trim().isEmpty();
    }

    public boolean isValidAssetCode(String assetCode) {
        return assetCode != null && !assetCode.trim().isEmpty();
    }

    public String validateAssetTransfer(String assetCode, double purchasePrice, String purchaseDate) {
        if (!isValidAssetCode(assetCode)) {
            return "Asset code cannot be empty";
        }
        if (!isValidCost(purchasePrice)) {
            return "Purchase price must be greater than 0";
        }
        if (!isValidDate(purchaseDate)) {
            return "Purchase date cannot be empty";
        }
        return null;
    }

    public String validateFinancialReport(String siteId, String dateOfService, double initialCost) {
        if (!isValidAssetCode(siteId)) {
            return "Site ID cannot be empty";
        }
        if (!isValidDate(dateOfService)) {
            return "Date of service cannot be empty";
        }
        if (!isValidCost(initialCost)) {
            return "Initial cost must be greater than 0";
        }
        return null;
    }
}
