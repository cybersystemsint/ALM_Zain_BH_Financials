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
 * Fetch / search / export service for Approval Workflow reports.
 * Supports pagination, filtering, CSV/XLSX export with dateFrom and dateTo parameters.
 */
@Service
public class ApprovalReportFetchService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalReportFetchService.class);

    private static final int EXPORT_BATCH_SIZE = 100_000;

    /**
     * Export headers for Approval Report
     */
    private static final String[] APPROVAL_HEADERS = {
            "ID", "ObjectType", "AssetID", "OriginalStatus", "UpdatedStatus",
            "ProcessID", "Comments", "InsertedBy", "ChangedBy", "InsertDate", "ChangeDate"
    };

    /**
     * SELECT clause for Approval Report
     */
    private static final String APPROVAL_SELECT =
            "SELECT aw.ID, aw.ObjectType, aw.ASSET_ID, aw.ORIGINAL_STATUS, aw.UPDATED_STATUS, " +
                    "aw.PROCESS_ID, aw.COMMENTS, aw.INSERTEDBY, aw.CHANGEDBY, aw.INSERTDATE, aw.CHANGEDATE " +
                    "FROM tb_ApprovalWorkflow aw WHERE 1=1 ";

    @PersistenceContext
    private EntityManager entityManager;

    // ==================================================================
    // PUBLIC API
    // ==================================================================

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> searchApprovals(ApprovalRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : 100;

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> data = fetchPageApprovals(request, page * size, size);

        long total = countMatchingApprovals(request);
        long ms = System.currentTimeMillis() - t0;

        logger.info("Approval fetch page={} size={} returned={} total={} took={}ms",
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
    public void streamApprovalsToCsv(ApprovalRequest request, PrintWriter writer) {
        logger.info("Approval CSV export started. exportAll={}", request.isExportAll());
        writer.println(String.join(",", APPROVAL_HEADERS));
        writer.flush();

        Long lastId = null;
        int exported = 0;
        long t0 = System.currentTimeMillis();

        while (true) {
            int batch = EXPORT_BATCH_SIZE;
            List<Map<String, Object>> rows = fetchBatchAfterApprovals(request, lastId, batch);
            if (rows.isEmpty()) break;

            for (Map<String, Object> row : rows) {
                writer.println(toCsvRow(row));
                exported++;
            }
            writer.flush();

            logger.info("Approval CSV batch flushed: rows={} lastId={} totalSoFar={}",
                    rows.size(), lastId, exported);

            if (rows.size() < batch) break;
            lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
        }

        logger.info("Approval CSV export completed: rows={} duration={}ms",
                exported, System.currentTimeMillis() - t0);
    }

    @Transactional(readOnly = true)
    public void streamApprovalsToExcel(ApprovalRequest request, OutputStream out) throws Exception {
        logger.info("Approval Excel export started. exportAll={}", request.isExportAll());
        SXSSFWorkbook workbook = new SXSSFWorkbook(500);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("Approvals");
            Row header = sheet.createRow(0);
            for (int i = 0; i < APPROVAL_HEADERS.length; i++) {
                header.createCell(i).setCellValue(APPROVAL_HEADERS[i]);
            }

            int rowIdx = 1;
            Long lastId = null;
            int exported = 0;
            long t0 = System.currentTimeMillis();

            while (true) {
                int batch = EXPORT_BATCH_SIZE;
                List<Map<String, Object>> rows = fetchBatchAfterApprovals(request, lastId, batch);
                if (rows.isEmpty()) break;

                for (Map<String, Object> row : rows) {
                    Row excelRow = sheet.createRow(rowIdx++);
                    fillExcelRow(excelRow, row);
                    exported++;
                }

                logger.info("Approval Excel batch added: rows={} lastId={} totalSoFar={}",
                        rows.size(), lastId, exported);

                if (rows.size() < batch) break;
                lastId = ((Number) rows.get(rows.size() - 1).get("ID")).longValue();
            }

            workbook.write(out);
            out.flush();

            logger.info("Approval Excel export completed: rows={} duration={}ms",
                    exported, System.currentTimeMillis() - t0);
        } finally {
            workbook.dispose();
            try { workbook.close(); } catch (Exception ignored) {}
        }
    }

    // ==================================================================
    // PRIVATE HELPERS
    // ==================================================================

    private List<Map<String, Object>> fetchPageApprovals(ApprovalRequest request, int offset, int limit) {
        StringBuilder sql = new StringBuilder(APPROVAL_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersApprovals(sql, params, request);
        sql.append(" ORDER BY aw.ID DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return runSelect(sql.toString(), params);
    }

    private List<Map<String, Object>> fetchBatchAfterApprovals(ApprovalRequest request, Long lastId, int limit) {
        StringBuilder sql = new StringBuilder(APPROVAL_SELECT);
        List<Object> params = new ArrayList<>();
        applyFiltersApprovals(sql, params, request);

        if (lastId != null && lastId > 0) {
            sql.append(" AND aw.ID > ?");
            params.add(lastId);
        }

        sql.append(" ORDER BY aw.ID ASC LIMIT ?");
        params.add(limit);
        return runSelect(sql.toString(), params);
    }

    private long countMatchingApprovals(ApprovalRequest request) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tb_ApprovalWorkflow aw WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        applyFiltersApprovals(sql, params, request);
        Query q = entityManager.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    private void applyFiltersApprovals(StringBuilder sql, List<Object> params, ApprovalRequest request) {
        if (request == null) return;

        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            for (ApprovalRequest.Filter f : request.getFilters()) {
                if (f == null || f.getColumn() == null) continue;

                String column = f.getColumn().trim();
                String value = f.getValue() != null ? f.getValue().trim() : "";
                String op = f.getOperator() != null ? f.getOperator().name() : "CONTAINS";

                String colExpr = resolveApprovalColumn(column);
                if (colExpr == null) {
                    logger.warn("Ignoring filter on unknown Approval column: {}", column);
                    continue;
                }
                appendCondition(sql, params, colExpr, value, op);
            }
        }

        // Team filter - map team names to UPDATED_STATUS values
        if (notBlank(request.getTeam())) {
            String expectedStatus = switch (request.getTeam()) {
                case "Financial L1" -> "Pending L1 Approval";
                case "Financial L2" -> "Pending L2 Approval";
                case "Financial L3" -> "Pending L3 Approval";
                default -> null;
            };
            if (expectedStatus != null) {
                sql.append(" AND LOWER(aw.UPDATED_STATUS) = ?");
                params.add(expectedStatus.toLowerCase());
            }
        }

        // Date filters for insertDate (dateFrom and dateTo)
        if (notBlank(request.getDateFrom())) {
            sql.append(" AND aw.INSERTDATE >= ?");
            params.add(request.getDateFrom() + " 00:00:00");
        }
        if (notBlank(request.getDateTo())) {
            sql.append(" AND aw.INSERTDATE <= ?");
            params.add(request.getDateTo() + " 23:59:59");
        }
    }

    private String resolveApprovalColumn(String column) {
        switch (column.toLowerCase()) {
            case "id": return "aw.ID";
            case "objecttype": return "aw.ObjectType";
            case "assetid": return "aw.ASSET_ID";
            case "originalstatus": return "aw.ORIGINAL_STATUS";
            case "updatedstatus": return "aw.UPDATED_STATUS";
            case "processid": return "aw.PROCESS_ID";
            case "comments": return "aw.COMMENTS";
            case "insertedby": return "aw.INSERTEDBY";
            case "changedby": return "aw.CHANGEDBY";
            case "team": return "aw.UPDATED_STATUS";
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
            // Map columns based on APPROVAL_SELECT order
            if (row.length >= 11) {
                item.put("ID", row[0]);
                item.put("ObjectType", row[1]);
                item.put("AssetID", row[2]);
                item.put("OriginalStatus", row[3]);
                item.put("UpdatedStatus", row[4]);
                item.put("ProcessID", row[5]);
                item.put("Comments", row[6]);
                item.put("InsertedBy", row[7]);
                item.put("ChangedBy", row[8]);
                item.put("InsertDate", row[9]);
                item.put("ChangeDate", row[10]);
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
