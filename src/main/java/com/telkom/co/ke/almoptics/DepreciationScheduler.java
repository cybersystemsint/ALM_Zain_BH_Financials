/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics;

//import com.opencsv.exceptions.CsvValidationException;
//import com.telkom.co.ke.almoptics.entities.tb_Asset;
import com.telkom.co.ke.almoptics.entities.tb_Asset_Depreciation;
import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.services.AssetService;
import com.telkom.co.ke.almoptics.services.tb_Asset_DepreciationService;
//import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
import com.telkom.co.ke.almoptics.services.FarReportService;
import com.telkom.co.ke.almoptics.services.FinancialReportService;
//import java.awt.print.Pageable;
//import java.io.IOException;
//import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
//import java.util.Iterator;
import java.util.List;
//import javax.xml.parsers.ParserConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 *
 * @author jgithu
 */
@Component
public class DepreciationScheduler {

    private static final Logger LOGGER = LogManager.getLogger(DepreciationScheduler.class.getName());

    @Autowired
    private tb_Asset_DepreciationService tb_Asset_DepreciationService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private FinancialReportService financialReportService;

    @Autowired
    private FarReportService farReportService;

    // @Scheduled(cron = "0 50 10 * * *", zone = "Africa/Nairobi")
    public void processDepreciation() {
        try {
            LOGGER.info("SCHEDULER STARTED FOR DEPRECIATION");

            int pageNumber = 0;
            Page<tb_FarReport> page;
            int pageSize = 2000;

            do {
                Pageable pageable1 = PageRequest.of(pageNumber, pageSize);
                page = farReportService.findAll(pageable1); // Corrected method call

                List<tb_FarReport> assetsHere = page.getContent();
                // List<tb_FarReport> assetsHere = page.getContent();

                processRecords(assetsHere);

                pageNumber++;
            } while (page.hasNext());

            LOGGER.info("SCHEDULER COMPLETED FOR DEPRECIATION");
        } catch (Exception ex) {
            LOGGER.error("Exception occurred during scheduled task: ", ex);
        }
    }

