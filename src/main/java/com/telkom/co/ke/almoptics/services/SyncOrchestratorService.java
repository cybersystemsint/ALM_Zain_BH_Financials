package com.telkom.co.ke.almoptics.services;

import com.telkom.co.ke.almoptics.repository.FinancialReportRepo;
import com.telkom.co.ke.almoptics.repository.UnmappedActiveInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedITInventoryRepository;
import com.telkom.co.ke.almoptics.repository.UnmappedPassiveInventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-source-of-truth orchestrator for the Inventory ↔ Financial Report
 * sync cycle. Replaces the per-row, multi-cron approach in
 * {@link AssetSyncService}, {@link MissingAssetCheckService},
 * {@link UnmappedInventoryService#scheduleUnmappedInventoryCheck()} and
 * {@link com.telkom.co.ke.almoptics.schedulers.FinancialSyncScheduler} with
 * one cron-driven, set-based, lock-protected workflow.
 *
 * <p><b>Pipeline stages</b> (executed in order):</p>
 * <ol>
 *   <li><b>promote</b> – bulk update FR rows older than 30d from NEW → EXISTING.</li>
 *   <li><b>recover</b> – bulk-clear RetirementDate for previously-missing FR
 *       rows that have reappeared in source inventory.</li>
 *   <li><b>mark-missing</b> – bulk set RetirementDate for FR rows that no
 *       longer exist in any source inventory.</li>
 *   <li><b>decommission</b> – bulk flip StatusFlag → DECOMMISSIONED for FR rows
 *       missing past the 14-day grace window.</li>
 *   <li><b>unmapped-add</b> – bulk INSERT into the three unmapped tables for
 *       inventory rows that don't have an FR record yet.</li>
 *   <li><b>unmapped-prune</b> – bulk DELETE from the unmapped tables for any
 *       row that now has an FR record (delta sync, never wipes the table).</li>
 * </ol>
 *
 * <p>Why this beats the old code:</p>
 * <ul>
 *   <li>One round-trip per stage instead of 3-7 per FR row, so a 100k-asset
 *       sync drops from ~hundreds of thousands of queries to about a dozen.</li>
 *   <li>Unmapped reports are never empty during sync (the legacy
 *       {@code rebuildUnmappedInventoriesAsync} did
 *       {@code DELETE FROM tb_unmappednode} as its first step).</li>
 *   <li>A {@link AtomicBoolean} latch makes overlapping runs no-ops instead of
 *       silently colliding on the same FR rows.</li>
 *   <li>Per-stage timing &amp; row-counts are kept in an in-memory ring buffer
 *       so {@code GET /api/asset-sync/history} can show real metrics.</li>
 * </ul>
 *
 * <p>The cron expression is configurable; default is hourly on the hour.
 * Set <code>sync.orchestrator.cron</code> in application.properties to
 * change it (or set <code>sync.orchestrator.enabled=false</code> to disable
 * the cron entirely while still allowing manual triggers via the
 * controller).</p>
 */
@Service
public class SyncOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(SyncOrchestratorService.class);
    private static final int HISTORY_SIZE = 50;

    @Autowired
    private FinancialReportRepo financialReportRepo;

    @Autowired
    private UnmappedActiveInventoryRepository unmappedActiveRepo;

    @Autowired
    private UnmappedPassiveInventoryRepository unmappedPassiveRepo;

    @Autowired
    private UnmappedITInventoryRepository unmappedITRepo;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    /**
     * Self-injection of the Spring-managed proxy. We need this because
     * each pipeline stage is annotated {@code @Transactional(REQUIRES_NEW)}
     * but those annotations are implemented by an AOP proxy that wraps
     * this bean. Calling {@code this.stagePromoteNewToExisting()} bypasses
     * the proxy (Spring docs: "self-invocation"), which means no
     * transaction is started and {@code @Modifying} JPQL fails with
     * {@code TransactionRequiredException}.
     *
     * <p>By going through {@code self.stagePromoteNewToExisting()} we go
     * through the proxy, so the transactional advice fires correctly.
     * {@code @Lazy} avoids the chicken-and-egg startup cycle.</p>
     */
    @Autowired
    @Lazy
    private SyncOrchestratorService self;

    /** Runtime kill-switch. Toggle via /pause and /resume controller endpoints. */
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /** Re-entrancy guard: only one cycle at a time, no queueing. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Snapshot of the last completed run, for /status. */
    private final AtomicReference<RunSummary> lastRun = new AtomicReference<>();

    /** Ring buffer of recent runs, for /history. */
    private final Deque<RunSummary> history = new ArrayDeque<>(HISTORY_SIZE);

    @Value("${sync.orchestrator.enabled:true}")
    private boolean schedulingEnabled;

    /**
     * Hourly by default. Override with sync.orchestrator.cron in config.
     * Cron format is Spring's: second minute hour day-of-month month day-of-week.
     */
    @Scheduled(cron = "${sync.orchestrator.cron:0 0 * * * *}")
    public void scheduledRun() {
        if (!schedulingEnabled) {
            logger.debug("Skipping scheduled sync run – sync.orchestrator.enabled=false");
            return;
        }
        runFullCycle("scheduler");
    }

    /**
     * Run the full cycle synchronously. Returns immediately if another run
     * is already in progress (so callers can issue this from REST without
     * fear of deadlock).
     */
    public RunSummary runFullCycle(String triggeredBy) {
        if (paused.get()) {
            logger.info("Sync orchestrator paused – skipping run requested by {}", triggeredBy);
            return RunSummary.skipped("paused");
        }
        if (!running.compareAndSet(false, true)) {
            logger.info("Sync orchestrator already running – skipping overlap requested by {}", triggeredBy);
            return RunSummary.skipped("already-running");
        }
        LocalDateTime start = LocalDateTime.now();
        List<StageResult> stages = new ArrayList<>();
        Throwable failure = null;
        try {
            logger.info("Sync cycle starting – triggeredBy={}", triggeredBy);
            // NOTE: every stage goes through `self` (the Spring proxy) so
            // the @Transactional advice on each stage method actually fires.
            // Calling this::stage… directly would skip the proxy and the
            // bulk @Modifying queries would fail with TransactionRequiredException.
            stages.add(runStage("promote",        self::stagePromoteNewToExisting));
            stages.add(runStage("recover",        self::stageRecoverMissingAssets));
            stages.add(runStage("mark-missing",   self::stageMarkMissingAssets));
            stages.add(runStage("decommission",   self::stageDecommissionExpired));
            stages.add(runStage("unmapped-add",   self::stageUnmappedInsertDelta));
            stages.add(runStage("unmapped-prune", self::stageUnmappedDeleteMapped));
        } catch (Throwable t) {
            failure = t;
            logger.error("Sync cycle aborted with error", t);
        } finally {
            running.set(false);
        }
        RunSummary summary = new RunSummary(triggeredBy, start, LocalDateTime.now(), stages, failure);
        recordRun(summary);
        if (auditLogService != null) {
            try {
                // tb_AuditLog.details is VARCHAR(255). The full
                // toShortString() can be much longer (per-stage timing
                // and row counts), which causes "Data truncation: Data
                // too long for column 'details' at row 1". Cap to 240
                // chars here so the audit insert always succeeds.
                auditLogService.logAction("SyncOrchestrator",
                        failure == null ? "RUN_COMPLETED" : "RUN_FAILED",
                        triggeredBy, truncate(summary.toShortString(), 240));
            } catch (Exception ignore) { /* never let audit-log break sync */ }
        }
        return summary;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Run a single named stage on demand. Useful for one-off corrections. */
    public RunSummary runSingleStage(String stage, String triggeredBy) {
        if (paused.get()) return RunSummary.skipped("paused");
        if (!running.compareAndSet(false, true)) return RunSummary.skipped("already-running");
        LocalDateTime start = LocalDateTime.now();
        List<StageResult> stages = new ArrayList<>();
        Throwable failure = null;
        try {
            // Same self-proxy reasoning as runFullCycle.
            switch (stage) {
                case "promote":         stages.add(runStage(stage, self::stagePromoteNewToExisting)); break;
                case "recover":         stages.add(runStage(stage, self::stageRecoverMissingAssets)); break;
                case "mark-missing":    stages.add(runStage(stage, self::stageMarkMissingAssets));    break;
                case "decommission":    stages.add(runStage(stage, self::stageDecommissionExpired));  break;
                case "unmapped-add":    stages.add(runStage(stage, self::stageUnmappedInsertDelta));  break;
                case "unmapped-prune":  stages.add(runStage(stage, self::stageUnmappedDeleteMapped)); break;
                default:
                    throw new IllegalArgumentException("Unknown stage: " + stage);
            }
        } catch (Throwable t) {
            failure = t;
            logger.error("Stage {} failed", stage, t);
        } finally {
            running.set(false);
        }
        RunSummary summary = new RunSummary("stage:" + stage + ":" + triggeredBy,
                start, LocalDateTime.now(), stages, failure);
        recordRun(summary);
        return summary;
    }

    public void pause()  { paused.set(true); }
    public void resume() { paused.set(false); }

    public boolean isPaused()  { return paused.get(); }
    public boolean isRunning() { return running.get(); }

    public RunSummary getLastRun() { return lastRun.get(); }

    public synchronized List<RunSummary> getHistory(int limit) {
        List<RunSummary> snapshot = new ArrayList<>(history);
        Collections.reverse(snapshot); // newest first
        if (limit > 0 && limit < snapshot.size()) {
            return snapshot.subList(0, limit);
        }
        return snapshot;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("paused", paused.get());
        s.put("running", running.get());
        s.put("schedulingEnabled", schedulingEnabled);
        RunSummary last = lastRun.get();
        s.put("lastRunAt", last == null ? null : last.start);
        s.put("lastRunDurationMs", last == null ? null : last.durationMillis());
        s.put("lastRunStatus", last == null ? "never-run"
                : (last.failure == null ? "ok" : "error: " + last.failure.getMessage()));
        s.put("lastRunTriggeredBy", last == null ? null : last.triggeredBy);
        return s;
    }

    // ------------------------------------------------------------------
    // Pipeline stages
    // ------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stagePromoteNewToExisting() {
        return financialReportRepo.bulkPromoteNewToExisting();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageRecoverMissingAssets() {
        return financialReportRepo.bulkClearRecoveredAssets();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageMarkMissingAssets() {
        return financialReportRepo.bulkMarkMissingAssets();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageDecommissionExpired() {
        return financialReportRepo.bulkDecommissionAfterGracePeriod();
    }

    /**
     * NOTE on transaction structure for the unmapped stages.
     *
     * <p>Each (active / passive / IT) sub-step is its own
     * {@code @Transactional(REQUIRES_NEW)} method. Why?</p>
     *
     * <p>If we put all 3 inside a single transaction and sub-step #1 throws
     * (e.g. the collation issue we just hit), Spring's transaction proxy
     * marks the *outer* transaction as <em>rollback-only</em>. From that
     * point on, any successful sub-step still ends with
     * {@code UnexpectedRollbackException: Transaction silently rolled back
     * because it has been marked as rollback-only}. That's exactly the
     * cascading failure we saw in the logs.</p>
     *
     * <p>By giving each sub-step its own physical transaction we get
     * <em>independent commit/rollback</em>: an Active failure no longer
     * dooms Passive and IT.</p>
     *
     * <p>The orchestration method below is intentionally <b>not</b>
     * {@code @Transactional} — it just adds up the per-step row counts.
     * Each {@code self.X()} call goes through the proxy so the inner
     * {@code REQUIRES_NEW} fires.</p>
     */
    public int stageUnmappedInsertDelta() {
        int active  = safeStep(self::stageUnmappedActiveAdd,  "unmapped-active-add");
        int passive = safeStep(self::stageUnmappedPassiveAdd, "unmapped-passive-add");
        int it      = safeStep(self::stageUnmappedItAdd,      "unmapped-it-add");
        return active + passive + it;
    }

    public int stageUnmappedDeleteMapped() {
        int active  = safeStep(self::stageUnmappedActivePrune,  "unmapped-active-prune");
        int passive = safeStep(self::stageUnmappedPassivePrune, "unmapped-passive-prune");
        int it      = safeStep(self::stageUnmappedItPrune,      "unmapped-it-prune");
        return active + passive + it;
    }

    // Each of these has its OWN transaction, so a thrown
    // exception only rolls back that one sub-step.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageUnmappedActiveAdd()   { return unmappedActiveRepo.bulkInsertMissingFromSource(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageUnmappedPassiveAdd()  { return unmappedPassiveRepo.bulkInsertMissingFromSource(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageUnmappedItAdd()       { return unmappedITRepo.bulkInsertMissingFromSource(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageUnmappedActivePrune() { return unmappedActiveRepo.bulkDeleteMappedRows(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageUnmappedPassivePrune(){ return unmappedPassiveRepo.bulkDeleteMappedRows(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int stageUnmappedItPrune()     { return unmappedITRepo.bulkDeleteMappedRows(); }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Execute a sub-step that's allowed to fail individually without
     *  taking the whole stage down. Returns 0 on failure. */
    private int safeStep(java.util.function.IntSupplier op, String name) {
        try {
            int rows = op.getAsInt();
            logger.info("Sync sub-step {} affected {} rows", name, rows);
            return rows;
        } catch (Exception e) {
            // The proxy unwrapping means the rollback-only marker only
            // applies to the inner REQUIRES_NEW transaction, which is
            // already gone by the time we get here. Safe to log & continue.
            logger.error("Sync sub-step {} failed: {}", name, e.getMessage(), e);
            return 0;
        }
    }

    private StageResult runStage(String name, java.util.function.IntSupplier op) {
        long t0 = System.nanoTime();
        int rows;
        Throwable err = null;
        try {
            rows = op.getAsInt();
        } catch (Throwable t) {
            err = t;
            rows = 0;
            logger.error("Stage {} failed", name, t);
        }
        long durationMs = (System.nanoTime() - t0) / 1_000_000;
        logger.info("Stage {} – rows={}, durationMs={}", name, rows, durationMs);
        return new StageResult(name, rows, durationMs, err == null ? null : err.getMessage());
    }

    private synchronized void recordRun(RunSummary summary) {
        lastRun.set(summary);
        if (history.size() == HISTORY_SIZE) history.removeFirst();
        history.addLast(summary);
    }

    // ------------------------------------------------------------------
    // POJOs
    // ------------------------------------------------------------------

    public static class StageResult {
        public final String name;
        public final int rowsAffected;
        public final long durationMs;
        public final String error;

        public StageResult(String name, int rowsAffected, long durationMs, String error) {
            this.name = name;
            this.rowsAffected = rowsAffected;
            this.durationMs = durationMs;
            this.error = error;
        }

        public String getName() { return name; }
        public int getRowsAffected() { return rowsAffected; }
        public long getDurationMs() { return durationMs; }
        public String getError() { return error; }
    }

    public static class RunSummary {
        public final String triggeredBy;
        public final LocalDateTime start;
        public final LocalDateTime end;
        public final List<StageResult> stages;
        public final Throwable failure;

        public RunSummary(String triggeredBy, LocalDateTime start, LocalDateTime end,
                          List<StageResult> stages, Throwable failure) {
            this.triggeredBy = triggeredBy;
            this.start = start;
            this.end = end;
            this.stages = stages;
            this.failure = failure;
        }

        public static RunSummary skipped(String reason) {
            LocalDateTime now = LocalDateTime.now();
            RunSummary r = new RunSummary("skipped:" + reason, now, now,
                    Collections.emptyList(), null);
            return r;
        }

        public long durationMillis() {
            return Duration.between(start, end).toMillis();
        }

        public String toShortString() {
            StringBuilder sb = new StringBuilder();
            sb.append("triggeredBy=").append(triggeredBy)
              .append(", durationMs=").append(durationMillis());
            for (StageResult s : stages) {
                sb.append(", ").append(s.name).append("=").append(s.rowsAffected)
                  .append('(').append(s.durationMs).append("ms)");
                if (s.error != null) sb.append("[ERR:").append(s.error).append(']');
            }
            if (failure != null) sb.append(", failure=").append(failure.getMessage());
            return sb.toString();
        }

        public String getTriggeredBy() { return triggeredBy; }
        public LocalDateTime getStart() { return start; }
        public LocalDateTime getEnd() { return end; }
        public List<StageResult> getStages() { return stages; }
        public String getFailureMessage() { return failure == null ? null : failure.getMessage(); }
    }
}
