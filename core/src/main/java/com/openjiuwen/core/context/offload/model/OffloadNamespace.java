/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.regex.Pattern;

/**
 * Four-level ownership identity of an offload entry
 * (tenant/user/session/context).
 *
 * <p>Namespace segments are sanitized to {@code [A-Za-z0-9._-]} before they
 * reach storage keys or filesystem paths to prevent path traversal
 * (共享约定 7). The namespace is always built from the server-side session
 * context, never from model input.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OffloadNamespace(
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("context_id") String contextId) {

    /** Characters allowed inside a single namespace segment. */
    private static final Pattern ILLEGAL_SEGMENT_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    /** Substitute for empty segments after sanitization. */
    private static final String SANITIZED_EMPTY = "_";

    /**
     * Factory normalizing {@code null}/blank segments to empty strings.
     *
     * @param tenantId  tenant identifier
     * @param userId    user identifier
     * @param sessionId session identifier
     * @param contextId context identifier
     * @return a new {@link OffloadNamespace} with normalized segments
     */
    public static OffloadNamespace of(String tenantId, String userId, String sessionId, String contextId) {
        return new OffloadNamespace(
                normalize(tenantId),
                normalize(userId),
                normalize(sessionId),
                normalize(contextId));
    }

    /**
     * Redis storage key: {@code offload:{tenant}:{user}:{session}:{context}:{handle}}.
     *
     * @param handle the entry handle appended as the final key segment
     * @return the fully-qualified Redis key string
     */
    public String toRedisKey(String handle) {
        OffloadNamespace safe = sanitized();
        return "offload:" + safe.tenantId + ":" + safe.userId + ":" + safe.sessionId + ":"
                + safe.contextId + ":" + handle;
    }

    /**
     * Filesystem directory name: {@code {tenant}/{user}/{session}_context}
     * (files live under {@code .../offload/{algorithmId}_{handle}.json}).
     *
     * @return the sanitized filesystem directory path
     */
    public String toFsDirName() {
        OffloadNamespace safe = sanitized();
        return safe.tenantId + "/" + safe.userId + "/" + safe.sessionId + "_context";
    }

    /**
     * Compares this namespace against another segment by segment, treating
     * {@code null} references as a non-match.
     *
     * @param other the namespace to compare against, may be {@code null}
     * @return {@code true} when all four segments are equal; {@code null}
     * never matches.
     */
    public boolean matches(OffloadNamespace other) {
        return other != null
                && stringEquals(tenantId, other.tenantId)
                && stringEquals(userId, other.userId)
                && stringEquals(sessionId, other.sessionId)
                && stringEquals(contextId, other.contextId);
    }

    /**
     * Returns a defensive copy of this namespace whose segments contain only
     * {@code [A-Za-z0-9._-]} characters, neutralizing path-traversal hazards.
     *
     * @return a copy whose segments contain only {@code [A-Za-z0-9._-]};
     * empty or fully-stripped segments become {@code "_"}.
     */
    public OffloadNamespace sanitized() {
        return new OffloadNamespace(
                sanitizeSegment(tenantId),
                sanitizeSegment(userId),
                sanitizeSegment(sessionId),
                sanitizeSegment(contextId));
    }

    private static String sanitizeSegment(String segment) {
        String normalized = normalize(segment);
        if (normalized.isEmpty()) {
            return SANITIZED_EMPTY;
        }
        String stripped = ILLEGAL_SEGMENT_CHARS.matcher(normalized).replaceAll("_");
        // "." / ".." consist solely of legal charset characters but are path
        // traversal hazards when used as a directory segment — neutralize them.
        if (stripped.isEmpty() || ".".equals(stripped) || "..".equals(stripped)) {
            return SANITIZED_EMPTY;
        }
        return stripped;
    }

    private static String normalize(String segment) {
        return segment == null ? "" : segment.trim();
    }

    private static boolean stringEquals(String left, String right) {
        String safeLeft = left == null ? "" : left;
        String safeRight = right == null ? "" : right;
        return safeLeft.equals(safeRight);
    }
}
