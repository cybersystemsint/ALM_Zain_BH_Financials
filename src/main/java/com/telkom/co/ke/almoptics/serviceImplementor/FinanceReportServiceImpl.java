/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.repository.FinancialReportRepo;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 *
 * @author jgithu
 */
@Service
@Transactional
public class FinanceReportServiceImpl implements FinancialReportService {

    @Autowired
    private FinancialReportRepo financeRepo;

    public List<tb_FinancialReport> findAll() {
        return this.financeRepo.findAll();

    }

    public tb_FinancialReport save(tb_FinancialReport boards) {
        return (tb_FinancialReport) this.financeRepo.save(boards);
    }

    public tb_FinancialReport findBySerialNumber(String paramString) {
        return this.financeRepo.findBySerialNumber(paramString);
    }

    public List<tb_FinancialReport> findByAssetId(String paramString) {
        return this.financeRepo.findByAssetId(paramString);

    }

}