    private void processRecords(List<tb_FarReport> assetsHere) {
        for (tb_FarReport asset : assetsHere) {
            try {

                // LOGGER.info("SCHEDULER STARTED FOR DEPRECIATION : ");
                int currentYear = Calendar.getInstance().get(1);
                LocalDate localDate = LocalDate.now();
                int purchaseYear = localDate.getYear();
                double accumulatedDepreciation = 0.0D;

                double initialCost = asset.getCost();
                double salvageValue = asset.getSalvageValue();
                int usefulLife = asset.getLife();
                Date dateInservice = asset.getDatePlacedInService();

                if (dateInservice == null) {
                    LOGGER.warn("Skipping asset " + asset.getAssetId() + " due to missing Date of Service");
                    continue;
                }

                //we dont have adjustment in new FAR
                double monthlyDepreciation = asset.getDepreciationAmount() != null ? asset.getDepreciationAmount() : 0;
                if (monthlyDepreciation == 0) {

                    monthlyDepreciation = (initialCost - salvageValue) / usefulLife;

                    LocalDate dateOfServiceLocal = dateInservice.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate dateNextMonth = dateOfServiceLocal.withDayOfMonth(1).plusMonths(1);
                    LocalDate currentDate = LocalDate.now();
                    long numberOfMonthsUtilised = ChronoUnit.MONTHS.between(dateNextMonth, currentDate);

                    numberOfMonthsUtilised = Math.min(numberOfMonthsUtilised, usefulLife);

                    accumulatedDepreciation = (monthlyDepreciation * numberOfMonthsUtilised);

                    double netCost = initialCost - accumulatedDepreciation;

                    double bookValue = initialCost - accumulatedDepreciation;

                    // Calculate Date of Retirement (DoAR)// this will be applied during disposal
                    LocalDate dateOfRetirement = dateNextMonth.plusMonths(usefulLife).minusDays(1);

                    // Check if asset code exists in tb_Asset_Depreciation table
                    tb_Asset_Depreciation tbassetDepreciation = this.tb_Asset_DepreciationService.findByAssetCode(asset.getAssetId());
                    if (tbassetDepreciation == null) {
                        tbassetDepreciation = new tb_Asset_Depreciation();
                        tbassetDepreciation.setAssetCode(asset.getAssetId());
                    }
                    // Update depreciation record
                    tbassetDepreciation.setAssetBookValue(netCost);
                    tbassetDepreciation.setDepreciationDate(currentDate.toString());
                    tbassetDepreciation.setRecordDatetime(new Date());
                    tbassetDepreciation.setAssetBookValue(bookValue);

                    if (netCost > 0) {
                        this.tb_Asset_DepreciationService.save(tbassetDepreciation);
                    }

                    asset.setMonthlyDepreciationAmt(monthlyDepreciation);
                    asset.setAccumulatedDepreciationAmt(accumulatedDepreciation);

                    if (netCost > 0) {
                        asset.setNetCost(netCost);
                    }

                    asset.setDepreciationDate(new Date());
                    this.farReportService.save(asset);
                } else {
                    // Calculate the number of months utilised
                    LocalDate dateOfServiceLocal = dateInservice.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate dateNextMonth = dateOfServiceLocal.withDayOfMonth(1).plusMonths(1);
                    LocalDate currentDate = LocalDate.now();
                    long numberOfMonthsUtilised = ChronoUnit.MONTHS.between(dateNextMonth, currentDate);

                    // Ensure NoMU does not exceed useful life
                    numberOfMonthsUtilised = Math.min(numberOfMonthsUtilised, usefulLife);

                    accumulatedDepreciation = (monthlyDepreciation * numberOfMonthsUtilised);

                    double netCost = initialCost - accumulatedDepreciation;

                    double bookValue = initialCost - accumulatedDepreciation;

                    tb_Asset_Depreciation tbassetDepreciation = this.tb_Asset_DepreciationService.findByAssetCode(asset.getAssetId());
                    if (tbassetDepreciation == null) {
                        tbassetDepreciation = new tb_Asset_Depreciation();
                        tbassetDepreciation.setAssetCode(asset.getAssetId());
                    }
                    // Update depreciation record
                    tbassetDepreciation.setAssetBookValue(netCost);
                    tbassetDepreciation.setDepreciationDate(currentDate.toString());
                    tbassetDepreciation.setRecordDatetime(new Date());
                    tbassetDepreciation.setAssetBookValue(bookValue);

                    if (netCost > 0) {
                        this.tb_Asset_DepreciationService.save(tbassetDepreciation);
                    }

                    asset.setMonthlyDepreciationAmt(monthlyDepreciation);
                    asset.setAccumulatedDepreciationAmt(accumulatedDepreciation);
                    if (netCost > 0) {
                        asset.setNetCost(netCost);
                    }

                    asset.setDepreciationDate(new Date());
                    this.farReportService.save(asset);

                }

            } catch (Exception ex) {

                LOGGER.info("Exception: ", ex);
            }
        }
    }

    public double calculateSalvageValue(double initialCost, double accumulatedDepreciation) {
        double salvageValue = initialCost - accumulatedDepreciation;
        return salvageValue;
    }

    public double calculateTotalDepreciation(double initialCost, double salvageValue, int usefulLife) {
        double annualDepreciation = (initialCost - salvageValue) / usefulLife;
        double totalDepreciation = annualDepreciation * usefulLife;
        return totalDepreciation;
    }

    public double calculateReducingBlDepre(double initialCost, double salvageValue, int usefulLife) {
        double depreciationRate = 0.25D;
        double bookValue = 0.0D;
        double accumulatedDepreciation = 0.0D;
        for (int year = 1; year <= usefulLife; year++) {
            double depreciation = (initialCost - accumulatedDepreciation) * depreciationRate;
            accumulatedDepreciation += depreciation;
            bookValue = initialCost - accumulatedDepreciation;
            System.out.println("Year " + year + " - Depreciation: " + depreciation + ", Book Value: " + bookValue);
        }
        return bookValue;
    }
}
