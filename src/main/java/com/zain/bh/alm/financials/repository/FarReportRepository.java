package com.zain.bh.alm.financials.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zain.bh.alm.financials.entity.FarReport;

import java.util.List;

@Repository
public interface FarReportRepository extends JpaRepository<FarReport, Long> {
    Page<FarReport> findAll(Pageable pageable);
    List<FarReport> findByAssetId(String assetId);
}
