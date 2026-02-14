package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.AssetDepreciation;

@Repository
public interface AssetDepreciationRepository extends JpaRepository<AssetDepreciation, Long> {
    AssetDepreciation findByAssetCode(String assetCode);
}
