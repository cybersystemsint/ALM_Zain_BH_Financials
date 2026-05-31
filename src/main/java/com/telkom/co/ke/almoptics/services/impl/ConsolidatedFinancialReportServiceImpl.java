package com.telkom.co.ke.almoptics.services.impl;

import com.telkom.co.ke.almoptics.dto.ConsolidatedReport;
import com.telkom.co.ke.almoptics.dto.ConsolidatedReportResponse;
import com.telkom.co.ke.almoptics.dto.FilterRequest;
import com.telkom.co.ke.almoptics.dto.PageResult;
import com.telkom.co.ke.almoptics.entities.ConsolidatedFinancialReport;
import com.telkom.co.ke.almoptics.repository.ConsolidatedFinancialReportRepository;
import com.telkom.co.ke.almoptics.services.ConsolidatedFinancialReportService;
import com.telkom.co.ke.almoptics.specification.ConsolidatedReportSpecification;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConsolidatedFinancialReportServiceImpl implements ConsolidatedFinancialReportService {

    private static final Logger log = LoggerFactory.getLogger(ConsolidatedFinancialReportServiceImpl.class);

    private static final String[] HEADERS = {
            "ID", "Oracle Asset ID", "Initial Cost", "Salvage Value",
            "Accumulated Depreciation", "Net Cost", "Adjustment",
            "PO Number", "PO Date", "Vendor Name", "Vendor Number",
            "Project Number", "PO Line Number", "Release Number"
    };

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 100;
    private static final int MAX_SIZE = 100_000;
    private static final String MONEY_FORMAT = "%.3f";
    private static final String ZERO_MONEY = "0.000";

    private final ConsolidatedFinancialReportRepository repository;

    @PersistenceContext
    private EntityManager em;

    public ConsolidatedFinancialReportServiceImpl(ConsolidatedFinancialReportRepository repository) {
        this.repository = repository;
    }

    // ── public API ────────────────────────────────────────────────────────────

    @Override
    public ConsolidatedReportResponse getReport(FilterRequest request) {
        long t0 = System.currentTimeMillis();

        int page = resolvePageNumber(request);
        int size = resolvePageSize(request);
        Pageable pageable = buildPageable(page, size);

        Specification<ConsolidatedFinancialReport> spec =
                ConsolidatedReportSpecification.build(request);

        // 1) Paginated rows — one query
        Page<ConsolidatedFinancialReport> resultPage = repository.findAll(spec, pageable);
        List<ConsolidatedReport> rows = mapToDto(resultPage.getContent());

        // 2) Grand totals — one aggregate query (no WHERE clause)
        SummaryTotals totalSummary = fetchGrandTotals();

        // 3) Filtered totals — one aggregate query pushed to DB (replaces the old in-memory loop)
        SummaryTotals filteredSummary = aggregateInDb(spec);

        log.info("getReport {}ms | page={} size={} total={}",
                System.currentTimeMillis() - t0, page, size, resultPage.getTotalElements());

        return new ConsolidatedReportResponse(
                new PageResult<>(rows, page, size, resultPage.getTotalElements()),
                totalSummary.totalCost(),
                totalSummary.totalNBV(),
                totalSummary.totalDepreciation(),
                filteredSummary.totalCost(),
                filteredSummary.totalNBV(),
                filteredSummary.totalDepreciation()
        );
    }

    @Override
    public PageResult<ConsolidatedReport> getPage(FilterRequest request) {
        long t0 = System.currentTimeMillis();

        int page = resolvePageNumber(request);
        int size = resolvePageSize(request);
        Pageable pageable = buildPageable(page, size);

        Specification<ConsolidatedFinancialReport> spec =
                ConsolidatedReportSpecification.build(request);

        Page<ConsolidatedFinancialReport> resultPage = repository.findAll(spec, pageable);
        List<ConsolidatedReport> rows = mapToDto(resultPage.getContent());

        log.info("getPage {}ms | page={} size={} total={}",
                System.currentTimeMillis() - t0, page, size, resultPage.getTotalElements());

        return new PageResult<>(rows, page, size, resultPage.getTotalElements());
    }

    @Override
    public byte[] exportCsv(FilterRequest request) {
        long t0 = System.currentTimeMillis();
        List<ConsolidatedFinancialReport> rows = fetchAllEntities(request);

        StringBuilder sb = new StringBuilder(Math.max(rows.size() * 300, 2048));
        appendCsvHeaders(sb);
        for (ConsolidatedFinancialReport fr : rows) {
            appendCsvRow(sb, fr);
        }

        byte[] result = sb.toString().getBytes(StandardCharsets.UTF_8);
        log.info("CSV export {}ms | {} rows | {} bytes",
                System.currentTimeMillis() - t0, rows.size(), result.length);
        return result;
    }

    @Override
    public byte[] exportExcel(FilterRequest request) {
        long t0 = System.currentTimeMillis();
        List<ConsolidatedFinancialReport> rows = fetchAllEntities(request);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Consolidated Report");
            createHeaderRow(sheet, buildHeaderStyle(workbook));
            CellStyle dataStyle = buildDataStyle(workbook);

            int rowIdx = 1;
            for (ConsolidatedFinancialReport fr : rows) {
                createDataRow(sheet, rowIdx++, fr, dataStyle);
            }
            autoSizeColumns(sheet);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();

            byte[] result = out.toByteArray();
            log.info("Excel export {}ms | {} rows | {} bytes",
                    System.currentTimeMillis() - t0, rows.size(), result.length);
            return result;

        } catch (Exception e) {
            log.error("Failed to generate Excel export", e);
            throw new RuntimeException("Failed to generate Excel export", e);
        }
    }

    // ── core optimisation: aggregate in the DB, not in Java heap ─────────────

    /**
     * Reuses the JPA Specification predicate but issues a SELECT SUM(…)
     * instead of SELECT *, so the DB does the work and only three numbers
     * come back over the wire.
     */
    private SummaryTotals aggregateInDb(Specification<ConsolidatedFinancialReport> spec) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<ConsolidatedFinancialReport> root = cq.from(ConsolidatedFinancialReport.class);

        // Apply the same predicates the caller already built
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }

        cq.multiselect(
                cb.sum(root.get("initialCost")),
                cb.sum(root.get("accumulatedDepreciation"))
        );

        Object[] row = em.createQuery(cq).getSingleResult();

        BigDecimal cost = row[0] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
        BigDecimal dep  = row[1] instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
        return new SummaryTotals(cost, cost.subtract(dep), dep);
    }

    private SummaryTotals fetchGrandTotals() {
        ConsolidatedFinancialReportRepository.SummaryTotalsProjection p =
                repository.calculateTotalSummary();
        return new SummaryTotals(
                nvl(p.getTotalCost()),
                nvl(p.getTotalNBV()),
                nvl(p.getTotalDepreciation())
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<ConsolidatedFinancialReport> fetchAllEntities(FilterRequest request) {
        Specification<ConsolidatedFinancialReport> spec =
                ConsolidatedReportSpecification.build(request);
        Sort sort = Sort.by(Sort.Direction.DESC, "dateOfService")
                        .and(Sort.by(Sort.Direction.ASC, "oracleAssetId"));
        return repository.findAll(spec, sort);
    }

    private Pageable buildPageable(int page, int size) {
        return PageRequest.of(
                page, size,
                Sort.by(Sort.Direction.DESC, "dateOfService")
                        .and(Sort.by(Sort.Direction.ASC, "oracleAssetId"))
        );
    }

    private List<ConsolidatedReport> mapToDto(List<ConsolidatedFinancialReport> entities) {
        return entities.stream().map(this::toRow).toList();
    }

    private ConsolidatedReport toRow(ConsolidatedFinancialReport fr) {
        if (fr == null) return null;
        ConsolidatedReport row = new ConsolidatedReport();
        row.setId(fr.getId());
        row.setOracleAssetId(nullSafe(fr.getOracleAssetId()));
        row.setInitialCost(formatMoney(fr.getInitialCost()));
        row.setSalvageValue(formatMoney(fr.getSalvageValue()));
        row.setAccumulatedDepreciation(formatMoney(fr.getAccumulatedDepreciation()));
        row.setNetCost(computeNetCost(fr.getInitialCost(), fr.getAccumulatedDepreciation()));
        row.setAdjustment(formatMoney(fr.getAdjustment()));
        row.setPoNumber(nullSafe(fr.getPoNumber()));
        row.setPoDate(nullSafe(fr.getPoDate()));
        row.setVendorName(nullSafe(fr.getVendorName()));
        row.setVendorNumber(nullSafe(fr.getVendorNumber()));
        row.setProjectNumber(nullSafe(fr.getProjectNumber()));
        row.setPoLineNumber(nullSafe(fr.getPoLineNumber()));
        row.setReleaseNumber(nullSafe(fr.getReleaseNumber()));
        return row;
    }

    // ── CSV / Excel rendering (unchanged logic) ───────────────────────────────

    private void appendCsvHeaders(StringBuilder sb) {
        for (int i = 0; i < HEADERS.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(HEADERS[i]);
        }
        sb.append("\n");
    }

    private void appendCsvRow(StringBuilder sb, ConsolidatedFinancialReport fr) {
        sb.append(fr.getId() != null ? fr.getId() : "").append(",");
        sb.append(escapeCsv(fr.getOracleAssetId())).append(",");
        sb.append(formatMoney(fr.getInitialCost())).append(",");
        sb.append(formatMoney(fr.getSalvageValue())).append(",");
        sb.append(formatMoney(fr.getAccumulatedDepreciation())).append(",");
        sb.append(computeNetCostString(fr.getInitialCost(), fr.getAccumulatedDepreciation())).append(",");
        sb.append(formatMoney(fr.getAdjustment())).append(",");
        sb.append(escapeCsv(fr.getPoNumber())).append(",");
        sb.append(escapeCsv(fr.getPoDate())).append(",");
        sb.append(escapeCsv(fr.getVendorName())).append(",");
        sb.append(escapeCsv(fr.getVendorNumber())).append(",");
        sb.append(escapeCsv(fr.getProjectNumber())).append(",");
        sb.append(escapeCsv(fr.getPoLineNumber())).append(",");
        sb.append(escapeCsv(fr.getReleaseNumber())).append("\n");
    }

    private void createHeaderRow(Sheet sheet, CellStyle style) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void createDataRow(Sheet sheet, int rowIndex,
                               ConsolidatedFinancialReport fr, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(fr.getId() != null ? fr.getId() : 0L);
        row.createCell(1).setCellValue(nullSafe(fr.getOracleAssetId()));
        row.createCell(2).setCellValue(safeDouble(fr.getInitialCost()));
        row.createCell(3).setCellValue(safeDouble(fr.getSalvageValue()));
        row.createCell(4).setCellValue(safeDouble(fr.getAccumulatedDepreciation()));
        row.createCell(5).setCellValue(computeNetCostDouble(fr.getInitialCost(), fr.getAccumulatedDepreciation()));
        row.createCell(6).setCellValue(safeDouble(fr.getAdjustment()));
        row.createCell(7).setCellValue(nullSafe(fr.getPoNumber()));
        row.createCell(8).setCellValue(nullSafe(fr.getPoDate()));
        row.createCell(9).setCellValue(nullSafe(fr.getVendorName()));
        row.createCell(10).setCellValue(nullSafe(fr.getVendorNumber()));
        row.createCell(11).setCellValue(nullSafe(fr.getProjectNumber()));
        row.createCell(12).setCellValue(nullSafe(fr.getPoLineNumber()));
        row.createCell(13).setCellValue(nullSafe(fr.getReleaseNumber()));
        for (int i = 0; i < 14; i++) row.getCell(i).setCellStyle(style);
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);
    }

    // ── numeric / string utilities ────────────────────────────────────────────

    private String computeNetCost(BigDecimal initialCost, BigDecimal accumulated) {
        return formatMoney(nvl(initialCost).subtract(nvl(accumulated)));
    }

    private String computeNetCostString(BigDecimal ic, BigDecimal acc) {
        return computeNetCost(ic, acc);
    }

    private double computeNetCostDouble(BigDecimal ic, BigDecimal acc) {
        return nvl(ic).subtract(nvl(acc)).doubleValue();
    }

    private String formatMoney(BigDecimal value) {
        return value == null ? ZERO_MONEY : String.format(MONEY_FORMAT, value.doubleValue());
    }

    private double safeDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle buildDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.000"));
        return style;
    }

    private int resolvePageNumber(FilterRequest request) {
        return (request == null || request.getPage() == null || request.getPage() < 0)
                ? DEFAULT_PAGE : request.getPage();
    }

    private int resolvePageSize(FilterRequest request) {
        return (request == null || request.getSize() == null || request.getSize() <= 0)
                ? DEFAULT_SIZE : Math.min(request.getSize(), MAX_SIZE);
    }

    private record SummaryTotals(
            BigDecimal totalCost,
            BigDecimal totalNBV,
            BigDecimal totalDepreciation) {
    }
}
