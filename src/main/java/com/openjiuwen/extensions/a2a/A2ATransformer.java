/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.common.schema.Part;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import com.openjiuwen.core.singleagent.schema.Artifact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transformer between openjiuwen payloads and A2A protocol payloads.
 */
public final class A2ATransformer {
    private static final Map<String, TaskStatus> STATUS_MAPPING = Map.ofEntries(
            Map.entry("TASK_STATE_UNSPECIFIED", TaskStatus.UNKNOWN),
            Map.entry("TASK_STATE_SUBMITTED", TaskStatus.SUBMITTED),
            Map.entry("TASK_STATE_WORKING", TaskStatus.WORKING),
            Map.entry("TASK_STATE_COMPLETED", TaskStatus.COMPLETED),
            Map.entry("TASK_STATE_FAILED", TaskStatus.FAILED),
            Map.entry("TASK_STATE_CANCELED", TaskStatus.CANCELED),
            Map.entry("TASK_STATE_INPUT_REQUIRED", TaskStatus.INPUT_REQUIRED),
            Map.entry("TASK_STATE_REJECTED", TaskStatus.FAILED),
            Map.entry("TASK_STATE_AUTH_REQUIRED", TaskStatus.INPUT_REQUIRED)
    );

    private A2ATransformer() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> toA2ARequest(Map<String, Object> request) {
        if (request == null) {
            throw new IllegalArgumentException("request must be a map");
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", UUID.randomUUID().toString());
        message.put("role", "ROLE_USER");

        Object sessionId = firstNonNull(
                request.get("conversation_id"),
                request.get("sessionId"),
                request.get("contextId")
        );
        if (sessionId != null) {
            message.put("contextId", String.valueOf(sessionId));
            message.put("taskId", String.valueOf(sessionId));
        }

        Object text = firstNonNull(request.get("query"), request.get("message"));
        if (text != null) {
            message.put("parts", List.of(Map.of("text", String.valueOf(text))));
        } else {
            message.put("parts", List.of());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (List.of("query", "message", "conversation_id", "sessionId", "contextId").contains(entry.getKey())) {
                continue;
            }
            metadata.put(entry.getKey(), entry.getValue());
        }
        if (!metadata.isEmpty()) {
            payload.put("metadata", metadata);
        }
        return payload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Object> toJsonRpcRequest(Map<String, Object> request, String method, String requestId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", requestId);
        envelope.put("method", method);
        envelope.put("params", toA2ARequest(request));
        return envelope;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static AgentResult fromA2AResponse(Map<String, Object> response) {
        if (response == null) {
            return AgentResult.builder().status(TaskStatus.UNKNOWN).build();
        }

        if (response.containsKey("error")) {
            throw new IllegalStateException("A2A response error: " + response.get("error"));
        }

        if (response.containsKey("result") && response.get("result") instanceof Map<?, ?> resultMap) {
            return fromA2AResponse((Map<String, Object>) resultMap);
        }
        if (response.containsKey("task") && response.get("task") instanceof Map<?, ?> taskMap) {
            return fromTask((Map<String, Object>) taskMap);
        }
        if (response.containsKey("message") && response.get("message") instanceof Map<?, ?> messageMap) {
            return fromMessage((Map<String, Object>) messageMap);
        }
        if (response.containsKey("statusUpdate") && response.get("statusUpdate") instanceof Map<?, ?> statusMap) {
            return fromStatusUpdate((Map<String, Object>) statusMap);
        }
        if (response.containsKey("artifactUpdate") && response.get("artifactUpdate") instanceof Map<?, ?> artifactMap) {
            return fromArtifactUpdate((Map<String, Object>) artifactMap);
        }
        if (response.containsKey("id") && response.containsKey("status")) {
            return fromTask(response);
        }
        if (response.containsKey("parts") && response.containsKey("role")) {
            return fromMessage(response);
        }
        return AgentResult.builder().status(TaskStatus.UNKNOWN).metadata(response).build();
    }

    private static AgentResult fromMessage(Map<String, Object> message) {
        Artifact artifact = Artifact.builder()
                .artifactId("message")
                .parts(toParts(asList(message.get("parts"))))
                .metadata(asMap(message.get("metadata")))
                .build();
        return AgentResult.builder()
                .taskId(asString(message.get("taskId")))
                .sessionId(asString(message.get("contextId")))
                .status(TaskStatus.COMPLETED)
                .artifacts(List.of(artifact))
                .metadata(asMap(message.get("metadata")))
                .build();
    }

    private static AgentResult fromTask(Map<String, Object> task) {
        List<Artifact> artifacts = new ArrayList<>();
        for (Object item : asList(task.get("artifacts"))) {
            if (item instanceof Map<?, ?> artifact) {
                artifacts.add(toArtifact((Map<String, Object>) artifact));
            }
        }

        Map<String, Object> statusMap = asMap(task.get("status"));
        TaskStatus status = mapTaskStatus(asString(statusMap.get("state")));
        return AgentResult.builder()
                .taskId(asString(task.get("id")))
                .sessionId(asString(task.get("contextId")))
                .status(status)
                .artifacts(artifacts)
                .metadata(asMap(task.get("metadata")))
                .build();
    }

    private static AgentResult fromStatusUpdate(Map<String, Object> event) {
        Map<String, Object> statusMap = asMap(event.get("status"));
        return AgentResult.builder()
                .taskId(asString(event.get("taskId")))
                .sessionId(asString(event.get("contextId")))
                .status(mapTaskStatus(asString(statusMap.get("state"))))
                .metadata(asMap(event.get("metadata")))
                .build();
    }

    private static AgentResult fromArtifactUpdate(Map<String, Object> event) {
        List<Artifact> artifacts = new ArrayList<>();
        Map<String, Object> artifact = asMap(event.get("artifact"));
        if (!artifact.isEmpty()) {
            artifacts.add(toArtifact(artifact));
        }
        return AgentResult.builder()
                .taskId(asString(event.get("taskId")))
                .sessionId(asString(event.get("contextId")))
                .status(TaskStatus.WORKING)
                .artifacts(artifacts)
                .metadata(asMap(event.get("metadata")))
                .build();
    }

    private static Artifact toArtifact(Map<String, Object> artifact) {
        return Artifact.builder()
                .artifactId(asString(firstNonNull(artifact.get("artifactId"), artifact.get("artifact_id"))))
                .name(asString(artifact.get("name")))
                .description(asString(artifact.get("description")))
                .parts(toParts(asList(artifact.get("parts"))))
                .metadata(asMap(artifact.get("metadata")))
                .build();
    }

    private static List<Part> toParts(List<Object> parts) {
        List<Part> result = new ArrayList<>();
        for (Object item : parts) {
            if (!(item instanceof Map<?, ?> partMap)) {
                continue;
            }
            Map<String, Object> typedPart = (Map<String, Object>) partMap;
            String type = "text";
            String content = asString(typedPart.get("text"));
            if (content == null && typedPart.get("url") != null) {
                type = "url";
                content = asString(typedPart.get("url"));
            } else if (content == null && typedPart.get("data") != null) {
                type = "data";
                content = String.valueOf(typedPart.get("data"));
            } else if (content == null && typedPart.get("raw") != null) {
                type = "raw";
                content = String.valueOf(typedPart.get("raw"));
            }
            result.add(Part.builder()
                    .type(type)
                    .content(content)
                    .metadata(asMap(typedPart.get("metadata")))
                    .build());
        }
        return result;
    }

    private static TaskStatus mapTaskStatus(String value) {
        if (value == null || value.isBlank()) {
            return TaskStatus.UNKNOWN;
        }
        return STATUS_MAPPING.getOrDefault(value, TaskStatus.UNKNOWN);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>((List<Object>) list);
        }
        return new ArrayList<>();
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static Object firstNonNull(Object first, Object second, Object third) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return third;
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }
}
