/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import com.openjiuwen.core.singleagent.schema.Artifact;
import com.openjiuwen.core.singleagent.schema.Part;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal transformer between openjiuwen payloads and A2A payloads.
 *
 * <p>Mirrors Python's {@code A2ATransformer} in
 * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.</p>
 */
public final class A2ATransformer {
    private static final Map<String, TaskStatus> A2A_STATUS_TO_OJW_STATUS = Map.ofEntries(
            Map.entry("TASK_STATE_UNSPECIFIED", TaskStatus.UNKNOWN),
            Map.entry("TASK_STATE_SUBMITTED", TaskStatus.SUBMITTED),
            Map.entry("TASK_STATE_WORKING", TaskStatus.WORKING),
            Map.entry("TASK_STATE_COMPLETED", TaskStatus.COMPLETED),
            Map.entry("TASK_STATE_FAILED", TaskStatus.FAILED),
            Map.entry("TASK_STATE_CANCELED", TaskStatus.CANCELED),
            Map.entry("TASK_STATE_INPUT_REQUIRED", TaskStatus.INPUT_REQUIRED),
            Map.entry("TASK_STATE_REJECTED", TaskStatus.FAILED),
            Map.entry("TASK_STATE_AUTH_REQUIRED", TaskStatus.INPUT_REQUIRED),
            Map.entry("submitted", TaskStatus.SUBMITTED),
            Map.entry("working", TaskStatus.WORKING),
            Map.entry("completed", TaskStatus.COMPLETED),
            Map.entry("failed", TaskStatus.FAILED),
            Map.entry("canceled", TaskStatus.CANCELED),
            Map.entry("input-required", TaskStatus.INPUT_REQUIRED),
            Map.entry("unknown", TaskStatus.UNKNOWN)
    );

    private A2ATransformer() {
    }

    public static SendMessageRequest toA2aRequest(Object request) {
        if (!(request instanceof Map<?, ?> rawRequest)) {
            String typeName = request == null ? "null" : request.getClass().getSimpleName();
            throw new IllegalArgumentException("request must be a dict, got " + typeName);
        }
        return toA2aRequest(stringifyKeys(rawRequest));
    }

    public static SendMessageRequest to_a2a_request(Object request) {
        return toA2aRequest(request);
    }

    public static SendMessageRequest toA2aRequest(Map<String, Object> request) {
        if (request == null) {
            throw new IllegalArgumentException("request must be a dict, got null");
        }

        A2aMessage message = new A2aMessage();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setRole(A2aRole.ROLE_USER);

        Object sessionId = firstNonNull(request.get("conversation_id"), request.get("sessionId"));
        if (isTruthy(sessionId)) {
            message.setContextId(String.valueOf(sessionId));
        }

        Object query = request.get("query");
        if (query != null) {
            A2aPart part = new A2aPart();
            part.setText(String.valueOf(query));
            message.getParts().add(part);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : request.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!"query".equals(key)
                    && !"sessionId".equals(key)
                    && !"conversation_id".equals(key)
                    && value != null) {
                metadata.put(key, value);
            }
        }
        if (!metadata.isEmpty()) {
            message.setMetadata(metadata);
        }

