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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetch / search / export service for WriteOff Report.
 */
@Service
public class WriteOffReportFetchService {

    private static final Logger logger = LoggerFactory.getLogger(WriteOffReportFetchService.class);

    private static final int EXPORT_BATCH_SIZE = 100_000;

    /**
     * Export headers for WriteOff Report
     */
    private static final String[] WRITEOFF_HEADERS = {
            "ID", "SerialNumber", "RFID", "TAG", "AssetType",
            "AssetID", "NEType", "WriteOffDate", "StatusFlag",
            "InsertedBy", "InsertDate"
    };

    /**
     * SELECT clause for WriteOff Report
     */
    private static final String WRITEOFF_SELECT =
            "SELECT w.id, w.SerialNumber, w.RFID, w.TAG, w.AssetType, " +
                    "w.AssetID, w.NEType, w.WriteOffDate, w.StatusFlag, " +
                    "w.InsertedBy, w.InsertDate " +
                    "FROM tb_WriteOffReport w WHERE 1=1 ";

    @PersistenceContext
    private EntityManager entityManager;

    // ==================================================================
    // PUBLIC API
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> searchWriteOff(ApprovalRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 100;

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> data = fetchPageWriteOff(request, page * size, size);

        long total = countMatchingWriteOff(request);
        long ms = System.currentTimeMillis() - t0;

        logger.info("WriteOff fetch page={} size={} returned={} total={} took={}ms",
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
    public void streamWriteOffToCsv(ApprovalRequest request, PrintWriter writer) {
        logger.info("WriteOff CSV export started. exportAll={}", request.isExportAll());
        writer.println(String.join(",", WRITEOFF_HEADERS));
        writer.flush();

        Long lastId = null;
        int exported = 0;
        long t0 = System.currentTimeMillis();

        while (true) {
            int batch = EXPORT_BATCH_SIZE;
            List<Map<String, Object>> rows = fetchBatchAfterWriteOff(request, lastId, batch);
            if (rows.isEmpty()) break;

            for (Map<String, Object> row : rows) {
                writer.println(toCsvRow(row));
                exported++;
            }
            writer.flush();

            logger.info("WriteOff CSV batch flushed: rows={} lastId={} totalSoFar={}",
                    rows.size(), lastId, exported);

            if (rows.size() < batch) break;
            lastId = ((Number) rows.get(rows.size() - 1).get("id")).longValue();
        }

        logger.info("WriteOff CSV export completed: rows={} duration={}ms",
                exported, System.currentTimeMillis() - t0);
    }

    @Transactional(readOnly = true)
    public void streamWriteOffToExcel(ApprovalRequest request, OutputStream out) throws Exception {
        logger.info("WriteOff Excel export started. exportAll={}", request.isExportAll());
        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("WriteOff");
            Row header = sheet.createRow(0);
            for (int i = 0; i < WRITEOFF_HEADERS.length; i++) {
                header.createCell(i).setCellValue(WRITEOFF_HEADERS[i]);
            }

            int rowIdx = 1;
            Long lastId = null;
            int exported = 0;
            long t0 = System.currentTimeMillis();

            while (true) {
                int batch = EXPORT_BATCH_SIZE;
                List<Map<String, Object>> rows = fetchBatchAfterWriteOff(request, lastId, batch);
                if (rows.isEmpty()) break;

                for (Map<String, Object> row : rows) {
                    Row excelRow = sheet.createRow(rowIdx++);
                    fillExcelRow(excelRow, row);
                    exported++;
                }

                logger.info("WriteOff Excel batch added: rows={} lastId={} totalSoFar={}",
                        rows.size(), lastId, exported);

                if (rows.size() < batch) break;
                lastId = ((Number) rows.get(rows.size() - 1).get("id")).longValue();
            }

            workbook.write(out);
            out.flush();

            logger.info("WriteOff Excel export completed: rows={} duration={}ms",
                    exported, System.currentTimeMillis() - t0);
        } finally {
            workbook.dispose();
            try { workbook.close(); } catch (Exception ignored) {}
        }
    }

    // ==================================================================
    // PRIVATE HELPERS
    // ==================================================================

    private List<Map<String, Object>> fetchPageWriteOff(ApprovalRequest request, int offset, int limit) {
        StringBuilder sql = new StringBuilder(WRITEOFF_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersWriteOff(sql, params, request);
        sql.append(" ORDER BY w.id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return runSelect(sql.toString(), params);
    }

    private List<Map<String, Object>> fetchBatchAfterWriteOff(ApprovalRequest request, Long lastId, int limit) {
        StringBuilder sql = new StringBuilder(WRITEOFF_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersWriteOff(sql, params, request);

        if (lastId != null && lastId > 0) {
            sql.append(" AND w.id > ?");
            params.add(lastId);
        }

        sql.append(" ORDER BY w.id ASC LIMIT ?");
        params.add(limit);
        return runSelect(sql.toString(), params);
    }

    private long countMatchingWriteOff(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tb_WriteOffReport w WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        applyFiltersWriteOff(sql, params, request);
        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    private void applyFiltersWriteOff(StringBuilder sql, List<Object> params, ApprovalRequest request) {
        if (request == null) return;

        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            for (ApprovalRequest.Filter f : request.getFilters()) {
                if (f == null || f.getColumn() == null) continue;

                String column = f.getColumn().trim();
                String value = f.getValue() != null ? f.getValue().trim() : "";
                String op = f.getOperator() != null ? f.getOperator().name() : "CONTAINS";

                String colExpr = resolveWriteOffColumn(column);
                if (colExpr == null) {
                    logger.warn("Ignoring filter on unknown WriteOff column: {}", column);
                    continue;
                }
                appendCondition(sql, params, colExpr, value, op);
            }
        }

        // Date filter for WriteOffDate (dateTo only, dateFrom excluded)
        if (notBlank(request.getDateTo())) {
            sql.append(" AND w.WriteOffDate <= ?");
            params.add(request.getDateTo() + " 23:59:59");
        }
    }

    private String resolveWriteOffColumn(String column) {
        switch (column.toLowerCase()) {
            case "id": return "w.id";
            case "serialnumber": return "w.SerialNumber";
            case "rfid": return "w.RFID";
            case "tag": return "w.TAG";
            case "assettype": return "w.AssetType";
            case "assetid": return "w.AssetID";
            case "netype": return "w.NEType";
            case "statusflag": return "w.StatusFlag";
            case "insertedby": return "w.InsertedBy";
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
            // Map columns based on WRITEOFF_SELECT order
            if (row.length >= 11) {
                item.put("id", row[0]);
                item.put("SerialNumber", row[1]);
                item.put("RFID", row[2]);
                item.put("TAG", row[3]);
                item.put("AssetType", row[4]);
                item.put("AssetID", row[5]);
                item.put("NEType", row[6]);
                item.put("WriteOffDate", row[7]);
                item.put("StatusFlag", row[8]);
                item.put("InsertedBy", row[9]);
                item.put("InsertDate", row[10]);
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
