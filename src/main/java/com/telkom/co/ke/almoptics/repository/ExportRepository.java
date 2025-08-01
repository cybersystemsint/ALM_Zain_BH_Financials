package com.telkom.co.ke.almoptics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;

public interface ExportRepository extends JpaRepository<tb_FinancialReport, Long> {}