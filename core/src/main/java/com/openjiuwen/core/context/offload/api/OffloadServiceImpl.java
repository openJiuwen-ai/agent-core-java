/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.model.OffloadRecord;
import com.openjiuwen.core.context.offload.model.OffloadRef;
import com.openjiuwen.core.context.offload.model.OffloadRequest;
import com.openjiuwen.core.context.offload.model.OffloadStatus;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link OffloadService}: write-ahead publish under the session
 * gate (gate → reserve PENDING → saveWithFallback → commit READY / abort),
 * five-step load validation (ownership → status → directed backend read →
 * sha256 → touch), and a save that never throws (empty Optional = keep
 * inline, 共享约定 13).
 *
 * <p>Mirrors 架构文档 §3.1 / §4 sequences; covers AC-2-02..10.</p>
 *
 * @since 2026-01-01
 */
public class OffloadServiceImpl implements OffloadService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OffloadServiceImpl.class);
    private final OffloadCatalog catalog;
    private final CompositeOffloadStore store;
    private final SessionOffloadGate gate;
    private final OffloadConfig config;

    /** T19: optional otel sink; empty keeps the P0 Slf4j-only path. */
    private final Optional<OffloadOtelReporter> otelReporter;

    public OffloadServiceImpl(OffloadCatalog catalog, CompositeOffloadStore store, SessionOffloadGate gate,
                              OffloadConfig config) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.gate = gate == null ? new SessionOffloadGate() : gate;
        this.config = config == null ? new OffloadConfig() : config;
        this.otelReporter = OffloadOtelReporter.create(this.config.isEnableOtel());
    }

    /**
     * Returns the otel reporter when enabled, empty otherwise (test hook).
     *
     * @return the otel reporter, or empty when otel is disabled
     */
    Optional<OffloadOtelReporter> otelReporter() {
        return otelReporter;
    }

    private void recordMetric(String event, OffloadMetric metric) {
        otelReporter.ifPresent(reporter -> reporter.record(event, metric));
    }

    @Override
    public Optional<OffloadRef> save(OffloadRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        OffloadNamespace ns = request.getNamespace() == null
                ? OffloadNamespace.of("", "", "")
                : request.getNamespace();
        String algorithmId = request.getAlgorithmId();
        long startedAt = System.currentTimeMillis();
        String handle = newHandle();
        long preTokens = request.getPreTokenCount();
        long postTokens = request.getPostTokenCount();
        Long expiresAt = expiresAtOf(request).orElse(null);
        boolean isReserved = false;
        try (SessionOffloadGate.AutoCloseableLock ignored = gate.lock(ns)) {
            OffloadPayload payload = OffloadPayload.create(handle, ns, request.getMessages(), expiresAt);
            long bytes = payloadBytes(payload);
            catalog.reserve(new OffloadRecord(handle, ns, null, null, payload.contentSha256(), bytes,
                    System.currentTimeMillis(), expiresAt, System.currentTimeMillis(), false,
                    OffloadStatus.PENDING, request.getSource()));
            isReserved = true;
            try {
                CompositeOffloadStore.ChainedSaveOutcome outcome =
                        store.saveWithFallback(ns, handle, payload, effectiveTtl(request).orElse(null));
                catalog.commit(handle, outcome.backend(), outcome.pathOrKey(), bytes, expiresAt);
                OffloadRef ref = new OffloadRef(handle, OffloadRef.markerType(outcome.backend()),
                        outcome.pathOrKey(), outcome.backend());
                OffloadMetric metric = new OffloadMetric(algorithmId, handle, true, null, preTokens, postTokens,
                        System.currentTimeMillis() - startedAt, outcome.backend());
                LOGGER.info("{}", metric.toLogLine());
                recordMetric("save", metric);
                return Optional.of(ref);
            } catch (OffloadException ex) {
                catalog.abort(handle);
                OffloadMetric metric = new OffloadMetric(algorithmId, handle, false, failureReason(ex),
                        preTokens, postTokens, System.currentTimeMillis() - startedAt, null);
                LOGGER.warn("{}", metric.toLogLine(), ex);
                recordMetric("save", metric);
                return Optional.empty();
            }
        } catch (RuntimeException ex) {
            // Intentional: save must never throw to the caller (共享约定 13).
            // The write-ahead reserve → store save → commit chain can surface
            // a variety of RuntimeException subtypes (NPE, ISE from the
            // catalog, OffloadException from the store, etc.). All are
            // rolled back uniformly here to keep the agent turn alive.
            // Roll the write-ahead reservation back so a failed save never
            // leaves a PENDING entry stranded in the catalog (orphan input).
            // A reserve itself failed → nothing was reserved, nothing to abort.
            if (isReserved) {
                catalog.abort(handle);
            }
            OffloadMetric metric = new OffloadMetric(algorithmId, handle, false, failureReasonOf(ex),
                    preTokens, postTokens, System.currentTimeMillis() - startedAt, null);
            LOGGER.warn("{}", metric.toLogLine(), ex);
            recordMetric("save", metric);
            return Optional.empty();
        }
    }

    @Override
    public OffloadPayload load(OffloadNamespace ns, String handle, String type) {
        Objects.requireNonNull(ns, "ns must not be null");
        Objects.requireNonNull(handle, "handle must not be null");
        long startedAt = System.currentTimeMillis();
        try {
            OffloadRecord record = resolveReadyRecord(ns, handle);
            // Step 3: directed backend read (catalog records the committed backend).
            OffloadPayload payload = readFromBackend(ns, handle, record);
            // Step 4: integrity.
            if (!payload.sha256Matches()) {
                catalog.markStatus(handle, OffloadStatus.CORRUPT);
                throw new OffloadException(OffloadErrorCode.OFFLOAD_CORRUPT,
                        "offload payload sha256 mismatch: " + handle);
            }
            // Step 5: LRU touch.
            catalog.touch(handle);
            long reloadedTokens = payloadTokens(payload);
            OffloadMetric metric = new OffloadMetric(type, handle, true, null, 0L, reloadedTokens,
                    System.currentTimeMillis() - startedAt, record.backend());
            LOGGER.info("{}", metric.toLogLine("load"));
            recordMetric("load", metric);
            return payload;
        } catch (OffloadException ex) {
            OffloadMetric metric = new OffloadMetric(type, handle, false, failureReason(ex), 0L, 0L,
                    System.currentTimeMillis() - startedAt, null);
            LOGGER.info("{}", metric.toLogLine("load"));
            recordMetric("load", metric);
            throw ex;
        }
    }

    /**
     * Steps 1-2 of the load validation: ownership (unknown → MISSING; foreign
     * namespace → FORBIDDEN; same-namespace PENDING → MISSING per AC-2-04) and
     * the status gate (PENDING invisible, terminal states map to load codes).
     *
     * @param ns     the owning namespace
     * @param handle the offload handle to resolve
     * @return the READY record owned by the namespace
     * @throws OffloadException when the handle is unknown, forbidden or not READY
     */
    private OffloadRecord resolveReadyRecord(OffloadNamespace ns, String handle) {
        // Step 1: ownership — unknown handle → MISSING; foreign namespace → FORBIDDEN;
        // same-namespace PENDING (write-ahead, not published) → MISSING (AC-2-04).
        Optional<OffloadRecord> anyHandle = catalog.findByHandle(handle);
        if (anyHandle.isEmpty()) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING,
                    "offload handle not found: " + handle);
        }
        Optional<OffloadRecord> owned = catalog.find(ns, handle);
        if (owned.isEmpty()) {
            boolean isSameNamespacePending = anyHandle.get().namespace().matches(ns)
                    && anyHandle.get().status() == OffloadStatus.PENDING;
            throw new OffloadException(
                    isSameNamespacePending ? OffloadErrorCode.OFFLOAD_MISSING : OffloadErrorCode.OFFLOAD_FORBIDDEN,
                    isSameNamespacePending
                            ? "offload handle not published yet: " + handle
                            : "offload handle does not belong to this namespace: " + handle);
        }
        OffloadRecord record = owned.get();
        // Step 2: status gate — PENDING is invisible, terminal states map to load codes.
        if (record.status() != OffloadStatus.READY) {
            throw new OffloadException(toLoadErrorCode(record.status()),
                    "offload handle " + handle + " is " + record.status());
        }
        return record;
    }

    /**
     * Step 3 of the load validation: directed backend read. A backend MISSING
     * is promoted to GONE and the catalog entry marked MISSING.
     *
     * @param ns     the owning namespace
     * @param handle the offload handle to read
     * @param record the catalog record carrying the committed backend
     * @return the loaded payload
     * @throws OffloadException when the backend read fails or the entry vanished
     */
    private OffloadPayload readFromBackend(OffloadNamespace ns, String handle, OffloadRecord record) {
        try {
            return store.storeOf(record.backend()).load(ns, handle);
        } catch (OffloadException ex) {
            if (ex.code() == OffloadErrorCode.OFFLOAD_MISSING) {
                catalog.markStatus(handle, OffloadStatus.MISSING);
                throw new OffloadException(OffloadErrorCode.OFFLOAD_GONE,
                        "offload backing entry vanished: " + handle, ex);
            }
            throw ex;
        }
    }

    /**
     * Estimates the token footprint of a payload's messages without a live
     * token counter (the service has no context binding); char/3 mirrors
     * {@code ContextUtils.estimateTokens}. Fills the AC-6-05 reload metric.
     *
     * @param payload the payload to estimate
     * @return the estimated token count
     */
    private static long payloadTokens(OffloadPayload payload) {
        long tokens = 0L;
        for (Map<String, Object> dump : payload.messages()) {
            Object content = dump == null ? null : dump.get("content");
            String text = content == null ? "" : String.valueOf(content);
            tokens += Math.max(text.length() / 3, 1);
        }
        return tokens;
    }

    @Override
    public boolean delete(OffloadNamespace ns, String handle) {
        Objects.requireNonNull(ns, "ns must not be null");
        Objects.requireNonNull(handle, "handle must not be null");
        try (SessionOffloadGate.AutoCloseableLock ignored = gate.lock(ns)) {
            Optional<OffloadRecord> record = catalog.find(ns, handle);
            if (record.isEmpty()) {
                return false;
            }
            try {
                store.storeOf(record.get().backend()).delete(ns, handle);
            } catch (OffloadException ex) {
                LOGGER.warn("[Offload] backend delete failed for {} : {}", handle, ex.code());
            }
            return catalog.remove(handle);
        }
    }

    @Override
    public void deleteNamespace(OffloadNamespace ns) {
        Objects.requireNonNull(ns, "ns must not be null");
        try (SessionOffloadGate.AutoCloseableLock ignored = gate.lock(ns)) {
            List<OffloadRecord> records = catalog.listByNamespace(ns);
            for (OffloadRecord record : records) {
                try {
                    store.storeOf(record.backend()).delete(ns, record.handle());
                } catch (OffloadException ex) {
                    LOGGER.warn("[Offload] backend delete failed for {} : {}", record.handle(), ex.code());
                }
                catalog.remove(record.handle());
            }
        }
    }

    @Override
    public void pin(String handle, Duration duration) {
        Objects.requireNonNull(handle, "handle must not be null");
        if (catalog.findByHandle(handle).isEmpty()) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "offload handle not found: " + handle);
        }
        catalog.setPinned(handle, true);
        if (duration != null && !duration.isZero() && !duration.isNegative()) {
            catalog.extendTtl(handle, System.currentTimeMillis() + duration.toMillis());
        }
    }

    @Override
    public void unpin(String handle) {
        catalog.setPinned(handle, false);
    }

    @Override
    public void extendTtl(String handle, Duration delta) {
        Objects.requireNonNull(handle, "handle must not be null");
        Objects.requireNonNull(delta, "delta must not be null");
        if (delta.isZero() || delta.isNegative()) {
            throw new IllegalArgumentException("ttl delta must be positive");
        }
        Optional<OffloadRecord> record = catalog.findByHandle(handle);
        if (record.isEmpty()) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "offload handle not found: " + handle);
        }
        long currentExpiry = record.get().expiresAt() == null
                ? System.currentTimeMillis()
                : record.get().expiresAt();
        catalog.extendTtl(handle, Math.max(currentExpiry, System.currentTimeMillis()) + delta.toMillis());
    }

    @Override
    public void capOrphanTtl(String handle) {
        Objects.requireNonNull(handle, "handle must not be null");
        long orphanTtlSeconds = config.getOrphanTtlSeconds();
        if (orphanTtlSeconds <= 0L) {
            // 0 disables orphan shortening (keep the full configured TTL).
            return;
        }
        Optional<OffloadRecord> record = catalog.findByHandle(handle);
        if (record.isEmpty()) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_MISSING, "offload handle not found: " + handle);
        }
        OffloadRecord entry = record.get();
        // Pinned entries are protected (a compression round may still pin the
        // entry; a real orphan would never be pinned at this point).
        if (entry.isPinned()) {
            return;
        }
        long orphanExpiry = System.currentTimeMillis() + Duration.ofSeconds(orphanTtlSeconds).toMillis();
        // Only ever shorten: never postpone an entry that already expires sooner.
        long currentExpiry = entry.expiresAt() == null ? Long.MAX_VALUE : entry.expiresAt();
        if (orphanExpiry < currentExpiry) {
            catalog.extendTtl(handle, orphanExpiry);
        }
    }

    private static String newHandle() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Optional<Long> expiresAtOf(OffloadRequest request) {
        if (request.getTtl() != null && !request.getTtl().isZero() && !request.getTtl().isNegative()) {
            return Optional.of(System.currentTimeMillis() + request.getTtl().toMillis());
        }
        // Empty means no expiry: the entry defers to the store default and
        // eventually to the Janitor's orphan scan.
        return config.getTtlSeconds() > 0L
                ? Optional.of(System.currentTimeMillis() + Duration.ofSeconds(config.getTtlSeconds()).toMillis())
                : Optional.empty();
    }

    private Optional<Duration> effectiveTtl(OffloadRequest request) {
        if (request.getTtl() != null && !request.getTtl().isZero() && !request.getTtl().isNegative()) {
            return Optional.of(request.getTtl());
        }
        // Empty means the store applies its own default TTL.
        return config.getTtlSeconds() > 0L ? Optional.of(Duration.ofSeconds(config.getTtlSeconds())) : Optional.empty();
    }

    private static long payloadBytes(OffloadPayload payload) {
        return payload.serializeJson().getBytes(StandardCharsets.UTF_8).length;
    }

    private static OffloadErrorCode toLoadErrorCode(OffloadStatus status) {
        return switch (status) {
            case EXPIRED -> OffloadErrorCode.OFFLOAD_EXPIRED;
            case CORRUPT -> OffloadErrorCode.OFFLOAD_CORRUPT;
            case MISSING, PENDING -> OffloadErrorCode.OFFLOAD_MISSING;
            default -> OffloadErrorCode.OFFLOAD_STATUS_INVALID;
        };
    }

    private static String failureReason(OffloadException ex) {
        return switch (ex.code()) {
            case OFFLOAD_STORE_IO -> OffloadFailureReason.IO_ERROR.name();
            case OFFLOAD_REDIS_DOWN -> OffloadFailureReason.REDIS_DOWN.name();
            default -> ex.code().name();
        };
    }

    private static String failureReasonOf(RuntimeException ex) {
        return ex instanceof OffloadException offloadException
                ? failureReason(offloadException)
                : OffloadFailureReason.IO_ERROR.name();
    }
}
