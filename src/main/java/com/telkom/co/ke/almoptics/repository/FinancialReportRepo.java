/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.repository;

import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author jgithu
 */
@Repository
public interface FinancialReportRepo extends JpaRepository<tb_FinancialReport, Long> {

    @Override
    List<tb_FinancialReport> findAll();

    tb_FinancialReport findBySerialNumber(String paramString);

      List<tb_FinancialReport> findByAssetId(String paramString);

}
