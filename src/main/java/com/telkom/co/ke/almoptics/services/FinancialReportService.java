/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
public interface FinancialReportService {

    List<tb_FinancialReport> findAll();

    tb_FinancialReport save(tb_FinancialReport paramtb_FinancialReport);

    tb_FinancialReport findBySerialNumber(String paramString);

    List<tb_FinancialReport> findByAssetId(String paramString);

}
