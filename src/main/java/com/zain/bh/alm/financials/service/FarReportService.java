package com.zain.bh.alm.financials.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.zain.bh.alm.financials.entity.FarReport;

public interface FarReportService {

    FarReport save(FarReport farReport);

    List<FarReport> findByAssetId(String assetId);

    Page<FarReport> findAll(Pageable pageable);
}
