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
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetch / search / export service for the Financial Report grid.
 */
@Service
public class FinanceReportFetchService {

    private static final Logger logger = LoggerFactory.getLogger(FinanceReportFetchService.class);

    private static final int EXPORT_BATCH_SIZE = 100_000;
    // EXPORT_LIMIT removed - no more artificial cap on exports

    /**
     * Output-column order for the API rows AND the CSV/XLSX exports.
     */
    private static final String[] EXPORT_HEADERS = {
            "ID", "SiteID", "Zone", "NodeType", "AssetName", "AssetType", "AssetCategory",
            "Model", "PartNumber", "AssetSerialNumber", "TAG", "OracleAssetID",
            "InstallationDate", "InitialCost", "MonthlyDepreciationAmount",
            "AccumulatedDepreciation", "NetCost", "SalvageValue", "PONumber", "PODate",
            "FA_Category", "L1", "L2", "L3", "L4",
            "AccumulatedDepreciationCode", "DepreciationCode", "UsefulLifeMonths",
            "VendorName", "VendorNumber", "ProjectNumber", "DateOfService",
            "OldFA_Category", "CostCenter", "Adjustment", "TaskID", "POLineNumber",
            "StatusFlag", "FinancialApprovalStatus", "RetirementDate", "WriteOffDate",
            "ItemBarCode", "RFID", "InvoiceNumber", "Description",
            "InsertDate", "InsertedBy", "ChangeDate", "ChangedBy"
    };

    /**
     * SELECT clause matching EXPORT_HEADERS 1:1.
     */
    private static final String SELECT_CLAUSE =
            "SELECT fr.Id, " +
                    "       fr.SiteID, fr.Zone, fr.NodeType, fr.AssetName, fr.AssetType, fr.AssetCategory, " +
                    "       fr.Model, fr.PartNumber, fr.AssetSerialNumber, fr.TAG, fr.OracleAssetID, " +
                    "       fr.InstallationDate, fr.InitialCost, fr.MonthlyDepreciationAmount, " +
                    "       fr.AccumulatedDepreciation, fr.NetCost, fr.SalvageValue, fr.PONumber, fr.PODate, " +
                    "       fr.`FA_CATEGORY(NEW)`, fr.`L1(NEW)`, fr.`L2(NEW)`, fr.`L3(NEW)`, fr.`L4(NEW)`, " +
                    "       fr.AccumulatedDepreciationCode, fr.DepreciationCode, fr.UsefulLife_Months, " +
                    "       fr.VENDOR_NAME, fr.VENDOR_NUMBER, fr.PROJECT_NUMBER, fr.DateOfService, " +
                    "       fr.OLDFARcategory, fr.CostCenterData, fr.Adjustment, fr.TaskId, fr.PoLineNumber, " +
                    "       fr.StatusFlag, fr.FinancialApprovalStatus, fr.RetirementDate, fr.WriteOffDate, " +
                    "       fr.ItemBarCode, fr.RFID, fr.InvoiceNumber, fr.Description, " +
                    "       fr.InsertDate, fr.InsertedBy, fr.ChangeDate, fr.ChangedBy " +
                    "FROM tb_FinancialReport fr WHERE 1=1 ";

    @PersistenceContext
    private EntityManager entityManager;

    // ==================================================================
    // PUBLIC API
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> multiFilterSearch(ApprovalRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 100;

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> data = fetchPage(request, page * size, size);

        // When the caller supplied a dateTo (snapshot date), recompute
        // each row's AccumulatedDepreciation and NetCost as of that
        // date — same formula /monthly-report uses, but on a row-by-row
        // basis. This gives an "as-of" view of the FR instead of the
        // current stored values. dateFrom alone does not trigger
        // recomputation: the stored values are already point-in-time.
        java.time.LocalDate asOf = resolveAsOfDate(request);
        if (asOf != null) {
            applyAsOfDateMath(data, asOf);
        }

        long total = countMatching(request);
        long ms = System.currentTimeMillis() - t0;

        logger.info("FR fetch page={} size={} returned={} total={} took={}ms asOf={}",
                page, size, data.size(), total, ms, asOf);

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setTotalElements(total);
        result.setTotalPages((int) Math.ceil((double) total / size));
        result.setPage(page);
        result.setSize(size);
        result.setdata(data);
        return result;
    }

