/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Protocol converter for the Ascend affinity {@code agent_hint} wire format.
 *
 * <p>Mirrors the Python {@code OpenAIModelClient} kv-cache helpers in
 * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}:
 * hint building, KV target edit validation, and range validation. Validation
 * failures fail closed with {@code MODEL_CONFIG_ERROR}; an invalid hint never
 * reaches the gateway.</p>
 *
 * @since 0.1.16
 */
public final class AgentHint {
    /** Request body field carrying the affinity hint. */
    public static final String FIELD_NAME = "agent_hint";

    /** Supported management actions. */
    public static final java.util.Set<String> KV_ACTIONS = java.util.Set.of("evict", "offload", "prefetch");

    /** Supported management targets. */
    public static final java.util.Set<String> KV_TARGETS = java.util.Set.of("session", "messages", "tools");

    private AgentHint() {
    }

    /**
     * Wire-level range parameters for one KV-cache management edit.
     *
     * @param target KV target name ({@code session} / {@code messages} / {@code tools})
     * @param msgStart message range start, inclusive
     * @param msgEnd message range end, exclusive
     * @param toolsStart tools range start, inclusive
     * @param toolsEnd tools range end, exclusive
     * @param shouldIncludeTools whether a messages edit also carries a tools edit
     * @since 0.1.16
     */
    public record KvCacheRange(String target, Integer msgStart, Integer msgEnd,
            Integer toolsStart, Integer toolsEnd, boolean shouldIncludeTools) {
    }

    /**
     * Build the identity-only hint used on normal inference requests.
     *
     * @param sessionId lineage cache id, required
     * @param parentSessionId lineage parent, falls back to {@code sessionId}
     * @return hint payload map
     */
    public static Map<String, Object> identityHint(String sessionId, String parentSessionId) {
        if (isBlank(sessionId)) {
            throw error("session_id is required");
        }
        if (isBlank(parentSessionId)) {
            throw error("parent_session_id is required");
        }
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("session_id", sessionId);
        hint.put("parent_session_id", parentSessionId);
        return hint;
    }

    /**
     * Build the management hint carrying {@code context_management.edits}.
     *
     * @param action KV action name
     * @param sessionId lineage cache id, required
     * @param parentSessionId lineage parent, falls back to {@code sessionId}
     * @param range target and range parameters for the edits
     * @return hint payload map
     */
    public static Map<String, Object> managementHint(
            String action, String sessionId, String parentSessionId, KvCacheRange range) {
        if (isBlank(sessionId)) {
            throw error("session_id is required");
        }
        if (isBlank(parentSessionId)) {
            throw error("parent_session_id is required");
        }
        Map<String, Object> hint = identityHint(sessionId, parentSessionId);
        Map<String, Object> contextManagement = new LinkedHashMap<>();
        contextManagement.put("manage_request", true);
        contextManagement.put("edits", buildKvTargetEdits(action, range));
        hint.put("context_management", contextManagement);
        return hint;
    }

    /**
     * Validate action/target and build the edits list.
     *
     * @param action KV action name
     * @param range target and range parameters for the edits
     * @return list of edit objects for {@code context_management.edits}
     */
    public static java.util.List<Map<String, Object>> buildKvTargetEdits(
            String action, KvCacheRange range) {
        validateKvActionTarget(action, range.target());
        if ("session".equals(range.target())) {
            if (hasAnyRange(range.msgStart(), range.msgEnd(), range.toolsStart(), range.toolsEnd())) {
                throw error("target=session does not accept message/tool ranges");
            }
            if (range.shouldIncludeTools()) {
                throw error("target=session does not accept include_tools=True");
            }
            Map<String, Object> edit = new LinkedHashMap<>();
            edit.put("type", action);
            edit.put("target", "session");
            return List.of(edit);
        }
        if ("messages".equals(range.target())) {
            Map<String, Object> edit = kvRangeEdit(action, "messages", range.msgStart(), range.msgEnd());
            if (range.shouldIncludeTools()) {
                return List.of(edit, kvRangeEdit(action, "tools", range.toolsStart(), range.toolsEnd()));
            }
            if (hasAnyRange(range.toolsStart(), range.toolsEnd())) {
                throw error("tools range requires include_tools=True or target=tools");
            }
            return List.of(edit);
        }
        if (range.shouldIncludeTools()) {
            throw error("target=tools should not also set include_tools=True");
        }
        if (hasAnyRange(range.msgStart(), range.msgEnd())) {
            throw error("messages range is invalid for target=tools");
        }
        return List.of(kvRangeEdit(action, "tools", range.toolsStart(), range.toolsEnd()));
    }

