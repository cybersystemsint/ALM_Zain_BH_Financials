package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.dto.ApprovalRequest;
import com.telkom.co.ke.almoptics.dto.PageResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetch / search / export service for Unmapped Inventory reports (Active, Passive, IT).
 */
@Service
public class UnmappedReportFetchService {

    private static final Logger logger = LoggerFactory.getLogger(UnmappedReportFetchService.class);

    private static final int EXPORT_BATCH_SIZE = 100_000;

    /**
     * Export headers for Unmapped Active Inventory
     */
    private static final String[] UNMAPPED_ACTIVE_HEADERS = {
            "ID", "SiteID", "NodeName", "AssetName", "AssetType", "NodeType",
            "Manufacturer", "Model", "PartNumber", "SerialNumber", "Description",
            "ManufacturingDate", "InstallationDate", "AssetUpdateDate", "Warranty",
            "InsertedBy", "InsertDate"
    };

    /**
     * Export headers for Unmapped Passive Inventory
     */
    private static final String[] UNMAPPED_PASSIVE_HEADERS = {
            "ID", "ElementID", "ElementType", "ParentNEType", "SiteID",
            "Category", "ItemBarCode", "Serial", "UOM", "EntryDate",
            "EntryUser", "Model", "ItemClassification", "ItemClassification2",
            "Notes", "PRPoNo"
    };

    /**
     * Export headers for Unmapped IT Inventory
     */
    private static final String[] UNMAPPED_IT_HEADERS = {
            "ID", "ElementID", "ElementType", "ParentNEType", "SiteID",
            "Floor", "OS", "HardwareVendor", "HostType", "HostSerialNumber",
            "DiskDriveSerialNumber", "HardwareSerialNumber", "MemoryPartNumber",
            "Domain", "Warranty", "SKUNumber", "AssetInsertDate", "IPAddress",
            "Model", "Manufacturer", "LastUpdateSuccess", "HostName"
    };

    /**
     * SELECT clause for Unmapped Active Inventory
     */
    private static final String UNMAPPED_ACTIVE_SELECT =
            "SELECT ua.id, ua.SiteId, ua.NodeName, ua.AssetName, ua.AssetType, ua.NodeType, " +
                    "ua.Manufacturer, ua.Model, ua.PartNumber, ua.SerialNumber, ua.Description, " +
                    "ua.ManufacturingDate, ua.InstallationDate, ua.AssetUpdateDate, ua.Warranty, " +
                    "ua.InsertedBy, ua.InsertDate " +
                    "FROM tb_unmappednode ua WHERE 1=1 ";

    /**
     * SELECT clause for Unmapped Passive Inventory
     */
    private static final String UNMAPPED_PASSIVE_SELECT =
            "SELECT up.ID, up.ElementID, up.Element_Type, up.Parent_NE_Type, up.Site_ID, " +
                    "up.Category, up.Item_BarCode, up.Serial, up.UOM, up.Entry_Date, " +
                    "up.Entry_User, up.Model, up.Item_Classification, up.Item_Classification_2, " +
                    "up.Notes, up.PR_PONo " +
                    "FROM tb_unmappedPassive_Inventory up WHERE 1=1 ";

    /**
     * SELECT clause for Unmapped IT Inventory
     */
    private static final String UNMAPPED_IT_SELECT =
            "SELECT ui.ID, ui.Element_ID, ui.Element_Type, ui.Parent_NE_Type, ui.Site_ID, " +
                    "ui.Floor, ui.OS, ui.Hardware_Vendor, ui.Host_Type, ui.Host_Serial_Number, " +
                    "ui.Disk_Drive_Serial_Number, ui.Hardware_Serial_Number, ui.Memory_Part_Number, " +
                    "ui.Domain, ui.Warranty, ui.SKU_Number, ui.Asset_Insert_Date, ui.IP_Address, " +
                    "ui.Model, ui.Manufacturer, ui.Last_Update_Success, ui.Host_Name " +
                    "FROM tb_unmappedIT_INVENTORY ui WHERE 1=1 ";

    @PersistenceContext
    private EntityManager entityManager;

