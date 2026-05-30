/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import java.util.*;

/**
 * A2A transformer — converts between openjiuwen and A2A formats.
 * <p>
 * Mirrors Python's {@code A2ATransformer} in
 * {@code openjiuwen.extensions.a2a.a2a_transformer}.
 */
public final class A2ATransformer {

    private A2ATransformer() {
    }

    /** Convert openjiuwen inputs to A2A request format. */
    public static Map<String, Object> toA2aRequest(Object request) {
        if (!(request instanceof Map<?, ?> rawRequest)) {
            String typeName = request == null ? "null" : request.getClass().getSimpleName();
            throw new IllegalArgumentException("request must be a Map, got " + typeName);
        }

        Map<String, Object> inputs = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawRequest.entrySet()) {
            inputs.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return toA2aRequest(inputs);
    }

    /** Convert openjiuwen inputs to A2A request format. */
    public static Map<String, Object> toA2aRequest(Map<String, Object> inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("request must be a Map, got null");
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("message_id", UUID.randomUUID().toString().replace("-", ""));
        message.put("role", "ROLE_USER");

        Object sessionId = inputs.get("sessionId");
        if (sessionId != null && !String.valueOf(sessionId).isBlank()) {
            message.put("context_id", String.valueOf(sessionId));
            message.put("task_id", String.valueOf(sessionId));
        }

        Object query = inputs.get("query");
        if (query != null) {
            message.put("parts", List.of(Map.of("text", String.valueOf(query))));
        } else {
            message.put("parts", List.of());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            if (!"query".equals(entry.getKey()) && !"sessionId".equals(entry.getKey()) && entry.getValue() != null) {
                metadata.put(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", message);
        if (!metadata.isEmpty()) {
            request.put("metadata", metadata);
        }
        return request;
    }

    /** Convert A2A response to openjiuwen result format. */
    public static Map<String, Object> fromA2aResponse(Map<String, Object> response) {
        if (response == null) {
            return buildAgentResult(null, null, "completed", List.of(), Map.of());
        }
        if (response.get("artifact_update") instanceof Map<?, ?> artifactUpdate) {
            Map<String, Object> event = stringifyKeys(artifactUpdate);
            Object artifact = event.get("artifact");
            List<Map<String, Object>> artifacts = artifact instanceof Map<?, ?> map
                    ? List.of(normalizeArtifact(stringifyKeys(map)))
                    : List.of();
            return buildAgentResult(
                    stringOrNull(event.get("task_id")),
                    stringOrNull(event.get("context_id")),
                    "working",
                    artifacts,
                    mapOrEmpty(event.get("metadata")));
        }
        if (response.get("status_update") instanceof Map<?, ?> statusUpdate) {
            Map<String, Object> event = stringifyKeys(statusUpdate);
            return buildAgentResult(
                    stringOrNull(event.get("task_id")),
                    stringOrNull(event.get("context_id")),
                    toOjwStatus(extractState(event.get("status"))),
                    List.of(),
                    mapOrEmpty(event.get("metadata")));
        }
        if (response.get("task") instanceof Map<?, ?> task) {
            return taskToResult(stringifyKeys(task));
        }
        if (response.get("message") instanceof Map<?, ?> message) {
            return messageToResult(stringifyKeys(message));
        }
        if (response.containsKey("artifacts") || response.containsKey("status")) {
            return taskToResult(response);
        }
        if (response.containsKey("parts") || response.containsKey("task_id")) {
            return messageToResult(response);
        }
        return buildAgentResult(null, null, "completed", List.of(), Map.of());
    }

    private static Map<String, Object> messageToResult(Map<String, Object> message) {
        List<Map<String, Object>> parts = normalizeParts(message.get("parts"));
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("artifactId", "message");
        artifact.put("parts", parts);
        artifact.put("metadata", Map.of());
        return buildAgentResult(
                stringOrNull(message.get("task_id")),
                stringOrNull(message.get("context_id")),
                "completed",
                List.of(artifact),
                mapOrEmpty(message.get("metadata")));
    }

    private static Map<String, Object> taskToResult(Map<String, Object> task) {
        return buildAgentResult(
                stringOrNull(task.getOrDefault("id", task.get("task_id"))),
                stringOrNull(task.get("context_id")),
                toOjwStatus(extractState(task.get("status"))),
                normalizeArtifacts(task.get("artifacts")),
                mapOrEmpty(task.get("metadata")));
    }

    private static Map<String, Object> buildAgentResult(
            String taskId,
            String sessionId,
            String status,
            List<Map<String, Object>> artifacts,
            Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", taskId);
        result.put("sessionId", sessionId);
        result.put("status", status);
        result.put("artifacts", artifacts == null ? List.of() : artifacts);
        result.put("metadata", metadata == null ? Map.of() : metadata);
        return result;
    }

    private static List<Map<String, Object>> normalizeArtifacts(Object rawArtifacts) {
        if (!(rawArtifacts instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> artifacts = new ArrayList<>();
        for (Object artifact : collection) {
            if (artifact instanceof Map<?, ?> map) {
                artifacts.add(normalizeArtifact(stringifyKeys(map)));
            }
        }
        return artifacts;
    }

    private static Map<String, Object> normalizeArtifact(Map<String, Object> artifact) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("artifactId", stringOrNull(artifact.getOrDefault("artifact_id", artifact.get("artifactId"))));
        normalized.put("name", stringOrNull(artifact.get("name")));
        normalized.put("description", stringOrNull(artifact.get("description")));
        normalized.put("parts", normalizeParts(artifact.get("parts")));
        normalized.put("metadata", mapOrEmpty(artifact.get("metadata")));
        return normalized;
    }

    private static List<Map<String, Object>> normalizeParts(Object rawParts) {
        if (!(rawParts instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        for (Object part : collection) {
            if (part instanceof Map<?, ?> map) {
                parts.add(stringifyKeys(map));
            }
        }
        return parts;
    }

    private static String extractState(Object status) {
        if (status instanceof Map<?, ?> map) {
            Object state = stringifyKeys(map).get("state");
            return state == null ? null : String.valueOf(state);
        }
        return status == null ? null : String.valueOf(status);
    }

    private static String toOjwStatus(String state) {
        if (state == null || state.isBlank()) {
            return "unknown";
        }
        return switch (state) {
            case "TASK_STATE_SUBMITTED", "submitted" -> "submitted";
            case "TASK_STATE_WORKING", "working" -> "working";
            case "TASK_STATE_COMPLETED", "completed" -> "completed";
            case "TASK_STATE_FAILED", "TASK_STATE_REJECTED", "failed" -> "failed";
            case "TASK_STATE_CANCELED", "canceled" -> "canceled";
            case "TASK_STATE_INPUT_REQUIRED", "TASK_STATE_AUTH_REQUIRED", "input-required" -> "input-required";
            default -> "unknown";
        };
    }

    private static Map<String, Object> mapOrEmpty(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringifyKeys(map);
        }
        return Map.of();
    }

    private static Map<String, Object> stringifyKeys(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
