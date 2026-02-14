package com.zain.bh.alm.financials.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.AssetJournal;

@Repository
public interface AssetJournalRepository extends JpaRepository<AssetJournal, Long> {
    AssetJournal findByAssetCode(String assetCode);
}

