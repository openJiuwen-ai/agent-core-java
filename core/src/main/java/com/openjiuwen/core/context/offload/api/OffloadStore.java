/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;

import java.time.Duration;

/**
 * Storage SPI for offloaded context payloads.
 *
 * <p>Implementations must be thread-safe and must make a payload visible
 * atomically: {@link #save} either succeeds fully or throws
 * {@link OffloadException} without leaving a readable partial artifact
 * (架构文档 §7.14). The sha256 integrity check lives in the service layer,
 * stores only move opaque payloads.</p>
 *
 * @since 2026-01-01
 */
public interface OffloadStore {
    /**
     * Returns the concrete backend this store fronts (never {@code COMPOSITE}).
     *
     * @return the concrete backend descriptor
     */
    OffloadBackend backend();

    /**
     * Persists the payload; when {@code ttl} is non-null the backend should
     * honor the expiry (Redis {@code SET EX}, filesystem best-effort cleanup
     * by the Janitor).
     *
     * @param ns      sanitized namespace (tenant/user/session/context)
     * @param handle  unique offload handle
     * @param payload payload to persist
     * @param ttl     time-to-live, may be {@code null} for store defaults
     * @throws OffloadException with {@code OFFLOAD_STORE_IO} or
     *                          {@code OFFLOAD_REDIS_DOWN} on failure
     */
    void save(OffloadNamespace ns, String handle, OffloadPayload payload, Duration ttl) throws OffloadException;

    /**
     * Reads back the payload. Implementations throw
     * {@code OffloadException(OFFLOAD_MISSING)} when the entry does not
     * exist; integrity checking happens in the service layer.
     *
     * @param ns     sanitized namespace
     * @param handle unique offload handle
     * @return the loaded payload
     * @throws OffloadException {@code OFFLOAD_MISSING} when not found
     */
    OffloadPayload load(OffloadNamespace ns, String handle) throws OffloadException;

    /**
     * Idempotent delete.
     *
     * @param ns     sanitized namespace
     * @param handle unique offload handle
     * @return {@code true} when an entry existed and was removed
     */
    boolean delete(OffloadNamespace ns, String handle);

    /**
     * Checks whether the entry exists in this store.
     *
     * @param ns     sanitized namespace
     * @param handle unique offload handle
     * @return {@code true} when the entry exists
     */
    boolean exists(OffloadNamespace ns, String handle);
}
