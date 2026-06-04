/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory.extractor;

import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TrajectoryExtractor: offline trajectory extractor.
 *
 * <p>Extracts complete Trajectory from Session.tracer() spans and uses
 * TrajectoryBuilder internally for assembly.
 *
 * <p>Mirrors Python's {@code TrajectoryExtractor} in
 * {@code openjiuwen.agent_evolving.trajectory.extractor}.
 */
public class TrajectoryExtractor {

    private final Object resourceManager;

    /**
     * Create extractor with optional resource manager.
     *
     * @param resourceManager Used to query Tool metadata
     */
    public TrajectoryExtractor(Object resourceManager) {
        this.resourceManager = resourceManager;
    }

    /**
     * Create extractor without resource manager.
     */
    public TrajectoryExtractor() {
        this(null);
    }

    /**
     * Extract trajectory from session spans.
     *
     * @param session Session object with tracer spans
     * @param caseId Optional identifier for the trajectory
     * @return Assembled Trajectory
     */
    public Trajectory extract(Object session, Optional<String> caseId) {
        Object tracer = getTracer(session);
        List<Object> spans = getAgentSpans(tracer);
        String effectiveCaseId = caseId != null && caseId.isPresent() ? caseId.get() : "unknown";

        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId(effectiveCaseId)
                .source("offline")
                .caseId(effectiveCaseId)
                .build();

        for (Object span : spans) {
            builder.recordStep(buildStep(span));
        }
        return builder.buildTrajectory();
    }

    /**
     * Extract trajectory with nullable case ID convenience overload.
     *
     * @param session Session object with tracer spans
     * @param caseId Optional identifier for the trajectory
     * @return Assembled Trajectory
     */
    public Trajectory extract(Object session, String caseId) {
        return extract(session, Optional.ofNullable(caseId));
    }

    /**
     * Extract trajectory with default case ID.
     *
     * @param session Session object with tracer spans
     * @return Assembled Trajectory
     */
    public Trajectory extract(Object session) {
        return extract(session, Optional.empty());
    }

    private TrajectoryStep buildStep(Object span) {
        String kind = classifyKind(span);
        Map<String, Object> baseMeta = asMap(getAttribute(span, "meta_data", "metaData"));
        Object detail = buildDetail(span, kind);
        Map<String, Object> fullMeta = buildMeta(span, baseMeta, kind);

        List<Integer> promptTokenIds = null;
        List<Integer> completionTokenIds = null;
        Object logprobs = null;
        if (detail instanceof LLMCallDetail llmDetail && llmDetail.getResponse() != null) {
            Map<String, Object> response = new LinkedHashMap<>(llmDetail.getResponse());
            promptTokenIds = intList(response.remove("prompt_token_ids"));
            completionTokenIds = intList(response.remove("completion_token_ids"));
            logprobs = response.remove("logprobs");
            llmDetail.setResponse(response);
        }

        return TrajectoryStep.builder()
                .kind(kind)
                .error(getAttribute(span, "error"))
                .startTimeMs(dtToMs(getAttribute(span, "start_time", "startTime")))
                .endTimeMs(dtToMs(getAttribute(span, "end_time", "endTime")))
                .detail(detail)
                .promptTokenIds(promptTokenIds)
                .completionTokenIds(completionTokenIds)
                .logprobs(logprobs)
                .meta(fullMeta)
                .build();
    }

    private Object buildDetail(Object span, String kind) {
        if ("llm".equals(kind)) {
            return buildLlmDetail(span);
        }
        if ("tool".equals(kind)) {
            return buildToolDetail(span);
        }
        return null;
    }

    private LLMCallDetail buildLlmDetail(Object span) {
        List<?> onInvoke = asList(getAttribute(span, "on_invoke_data", "onInvokeData"));
        if (onInvoke.isEmpty()) {
            return null;
        }

        Map<String, Object> llmParams = null;
        for (Object record : onInvoke) {
            Map<String, Object> recordMap = asMap(record);
            if (recordMap.containsKey("llm_params")) {
                llmParams = asMap(recordMap.get("llm_params"));
                break;
            }
            if (recordMap.containsKey("llmParams")) {
                llmParams = asMap(recordMap.get("llmParams"));
                break;
            }
        }
        if (llmParams == null || llmParams.isEmpty()) {
            return null;
        }

        Map<String, Object> response = parseLlmResponse(extractOutputs(span));
        Map<String, Object> usage = response != null ? asMap(response.get("usage")) : new LinkedHashMap<>();
        if (usage.isEmpty()) {
            usage = asMap(llmParams.get("usage"));
        }

        return LLMCallDetail.builder()
                .model(String.valueOf(llmParams.getOrDefault("model", "")))
                .messages(mapList(llmParams.get("messages")))
                .tools(mapListOrNull(llmParams.get("tools")))
                .response(response)
                .usage(usage.isEmpty() ? null : usage)
                .build();
    }

