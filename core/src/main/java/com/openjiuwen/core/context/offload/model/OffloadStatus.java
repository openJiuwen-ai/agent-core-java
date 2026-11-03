/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

/**
 * Catalog entry lifecycle states (架构文档 §3.1).
 *
 * <pre>
 * reserve → PENDING → commit → READY → markStatus → EXPIRED | CORRUPT | MISSING
 *                      abort   → (removed)
 * </pre>
 *
 * <p>PENDING entries are invisible to load paths (write-ahead publish);
 * transitions out of READY are one-way and audited by the service layer.</p>
 *
 * @since 2026-01-01
 */
public enum OffloadStatus {
    /** Reserved, store write in flight; not readable. */
    PENDING,
    /** Published and loadable. */
    READY,
    /** Past its TTL. */
    EXPIRED,
    /** sha256 mismatch or unparsable JSON. */
    CORRUPT,
    /** Backing store copy vanished (e.g. file deleted externally). */
    MISSING
}
