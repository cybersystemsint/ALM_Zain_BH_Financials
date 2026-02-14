package com.zain.bh.alm.financials.service.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.zain.bh.alm.financials.entity.FarReport;
import com.zain.bh.alm.financials.repository.FarReportRepository;
import com.zain.bh.alm.financials.service.FarReportService;

@Service
@Transactional
public class FarReportServiceImpl implements FarReportService {

    private final FarReportRepository farReportRepository;

    public FarReportServiceImpl(FarReportRepository farReportRepository) {
        this.farReportRepository = farReportRepository;
    }

    public Page<FarReport> findAll(Pageable pageable) {
        return farReportRepository.findAll(pageable);
    }

    public FarReport save(FarReport farReport) {
        return farReportRepository.save(farReport);
    }

    public List<FarReport> findByAssetId(String assetId) {
        return farReportRepository.findByAssetId(assetId);
    }
}
