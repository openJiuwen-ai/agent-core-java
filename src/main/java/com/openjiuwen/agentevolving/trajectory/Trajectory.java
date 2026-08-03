/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.Trajectory.
 */
public class Trajectory {

    private String caseId;
    private String executionId;
    private String traceId;
    private List<TrajectoryStep> steps;
    private List<int[]> edges;
    private String source = "offline";
    private String sessionId;
    private Map<String, Integer> cost;
    private Map<String, Object> meta = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory(String caseId, String executionId, String traceId, List<TrajectoryStep> steps, List<int[]> edges) {
        this(caseId, executionId, traceId, steps, edges, "offline", null, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory(String caseId,
                      String executionId,
                      String traceId,
                      List<TrajectoryStep> steps,
                      List<int[]> edges,
                      String source,
                      String sessionId,
                      Map<String, Integer> cost,
                      Map<String, Object> meta) {
        this.caseId = caseId;
        this.executionId = executionId;
        this.traceId = traceId;
        this.steps = steps;
        this.edges = edges;
        this.source = source != null ? source : "offline";
        this.sessionId = sessionId;
        this.cost = cost != null ? new LinkedHashMap<>(cost) : null;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TrajectoryStep> getSteps() {
        return steps;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSteps(List<TrajectoryStep> steps) {
        this.steps = steps;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<int[]> getEdges() {
        return edges;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEdges(List<int[]> edges) {
        this.edges = edges;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSource() {
        return source;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSource(String source) {
        this.source = source != null ? source : "offline";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Integer> getCost() {
        return cost;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCost(Map<String, Integer> cost) {
        this.cost = cost != null ? new LinkedHashMap<>(cost) : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    /**
     * Convert trajectory steps to a flat list of message dicts.
     *
     * <p>Mirrors Python's {@code Trajectory.to_messages()} in
     * {@code openjiuwen/agent_evolving/trajectory/types.py}.</p>
     *
     * @return list of message dictionaries
     */
    public List<Map<String, Object>> toMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (steps == null) {
            return messages;
        }
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

        java.lang.reflect.Field field = findField(source.getClass(), fieldName);
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
        java.lang.reflect.Method method = findMethod(source.getClass(), methodName);
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

    private static java.lang.reflect.Method findMethod(Class<?> type, String methodName) {
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

    private static java.lang.reflect.Field findField(Class<?> type, String fieldName) {
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private String caseId;
        private String executionId;
        private String traceId;
        private List<TrajectoryStep> steps;
        private List<int[]> edges;
        private String source = "offline";
        private String sessionId;
        private Map<String, Integer> cost;
        private Map<String, Object> meta;

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder steps(List<TrajectoryStep> steps) {
            this.steps = steps;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder edges(List<int[]> edges) {
            this.edges = edges;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder cost(Map<String, Integer> cost) {
            this.cost = cost;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder meta(Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Trajectory build() {
            return new Trajectory(caseId, executionId, traceId, steps, edges, source, sessionId, cost, meta);
        }
    }
}
