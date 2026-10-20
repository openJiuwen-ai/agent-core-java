/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadRecord;
import com.openjiuwen.core.context.offload.model.OffloadStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory {@link OffloadCatalog} over a {@link ConcurrentHashMap} of
 * immutable records; state transitions swap whole records via
 * {@code compute} so the PENDING → READY → terminal machine stays
 * lock-light and race-free (架构文档 §3.1).
 *
 * @since 2026-01-01
 */
public class OffloadCatalogImpl implements OffloadCatalog {
    private final ConcurrentHashMap<String, OffloadRecord> records = new ConcurrentHashMap<>();

    @Override
    public OffloadRecord reserve(OffloadRecord pending) {
        Objects.requireNonNull(pending, "pending record must not be null");
        Objects.requireNonNull(pending.handle(), "handle must not be null");
        if (pending.status() != OffloadStatus.PENDING) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_STATUS_INVALID,
                    "reserved record must be PENDING: " + pending.handle());
        }
        OffloadRecord existing = records.putIfAbsent(pending.handle(), pending);
        if (existing != null) {
            throw new OffloadException(OffloadErrorCode.OFFLOAD_STATUS_INVALID,
                    "offload handle already reserved: " + pending.handle());
        }
        return pending;
    }

    @Override
    public void commit(String handle, OffloadBackend backend, String pathOrKey, long bytes, Long expiresAt) {
        records.compute(handle, (key, record) -> {
            if (record == null) {
                throw new OffloadException(OffloadErrorCode.OFFLOAD_STATUS_INVALID,
                        "cannot commit unknown offload handle: " + handle);
            }
            if (record.status() != OffloadStatus.PENDING) {
                throw new OffloadException(OffloadErrorCode.OFFLOAD_STATUS_INVALID,
                        "cannot commit offload handle in status " + record.status() + ": " + handle);
            }
            return new OffloadRecord(record.handle(), record.namespace(), backend, pathOrKey,
                    record.contentSha256(), bytes, record.createdAt(),
                    expiresAt != null ? expiresAt : record.expiresAt(), record.lastAccessAt(),
                    record.isPinned(), OffloadStatus.READY, record.source());
        });
    }

    @Override
    public void abort(String handle) {
        records.computeIfPresent(handle, (key, record) ->
                record.status() == OffloadStatus.PENDING ? null : record);
    }

    @Override
    public Optional<OffloadRecord> find(OffloadNamespace ns, String handle) {
        return Optional.ofNullable(records.get(handle))
                .filter(record -> record.namespace().matches(ns))
                .filter(record -> record.status() != OffloadStatus.PENDING);
    }

    @Override
    public Optional<OffloadRecord> findByHandle(String handle) {
        return Optional.ofNullable(records.get(handle));
    }

    @Override
    public boolean markStatus(String handle, OffloadStatus status) {
        if (status == OffloadStatus.PENDING || status == OffloadStatus.READY) {
            return false;
        }
        records.computeIfPresent(handle, (key, record) ->
                record.status() == OffloadStatus.READY ? withStatus(record, status) : record);
        OffloadRecord updated = records.get(handle);
        return updated != null && updated.status() == status;
    }

    @Override
    public void touch(String handle) {
        long now = System.currentTimeMillis();
        records.computeIfPresent(handle, (key, record) ->
                new OffloadRecord(record.handle(), record.namespace(), record.backend(), record.pathOrKey(),
                        record.contentSha256(), record.bytes(), record.createdAt(), record.expiresAt(),
                        now, record.isPinned(), record.status(), record.source()));
    }

    @Override
    public boolean setPinned(String handle, boolean isPinned) {
        records.computeIfPresent(handle, (key, record) ->
                record.isPinned() == isPinned ? record : withPinned(record, isPinned));
        OffloadRecord updated = records.get(handle);
        return updated != null && updated.isPinned() == isPinned;
    }

    @Override
    public boolean extendTtl(String handle, long newExpiresAtMs) {
        records.computeIfPresent(handle, (key, record) -> withExpiresAt(record, newExpiresAtMs));
        OffloadRecord updated = records.get(handle);
        return updated != null && updated.expiresAt() != null && updated.expiresAt() == newExpiresAtMs;
    }

    @Override
    public List<OffloadRecord> listExpired(long now, int limit) {
        return records.values().stream()
                .filter(record -> record.status() == OffloadStatus.READY)
                .filter(record -> record.expiresAt() != null && record.expiresAt() <= now)
                .sorted(Comparator.comparingLong(OffloadRecord::lastAccessAt))
                .limit(Math.max(0, limit))
                .collect(Collectors.toList());
    }

    @Override
    public List<OffloadRecord> listByNamespace(OffloadNamespace ns) {
        return records.values().stream()
                .filter(record -> record.namespace().matches(ns))
                .collect(Collectors.toList());
    }

    @Override
    public long usedBytes(OffloadNamespace ns) {
        return readyRecords(ns).stream().mapToLong(OffloadRecord::bytes).sum();
    }

    @Override
    public int usedEntries(OffloadNamespace ns) {
        return readyRecords(ns).size();
    }

    @Override
    public boolean evictLru(OffloadNamespace ns) {
        Optional<OffloadRecord> victim = readyRecords(ns).stream()
                .filter(record -> !record.isPinned())
                .min(Comparator.comparingLong(OffloadRecord::lastAccessAt));
        return victim.isPresent() && remove(victim.get().handle());
    }

    @Override
    public boolean remove(String handle) {
        return records.remove(handle) != null;
    }

    @Override
    public Map<String, OffloadRecord> snapshot() {
        return new java.util.LinkedHashMap<>(records);
    }

    private List<OffloadRecord> readyRecords(OffloadNamespace ns) {
        return records.values().stream()
                .filter(record -> record.namespace().matches(ns))
                .filter(record -> record.status() == OffloadStatus.READY)
                .collect(Collectors.toList());
    }

    private static OffloadRecord withStatus(OffloadRecord record, OffloadStatus status) {
        return new OffloadRecord(record.handle(), record.namespace(), record.backend(), record.pathOrKey(),
                record.contentSha256(), record.bytes(), record.createdAt(), record.expiresAt(),
                record.lastAccessAt(), record.isPinned(), status, record.source());
    }

    private static OffloadRecord withPinned(OffloadRecord record, boolean isPinned) {
        return new OffloadRecord(record.handle(), record.namespace(), record.backend(), record.pathOrKey(),
                record.contentSha256(), record.bytes(), record.createdAt(), record.expiresAt(),
                record.lastAccessAt(), isPinned, record.status(), record.source());
    }

    private static OffloadRecord withExpiresAt(OffloadRecord record, long expiresAtMs) {
        return new OffloadRecord(record.handle(), record.namespace(), record.backend(), record.pathOrKey(),
                record.contentSha256(), record.bytes(), record.createdAt(), expiresAtMs,
                record.lastAccessAt(), record.isPinned(), record.status(), record.source());
    }
}