    // ==================================================================
    // PUBLIC API - UNMAPPED ACTIVE
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> searchUnmappedActive(ApprovalRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 100;

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> data = fetchPageUnmappedActive(request, page * size, size);

        long total = countMatchingUnmappedActive(request);
        long ms = System.currentTimeMillis() - t0;

        logger.info("Unmapped Active fetch page={} size={} returned={} total={} took={}ms",
                page, size, data.size(), total, ms);

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setTotalElements(total);
        result.setTotalPages((int) Math.ceil((double) total / size));
        result.setPage(page);
        result.setSize(size);
        result.setdata(data);
        return result;
    }

    @Transactional(readOnly = true)
    public void streamUnmappedActiveToCsv(ApprovalRequest request, PrintWriter writer) {
        logger.info("Unmapped Active CSV export started. exportAll={}", request.isExportAll());
        writer.println(String.join(",", UNMAPPED_ACTIVE_HEADERS));
        writer.flush();

        Long lastId = null;
        int exported = 0;
        long t0 = System.currentTimeMillis();

        while (true) {
            int batch = EXPORT_BATCH_SIZE;
            List<Map<String, Object>> rows = fetchBatchAfterUnmappedActive(request, lastId, batch);
            if (rows.isEmpty()) break;

            for (Map<String, Object> row : rows) {
                writer.println(toCsvRow(row));
                exported++;
            }
            writer.flush();

            logger.info("Unmapped Active CSV batch flushed: rows={} lastId={} totalSoFar={}",
                    rows.size(), lastId, exported);

            if (rows.size() < batch) break;
            lastId = ((Number) rows.get(rows.size() - 1).get("id")).longValue();
        }

        logger.info("Unmapped Active CSV export completed: rows={} duration={}ms",
                exported, System.currentTimeMillis() - t0);
    }

