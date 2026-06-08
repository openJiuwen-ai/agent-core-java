/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code Trajectory} in
 * {@code openjiuwen/agent_evolving/trajectory/types.py}.
 * Complete execution trajectory.
 */
public class Trajectory {

    private String executionId;
    /** Unique identifier for this execution. */

    private List<TrajectoryStep> steps;
    /** Ordered list of execution steps. */

    private String source = "offline";
    /** Execution source: 'online' (deepagents) | 'offline' (trainer) */

    private String caseId;
    /** Offline: dataset case identifier. Online: None. */

    private String sessionId;
    /** Online: conversation session ID. Offline: can reuse caseId or None. */

    private String traceId;

    private Map<String, Integer> cost;
    /** Aggregated cost metrics: input_tokens, output_tokens. */

    private List<int[]> edges;

    private Map<String, Object> meta;
    /** Extension metadata for trajectory-level attributes. */

    public Trajectory() {
        this.steps = new ArrayList<>();
        this.meta = new LinkedHashMap<>();
        this.source = "offline";
    }

    public Trajectory(String executionId, String sessionId, String source, 
                      List<TrajectoryStep> steps, Map<String, Integer> cost, Map<String, Object> meta) {
        this.executionId = executionId;
        this.sessionId = sessionId;
        this.source = source != null ? source : "offline";
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.cost = cost;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public Trajectory(String executionId, List<TrajectoryStep> steps, String source,
                      String caseId, String sessionId, String traceId,
                      Map<String, Integer> cost, List<int[]> edges, Map<String, Object> meta) {
        this.executionId = executionId;
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.source = source != null ? source : "offline";
        this.caseId = caseId;
        this.sessionId = sessionId;
        this.traceId = traceId;
        this.cost = cost;
        this.edges = edges;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public static Builder builder() { return new Builder(); }

    // Getters
    public String getExecutionId() { return executionId; }
    public List<TrajectoryStep> getSteps() { return steps; }
    public String getSource() { return source; }
    public String getCaseId() { return caseId; }
    public String getSessionId() { return sessionId; }
    public String getTraceId() { return traceId; }
    public Map<String, Integer> getCost() { return cost; }
    public List<int[]> getEdges() { return edges; }
    public Map<String, Object> getMeta() { return meta; }

    // Setters
    public void setExecutionId(String executionId) { this.executionId = executionId; }
    public void setSteps(List<TrajectoryStep> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }
    public void setSource(String source) { this.source = source != null ? source : "offline"; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public void setCost(Map<String, Integer> cost) { this.cost = cost; }
    public void setEdges(List<int[]> edges) { this.edges = edges; }
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public static final class Builder {
        private String executionId;
        private List<TrajectoryStep> steps;
        private String source;
        private String caseId;
        private String sessionId;
        private String traceId;
        private Map<String, Integer> cost;
        private List<int[]> edges;
        private Map<String, Object> meta;

        private Builder() {
            this.steps = new ArrayList<>();
            this.meta = new LinkedHashMap<>();
            this.source = "offline";
        }

        public Builder executionId(String executionId) { this.executionId = executionId; return this; }
        public Builder steps(List<TrajectoryStep> steps) {
            this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
            return this;
        }
        public Builder source(String source) { this.source = source; return this; }
        public Builder caseId(String caseId) { this.caseId = caseId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder cost(Map<String, Integer> cost) { this.cost = cost; return this; }
        public Builder edges(List<int[]> edges) { this.edges = edges; return this; }
        public Builder meta(Map<String, Object> meta) {
            this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
            return this;
        }

        public Trajectory build() {
            return new Trajectory(executionId, steps, source, caseId, sessionId, traceId, cost, edges, meta);
        }
    }

    public List<Map<String, Object>> toMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (TrajectoryStep step : steps) {
            if (!"llm".equals(step.getKind()) || !(step.getDetail() instanceof LLMCallDetail detail)) {
                continue;
            }
            for (Object message : detail.getMessages()) {
                messages.add(messageToDict(message));
            }
            Object response = detail.getResponse();
            if (response != null) {
                Map<String, Object> responseMessage = messageToDict(response);
                if (responseMessage.containsKey("role") || responseMessage.containsKey("content")) {
                    messages.add(responseMessage);
                }
            }
        }
        return messages;
    }

    private static Map<String, Object> messageToDict(Object message) {
        if (message instanceof Map<?, ?> map) {
            return castMap(jsonSafe(map));
        }

        Object role = nestedValue(message, "role");
        if (role != null) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", role);
            item.put("content", jsonSafe(defaultIfNull(nestedValue(message, "content"), "")));

            Object name = nestedValue(message, "name");
            if (name != null) {
                item.put("name", name);
            }

            Object metadata = nestedValue(message, "metadata");
            if (metadata instanceof Map<?, ?> metadataMap && !metadataMap.isEmpty()) {
                item.put("metadata", jsonSafe(metadataMap));
            }

            Object toolCalls = nestedValue(message, "toolCalls");
            if (toolCalls == null) {
                toolCalls = nestedValue(message, "tool_calls");
            }
            if (toolCalls instanceof List<?> toolCallList && !toolCallList.isEmpty()) {
                item.put("tool_calls", jsonSafe(toolCallList));
            }
            return item;
        }

        Object dumped = invokeNoArg(message, "modelDump");
        if (dumped == null) {
            dumped = invokeNoArg(message, "model_dump");
        }
        if (dumped instanceof Map<?, ?> dumpedMap) {
            return castMap(jsonSafe(dumpedMap));
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("role", "unknown");
        fallback.put("content", String.valueOf(message));
        return fallback;
    }

    private static Object jsonSafe(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof List<?> list) {
            List<Object> safe = new ArrayList<>();
            for (Object item : list) {
                safe.add(jsonSafe(item));
            }
            return safe;
        }
        if (value instanceof Object[] array) {
            List<Object> safe = new ArrayList<>();
            for (Object item : array) {
                safe.add(jsonSafe(item));
            }
            return safe;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                safe.put(String.valueOf(entry.getKey()), jsonSafe(entry.getValue()));
            }
            return safe;
        }

        Object dumped = invokeNoArg(value, "modelDump");
        if (dumped == null) {
            dumped = invokeNoArg(value, "model_dump");
        }
        if (dumped instanceof Map<?, ?> dumpedMap) {
            return jsonSafe(dumpedMap);
        }

        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static Object nestedValue(Object source, String fieldName) {
        if (source == null) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }

        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (String methodName : List.of("get" + suffix, "is" + suffix, fieldName)) {
            Object value = invokeNoArg(source, methodName);
            if (value != null) {
                return value;
            }
        }

        Field field = findField(source.getClass(), fieldName);
        if (field != null) {
            try {
                field.setAccessible(true);
                return field.get(source);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object source, String methodName) {
        if (source == null) {
            return null;
        }
        Method method = findMethod(source.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(source);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object defaultIfNull(Object value, Object fallback) {
        return value != null ? value : fallback;
    }
}