    /** Stream the matching rows out as CSV - NO EXPORT LIMIT */
    @Transactional(readOnly = true)
    public void streamExportToCsv(ApprovalRequest request, PrintWriter writer) {
        // Snapshot date for as-of-date math; null = export stored values as-is.
        java.time.LocalDate asOf = resolveAsOfDate(request);
        logger.info("FR CSV export started. exportAll={} asOf={}", request.isExportAll(), asOf);
        writer.println(String.join(",", EXPORT_HEADERS));
        writer.flush();

        Long lastId = null;
        int exported = 0;
        long t0 = System.currentTimeMillis();

        while (true) {
            int batch = EXPORT_BATCH_SIZE;   // No limit applied

            List<Map<String, Object>> rows = fetchBatchAfter(request, lastId, batch);
            if (rows.isEmpty()) break;

            // Per-batch as-of-date recomputation so the exported file
            // mirrors what the JSON fetch returns when dateTo is set.
            if (asOf != null) applyAsOfDateMath(rows, asOf);

            for (Map<String, Object> row : rows) {
                writer.println(toCsvRow(row));
                exported++;
            }
            writer.flush();

            logger.info("FR CSV batch flushed: rows={} lastId={} totalSoFar={}",
                    rows.size(), lastId, exported);

            if (rows.size() < batch) break;
            lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
        }

        logger.info("FR CSV export completed: rows={} duration={}ms",
                exported, System.currentTimeMillis() - t0);
    }

    /** Stream the matching rows out as XLSX - NO EXPORT LIMIT */
    @Transactional(readOnly = true)
    public void streamExportToExcel(ApprovalRequest request, OutputStream out) throws Exception {
        // Snapshot date for as-of-date math; null = export stored values as-is.
        java.time.LocalDate asOf = resolveAsOfDate(request);
        logger.info("FR Excel export started. exportAll={} asOf={}", request.isExportAll(), asOf);
        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("FinancialReports");
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                header.createCell(i).setCellValue(EXPORT_HEADERS[i]);
            }

            int rowIdx = 1;
            Long lastId = null;
            int exported = 0;
            long t0 = System.currentTimeMillis();

            while (true) {
                int batch = EXPORT_BATCH_SIZE;   // No limit applied

                List<Map<String, Object>> rows = fetchBatchAfter(request, lastId, batch);
                if (rows.isEmpty()) break;

                // Per-batch as-of-date recomputation so the exported file
                // mirrors what the JSON fetch returns when dateTo is set.
                if (asOf != null) applyAsOfDateMath(rows, asOf);

                for (Map<String, Object> row : rows) {
                    Row excelRow = sheet.createRow(rowIdx++);
                    fillExcelRow(excelRow, row);
                    exported++;
                }

                logger.info("FR Excel batch added: rows={} lastId={} totalSoFar={}",
                        rows.size(), lastId, exported);

                if (rows.size() < batch) break;
                lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
            }

            workbook.write(out);
            out.flush();