    private ToolCallDetail buildToolDetail(Object span) {
        String toolName = stringValue(getAttribute(span, "name"), "");
        String toolDescription = null;
        Map<String, Object> toolSchema = null;

        if (resourceManager != null && !toolName.isEmpty()) {
            try {
                Object toolInfo = invokeNoArgs(resourceManager, "get_tool_infos", "getToolInfos", toolName);
                if (toolInfo != null) {
                    toolDescription = stringOrNull(getAttribute(toolInfo, "description"));
                    Object parameters = getAttribute(toolInfo, "parameters");
                    if (parameters instanceof Map<?, ?>) {
                        toolSchema = asMap(parameters);
                    } else if (parameters != null) {
                        Object schema = invokeNoArgs(parameters, "model_json_schema", "modelJsonSchema");
                        if (schema instanceof Map<?, ?>) {
                            toolSchema = asMap(schema);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Python logs and continues when resource manager lookup fails.
            }
        }

        return ToolCallDetail.builder()
                .toolName(toolName)
                .callArgs(extractInputs(span))
                .callResult(extractOutputs(span))
                .toolDescription(toolDescription)
                .toolSchema(toolSchema)
                .build();
    }

    private Map<String, Object> buildMeta(Object span, Map<String, Object> baseMeta, String kind) {
        Map<String, Object> meta = deepCopyMap(baseMeta);
        meta.put("operator_id", getOperatorId(span, baseMeta));

        Object agentId = firstNonNull(getAttribute(span, "agent_id", "agentId"), baseMeta.get("agent_id"));
        if (agentId != null && !String.valueOf(agentId).isEmpty()) {
            meta.put("agent_id", agentId);
        }

        if (!"llm".equals(kind) && !"tool".equals(kind)) {
            meta.put("inputs", extractInputs(span));
            meta.put("outputs", extractOutputs(span));
        }

        meta.put("span_name", getAttribute(span, "name"));
        meta.put("invoke_id", getAttribute(span, "invoke_id", "invokeId"));
        meta.put("parent_invoke_id", getAttribute(span, "parent_invoke_id", "parentInvokeId"));
        meta.put("child_invokes", getAttribute(span, "child_invokes_id", "childInvokesId"));
        return meta;
    }

    private Map<String, Object> parseLlmResponse(Object outputs) {
        if (outputs instanceof Map<?, ?>) {
            return asMap(outputs);
        }
        Object dumped = invokeNoArgs(outputs, "model_dump", "modelDump");
        if (dumped instanceof Map<?, ?>) {
            return asMap(dumped);
        }
        Map<String, Object> fields = fieldsAsMap(outputs);
        return fields.isEmpty() ? null : fields;
    }

    private Object getTracer(Object session) {
        if (session == null) {
            return null;
        }
        Object tracer = invokeNoArgs(session, "tracer");
        if (tracer != null) {
            return tracer;
        }
        return getAttribute(session, "tracer", "getTracer");
    }

    private List<Object> getAgentSpans(Object tracer) {
        if (tracer == null) {
            return List.of();
        }
        Object spanManager = getAttribute(tracer, "tracer_agent_span_manager", "tracerAgentSpanManager");
        if (spanManager == null) {
            return List.of();
        }
        Object spans = invokeNoArgs(spanManager, "get_all_spans", "getAllSpans");
        if (spans instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of();
    }

    private String classifyKind(Object span) {
        Object invokeType = getAttribute(span, "invoke_type", "invokeType");
        String invokeString = invokeType != null ? String.valueOf(invokeType) : "";
        if ("plugin".equals(invokeString)) {
            return "tool";
        }
        if ("llm".equals(invokeString) || "workflow".equals(invokeString) || "memory".equals(invokeString)) {
            return invokeString;
        }
        return "agent";
    }

    private Object getOperatorId(Object span, Map<String, Object> meta) {
        return firstNonNull(
                getAttribute(span, "operator_id", "operatorId"),
                getAttribute(span, "llm_call_id", "llmCallId"),
                meta.get("operator_id"),
                getAttribute(span, "name")
        );
    }

    private Object extractInputs(Object span) {
        Object raw = getAttribute(span, "inputs");
        if (raw instanceof Map<?, ?> map && map.containsKey("inputs")) {
            return map.get("inputs");
        }
        return raw;
    }

    private Object extractOutputs(Object span) {
        Object raw = getAttribute(span, "outputs");
        if (raw instanceof Map<?, ?> map && map.containsKey("outputs")) {
            return map.get("outputs");
        }
        return raw;
    }

    private Long dtToMs(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant.toEpochMilli();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant().toEpochMilli();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toEpochMilli();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of();
    }

    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = mapListOrNull(value);
        return result != null ? result : new ArrayList<>();
    }

    private List<Map<String, Object>> mapListOrNull(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            result.add(asMap(item));
        }
        return result;
    }

    private List<Integer> intList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            }
        }
        return result;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value, String defaultValue) {
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private String stringOrNull(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Object getAttribute(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Object value = invokeGetter(target, name);
            if (value != Missing.VALUE) {
                return value;
            }
            value = getField(target, name);
            if (value != Missing.VALUE) {
                return value;
            }
        }
        return null;
    }

    private Object invokeGetter(Object target, String name) {
        List<String> candidates = new ArrayList<>();
        candidates.add(name);
        if (!name.startsWith("get") && !name.contains("_")) {
            candidates.add("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        }
        for (String candidate : candidates) {
            try {
                Method method = target.getClass().getMethod(candidate);
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }
        return Missing.VALUE;
    }

    private Object invokeNoArgs(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Object invokeNoArgs(Object target, String firstMethod, String secondMethod, Object arg) {
        if (target == null) {
            return null;
        }
        for (String methodName : List.of(firstMethod, secondMethod)) {
            try {
                Method method = target.getClass().getMethod(methodName, String.class);
                return method.invoke(target, arg);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Object getField(Object target, String name) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return Missing.VALUE;
        }
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private Map<String, Object> fieldsAsMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return result;
        }
        Class<?> current = value.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    result.put(field.getName(), field.get(value));
                } catch (Exception ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) map));
            } else if (value instanceof List<?> list) {
                copy.put(entry.getKey(), new ArrayList<>(list));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private enum Missing {
        VALUE
    }
}