    /**
     * Normalize assistant tool_calls for affinity gateways.
     *
     * @param messages converted request messages
     * @return the same list with assistant tool_calls sanitized in place
     */
    public static java.util.List<Map<String, Object>> sanitizeToolCalls(
            java.util.List<Map<String, Object>> messages) {
        for (Map<String, Object> message : messages) {
            if (!"assistant".equals(message.get("role"))) {
                continue;
            }
            Object rawToolCalls = message.get("tool_calls");
            if (!(rawToolCalls instanceof java.util.List<?> toolCalls)) {
                continue;
            }
            java.util.List<Map<String, Object>> cleaned = new java.util.ArrayList<>();
            for (Object rawToolCall : toolCalls) {
                if (!(rawToolCall instanceof Map<?, ?> toolCall)) {
                    continue;
                }
                Map<?, ?> functionMap = toolCall.get("function") instanceof Map<?, ?> fnMap ? fnMap : Map.of();
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", functionMap.get("name") == null ? "" : functionMap.get("name"));
                function.put("arguments", functionMap.get("arguments") == null ? "" : functionMap.get("arguments"));
                Map<String, Object> cleanedCall = new LinkedHashMap<>();
                cleanedCall.put("id", toolCall.get("id") == null ? "" : toolCall.get("id"));
                cleanedCall.put("type", "function");
                cleanedCall.put("index", toolCall.get("index"));
                cleanedCall.put("function", function);
                cleaned.add(cleanedCall);
            }
            message.put("tool_calls", cleaned);
        }
        return messages;
    }

    /**
     * Build the invoke kwargs ({@code session_id}/{@code parent_session_id})
     * used both for hint encoding and runtime accounting.
     *
     * @param sessionId resolved lineage session id
     * @param parentSessionId resolved lineage parent, may be {@code null}
     * @return kwargs map
     */
    public static Map<String, Object> buildInvokeKwargs(String sessionId, String parentSessionId) {
        if (isBlank(sessionId)) {
            throw error("session_id is required when KV cache affinity is enabled");
        }
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("session_id", sessionId);
        kwargs.put("parent_session_id", isBlank(parentSessionId) ? sessionId : parentSessionId);
        return kwargs;
    }

    private static Map<String, Object> kvRangeEdit(String action, String target, Integer start, Integer end) {
        if (start == null || end == null) {
            throw error("target=" + target + " requires both start and end");
        }
        if (start < 0 || end < 0) {
            throw error("target=" + target + " range must be non-negative");
        }
        if (start >= end) {
            throw error("target=" + target + " half-open range requires start < end");
        }
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("type", action);
        edit.put("target", target);
        edit.put("start", start);
        edit.put("end", end);
        return edit;
    }

    private static void validateKvActionTarget(String action, String target) {
        if (!KV_ACTIONS.contains(action)) {
            throw error("unsupported KV affinity action: " + action);
        }
        if (!KV_TARGETS.contains(target)) {
            throw error("unsupported KV affinity target: " + target);
        }
    }

    private static boolean hasAnyRange(Integer... ranges) {
        for (Integer range : ranges) {
            if (range != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.strip().isEmpty();
    }

    private static RuntimeException error(String message) {
        return com.openjiuwen.core.common.exception.ErrorHelper.buildError(
                com.openjiuwen.core.common.exception.StatusCode.MODEL_CONFIG_ERROR,
                "error_msg",
                "[openjiuwen kv_cache] " + message);
    }

    /**
     * Convenience wrapper returning a completed false for unsupported
     * action attempts on plain clients.
     *
     * @param action action name for diagnostics
     * @return failed future carrying the protocol error
     */
    public static CompletableFuture<Boolean> failedAction(String action) {
        CompletableFuture<Boolean> failed = new CompletableFuture<>();
        failed.completeExceptionally(error("KV cache action " + action + " is not supported by this client"));
        return failed;
    }
}