        SendMessageRequest sendRequest = new SendMessageRequest();
        sendRequest.setMessage(message);
        return sendRequest;
    }

    public static Map<String, Object> fromA2aRequest(Object request) {
        if (request instanceof Map<?, ?> map) {
            return dictRequestToPayload(stringifyKeys(map));
        }
        if (request instanceof RequestContext context) {
            return requestContextToPayload(context);
        }
        if (request instanceof SendMessageRequest sendRequest) {
            return sendMessageRequestToPayload(sendRequest);
        }
        if (request instanceof A2aMessage message) {
            return messageToPayload(message);
        }
        return new LinkedHashMap<>();
    }

    public static Map<String, Object> from_a2a_request(Object request) {
        return fromA2aRequest(request);
    }

    public static AgentResult fromA2aResponse(Object response) {
        if (response instanceof List<?> tuple && tuple.size() == 2) {
            Object streamResponse = tuple.get(0);
            Object task = tuple.get(1);
            Object artifactUpdate = fieldValue(streamResponse, "artifact_update", "artifactUpdate");
            if (hasField(streamResponse, "artifact_update", "artifactUpdate")) {
                return a2aArtifactUpdateToResult(artifactUpdate);
            }
            Object statusUpdate = fieldValue(streamResponse, "status_update", "statusUpdate");
            if (hasField(streamResponse, "status_update", "statusUpdate")) {
                return a2aStatusUpdateToResult(statusUpdate);
            }
            Object message = fieldValue(streamResponse, "message");
            if (hasField(streamResponse, "message")) {
                return a2aMessageToResult(message);
            }
            Object responseTask = fieldValue(streamResponse, "task");
            if (hasField(streamResponse, "task")) {
                return a2aTaskToResult(responseTask);
            }
            if (task != null) {
                return a2aTaskToResult(task);
            }
            return buildAgentResult(null, null, TaskStatus.COMPLETED, List.of(), Map.of());
        }

        if (response instanceof TaskArtifactUpdateEvent event) {
            return a2aArtifactUpdateToResult(event);
        }
        if (response instanceof TaskStatusUpdateEvent event) {
            return a2aStatusUpdateToResult(event);
        }
        if (response instanceof A2aMessage message) {
            return a2aMessageToResult(message);
        }
        if (response instanceof A2aTask task) {
            return a2aTaskToResult(task);
        }
        if (response instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = stringifyKeys(rawMap);
            if (map.containsKey("artifact_update")) {
                return a2aArtifactUpdateToResult(map.get("artifact_update"));
            }
            if (map.containsKey("status_update")) {
                return a2aStatusUpdateToResult(map.get("status_update"));
            }
            if (map.containsKey("message")) {
                return a2aMessageToResult(map.get("message"));
            }
            if (map.containsKey("task")) {
                return a2aTaskToResult(map.get("task"));
            }
            if (map.containsKey("artifacts") || map.containsKey("status")) {
                return a2aTaskToResult(map);
            }
            if (map.containsKey("parts") || map.containsKey("task_id")) {
                return a2aMessageToResult(map);
            }
        }

        Object artifactUpdate = fieldValue(response, "artifact_update", "artifactUpdate");
        if (hasField(response, "artifact_update", "artifactUpdate")) {
            return a2aArtifactUpdateToResult(artifactUpdate);
        }
        Object statusUpdate = fieldValue(response, "status_update", "statusUpdate");
        if (hasField(response, "status_update", "statusUpdate")) {
            return a2aStatusUpdateToResult(statusUpdate);
        }
        Object message = fieldValue(response, "message");
        if (hasField(response, "message")) {
            return a2aMessageToResult(message);
        }
        Object task = fieldValue(response, "task");
        if (hasField(response, "task")) {
            return a2aTaskToResult(task);
        }

        return buildAgentResult(null, null, TaskStatus.COMPLETED, List.of(), Map.of());
    }

    public static AgentResult from_a2a_response(Object response) {
        return fromA2aResponse(response);
    }

    public static A2aPart toA2aPart(Part part) {
        Objects.requireNonNull(part, "part");
        A2aPart a2aPart = new A2aPart();
        if (part.getText() != null) {
            a2aPart.setText(part.getText());
        }
        if (part.getRaw() != null) {
            a2aPart.setRaw(part.getRaw());
        }
        if (part.getUrl() != null) {
            a2aPart.setUrl(part.getUrl());
        }
        if (part.getData() != null) {
            Object data = part.getData();
            if (data instanceof Map<?, ?> map) {
                a2aPart.setData(stringifyKeys(map));
            } else {
                a2aPart.setData(String.valueOf(data));
            }
        }
        if (part.getFilename() != null) {
            a2aPart.setFilename(part.getFilename());
        }
        if (part.getMediaType() != null) {
            a2aPart.setMediaType(part.getMediaType());
        }
        if (!part.getMetadata().isEmpty()) {
            a2aPart.setMetadata(part.getMetadata());
        }
        return a2aPart;
    }

    public static A2aPart to_a2a_part(Part part) {
        return toA2aPart(part);
    }

    private static AgentResult a2aMessageToResult(Object rawMessage) {
        A2aMessage message = coerceMessage(rawMessage);
        Artifact artifact = new Artifact();
        artifact.setArtifactId("message");
        List<Part> parts = new ArrayList<>();
        for (A2aPart part : message.getParts()) {
            parts.add(a2aPartToPart(part));
        }
        artifact.setParts(parts);
        artifact.setMetadata(Map.of());
        return buildAgentResult(
                nullIfBlank(message.getTaskId()),
                nullIfBlank(message.getContextId()),
                TaskStatus.COMPLETED,
                List.of(artifact),
                message.getMetadata());
    }

    private static AgentResult a2aTaskToResult(Object rawTask) {
        A2aTask task = coerceTask(rawTask);
        List<Artifact> artifacts = new ArrayList<>();
        for (A2aArtifact artifact : task.getArtifacts()) {
            artifacts.add(a2aArtifactToArtifact(artifact));
        }
        return buildAgentResult(
                nullIfBlank(task.getId()),
                nullIfBlank(task.getContextId()),
                toOjwStatus(task.getStatus() == null ? null : task.getStatus().getState()),
                artifacts,
                task.getMetadata());
    }

    private static AgentResult a2aStatusUpdateToResult(Object rawEvent) {
        TaskStatusUpdateEvent event = coerceStatusUpdate(rawEvent);
        return buildAgentResult(
                nullIfBlank(event.getTaskId()),
                nullIfBlank(event.getContextId()),
                toOjwStatus(event.getStatus() == null ? null : event.getStatus().getState()),
                List.of(),
                event.getMetadata());
    }

    private static AgentResult a2aArtifactUpdateToResult(Object rawEvent) {
        TaskArtifactUpdateEvent event = coerceArtifactUpdate(rawEvent);
        List<Artifact> artifacts = event.getArtifact() == null
                ? List.of()
                : List.of(a2aArtifactToArtifact(event.getArtifact()));
        return buildAgentResult(
                nullIfBlank(event.getTaskId()),
                nullIfBlank(event.getContextId()),
                TaskStatus.WORKING,
                artifacts,
                event.getMetadata());
    }

    private static Artifact a2aArtifactToArtifact(A2aArtifact artifact) {
        Artifact result = new Artifact();
        result.setArtifactId(nullIfBlank(artifact.getArtifactId()));
        result.setName(nullIfBlank(artifact.getName()));
        result.setDescription(nullIfBlank(artifact.getDescription()));
        List<Part> parts = new ArrayList<>();
        for (A2aPart part : artifact.getParts()) {
            parts.add(a2aPartToPart(part));
        }
        result.setParts(parts);
        result.setMetadata(artifact.getMetadata());
        return result;
    }

    private static Part a2aPartToPart(A2aPart part) {
        Part result = new Part();
        result.setText(nullIfBlank(part.getText()));
        byte[] raw = part.getRaw();
        if (raw != null && raw.length > 0) {
            result.setRaw(raw);
        }
        result.setUrl(nullIfBlank(part.getUrl()));
        if (part.isDataSet()) {
            result.setData(String.valueOf(part.getData()));
        }
        result.setFilename(nullIfBlank(part.getFilename()));
        result.setMediaType(nullIfBlank(part.getMediaType()));
        result.setMetadata(part.getMetadata());
        return result;
    }

    private static Map<String, Object> dictRequestToPayload(Map<String, Object> request) {
        Map<String, Object> payload = new LinkedHashMap<>(request);
        Object metadata = payload.remove("metadata");
        return mergeMetadata(payload, metadata instanceof Map<?, ?> map ? stringifyKeys(map) : null);
    }

    private static Map<String, Object> requestContextToPayload(RequestContext request) {
        Map<String, Object> payload;
        if (request.getMessage() != null) {
            payload = messageToPayload(request.getMessage());
        } else if (request.getTask() != null) {
            payload = agentResultToPayload(fromA2aResponse(request.getTask()));
        } else {
            payload = new LinkedHashMap<>();
        }
        if (isTruthy(request.getTaskId()) && !payload.containsKey("task_id")) {
            payload.put("task_id", request.getTaskId());
        }
        if (isTruthy(request.getContextId()) && !payload.containsKey("sessionId")) {
            payload.put("sessionId", request.getContextId());
        }
        return payload;
    }

    private static Map<String, Object> sendMessageRequestToPayload(SendMessageRequest request) {
        Map<String, Object> payload = messageToPayload(request.getMessage());
        return mergeMetadata(payload, request.getMetadata());
    }

    private static Map<String, Object> messageToPayload(A2aMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (message == null) {
            return payload;
        }
        for (A2aPart part : message.getParts()) {
            if (part.getText() != null) {
                payload.put("query", String.valueOf(part.getText()));
                break;
            }
        }
        if (isTruthy(message.getTaskId())) {
            payload.put("task_id", message.getTaskId());
        }
        if (isTruthy(message.getContextId())) {
            payload.put("sessionId", message.getContextId());
        }
        return mergeMetadata(payload, message.getMetadata());
    }

    private static Map<String, Object> mergeMetadata(Map<String, Object> payload, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return payload;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getValue() != null && !payload.containsKey(entry.getKey())) {
                payload.put(entry.getKey(), entry.getValue());
            }
        }
        return payload;
    }

    private static TaskStatus toOjwStatus(Object status) {
        if (status == null) {
            return TaskStatus.UNKNOWN;
        }
        if (status instanceof A2aTaskState state) {
            return A2A_STATUS_TO_OJW_STATUS.getOrDefault(state.name(), TaskStatus.UNKNOWN);
        }
        if (status instanceof Number number) {
            return switch (number.intValue()) {
                case 0 -> TaskStatus.UNKNOWN;
                case 1 -> TaskStatus.SUBMITTED;
                case 2 -> TaskStatus.WORKING;
                case 3 -> TaskStatus.COMPLETED;
                case 4 -> TaskStatus.FAILED;
                case 5 -> TaskStatus.CANCELED;
                case 6 -> TaskStatus.INPUT_REQUIRED;
                case 7 -> TaskStatus.FAILED;
                case 8 -> TaskStatus.INPUT_REQUIRED;
                default -> TaskStatus.UNKNOWN;
            };
        }
        return A2A_STATUS_TO_OJW_STATUS.getOrDefault(String.valueOf(status), TaskStatus.UNKNOWN);
    }

    private static AgentResult buildAgentResult(String taskId,
                                                String sessionId,
                                                TaskStatus status,
                                                List<Artifact> artifacts,
                                                Map<String, Object> metadata) {
        AgentResult result = new AgentResult();
        result.setTaskId(taskId);
        result.setSessionId(sessionId);
        result.setStatus(status);
        result.setArtifacts(artifacts);
        result.setMetadata(metadata);
        return result;
    }

    private static Map<String, Object> agentResultToPayload(AgentResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (result.getTaskId() != null) {
            payload.put("task_id", result.getTaskId());
        }
        if (result.getSessionId() != null) {
            payload.put("sessionId", result.getSessionId());
        }
        if (result.getStatus() != null) {
            payload.put("status", result.getStatus());
        }
        if (!result.getArtifacts().isEmpty()) {
            payload.put("artifacts", result.getArtifacts());
        }
        if (!result.getMetadata().isEmpty()) {
            payload.put("metadata", result.getMetadata());
        }
        return payload;
    }

    private static A2aMessage coerceMessage(Object value) {
        if (value instanceof A2aMessage message) {
            return message;
        }
        Map<String, Object> map = asMap(value);
        A2aMessage message = new A2aMessage();
        message.setMessageId(stringOrNull(map.get("message_id")));
        message.setRole(roleOrNull(map.get("role")));
        message.setTaskId(stringOrNull(map.get("task_id")));
        message.setContextId(stringOrNull(map.get("context_id")));
        message.setMetadata(mapOrEmpty(map.get("metadata")));
        message.setParts(coerceParts(map.get("parts")));
        return message;
    }

    private static A2aTask coerceTask(Object value) {
        if (value instanceof A2aTask task) {
            return task;
        }
        Map<String, Object> map = asMap(value);
        A2aTask task = new A2aTask();
        task.setId(stringOrNull(firstNonNull(map.get("id"), map.get("task_id"))));
        task.setContextId(stringOrNull(map.get("context_id")));
        task.setStatus(coerceTaskStatus(map.get("status")));
        task.setArtifacts(coerceArtifacts(map.get("artifacts")));
        task.setMetadata(mapOrEmpty(map.get("metadata")));
        return task;
    }

    private static TaskStatusUpdateEvent coerceStatusUpdate(Object value) {
        if (value instanceof TaskStatusUpdateEvent event) {
            return event;
        }
        Map<String, Object> map = asMap(value);
        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
        event.setTaskId(stringOrNull(map.get("task_id")));
        event.setContextId(stringOrNull(map.get("context_id")));
        event.setStatus(coerceTaskStatus(map.get("status")));
        event.setMetadata(mapOrEmpty(map.get("metadata")));
        return event;
    }

    private static TaskArtifactUpdateEvent coerceArtifactUpdate(Object value) {
        if (value instanceof TaskArtifactUpdateEvent event) {
            return event;
        }
        Map<String, Object> map = asMap(value);
        TaskArtifactUpdateEvent event = new TaskArtifactUpdateEvent();
        event.setTaskId(stringOrNull(map.get("task_id")));
        event.setContextId(stringOrNull(map.get("context_id")));
        Object artifact = map.get("artifact");
        event.setArtifact(artifact == null ? null : coerceArtifact(artifact));
        event.setMetadata(mapOrEmpty(map.get("metadata")));
        return event;
    }

    private static A2aTaskStatus coerceTaskStatus(Object value) {
        if (value instanceof A2aTaskStatus status) {
            return status;
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Object state = stringifyKeys(map).get("state");
            return new A2aTaskStatus(state);
        }
        Object state = fieldValue(value, "state");
        return new A2aTaskStatus(state == null ? value : state);
    }

    private static A2aArtifact coerceArtifact(Object value) {
        if (value instanceof A2aArtifact artifact) {
            return artifact;
        }
        Map<String, Object> map = asMap(value);
        A2aArtifact artifact = new A2aArtifact();
        artifact.setArtifactId(stringOrNull(firstNonNull(map.get("artifact_id"), map.get("artifactId"))));
        artifact.setName(stringOrNull(map.get("name")));
        artifact.setDescription(stringOrNull(map.get("description")));
        artifact.setParts(coerceParts(map.get("parts")));
        artifact.setMetadata(mapOrEmpty(map.get("metadata")));
        return artifact;
    }

    private static List<A2aArtifact> coerceArtifacts(Object rawArtifacts) {
        if (!(rawArtifacts instanceof Collection<?> collection)) {
            return List.of();
        }
        List<A2aArtifact> artifacts = new ArrayList<>();
        for (Object artifact : collection) {
            artifacts.add(coerceArtifact(artifact));
        }
        return artifacts;
    }

    private static A2aPart coercePart(Object value) {
        if (value instanceof A2aPart part) {
            return part;
        }
        Map<String, Object> map = asMap(value);
        A2aPart part = new A2aPart();
        part.setText(stringOrNull(map.get("text")));
        Object raw = map.get("raw");
        if (raw instanceof byte[] bytes) {
            part.setRaw(bytes);
        }
        part.setUrl(stringOrNull(map.get("url")));
        if (map.containsKey("data")) {
            part.setData(map.get("data"));
        }
        part.setFilename(stringOrNull(map.get("filename")));
        part.setMediaType(stringOrNull(map.get("media_type")));
        part.setMetadata(mapOrEmpty(map.get("metadata")));
        return part;
    }

    private static List<A2aPart> coerceParts(Object rawParts) {
        if (!(rawParts instanceof Collection<?> collection)) {
            return List.of();
        }
        List<A2aPart> parts = new ArrayList<>();
        for (Object part : collection) {
            parts.add(coercePart(part));
        }
        return parts;
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringifyKeys(map);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (value == null) {
            return map;
        }
        copyField(map, value, "id", "getId");
        copyField(map, value, "task_id", "getTaskId");
        copyField(map, value, "context_id", "getContextId");
        copyField(map, value, "status", "getStatus");
        copyField(map, value, "artifacts", "getArtifacts");
        copyField(map, value, "artifact_id", "getArtifactId");
        copyField(map, value, "artifactId", "getArtifactId");
        copyField(map, value, "artifact", "getArtifact");
        copyField(map, value, "name", "getName");
        copyField(map, value, "description", "getDescription");
        copyField(map, value, "parts", "getParts");
        copyField(map, value, "text", "getText");
        copyField(map, value, "raw", "getRaw");
        copyField(map, value, "url", "getUrl");
        copyField(map, value, "data", "getData");
        copyField(map, value, "filename", "getFilename");
        copyField(map, value, "media_type", "getMediaType");
        copyField(map, value, "metadata", "getMetadata");
        return map;
    }

    private static void copyField(Map<String, Object> target, Object value, String fieldName, String getterName) {
        Object fieldValue = fieldValue(value, fieldName, getterName);
        if (fieldValue != null) {
            target.put(fieldName, fieldValue);
        }
    }

    private static Object fieldValue(Object value, String... fieldNames) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = stringifyKeys(rawMap);
            for (String name : fieldNames) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
            }
            return null;
        }
        for (String name : fieldNames) {
            String getterName = name.startsWith("get") || name.startsWith("is")
                    ? name
                    : "get" + toUpperCamel(name);
            try {
                Method method = value.getClass().getMethod(getterName);
                return method.invoke(value);
            } catch (NoSuchMethodException ignored) {
                Object publicField = publicFieldValue(value, name);
                if (publicField != null) {
                    return publicField;
                }
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalArgumentException("Failed to read field " + name, exception);
            }
        }
        return null;
    }

    private static Object publicFieldValue(Object value, String name) {
        try {
            return value.getClass().getField(name).get(value);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
    }

    private static boolean hasField(Object value, String... fieldNames) {
        if (value == null) {
            return false;
        }
        if (value instanceof StreamResponse response) {
            for (String fieldName : fieldNames) {
                if (response.hasField(fieldName)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = stringifyKeys(rawMap);
            for (String fieldName : fieldNames) {
                if (map.containsKey(fieldName) && map.get(fieldName) != null) {
                    return true;
                }
            }
            return false;
        }
        Object hasFieldMethod = invokeHasField(value, fieldNames);
        if (hasFieldMethod instanceof Boolean bool) {
            return bool;
        }
        for (String fieldName : fieldNames) {
            if (fieldValue(value, fieldName) != null) {
                return true;
            }
        }
        return false;
    }

    private static Object invokeHasField(Object value, String[] fieldNames) {
        try {
            Method method = value.getClass().getMethod("hasField", String.class);
            for (String fieldName : fieldNames) {
                Object result = method.invoke(value, fieldName);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            }
            return false;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException("hasField failed", exception);
        }
    }

    private static Map<String, Object> mapOrEmpty(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringifyKeys(map);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> stringifyKeys(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        return !String.valueOf(value).isEmpty();
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static A2aRole roleOrNull(Object value) {
        if (value instanceof A2aRole role) {
            return role;
        }
        if (value == null) {
            return null;
        }
        return A2aRole.valueOf(String.valueOf(value));
    }

    private static String toUpperCamel(String name) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = true;
        for (char character : name.toCharArray()) {
            if (character == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(character) : character);
            upperNext = false;
        }
        return builder.toString();
    }

    /**
     * Mirrors Python's {@code Role} A2A SDK enum boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public enum A2aRole {
        ROLE_USER
    }

    /**
     * Mirrors Python's {@code TaskState} A2A SDK enum boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public enum A2aTaskState {
        TASK_STATE_UNSPECIFIED,
        TASK_STATE_SUBMITTED,
        TASK_STATE_WORKING,
        TASK_STATE_COMPLETED,
        TASK_STATE_FAILED,
        TASK_STATE_CANCELED,
        TASK_STATE_INPUT_REQUIRED,
        TASK_STATE_REJECTED,
        TASK_STATE_AUTH_REQUIRED
    }

    /**
     * Mirrors Python's {@code Part as A2APart} boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class A2aPart {
        private String text;
        private byte[] raw;
        private String url;
        private Object data;
        private boolean dataSet;
        private String filename;
        private String mediaType;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public byte[] getRaw() {
            return raw == null ? null : raw.clone();
        }

        public void setRaw(byte[] raw) {
            this.raw = raw == null ? null : raw.clone();
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
            this.dataSet = true;
        }

        public boolean isDataSet() {
            return dataSet;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getMediaType() {
            return mediaType;
        }

        public void setMediaType(String mediaType) {
            this.mediaType = mediaType;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Mirrors Python's {@code Message} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class A2aMessage {
        private String messageId;
        private A2aRole role;
        private String taskId;
        private String contextId;
        private List<A2aPart> parts = new ArrayList<>();
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public A2aRole getRole() {
            return role;
        }

        public void setRole(A2aRole role) {
            this.role = role;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getContextId() {
            return contextId;
        }

        public void setContextId(String contextId) {
            this.contextId = contextId;
        }

        public List<A2aPart> getParts() {
            return parts;
        }

        public void setParts(List<A2aPart> parts) {
            this.parts = parts == null ? new ArrayList<>() : new ArrayList<>(parts);
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Mirrors Python's {@code SendMessageRequest} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class SendMessageRequest {
        private A2aMessage message;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public A2aMessage getMessage() {
            return message;
        }

        public void setMessage(A2aMessage message) {
            this.message = message;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Mirrors Python's {@code TaskStatus as A2ATaskStatus} boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class A2aTaskStatus {
        private Object state;

        public A2aTaskStatus() {
        }

        public A2aTaskStatus(Object state) {
            this.state = state;
        }

        public Object getState() {
            return state;
        }

        public void setState(Object state) {
            this.state = state;
        }
    }

    /**
     * Mirrors Python's {@code Artifact} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class A2aArtifact {
        private String artifactId;
        private String name;
        private String description;
        private List<A2aPart> parts = new ArrayList<>();
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<A2aPart> getParts() {
            return parts;
        }

        public void setParts(List<A2aPart> parts) {
            this.parts = parts == null ? new ArrayList<>() : new ArrayList<>(parts);
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Mirrors Python's {@code Task} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class A2aTask {
        private String id;
        private String contextId;
        private A2aTaskStatus status;
        private List<A2aArtifact> artifacts = new ArrayList<>();
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getContextId() {
            return contextId;
        }

        public void setContextId(String contextId) {
            this.contextId = contextId;
        }

        public A2aTaskStatus getStatus() {
            return status;
        }

        public void setStatus(A2aTaskStatus status) {
            this.status = status;
        }

        public List<A2aArtifact> getArtifacts() {
            return artifacts;
        }

        public void setArtifacts(List<A2aArtifact> artifacts) {
            this.artifacts = artifacts == null ? new ArrayList<>() : new ArrayList<>(artifacts);
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Mirrors Python's {@code TaskStatusUpdateEvent} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class TaskStatusUpdateEvent {
        private String taskId;
        private String contextId;
        private A2aTaskStatus status;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getContextId() {
            return contextId;
        }

        public void setContextId(String contextId) {
            this.contextId = contextId;
        }

        public A2aTaskStatus getStatus() {
            return status;
        }

        public void setStatus(A2aTaskStatus status) {
            this.status = status;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Mirrors Python's {@code TaskArtifactUpdateEvent} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class TaskArtifactUpdateEvent {
        private String taskId;
        private String contextId;
        private A2aArtifact artifact;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getContextId() {
            return contextId;
        }

        public void setContextId(String contextId) {
            this.contextId = contextId;
        }

        public A2aArtifact getArtifact() {
            return artifact;
        }

        public void setArtifact(A2aArtifact artifact) {
            this.artifact = artifact;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Mirrors Python's {@code RequestContext} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class RequestContext {
        private A2aMessage message;
        private A2aTask task;
        private String taskId;
        private String contextId;

        public A2aMessage getMessage() {
            return message;
        }

        public void setMessage(A2aMessage message) {
            this.message = message;
        }

        public A2aTask getTask() {
            return task;
        }

        public void setTask(A2aTask task) {
            this.task = task;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getContextId() {
            return contextId;
        }

        public void setContextId(String contextId) {
            this.contextId = contextId;
        }
    }

    /**
     * Mirrors Python's {@code StreamResponse} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_transformer.py}.
     */
    public static final class StreamResponse {
        private TaskArtifactUpdateEvent artifactUpdate;
        private TaskStatusUpdateEvent statusUpdate;
        private A2aMessage message;
        private A2aTask task;

        public boolean hasField(String fieldName) {
            return switch (fieldName) {
                case "artifact_update", "artifactUpdate" -> artifactUpdate != null;
                case "status_update", "statusUpdate" -> statusUpdate != null;
                case "message" -> message != null;
                case "task" -> task != null;
                default -> false;
            };
        }

        public TaskArtifactUpdateEvent getArtifactUpdate() {
            return artifactUpdate;
        }

        public void setArtifactUpdate(TaskArtifactUpdateEvent artifactUpdate) {
            this.artifactUpdate = artifactUpdate;
        }

        public TaskStatusUpdateEvent getStatusUpdate() {
            return statusUpdate;
        }

        public void setStatusUpdate(TaskStatusUpdateEvent statusUpdate) {
            this.statusUpdate = statusUpdate;
        }

        public A2aMessage getMessage() {
            return message;
        }

        public void setMessage(A2aMessage message) {
            this.message = message;
        }

        public A2aTask getTask() {
            return task;
        }

        public void setTask(A2aTask task) {
            this.task = task;
        }
    }
}