    @Transactional(readOnly = true)
    public void streamUnmappedActiveToExcel(ApprovalRequest request, OutputStream out) throws Exception {
        logger.info("Unmapped Active Excel export started. exportAll={}", request.isExportAll());
        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("UnmappedActive");
            Row header = sheet.createRow(0);
            for (int i = 0; i < UNMAPPED_ACTIVE_HEADERS.length; i++) {
                header.createCell(i).setCellValue(UNMAPPED_ACTIVE_HEADERS[i]);
            }

            int rowIdx = 1;
            Long lastId = null;
            int exported = 0;
            long t0 = System.currentTimeMillis();

            while (true) {
                int batch = EXPORT_BATCH_SIZE;
                List<Map<String, Object>> rows = fetchBatchAfterUnmappedActive(request, lastId, batch);
                if (rows.isEmpty()) break;

                for (Map<String, Object> row : rows) {
                    Row excelRow = sheet.createRow(rowIdx++);
                    fillExcelRow(excelRow, row);
                    exported++;
                }

                logger.info("Unmapped Active Excel batch added: rows={} lastId={} totalSoFar={}",
                        rows.size(), lastId, exported);

                if (rows.size() < batch) break;
                lastId = ((Number) rows.get(rows.size() - 1).get("id")).longValue();
            }

            workbook.write(out);
            out.flush();

            logger.info("Unmapped Active Excel export completed: rows={} duration={}ms",
                    exported, System.currentTimeMillis() - t0);
        } finally {
            workbook.dispose();
            try { workbook.close(); } catch (Exception ignored) {}
        }
    }

    // ==================================================================
    // PUBLIC API - UNMAPPED PASSIVE
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> searchUnmappedPassive(ApprovalRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 100;

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> data = fetchPageUnmappedPassive(request, page * size, size);

        long total = countMatchingUnmappedPassive(request);
        long ms = System.currentTimeMillis() - t0;

        logger.info("Unmapped Passive fetch page={} size={} returned={} total={} took={}ms",
                page, size, data.size(), total, ms);

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setTotalElements(total);
        result.setTotalPages((int) Math.ceil((double) total / size));
        result.setPage(page);
        result.setSize(size);
        result.setdata(data);
        return result;
    }

    @Transactional(readOnly = true)
    public void streamUnmappedPassiveToCsv(ApprovalRequest request, PrintWriter writer) {
        logger.info("Unmapped Passive CSV export started. exportAll={}", request.isExportAll());
        writer.println(String.join(",", UNMAPPED_PASSIVE_HEADERS));
        writer.flush();

        Long lastId = null;
        int exported = 0;
        long t0 = System.currentTimeMillis();

        while (true) {
            int batch = EXPORT_BATCH_SIZE;
            List<Map<String, Object>> rows = fetchBatchAfterUnmappedPassive(request, lastId, batch);
            if (rows.isEmpty()) break;

            for (Map<String, Object> row : rows) {
                writer.println(toCsvRow(row));
                exported++;
            }
            writer.flush();

            logger.info("Unmapped Passive CSV batch flushed: rows={} lastId={} totalSoFar={}",
                    rows.size(), lastId, exported);

            if (rows.size() < batch) break;
            lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
        }

        logger.info("Unmapped Passive CSV export completed: rows={} duration={}ms",
                exported, System.currentTimeMillis() - t0);
    }

    @Transactional(readOnly = true)
    public void streamUnmappedPassiveToExcel(ApprovalRequest request, OutputStream out) throws Exception {
        logger.info("Unmapped Passive Excel export started. exportAll={}", request.isExportAll());
        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("UnmappedPassive");
            Row header = sheet.createRow(0);
            for (int i = 0; i < UNMAPPED_PASSIVE_HEADERS.length; i++) {
                header.createCell(i).setCellValue(UNMAPPED_PASSIVE_HEADERS[i]);
            }

            int rowIdx = 1;
            Long lastId = null;
            int exported = 0;
            long t0 = System.currentTimeMillis();

            while (true) {
                int batch = EXPORT_BATCH_SIZE;
                List<Map<String, Object>> rows = fetchBatchAfterUnmappedPassive(request, lastId, batch);
                if (rows.isEmpty()) break;

                for (Map<String, Object> row : rows) {
                    Row excelRow = sheet.createRow(rowIdx++);
                    fillExcelRow(excelRow, row);
                    exported++;
                }

                logger.info("Unmapped Passive Excel batch added: rows={} lastId={} totalSoFar={}",
                        rows.size(), lastId, exported);

                if (rows.size() < batch) break;
                lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
            }

            workbook.write(out);
            out.flush();

            logger.info("Unmapped Passive Excel export completed: rows={} duration={}ms",
                    exported, System.currentTimeMillis() - t0);
        } finally {
            workbook.dispose();
            try { workbook.close(); } catch (Exception ignored) {}
        }
    }

    // ==================================================================
    // PUBLIC API - UNMAPPED IT
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> searchUnmappedIT(ApprovalRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 100;

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> data = fetchPageUnmappedIT(request, page * size, size);

        long total = countMatchingUnmappedIT(request);
        long ms = System.currentTimeMillis() - t0;

        logger.info("Unmapped IT fetch page={} size={} returned={} total={} took={}ms",
                page, size, data.size(), total, ms);

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setTotalElements(total);
        result.setTotalPages((int) Math.ceil((double) total / size));
        result.setPage(page);
        result.setSize(size);
        result.setdata(data);
        return result;
    }

    @Transactional(readOnly = true)
    public void streamUnmappedITToCsv(ApprovalRequest request, PrintWriter writer) {
        logger.info("Unmapped IT CSV export started. exportAll={}", request.isExportAll());
        writer.println(String.join(",", UNMAPPED_IT_HEADERS));
        writer.flush();

        Long lastId = null;
        int exported = 0;
        long t0 = System.currentTimeMillis();

        while (true) {
            int batch = EXPORT_BATCH_SIZE;
            List<Map<String, Object>> rows = fetchBatchAfterUnmappedIT(request, lastId, batch);
            if (rows.isEmpty()) break;

            for (Map<String, Object> row : rows) {
                writer.println(toCsvRow(row));
                exported++;
            }
            writer.flush();

            logger.info("Unmapped IT CSV batch flushed: rows={} lastId={} totalSoFar={}",
                    rows.size(), lastId, exported);

            if (rows.size() < batch) break;
            lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
        }

        logger.info("Unmapped IT CSV export completed: rows={} duration={}ms",
                exported, System.currentTimeMillis() - t0);
    }

    @Transactional(readOnly = true)
    public void streamUnmappedITToExcel(ApprovalRequest request, OutputStream out) throws Exception {
        logger.info("Unmapped IT Excel export started. exportAll={}", request.isExportAll());
        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("UnmappedIT");
            Row header = sheet.createRow(0);
            for (int i = 0; i < UNMAPPED_IT_HEADERS.length; i++) {
                header.createCell(i).setCellValue(UNMAPPED_IT_HEADERS[i]);
            }

            int rowIdx = 1;
            Long lastId = null;
            int exported = 0;
            long t0 = System.currentTimeMillis();

            while (true) {
                int batch = EXPORT_BATCH_SIZE;
                List<Map<String, Object>> rows = fetchBatchAfterUnmappedIT(request, lastId, batch);
                if (rows.isEmpty()) break;

                for (Map<String, Object> row : rows) {
                    Row excelRow = sheet.createRow(rowIdx++);
                    fillExcelRow(excelRow, row);
                    exported++;
                }

                logger.info("Unmapped IT Excel batch added: rows={} lastId={} totalSoFar={}",
                        rows.size(), lastId, exported);

                if (rows.size() < batch) break;
                lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
            }

            workbook.write(out);
            out.flush();

            logger.info("Unmapped IT Excel export completed: rows={} duration={}ms",
                    exported, System.currentTimeMillis() - t0);
        } finally {
            workbook.dispose();
            try { workbook.close(); } catch (Exception ignored) {}
        }
    }

    // ==================================================================
    // PRIVATE HELPERS - UNMAPPED ACTIVE
    // ==================================================================

    private List<Map<String, Object>> fetchPageUnmappedActive(ApprovalRequest request, int offset, int limit) {
        StringBuilder sql = new StringBuilder(UNMAPPED_ACTIVE_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedActive(sql, params, request);
        sql.append(" ORDER BY ua.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return runSelect(sql.toString(), params);
    }

    private List<Map<String, Object>> fetchBatchAfterUnmappedActive(ApprovalRequest request, Long lastId, int limit) {
        StringBuilder sql = new StringBuilder(UNMAPPED_ACTIVE_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedActive(sql, params, request);

        if (lastId != null && lastId > 0) {
            sql.append(" AND ua.id > ?");
            params.add(lastId);
        }

        sql.append(" ORDER BY ua.id ASC LIMIT ?");
        params.add(limit);
        return runSelect(sql.toString(), params);
    }

    private long countMatchingUnmappedActive(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tb_unmappednode ua WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedActive(sql, params, request);
        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    private void applyFiltersUnmappedActive(StringBuilder sql, List<Object> params, ApprovalRequest request) {
        if (request == null) return;

        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            for (ApprovalRequest.Filter f : request.getFilters()) {
                if (f == null || f.getColumn() == null) continue;

                String column = f.getColumn().trim();
                String value = f.getValue() != null ? f.getValue().trim() : "";
                String op = f.getOperator() != null ? f.getOperator().name() : "CONTAINS";

                String colExpr = resolveUnmappedActiveColumn(column);
                if (colExpr == null) {
                    logger.warn("Ignoring filter on unknown Unmapped Active column: {}", column);
                    continue;
                }
                appendCondition(sql, params, colExpr, value, op);
            }
        }

        // Date filter for InsertDate (dateTo only, dateFrom excluded)
        if (notBlank(request.getDateTo())) {
            sql.append(" AND ua.InsertDate <= ?");
            params.add(request.getDateTo() + " 23:59:59");
        }
    }

    private String resolveUnmappedActiveColumn(String column) {
        // FIXED: Normalize by removing underscores and converting to lowercase
        // Accepts any case variation and underscore combination
        String normalized = column.replaceAll("_", "").toLowerCase();

        switch (normalized) {
            case "id": return "ua.id";
            case "siteid": return "ua.SiteId";
            case "nodename": return "ua.NodeName";
            case "assetname": return "ua.AssetName";
            case "assettype": return "ua.AssetType";
            case "nodetype": return "ua.NodeType";
            case "manufacturer": return "ua.Manufacturer";
            case "model": return "ua.Model";
            case "partnumber": return "ua.PartNumber";
            case "serialnumber": return "ua.SerialNumber";
            case "description": return "ua.Description";
            case "manufacturingdate": return "ua.ManufacturingDate";  // ← ADDED
            case "installationdate": return "ua.InstallationDate";    // ← ADDED
            case "assetupdatedate": return "ua.AssetUpdateDate";      // ← ADDED
            case "warranty": return "ua.Warranty";
            case "insertedby": return "ua.InsertedBy";
            case "insertdate": return "ua.InsertDate";               // ← ADDED
            default: return null;
        }
    }

    // ==================================================================
    // PRIVATE HELPERS - UNMAPPED PASSIVE
    // ==================================================================

    private List<Map<String, Object>> fetchPageUnmappedPassive(ApprovalRequest request, int offset, int limit) {
        StringBuilder sql = new StringBuilder(UNMAPPED_PASSIVE_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedPassive(sql, params, request);
        sql.append(" ORDER BY up.ID DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return runSelect(sql.toString(), params);
    }

    private List<Map<String, Object>> fetchBatchAfterUnmappedPassive(ApprovalRequest request, Long lastId, int limit) {
        StringBuilder sql = new StringBuilder(UNMAPPED_PASSIVE_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedPassive(sql, params, request);

        if (lastId != null && lastId > 0) {
            sql.append(" AND up.ID > ?");
            params.add(lastId);
        }

        sql.append(" ORDER BY up.ID ASC LIMIT ?");
        params.add(limit);
        return runSelect(sql.toString(), params);
    }

    private long countMatchingUnmappedPassive(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tb_unmappedPassive_Inventory up WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedPassive(sql, params, request);
        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    private void applyFiltersUnmappedPassive(StringBuilder sql, List<Object> params, ApprovalRequest request) {
        if (request == null) return;

        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            for (ApprovalRequest.Filter f : request.getFilters()) {
                if (f == null || f.getColumn() == null) continue;

                String column = f.getColumn().trim();
                String value = f.getValue() != null ? f.getValue().trim() : "";
                String op = f.getOperator() != null ? f.getOperator().name() : "CONTAINS";

                String colExpr = resolveUnmappedPassiveColumn(column);
                if (colExpr == null) {
                    logger.warn("Ignoring filter on unknown Unmapped Passive column: {}", column);
                    continue;
                }
                appendCondition(sql, params, colExpr, value, op);
            }
        }

        // Date filter for EntryDate (dateTo only, dateFrom excluded)
        if (notBlank(request.getDateTo())) {
            sql.append(" AND up.Entry_Date <= ?");
            params.add(request.getDateTo() + " 23:59:59");
        }
    }

    private String resolveUnmappedPassiveColumn(String column) {
        // FIXED: Normalize by removing underscores and converting to lowercase
        // Accepts: "Site_ID", "SiteID", "site_id", "SITE_ID" - all work!
        String normalized = column.replaceAll("_", "").toLowerCase();

        switch (normalized) {
            case "id": return "up.ID";
            case "elementid": return "up.ElementID";
            case "elementtype": return "up.Element_Type";
            case "parentnetype": return "up.Parent_NE_Type";
            case "siteid": return "up.Site_ID";
            case "category": return "up.Category";
            case "itembarcode": return "up.Item_BarCode";
            case "serial": return "up.Serial";
            case "uom": return "up.UOM";
            case "entrydate": return "up.Entry_Date";              // ← ADDED (was missing)
            case "entryuser": return "up.Entry_User";
            case "model": return "up.Model";
            case "itemclassification": return "up.Item_Classification";
            case "itemclassification2": return "up.Item_Classification_2";
            case "notes": return "up.Notes";
            case "prpono": return "up.PR_PONo";
            default: return null;
        }
    }

    // ==================================================================
    // PRIVATE HELPERS - UNMAPPED IT
    // ==================================================================

    private List<Map<String, Object>> fetchPageUnmappedIT(ApprovalRequest request, int offset, int limit) {
        StringBuilder sql = new StringBuilder(UNMAPPED_IT_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedIT(sql, params, request);
        sql.append(" ORDER BY ui.ID DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return runSelect(sql.toString(), params);
    }

    private List<Map<String, Object>> fetchBatchAfterUnmappedIT(ApprovalRequest request, Long lastId, int limit) {
        StringBuilder sql = new StringBuilder(UNMAPPED_IT_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedIT(sql, params, request);

        if (lastId != null && lastId > 0) {
            sql.append(" AND ui.ID > ?");
            params.add(lastId);
        }

        sql.append(" ORDER BY ui.ID ASC LIMIT ?");
        params.add(limit);
        return runSelect(sql.toString(), params);
    }

    private long countMatchingUnmappedIT(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tb_unmappedIT_INVENTORY ui WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        applyFiltersUnmappedIT(sql, params, request);
        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    private void applyFiltersUnmappedIT(StringBuilder sql, List<Object> params, ApprovalRequest request) {
        if (request == null) return;

        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            for (ApprovalRequest.Filter f : request.getFilters()) {
                if (f == null || f.getColumn() == null) continue;

                String column = f.getColumn().trim();
                String value = f.getValue() != null ? f.getValue().trim() : "";
                String op = f.getOperator() != null ? f.getOperator().name() : "CONTAINS";

                String colExpr = resolveUnmappedITColumn(column);
                if (colExpr == null) {
                    logger.warn("Ignoring filter on unknown Unmapped IT column: {}", column);
                    continue;
                }
                appendCondition(sql, params, colExpr, value, op);
            }
        }

        // Date filter for AssetInsertDate (dateTo only, dateFrom excluded)
        if (notBlank(request.getDateTo())) {
            sql.append(" AND ui.Asset_Insert_Date <= ?");
            params.add(request.getDateTo() + " 23:59:59");
        }
    }

    private String resolveUnmappedITColumn(String column) {
        // FIXED: Normalize by removing underscores and converting to lowercase
        // Accepts: "Host_Name", "HostName", "host_name", "HOST_NAME" - all work!
        String normalized = column.replaceAll("_", "").toLowerCase();

        switch (normalized) {
            case "id": return "ui.ID";
            case "elementid": return "ui.Element_ID";
            case "elementtype": return "ui.Element_Type";
            case "parentnetype": return "ui.Parent_NE_Type";
            case "siteid": return "ui.Site_ID";
            case "floor": return "ui.Floor";
            case "os": return "ui.OS";
            case "hardwarevendor": return "ui.Hardware_Vendor";
            case "hosttype": return "ui.Host_Type";
            case "hostserialnumber": return "ui.Host_Serial_Number";
            case "diskdriveserialnumber": return "ui.Disk_Drive_Serial_Number";
            case "hardwareserialnumber": return "ui.Hardware_Serial_Number";
            case "memorypartnumber": return "ui.Memory_Part_Number";
            case "domain": return "ui.Domain";
            case "warranty": return "ui.Warranty";
            case "skunumber": return "ui.SKU_Number";
            case "ipaddress": return "ui.IP_Address";
            case "model": return "ui.Model";
            case "manufacturer": return "ui.Manufacturer";
            case "lastupdatesuccess": return "ui.Last_Update_Success";
            case "hostname": return "ui.Host_Name";
            case "assetinsertdate": return "ui.Asset_Insert_Date";  // ← ADDED (was missing)
            default: return null;
        }
    }

    // ==================================================================
    // COMMON HELPERS
    // ==================================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runSelect(String sql, List<Object> params) {
        Query q = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        List<Object[]> rows = q.getResultList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            // Determine which query this is based on column count and map accordingly
            if (sql.contains("ua.id") || sql.contains("FROM tb_unmappednode")) {
                // Unmapped Active columns
                if (row.length >= 17) {
                    item.put("id", row[0]);
                    item.put("SiteId", row[1]);
                    item.put("NodeName", row[2]);
                    item.put("AssetName", row[3]);
                    item.put("AssetType", row[4]);
                    item.put("NodeType", row[5]);
                    item.put("Manufacturer", row[6]);
                    item.put("Model", row[7]);
                    item.put("PartNumber", row[8]);
                    item.put("SerialNumber", row[9]);
                    item.put("Description", row[10]);
                    item.put("ManufacturingDate", row[11]);
                    item.put("InstallationDate", row[12]);
                    item.put("AssetUpdateDate", row[13]);
                    item.put("Warranty", row[14]);
                    item.put("InsertedBy", row[15]);
                    item.put("InsertDate", row[16]);
                }
            } else if (sql.contains("up.ID") || sql.contains("FROM tb_unmappedPassive_Inventory")) {
                // Unmapped Passive columns
                if (row.length >= 15) {
                    item.put("ID", row[0]);
                    item.put("ElementID", row[1]);
                    item.put("Element_Type", row[2]);
                    item.put("Parent_NE_Type", row[3]);
                    item.put("Site_ID", row[4]);
                    item.put("Category", row[5]);
                    item.put("Item_BarCode", row[6]);
                    item.put("Serial", row[7]);
                    item.put("UOM", row[8]);
                    item.put("Entry_Date", row[9]);
                    item.put("Entry_User", row[10]);
                    item.put("Model", row[11]);
                    item.put("Item_Classification", row[12]);
                    item.put("Item_Classification_2", row[13]);
                    item.put("Notes", row[14]);
                    if (row.length > 15) {
                        item.put("PR_PONo", row[15]);
                    }
                }
            } else if (sql.contains("ui.ID") || sql.contains("FROM tb_unmappedIT_INVENTORY")) {
                // Unmapped IT columns
                if (row.length >= 22) {
                    item.put("ID", row[0]);
                    item.put("Element_ID", row[1]);
                    item.put("Element_Type", row[2]);
                    item.put("Parent_NE_Type", row[3]);
                    item.put("Site_ID", row[4]);
                    item.put("Floor", row[5]);
                    item.put("OS", row[6]);
                    item.put("Hardware_Vendor", row[7]);
                    item.put("Host_Type", row[8]);
                    item.put("Host_Serial_Number", row[9]);
                    item.put("Disk_Drive_Serial_Number", row[10]);
                    item.put("Hardware_Serial_Number", row[11]);
                    item.put("Memory_Part_Number", row[12]);
                    item.put("Domain", row[13]);
                    item.put("Warranty", row[14]);
                    item.put("SKU_Number", row[15]);
                    item.put("Asset_Insert_Date", row[16]);
                    item.put("IP_Address", row[17]);
                    item.put("Model", row[18]);
                    item.put("Manufacturer", row[19]);
                    item.put("Last_Update_Success", row[20]);
                    item.put("Host_Name", row[21]);
                }
            }
            result.add(item);
        }
        return result;
    }

    private String toCsvRow(Map<String, Object> item) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Object val : item.values()) {
            if (count > 0) sb.append(",");
            sb.append(csvSafe(val));
            count++;
        }
        return sb.toString();
    }

    private void fillExcelRow(Row row, Map<String, Object> item) {
        int cellIdx = 0;
        for (Object val : item.values()) {
            row.createCell(cellIdx++).setCellValue(safe(val));
        }
    }

    private String csvSafe(Object val) {
        if (val == null) return "";
        String s = val.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String safe(Object val) {
        return val != null ? val.toString() : "";
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private void appendCondition(StringBuilder sql, List<Object> params,
                                 String colExpr, String value, String op) {
        switch (op.toUpperCase()) {
            case "EQUALS":
                if (value.isEmpty()) return;
                sql.append(" AND ").append(colExpr).append(" = ?");
                params.add(value);
                break;
            case "CONTAINS":
                if (value.isEmpty()) return;
                sql.append(" AND ").append(colExpr).append(" LIKE ?");
                params.add("%" + value + "%");
                break;
            case "STARTS_WITH":
                if (value.isEmpty()) return;
                sql.append(" AND ").append(colExpr).append(" LIKE ?");
                params.add(value + "%");
                break;
            case "ENDS_WITH":
                if (value.isEmpty()) return;
                sql.append(" AND ").append(colExpr).append(" LIKE ?");
                params.add("%" + value);
                break;
            case "IS_EMPTY":
                sql.append(" AND (").append(colExpr).append(" IS NULL OR ").append(colExpr).append(" = '')");
                break;
            case "IS_NOT_EMPTY":
                sql.append(" AND ").append(colExpr).append(" IS NOT NULL AND ").append(colExpr).append(" != ''");
                break;
            case "IS_ANY_OF":
                if (value.isEmpty()) return;
                String[] values = value.split(",");
                sql.append(" AND ").append(colExpr).append(" IN (");
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) sql.append(", ");
                    sql.append("?");
                    params.add(values[i].trim());
                }
                sql.append(")");
                break;
            default:
                logger.warn("Unknown operator: {}", op);
        }
    }
}