            logger.info("FR Excel export completed: rows={} duration={}ms",
                    exported, System.currentTimeMillis() - t0);
        } finally {
            workbook.dispose();
            try { workbook.close(); } catch (Exception ignored) {}
        }
    }

    // ==================================================================
    // PRIVATE HELPERS (unchanged except removal of limit logic)
    // ==================================================================

    private List<Map<String, Object>> fetchPage(ApprovalRequest request, int offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_CLAUSE);
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, request);
        sql.append(" ORDER BY fr.Id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return runSelect(sql.toString(), params);
    }

    private List<Map<String, Object>> fetchBatchAfter(ApprovalRequest request, Long lastId, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_CLAUSE);
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, request);

        if (lastId != null && lastId > 0) {
            sql.append(" AND fr.Id > ?");
            params.add(lastId);
        }

        sql.append(" ORDER BY fr.Id ASC LIMIT ?");
        params.add(limit);
        return runSelect(sql.toString(), params);
    }

    private long countMatching(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tb_FinancialReport fr WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, request);
        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> runSelect(String sql, List<Object> params) {
        Query q = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }

        List<Object[]> rows = q.getResultList();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            fillRow(item, row);

            // Format Date columns
            formatDate(item, "RetirementDate", row[39], sdf);
            formatDate(item, "WriteOffDate",   row[40], sdf);
            formatDate(item, "InsertDate",     row[45], sdf);
            formatDate(item, "ChangeDate",     row[47], sdf);

            out.add(item);
        }
        return out;
    }

    private void fillRow(Map<String, Object> item, Object[] row) {
        item.put("ID",                        row[0]);
        item.put("SiteID",                    row[1]);
        item.put("Zone",                      row[2]);
        item.put("NodeType",                  row[3]);
        item.put("AssetName",                 row[4]);
        item.put("AssetType",                 row[5]);
        item.put("AssetCategory",             row[6]);
        item.put("Model",                     row[7]);
        item.put("PartNumber",                row[8]);
        item.put("AssetSerialNumber",         row[9]);
        item.put("TAG",                       row[10]);
        item.put("OracleAssetID",             row[11]);
        item.put("InstallationDate",          row[12]);
        item.put("InitialCost",               row[13]);
        item.put("MonthlyDepreciationAmount", row[14]);
        item.put("AccumulatedDepreciation",   row[15]);
        item.put("NetCost",                   row[16]);
        item.put("SalvageValue",              row[17]);
        item.put("PONumber",                  row[18]);
        item.put("PODate",                    row[19]);
        item.put("FA_Category",               row[20]);
        item.put("L1",                        row[21]);
        item.put("L2",                        row[22]);
        item.put("L3",                        row[23]);
        item.put("L4",                        row[24]);
        item.put("AccumulatedDepreciationCode", row[25]);
        item.put("DepreciationCode",          row[26]);
        item.put("UsefulLifeMonths",          row[27]);
        item.put("VendorName",                row[28]);
        item.put("VendorNumber",                row[29]);
        item.put("ProjectNumber",             row[30]);
        item.put("DateOfService",             row[31]);
        item.put("OldFA_Category",            row[32]);
        item.put("CostCenter",                row[33]);
        item.put("Adjustment",                row[34]);
        item.put("TaskID",                    row[35]);
        item.put("POLineNumber",              row[36]);
        item.put("StatusFlag",                row[37]);
        item.put("FinancialApprovalStatus",   row[38]);
        item.put("RetirementDate",            row[39]);
        item.put("WriteOffDate",              row[40]);
        item.put("ItemBarCode",               row[41]);
        item.put("RFID",                      row[42]);
        item.put("InvoiceNumber",             row[43]);
        item.put("Description",               row[44]);
        item.put("InsertDate",                row[45]);
        item.put("InsertedBy",                row[46]);
        item.put("ChangeDate",                row[47]);
        item.put("ChangedBy",                 row[48]);
    }

    private void formatDate(Map<String, Object> item, String key, Object raw, SimpleDateFormat sdf) {
        if (raw == null) {
            item.put(key, "");
            return;
        }
        if (raw instanceof Date) {
            item.put(key, sdf.format((Date) raw));
        } else {
            item.put(key, raw.toString());
        }
    }

    private void applyFilters(StringBuilder sql, List<Object> params, ApprovalRequest request) {
        if (request == null) return;

        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            for (ApprovalRequest.Filter f : request.getFilters()) {
                if (f == null || f.getColumn() == null) continue;

                String column = f.getColumn().trim();
                String value  = f.getValue() != null ? f.getValue().trim() : "";
                String op     = f.getOperator() != null ? f.getOperator().name() : "CONTAINS";

                String colExpr = resolveColumn(column);
                if (colExpr == null) {
                    logger.warn("Ignoring filter on unknown FR column: {}", column);
                    continue;
                }
                appendCondition(sql, params, colExpr, value, op);
            }
        }

        // ------------------------------------------------------------------
        // Date range filters for FINANCIAL REPORT fetch endpoint
        // ------------------------------------------------------------------
        // dateFrom/dateTo → fr.DateOfService (the FR's "as-of" accounting date)
        //
        // This mirrors the filter target used by /monthly-report, so callers
        // can replicate that endpoint's window through this fetch endpoint
        // and get exports too.
        //
        // DateOfService is stored as a yyyy-MM-dd string in the schema;
        // string comparison sorts identically to date comparison for that
        // format, so plain '>=' / '<=' is correct without CAST.
        //
        // NOTE: startDate/endDate are Finance Approval Specific fields and
        // should NOT be used here. This endpoint only uses dateFrom/dateTo.
        // ------------------------------------------------------------------
        if (notBlank(request.getDateFrom())) {
            sql.append(" AND fr.DateOfService >= ?");
            params.add(normaliseDate(request.getDateFrom()));
        }
        if (notBlank(request.getDateTo())) {
            sql.append(" AND fr.DateOfService <= ?");
            params.add(normaliseDate(request.getDateTo()));
        }
    }

    /**
     * Accept a couple of common date input formats and emit the canonical
     * {@code yyyy-MM-dd} that DateOfService is stored as. Anything we
     * don't recognise is passed through untouched, so callers using ISO
     * already (the common case) take the fast path.
     */
    private String normaliseDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return s;
        // Already ISO?
        if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return s.length() > 10 ? s.substring(0, 10) : s;
        }
        // Try "d MMM yyyy" (the format /monthly-report accepts).
        try {
            java.text.SimpleDateFormat in  = new java.text.SimpleDateFormat("d MMM yyyy");
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("yyyy-MM-dd");
            return out.format(in.parse(s));
        } catch (java.text.ParseException ignored) { /* fall through */ }
        // Try "dd/MM/yyyy" — common European data entry.
        try {
            java.text.SimpleDateFormat in  = new java.text.SimpleDateFormat("dd/MM/yyyy");
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat("yyyy-MM-dd");
            return out.format(in.parse(s));
        } catch (java.text.ParseException ignored) { /* fall through */ }
        // Unknown — let MySQL handle it (or let the predicate match nothing).
        return s;
    }

    private void appendCondition(StringBuilder sql, List<Object> params,
                                 String colExpr, String value, String op) {
        switch (op.toUpperCase()) {
            case "EQUALS":
                if (value.isEmpty()) return;
                sql.append(" AND LOWER(").append(colExpr).append(") = LOWER(?)");
                params.add(value);
                break;
            case "STARTS_WITH":
                if (value.isEmpty()) return;
                sql.append(" AND LOWER(").append(colExpr).append(") LIKE LOWER(?)");
                params.add(value + "%");
                break;
            case "ENDS_WITH":
                if (value.isEmpty()) return;
                sql.append(" AND LOWER(").append(colExpr).append(") LIKE LOWER(?)");
                params.add("%" + value);
                break;
            case "IS_EMPTY":
                sql.append(" AND (").append(colExpr).append(" IS NULL OR TRIM(")
                        .append(colExpr).append(") = '')");
                break;
            case "IS_NOT_EMPTY":
                sql.append(" AND ").append(colExpr).append(" IS NOT NULL AND TRIM(")
                        .append(colExpr).append(") <> ''");
                break;
            case "IS_ANY_OF":
                if (value.isEmpty()) return;
                String[] parts = value.split(",");
                sql.append(" AND LOWER(").append(colExpr).append(") IN (");
                for (int i = 0; i < parts.length; i++) {
                    sql.append(i == 0 ? "LOWER(?)" : ", LOWER(?)");
                    params.add(parts[i].trim());
                }
                sql.append(")");
                break;
            case "CONTAINS":
            default:
                if (value.isEmpty()) return;
                sql.append(" AND LOWER(").append(colExpr).append(") LIKE LOWER(?)");
                params.add("%" + value + "%");
        }
    }

    private String resolveColumn(String column) {
        String key = column.trim().toLowerCase();
        switch (key) {
            case "id":                          return "fr.Id";
            case "siteid":                      return "fr.SiteID";
            case "zone":                        return "fr.Zone";
            case "nodetype":                    return "fr.NodeType";
            case "assetname":                   return "fr.AssetName";
            case "assettype":                   return "fr.AssetType";
            case "assetcategory":               return "fr.AssetCategory";
            case "model":                       return "fr.Model";
            case "partnumber":                  return "fr.PartNumber";
            case "assetserialnumber":
            case "serialnumber":                return "fr.AssetSerialNumber";
            case "tag":                         return "fr.TAG";
            case "oracleassetid":               return "fr.OracleAssetID";
            case "installationdate":            return "fr.InstallationDate";
            case "initialcost":                 return "fr.InitialCost";
            case "monthlydepreciationamount":   return "fr.MonthlyDepreciationAmount";
            case "accumulateddepreciation":     return "fr.AccumulatedDepreciation";
            case "netcost":                     return "fr.NetCost";
            case "salvagevalue":                return "fr.SalvageValue";
            case "ponumber":                    return "fr.PONumber";
            case "podate":                      return "fr.PODate";
            case "facategory":
            case "fa_category":                 return "fr.`FA_CATEGORY(NEW)`";
            case "l1":                          return "fr.`L1(NEW)`";
            case "l2":                          return "fr.`L2(NEW)`";
            case "l3":                          return "fr.`L3(NEW)`";
            case "l4":                          return "fr.`L4(NEW)`";
            case "accumulateddepreciationcode": return "fr.AccumulatedDepreciationCode";
            case "depreciationcode":            return "fr.DepreciationCode";
            case "usefullifemonths":
            case "usefullife":                  return "fr.UsefulLife_Months";
            case "vendorname":                  return "fr.VENDOR_NAME";
            case "vendornumber":                return "fr.VENDOR_NUMBER";
            case "projectnumber":               return "fr.PROJECT_NUMBER";
            case "description":                 return "fr.Description";
            case "dateofservice":               return "fr.DateOfService";
            case "insertdate":                  return "fr.InsertDate";
            case "insertedby":                  return "fr.InsertedBy";
            case "changedate":                  return "fr.ChangeDate";
            case "changedby":                   return "fr.ChangedBy";
            case "statusflag":                  return "fr.StatusFlag";
            case "retirementdate":              return "fr.RetirementDate";
            case "oldfarcategory":
            case "oldfa_category":              return "fr.OLDFARcategory";
            case "costcenter":
            case "costcenterdata":              return "fr.CostCenterData";
            case "financialapprovalstatus":     return "fr.FinancialApprovalStatus";
            case "adjustment":                  return "fr.Adjustment";
            case "writeoffdate":                return "fr.WriteOffDate";
            case "taskid":                      return "fr.TaskId";
            case "polinenumber":                return "fr.PoLineNumber";
            case "itembarcode":                 return "fr.ItemBarCode";
            case "rfid":                        return "fr.RFID";
            case "invoicenumber":               return "fr.InvoiceNumber";
            // Add more mappings here if needed in future
            default:                            return null;
        }
    }

    private String toCsvRow(Map<String, Object> item) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < EXPORT_HEADERS.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvSafe(item.get(EXPORT_HEADERS[i])));
        }
        return sb.toString();
    }

    private void fillExcelRow(Row row, Map<String, Object> item) {
        for (int i = 0; i < EXPORT_HEADERS.length; i++) {
            row.createCell(i).setCellValue(safe(item.get(EXPORT_HEADERS[i])));
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
        return val == null ? "" : val.toString();
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }


    /**
     * Returns grand totals (totalCost, totalDepreciation, totalNBV)
     * for the ENTIRE filtered dataset.
     *
     * <p>Two paths:</p>
     * <ul>
     *   <li><b>Fast path</b> (no dateTo) — single native SUM on stored
     *       columns. O(1) round-trip.</li>
     *   <li><b>As-of-date path</b> (dateTo supplied) — stream all matching
     *       rows in {@code EXPORT_BATCH_SIZE}-row cursor batches, recompute
     *       each row's depreciation against the snapshot date, accumulate.
     *       This keeps the totals consistent with what each row reports in
     *       the JSON / export, at the cost of one extra full scan.</li>
     * </ul>
     */
    public Map<String, BigDecimal> getAggregateTotals(ApprovalRequest request) {
        java.time.LocalDate asOf = resolveAsOfDate(request);
        if (asOf == null) {
            return aggregateTotalsFastPath(request);
        }
        return aggregateTotalsAsOfDate(request, asOf);
    }

    /** Single native-SQL SUM. Used when no snapshot date is supplied. */
    private Map<String, BigDecimal> aggregateTotalsFastPath(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder(
                "SELECT " +
                        "COALESCE(SUM(fr.InitialCost), 0) as totalCost, " +
                        "COALESCE(SUM(fr.AccumulatedDepreciation), 0) as totalDepreciation " +
                        "FROM tb_FinancialReport fr WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, request);

        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }

        Object[] result = (Object[]) q.getSingleResult();

        BigDecimal totalCost = convertToBigDecimal(result[0]);
        BigDecimal totalDepreciation = convertToBigDecimal(result[1]);
        BigDecimal totalNBV = totalCost.subtract(totalDepreciation)
                .setScale(3, RoundingMode.HALF_UP);

        Map<String, BigDecimal> totals = new HashMap<>();
        totals.put("totalCost", totalCost);
        totals.put("totalDepreciation", totalDepreciation);
        totals.put("totalNBV", totalNBV);
        return totals;
    }

    /**
     * Cursor-streaming aggregator that recomputes each row's
     * AccumulatedDepreciation as of the snapshot date, then sums.
     *
     * <p>The stream uses the same {@link #fetchBatchAfter} cursor (id ASC)
     * the exports use, so memory stays bounded at one batch
     * ({@code EXPORT_BATCH_SIZE} rows) regardless of dataset size.</p>
     */
    private Map<String, BigDecimal> aggregateTotalsAsOfDate(ApprovalRequest request, java.time.LocalDate asOf) {
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalDep  = BigDecimal.ZERO;
        Long lastId = null;
        long t0 = System.currentTimeMillis();
        int scanned = 0;

        while (true) {
            List<Map<String, Object>> rows = fetchBatchAfter(request, lastId, EXPORT_BATCH_SIZE);
            if (rows.isEmpty()) break;
            applyAsOfDateMath(rows, asOf);
            for (Map<String, Object> r : rows) {
                totalCost = totalCost.add(convertToBigDecimal(r.get("InitialCost")));
                totalDep  = totalDep.add(convertToBigDecimal(r.get("AccumulatedDepreciation")));
            }
            scanned += rows.size();
            if (rows.size() < EXPORT_BATCH_SIZE) break;
            lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
        }
        BigDecimal totalNBV = totalCost.subtract(totalDep).setScale(3, RoundingMode.HALF_UP);
        logger.info("FR as-of-date grand totals: scanned={} asOf={} took={}ms",
                scanned, asOf, System.currentTimeMillis() - t0);

        Map<String, BigDecimal> totals = new HashMap<>();
        totals.put("totalCost", totalCost.setScale(3, RoundingMode.HALF_UP));
        totals.put("totalDepreciation", totalDep.setScale(3, RoundingMode.HALF_UP));
        totals.put("totalNBV", totalNBV);
        return totals;
    }

    // ==================================================================
    // AS-OF-DATE DEPRECIATION MATH
    //
    // Mirrors the per-asset formula used by /monthly-report:
    //
    //   d              = first-of-month AFTER DateOfService
    //   monthsActive   = months between d and asOf, capped at UsefulLifeMonths
    //   monthlyDep     = MonthlyDepreciationAmount   (when stored)
    //                  | (InitialCost − SalvageValue) / UsefulLifeMonths
    //   accDep         = monthlyDep × monthsActive
    //                  ≤ (InitialCost − SalvageValue)
    //   netCost        = max(InitialCost − accDep, SalvageValue)
    //
    // We mutate the row map in place — the controller / exporter is not
    // aware that the values were recomputed; from their perspective these
    // are just the row's depreciation values.
    // ==================================================================

    /** Returns the snapshot date if dateTo is supplied, else null. */
    private java.time.LocalDate resolveAsOfDate(ApprovalRequest request) {
        if (request == null || !notBlank(request.getDateTo())) return null;
        try {
            return java.time.LocalDate.parse(normaliseDate(request.getDateTo()));
        } catch (Exception e) {
            logger.warn("Unparseable dateTo='{}' — as-of-date math skipped.", request.getDateTo());
            return null;
        }
    }

    private void applyAsOfDateMath(List<Map<String, Object>> rows, java.time.LocalDate asOf) {
        if (rows == null || rows.isEmpty() || asOf == null) return;
        for (Map<String, Object> row : rows) {
            recomputeAsOfRow(row, asOf);
        }
    }

    private void recomputeAsOfRow(Map<String, Object> row, java.time.LocalDate asOf) {
        String dosRaw = asString(row.get("DateOfService"));
        if (dosRaw == null || dosRaw.isEmpty()) return;
        java.time.LocalDate serviceDate;
        try {
            serviceDate = java.time.LocalDate.parse(
                    dosRaw.length() > 10 ? dosRaw.substring(0, 10) : dosRaw);
        } catch (Exception e) {
            return; // unparseable → leave row untouched
        }

        BigDecimal initialCost  = convertToBigDecimal(row.get("InitialCost"));
        BigDecimal salvageValue = convertToBigDecimal(row.get("SalvageValue"));
        BigDecimal monthlyDep   = convertToBigDecimal(row.get("MonthlyDepreciationAmount"));
        Integer    usefulMonths = toInteger(row.get("UsefulLifeMonths"));

        if (initialCost.signum() == 0 || usefulMonths == null || usefulMonths <= 0) {
            return; // not enough info to recompute
        }

        // If asset isn't yet in service as of `asOf`, depreciation is zero
        // and netCost equals initialCost.
        if (serviceDate.isAfter(asOf)) {
            row.put("AccumulatedDepreciation", BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
            row.put("NetCost", initialCost.setScale(3, RoundingMode.HALF_UP));
            return;
        }

        // d = 1st of the month AFTER the service date.
        java.time.LocalDate d = serviceDate.withDayOfMonth(1).plusMonths(1);
        long monthsActive = java.time.temporal.ChronoUnit.MONTHS.between(
                java.time.YearMonth.from(d), java.time.YearMonth.from(asOf));
        if (monthsActive < 0) monthsActive = 0;
        if (monthsActive > usefulMonths) monthsActive = usefulMonths;

        // If no MonthlyDepreciationAmount is stored, derive it.
        if (monthlyDep == null || monthlyDep.signum() == 0) {
            BigDecimal depreciable = initialCost.subtract(salvageValue == null ? BigDecimal.ZERO : salvageValue);
            monthlyDep = depreciable.divide(BigDecimal.valueOf(usefulMonths), 3, RoundingMode.HALF_UP);
        }

        BigDecimal accDep = monthlyDep.multiply(BigDecimal.valueOf(monthsActive))
                                       .setScale(3, RoundingMode.HALF_UP);

        // Cap accumulated depreciation at (InitialCost − SalvageValue).
        BigDecimal maxAccDep = initialCost.subtract(salvageValue == null ? BigDecimal.ZERO : salvageValue)
                                          .setScale(3, RoundingMode.HALF_UP);
        if (accDep.compareTo(maxAccDep) > 0) accDep = maxAccDep;
        if (accDep.signum() < 0) accDep = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

        // Net cost can never drop below salvage value.
        BigDecimal netCost = initialCost.subtract(accDep).setScale(3, RoundingMode.HALF_UP);
        if (salvageValue != null && netCost.compareTo(salvageValue) < 0) netCost = salvageValue;

        row.put("AccumulatedDepreciation", accDep);
        row.put("NetCost", netCost);
    }

    private String asString(Object v) {
        if (v == null) return null;
        if (v instanceof java.util.Date) {
            return new SimpleDateFormat("yyyy-MM-dd").format((java.util.Date) v);
        }
        return v.toString();
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); }
        catch (NumberFormatException e) { return null; }
    }

    /**
     * Calculates aggregates ONLY for the current page (filteredCost, filteredDepreciation, filteredNBV)
     */
    public Map<String, BigDecimal> calculatePageAggregates(List<Map<String, Object>> pageData) {
        BigDecimal filteredCost = BigDecimal.ZERO;
        BigDecimal filteredDep = BigDecimal.ZERO;

        for (Map<String, Object> row : pageData) {
            filteredCost = filteredCost.add(convertToBigDecimal(row.get("InitialCost")));
            filteredDep = filteredDep.add(convertToBigDecimal(row.get("AccumulatedDepreciation")));
        }

        BigDecimal filteredNBV = filteredCost.subtract(filteredDep)
                .setScale(3, RoundingMode.HALF_UP);

        Map<String, BigDecimal> agg = new HashMap<>();
        agg.put("filteredCost", filteredCost);
        agg.put("filteredDepreciation", filteredDep);
        agg.put("filteredNBV", filteredNBV);

        return agg;
    }

    /**
     * Safe conversion from any numeric value to BigDecimal (Java 15 compatible)
     */
    public BigDecimal convertToBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }

        // Java 15 compatible instanceof
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(3, RoundingMode.HALF_UP);
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue())
                    .setScale(3, RoundingMode.HALF_UP);
        }

        // Try String conversion as fallback
        try {
            String str = value.toString().trim();
            if (!str.isEmpty()) {
                return new BigDecimal(str).setScale(3, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            logger.warn("Failed to convert value '{}' to BigDecimal", value);
        }

        return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    }
}