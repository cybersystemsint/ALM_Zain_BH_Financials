package com.zain.bh.alm.financials.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.zain.bh.alm.financials.dto.AssetAllocationRequestDTO;
import com.zain.bh.alm.financials.dto.AssetTransferRequestDTO;
import com.zain.bh.alm.financials.entity.Asset;
import com.zain.bh.alm.financials.entity.AssetAllocation;

public final class AssetMapper {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private AssetMapper() {
    }

    public static Asset toEntity(AssetTransferRequestDTO request) throws ParseException {
        Asset asset = new Asset();
        asset.setAssetCode(request.getAssetCode());
        asset.setCreatedBy(request.getCreatedBy());
        asset.setInventoryId(request.getInventoryId());
        asset.setPoId(request.getPoId());
        asset.setSalvageValue("0");
        asset.setPurchaseDate(DATE_FORMAT.parse(request.getPurchaseDate()));
        asset.setPurchasePrice((float) request.getPurchasePrice());
        asset.setRecordDatetime(DATE_FORMAT.parse(request.getPurchaseDate()));
        asset.setWarrantExpiryDate(request.getWarrantExpiryDate());
        asset.setSerialNumber(request.getSerialNumber());
        asset.setApproved(false);
        asset.setUsefulLife("0");
        asset.setStatus(request.getStatus());
        asset.setSupplierId(request.getSupplierId());
        asset.setWarrantyDetails(request.getWarrantyDetails());
        return asset;
    }

    public static AssetAllocation toEntity(AssetAllocationRequestDTO request) {
        AssetAllocation allocation = new AssetAllocation();
        allocation.setAllocationDate(new Date());
        allocation.setAssetCode(request.getAssetCode());
        allocation.setDetails(request.getDetails());
        allocation.setLocationId(request.getLocationId());
        allocation.setPersonId(request.getPersonId());
        allocation.setStatus(request.getStatus());
        allocation.setRecordDatetime(new Date());
        return allocation;
    }
}
