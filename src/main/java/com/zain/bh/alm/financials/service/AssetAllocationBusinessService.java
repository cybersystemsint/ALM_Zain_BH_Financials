package com.zain.bh.alm.financials.service;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.AssetAllocation;

@Service
public class AssetAllocationBusinessService {

    private final AssetAllocationService assetAllocationService;

    public AssetAllocationBusinessService(AssetAllocationService assetAllocationService) {
        this.assetAllocationService = assetAllocationService;
    }

    public void allocateAsset(String assetCode, String locationId, String personId, String status, String details) {
        AssetAllocation allocation = new AssetAllocation();
        allocation.setAssetCode(assetCode);
        allocation.setLocationId(locationId);
        allocation.setPersonId(personId);
        allocation.setStatus(status);
        allocation.setDetails(details);
        allocation.setAllocationDate(new Date());
        allocation.setRecordDatetime(new Date());
        assetAllocationService.save(allocation);
    }

    public void approveAllocation(String locationId, String personId, String status) {
        AssetAllocation allocation = !locationId.isEmpty()
                ? assetAllocationService.findByLocationId(locationId)
                : assetAllocationService.findByPersonId(personId);
        allocation.setStatus(status);
        assetAllocationService.save(allocation);
    }
}
