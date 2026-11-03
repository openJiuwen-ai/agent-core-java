/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import java.util.Objects;

/**
 * Final (ended) span record handed to trajectory consumers.
 *
 * <p>Carries the routing facts the swarm sink needs to partition a record by
 * session and execution subject, plus the lazily-encoded single-span OTLP
 * JSON payload for persistence.</p>
 *
 * @param traceId lower-case hex trace id, 32 characters
 * @param spanId lower-case hex span id, 16 characters
 * @param parentSpanId lower-case hex parent span id, 16 characters, or null for roots
 * @param sessionId session id taken from {@code gen_ai.conversation.id}, may be null
 * @param requestId stable request id, may be null
 * @param runId stable run id, may be null
 * @param agentMode swarm agent mode, may be null
 * @param executionSubjectId execution subject id, may be null
 * @param executionSubjectDisplayName execution subject display name, may be null
 * @param executionSubjectKind execution subject kind, may be null
 * @param executionSubjectParentId parent execution subject id, may be null
 * @param executionSubjectSessionId session id owning the execution subject, may be null
 * @param recordKind trajectory record kind, may be null
 * @param recordRevision monotonically increasing revision per span record
 * @param schemaVersion trajectory payload schema version
 * @param hasError true when the span ended with an error status
 * @param observedTimeUnixNano observation timestamp in Unix nanoseconds
 * @param otlpJson encoded single-span OTLP JSON document
 * @since 0.1.18
 */
public record OtlpSpanRecord(
        String traceId,
        String spanId,
        String parentSpanId,
        String sessionId,
        String requestId,
        String runId,
        String agentMode,
        String executionSubjectId,
        String executionSubjectDisplayName,
        String executionSubjectKind,
        String executionSubjectParentId,
        String executionSubjectSessionId,
        String recordKind,
        long recordRevision,
        String schemaVersion,
        boolean hasError,
        long observedTimeUnixNano,
        String otlpJson) {
    /**
     * Stable record id in the {@code <traceId>:<spanId>} form.
     *
     * @return the record id, never null
     */
    public String recordId() {
        return traceId + ":" + spanId;
    }

    /**
     * Lifecycle label stored by the swarm persistence layer.
     *
     * @return {@code final}
     */
    public String lifecycle() {
        return "final";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OtlpSpanRecord that)) {
            return false;
        }
        return recordRevision == that.recordRevision
                && hasError == that.hasError
                && observedTimeUnixNano == that.observedTimeUnixNano
                && Objects.equals(traceId, that.traceId)
                && Objects.equals(spanId, that.spanId)
                && Objects.equals(parentSpanId, that.parentSpanId)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(runId, that.runId)
                && Objects.equals(agentMode, that.agentMode)
                && Objects.equals(executionSubjectId, that.executionSubjectId)
                && Objects.equals(executionSubjectDisplayName, that.executionSubjectDisplayName)
                && Objects.equals(executionSubjectKind, that.executionSubjectKind)
                && Objects.equals(executionSubjectParentId, that.executionSubjectParentId)
                && Objects.equals(executionSubjectSessionId, that.executionSubjectSessionId)
                && Objects.equals(recordKind, that.recordKind)
                && Objects.equals(schemaVersion, that.schemaVersion)
                && Objects.equals(otlpJson, that.otlpJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, spanId, parentSpanId, sessionId, requestId, runId,
                agentMode, executionSubjectId, executionSubjectDisplayName, executionSubjectKind,
                executionSubjectParentId, executionSubjectSessionId, recordKind, recordRevision,
                schemaVersion, hasError, observedTimeUnixNano, otlpJson);
    }
}
