package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.repository.ExportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import java.util.List;

@Service
public class FinancialReportExport {
    @Autowired
    private ExportRepository repository;

    public List<tb_FinancialReport> getPage(int page, int size) {
        return repository.findAll(PageRequest.of(page, size)).getContent();
    }
}