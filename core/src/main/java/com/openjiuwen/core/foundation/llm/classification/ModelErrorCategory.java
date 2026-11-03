/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

/**
 * Unified taxonomy of model call failure categories. Every model call failure
 * is reduced to exactly one of these categories before leaving the client
 * layer; there is no "unclassified" outcome. The set is stable: endpoint
 * specific quirks are absorbed by evolving classification rules, not by adding
 * new categories. Control flow (retry, circuit breaker, guardrails) must
 * switch on this type, never on numeric error codes.
 *
 * @since 2026-10-19
 */
public enum ModelErrorCategory {
    /**
     * Provider side rate limiting (HTTP 429). Retryable after a delay.
     */
    RATE_LIMITED,

    /**
     * Insufficient balance or quota (HTTP 402). Retry is pointless.
     */
    QUOTA_EXCEEDED,

    /**
     * Permanently invalid or revoked credentials (HTTP 401/403). Retry is pointless.
     */
    AUTH_FAILED,

    /**
     * Transient failure: 5xx, 408/409, network errors, call timeouts. Usually retryable.
     */
    TEMPORARY,

    /**
     * The caller cancelled the call. Never retried, never counted as a failure.
     */
    USER_CANCELLED,

    /**
     * Prompt or context rejected as too long. No plain retry; surfaced cleanly
     * for a later compression feature to consume.
     */
    CONTEXT_LENGTH_EXCEEDED
}
