/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
//import java.awt.print.Pageable;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface FarReportService {

    //List<tb_FarReport> findAll();

    tb_FarReport save(tb_FarReport paramtb_FinancialReport);

    //tb_FinancialReport findBySerialNumber(String paramString);
    List<tb_FarReport> findByAssetId(String paramString);

    Page<tb_FarReport> findAll(Pageable pageable);

    // Page<tb_FarReport> findAll(Pageable pageable);
    //Page<tb_FarReport> findByAssetId(String assetId, Pageable pageable);
}
