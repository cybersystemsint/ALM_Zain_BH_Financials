//package com.telkom.co.ke.almoptics.controllers;
//
//import com.mysql.cj.result.Row;
//import com.telkom.co.ke.almoptics.services.FinancialReportExport;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.streaming.SXSSFWorkbook;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//import com.telkom.co.ke.almoptics.entities.tb_FinancialReport;
//
//import javax.servlet.http.HttpServletResponse;
//import java.io.IOException;
//import java.util.Date;
//import java.util.List;
//
//@RestController
//public class ExportController {
//
//    @Autowired
//    private FinancialReportExport financialReportExport;
//
//    @GetMapping("/export")
//    public void exportExcel(HttpServletResponse response) throws IOException {
//        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//        response.setHeader("Content-Disposition", "attachment; filename=financial_report.xlsx");
//
//        // Create SXSSFWorkbook with a window size of 100 rows
//        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
//        Sheet sheet = workbook.createSheet("Financial Report");
//
//        // Create styles
//        DataFormat dataFormat = workbook.createDataFormat();
//        short textFormat = dataFormat.getFormat("@"); // Text format for BigDecimal fields
//        CellStyle textStyle = workbook.createCellStyle();
//        textStyle.setDataFormat(textFormat);
//
//        short dateFormat = dataFormat.getFormat("yyyy-MM-dd HH:mm:ss"); // Date format for Date fields
//        CellStyle dateStyle = workbook.createCellStyle();
//        dateStyle.setDataFormat(dateFormat);
//
//        // Add headers
//        Row headerRow = sheet.createRow(0);
//        headerRow.createCell(0).setCellValue("Id");
//        headerRow.createCell(1).setCellValue("SiteID");
//        headerRow.createCell(2).setCellValue("Zone");
//        headerRow.createCell(3).setCellValue("NodeType");
//        headerRow.createCell(4).setCellValue("AssetName");
//        headerRow.createCell(5).setCellValue("AssetType");
//        headerRow.createCell(6).setCellValue("AssetCategory");
//        headerRow.createCell(7).setCellValue("Model");
//        headerRow.createCell(8).setCellValue("PartNumber");
//        headerRow.createCell(9).setCellValue("AssetSerialNumber");
//        headerRow.createCell(10).setCellValue("InstallationDate");
//        headerRow.createCell(11).setCellValue("InitialCost");
//        headerRow.createCell(12).setCellValue("MonthlyDepreciationAmount");
//        headerRow.createCell(13).setCellValue("AccumulatedDepreciation");
//        headerRow.createCell(14).setCellValue("NetCost");
//        headerRow.createCell(15).setCellValue("SalvageValue");
//        headerRow.createCell(16).setCellValue("PONumber");
//        headerRow.createCell(17).setCellValue("PODate");
//        headerRow.createCell(18).setCellValue("FA_CATEGORY");
//        headerRow.createCell(19).setCellValue("L1");
//        headerRow.createCell(20).setCellValue("L2");
//        headerRow.createCell(21).setCellValue("L3");
//        headerRow.createCell(22).setCellValue("L4");
//        headerRow.createCell(23).setCellValue("AccumulatedDepreciationCode");
//        headerRow.createCell(24).setCellValue("DepreciationCode");
//        headerRow.createCell(25).setCellValue("UsefulLifeMonths");
//        headerRow.createCell(26).setCellValue("VENDOR_NAME");
//        headerRow.createCell(27).setCellValue("VENDOR_NUMBER");
//        headerRow.createCell(28).setCellValue("PROJECT_NUMBER");
//        headerRow.createCell(29).setCellValue("Description");
//        headerRow.createCell(30).setCellValue("OracleAssetID");
//        headerRow.createCell(31).setCellValue("DateOfService");
//        headerRow.createCell(32).setCellValue("InsertDate");
//        headerRow.createCell(33).setCellValue("InsertedBy");
//        headerRow.createCell(34).setCellValue("ChangeDate");
//        headerRow.createCell(35).setCellValue("ChangedBy");
//        headerRow.createCell(36).setCellValue("StatusFlag");
//        headerRow.createCell(37).setCellValue("TechnologySupported");
//        headerRow.createCell(38).setCellValue("RetirementDate");
//        headerRow.createCell(39).setCellValue("OLDFARcategory");
//        headerRow.createCell(40).setCellValue("CostCenterData");
//        headerRow.createCell(41).setCellValue("FinancialApprovalStatus");
//        headerRow.createCell(42).setCellValue("NEPAssetID");
//        headerRow.createCell(43).setCellValue("Deleted");
//        headerRow.createCell(44).setCellValue("Adjustment");
//        headerRow.createCell(45).setCellValue("WriteOffDate");
//        headerRow.createCell(46).setCellValue("TAG");
//        headerRow.createCell(47).setCellValue("HostSerialNumber");
//        headerRow.createCell(48).setCellValue("TaskId");
//        headerRow.createCell(49).setCellValue("PoLineNumber");
//        headerRow.createCell(50).setCellValue("ReleaseNumber");
//        headerRow.createCell(51).setCellValue("SpectrumLicenseDate");
//        headerRow.createCell(52).setCellValue("ItemBarCode");
//        headerRow.createCell(53).setCellValue("RFID");
//        headerRow.createCell(54).setCellValue("InvoiceNumber");
//        headerRow.createCell(55).setCellValue("OriginalState");
//        headerRow.createCell(56).setCellValue("Version");
//
//        // Fetch data in batches
//        int page = 0;
//        int size = 1000;
//        List<tb_FinancialReport> reportList;
//        do {
//            reportList = financialReportExport.getPage(page, size);
//            for (tb_FinancialReport report : reportList) {
//                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
//                row.createCell(0).setCellValue(report.getId() != null ? report.getId() : 0);
//                row.createCell(1).setCellValue(report.getSiteId() != null ? report.getSiteId() : "");
//                row.createCell(2).setCellValue(report.getZone() != null ? report.getZone() : "");
//                row.createCell(3).setCellValue(report.getNodeType() != null ? report.getNodeType() : "");
//                row.createCell(4).setCellValue(report.getAssetName() != null ? report.getAssetName() : "");
//                row.createCell(5).setCellValue(report.getAssetType() != null ? report.getAssetType() : "");
//                row.createCell(6).set挿
//
//                row.createCell(7).setCellValue(report.getModel() != null ? report.getModel() : "");
//                row.createCell(8).setCellValue(report.getPartNumber() != null ? report.getPartNumber() : "");
//                row.createCell(9).setCellValue(report.getAssetSerialNumber() != null ? report.getAssetSerialNumber() : "");
//                row.createCell(10).setCellValue(report.getInstallationDate() != null ? report.getInstallationDate() : "");
//                Cell cell11 = row.createCell(11);
//                cell11.setCellValue(report.getInitialCost() != null ? report.getInitialCost().toString() : "");
//                cell11.setCellStyle(textStyle);
//                Cell cell12 = row.createCell(12);
//                cell12.setCellValue(report.getMonthlyDepreciationAmount() != null ? report.getMonthlyDepreciationAmount().toString() : "");
//                cell12.setCellStyle(textStyle);
//                Cell cell13 = row.createCell(13);
//                cell13.setCellValue(report.getAccumulatedDepreciation() != null ? report.getAccumulatedDepreciation().toString() : "");
//                cell13.setCellStyle(textStyle);
//                Cell cell14 = row.createCell(14);
//                cell14.setCellValue(report.getNetCost() != null ? report.getNetCost().toString() : "");
//                cell14.setCellStyle(textStyle);
//                Cell cell15 = row.createCell(15);
//                cell15.setCellValue(report.getSalvageValue() != null ? report.getSalvageValue().toString() : "");
//                cell15.setCellStyle(textStyle);
//                row.createCell(16).setCellValue(report.getPoNumber() != null ? report.getPoNumber() : "");
//                row.createCell(17).setCellValue(report.getPoDate() != null ? report.getPoDate() : "");
//                row.createCell(18).setCellValue(report.getFaCategory() != null ? report.getFaCategory() : "");
//                row.createCell(19).setCellValue(report.getL1() != null ? report.getL1() : "");
//                row.createCell(20).setCellValue(report.getL2() != null ? report.getL2() : "");
//                row.createCell(21).setCellValue(report.getL3() != null ? report.getL3() : "");
//                row.createCell(22).setCellValue(report.getL4() != null ? report.getL4() : "");
//                row.createCell(23).setCellValue(report.getAccumulatedDepreciationCode() != null ? report.getAccumulatedDepreciationCode() : "");
//                row.createCell(24).setCellValue(report.getDepreciationCode() != null ? report.getDepreciationCode() : "");
//                row.createCell(25).setCellValue(report.getUsefulLifeMonths() != null ? report.getUsefulLifeMonths() : 0);
//                row.createCell(26).setCellValue(report.getVendorName() != null ? report.getVendorName() : "");
//                row.createCell(27).setCellValue(report.getVendorNumber() != null ? report.getVendorNumber() : "");
//                row.createCell(28).setCellValue(report.getProjectNumber() != null ? report.getProjectNumber() : "");
//                row.createCell(29).setCellValue(report.getDescription() != null ? report.getDescription() : "");
//                row.createCell(30).setCellValue(report.getOracleAssetId() != null ? report.getOracleAssetId() : "");
//                row.createCell(31).setCellValue(report.getDateOfService() != null ? report.getDateOfService() : "");
//                Cell cell32 = row.createCell(32);
//                cell32.setCellValue(report.getInsertDate() != null ? report.getInsertDate() : new Date(0));
//                cell32.setCellStyle(dateStyle);
//                row.createCell(33).setCellValue(report.getInsertedBy() != null ? report.getInsertedBy() : "");
//                Cell cell34 = row.createCell(34);
//                cell34.setCellValue(report.getChangeDate() != null ? report.getChangeDate() : new Date(0));
//                cell34.setCellStyle(dateStyle);
//                row.createCell(35).setCellValue(report.getChangedBy() != null ? report.getChangedBy() : "");
//                row.createCell(36).setCellValue(report.getStatusFlag() != null ? report.getStatusFlag() : "");
//                row.createCell(37).setCellValue(report.getTechnologySupported() != null ? report.getTechnologySupported() : "");
//                Cell cell38 = row.createCell(38);
//                cell38.setCellValue(report.getRetirementDate() != null ? report.getRetirementDate() : new Date(0));
//                cell38.setCellStyle(dateStyle);
//                row.createCell(39).setCellValue(report.getOldFarCategory() != null ? report.getOldFarCategory() : "");
//                row.createCell(40).setCellValue(report.getCostCenterData() != null ? report.getCostCenterData() : "");
//                row.createCell(41).setCellValue(report.getFinancialApprovalStatus() != null ? report.getFinancialApprovalStatus() : "");
//                row.createCell(42).setCellValue(report.getNepAssetId() != null ? report.getNepAssetId() : "");
//                row.createCell(43).setCellValue(report.getDeleted() != null ? report.getDeleted() : "");
//                Cell cell44 = row.createCell(44);
//                cell44.setCellValue(report.getAdjustment() != null ? report.getAdjustment().toString() : "");
//                cell44.setCellStyle(textStyle);
//                Cell cell45 = row.createCell(45);
//                cell45.setCellValue(report.getWriteOffDate() != null ? report.getWriteOffDate() : new Date(0));
//                cell45.setCellStyle(dateStyle);
//                row.createCell(46).setCellValue(report.getTag() != null ? report.getTag() : "");
//                row.createCell(47).setCellValue(report.getHostSerialNumber() != null ? report.getHostSerialNumber() : "");
//                row.createCell(48).setCellValue(report.getTaskId() != null ? report.getTaskId() : "");
//                row.createCell(49).setCellValue(report.getPo | null ? report.getPoLineNumber() : "");
//                row.createCell(50).setCellValue(report.getReleaseNumber() != null ? report.getReleaseNumber() : "");
//                Cell cell51 = row.createCell(51);
//                cell51.setCellValue(report.getSpectrumLicenseDate() != null ? report.getSpectrumLicenseDate() : new Date(0));
//                cell51.setCellStyle(dateStyle);
//                row.createCell(52).setCellValue(report.getItemBarCode() != null ? report.getItemBarCode() : "");
//                row.createCell(53).setCellValue(report.getRfid() != null ? report.getRfid() : "");
//                row.createCell(54).setCellValue(report.getInvoiceNumber() != null ? report.getInvoiceNumber() : "");
//                row.createCell(55).setCellValue(report.getOriginalState() != null ? report.getOriginalState() : "");
//                row.createCell(56).setCellValue(report.getVersion() != null ? report.getVersion() : 0);
//            }
//            page++;
//        } while (reportList.size() == size);
//
//        // Write the workbook to the response output stream
//        workbook.write(response.getOutputStream());
//        workbook.dispose(); // Dispose to free up resources
//    }
//}