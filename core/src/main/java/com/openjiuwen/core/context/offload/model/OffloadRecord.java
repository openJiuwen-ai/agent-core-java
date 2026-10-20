/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

/**
 * Catalog entry (架构文档 §7.1, twelve fields).
 *
 * <p>Immutable snapshot; the {@code OffloadCatalogImpl} swaps whole records
 * on state transitions to keep the state machine lock-light.</p>
 *
 * @param handle        unique offload handle (32-char hex)
 * @param namespace     owning namespace
 * @param backend       backend the payload committed to (concrete, not COMPOSITE)
 * @param pathOrKey     file path or Redis key of the stored payload
 * @param contentSha256 digest recorded at commit time
 * @param bytes         persisted payload size in bytes
 * @param createdAt     epoch millis of reserve
 * @param expiresAt     epoch millis expiry, {@code null} when no TTL
 * @param lastAccessAt  epoch millis of the latest touch (LRU basis)
 * @param isPinned      true when protected from Janitor eviction
 * @param status        lifecycle state
 * @param source        trigger description, e.g. tool call id
 */
public record OffloadRecord(String handle, OffloadNamespace namespace, OffloadBackend backend,
                            String pathOrKey, String contentSha256, long bytes, long createdAt,
                            Long expiresAt, long lastAccessAt, boolean isPinned,
                            OffloadStatus status, String source) {
}
