/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

/**
 * Structured error codes surfaced to the model when an offload reload fails.
 *
 * <p>The ReloaderTool formats these as {@code OFFLOAD_MISSING: handle=<id>} style text,
 * without stack traces or server absolute paths. Mirrors the error taxonomy of
 * 《Context卸载加载补齐方案-v2》 §7.3.2 / §12.3.</p>
 *
 * @since 2026-01-01
 */
public enum OffloadErrorCode {
    /** Handle unknown to the catalog, or the entry is not published yet. */
    OFFLOAD_MISSING,
    /** Entry lived past its TTL (lazy check or Janitor). */
    OFFLOAD_EXPIRED,
    /** Payload sha256 mismatch or unparsable JSON. */
    OFFLOAD_CORRUPT,
    /** Entry existed but its backing store copy is gone. */
    OFFLOAD_GONE,
    /** Namespace (tenant/user/session/context) does not own the handle. */
    OFFLOAD_FORBIDDEN,
    /** Filesystem / generic store write-read failure. */
    OFFLOAD_STORE_IO,
    /** Redis backend unavailable. */
    OFFLOAD_REDIS_DOWN,
    /** Illegal state-machine transition (reserve/commit/abort misuse). */
    OFFLOAD_STATUS_INVALID,
    /** Request namespace does not match the stored namespace. */
    OFFLOAD_NAMESPACE_MISMATCH
}
