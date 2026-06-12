/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory.extractor;

import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

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
 * <p>Extracts complete Trajectory from Session.tracer() spans.
 * Uses TrajectoryBuilder internally for assembly.</p>
 *
 * <p>Mirrors Python's {@code TrajectoryExtractor} in
 * {@code openjiuwen/agent_evolving/trajectory/extractor.py}.</p>
 */
public class TrajectoryExtractor {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;

    private final Object resourceManager;

    public TrajectoryExtractor(Object resourceManager) {
        this.resourceManager = resourceManager;
    }

    public TrajectoryExtractor() {
        this(null);
    }

    public Trajectory extract(Object session, Optional<String> caseId) {
        Object tracer = getTracer(session);
        List<Object> spans = getAgentSpans(tracer);
        String effectiveCaseId =
                caseId != null && caseId.isPresent() ? caseId.get() : "unknown";

        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId(effectiveCaseId)
                .source("offline")
                .caseId(effectiveCaseId)
                .build();

        for (Object span : spans) {
            builder.recordStep(buildStep(span));
        }
        return builder.build();
    }

    public Trajectory extract(Object session, String caseId) {
        return extract(session, Optional.ofNullable(caseId));
    }

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
        if (detail instanceof LLMCallDetail llmDetail) {
            Map<String, Object> response = asMapOrNull(llmDetail.getResponse());
            if (response != null) {
                promptTokenIds = intList(response.remove("prompt_token_ids"));
                completionTokenIds = intList(response.remove("completion_token_ids"));
                logprobs = response.remove("logprobs");
                llmDetail.setResponse(response);
            }
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
        }
        if (llmParams == null || llmParams.isEmpty()) {
            return null;
        }

        Map<String, Object> response = parseLlmResponse(extractOutputs(span));
        Map<String, Object> usage = response != null ? asMapOrNull(response.get("usage")) : null;
        if (usage == null || usage.isEmpty()) {
            usage = asMapOrNull(llmParams.get("usage"));
        }

        return LLMCallDetail.builder()
                .model(stringValue(llmParams.get("model"), ""))
                .messages(objectList(llmParams.get("messages")))
                .tools(mapListOrNull(llmParams.get("tools")))
                .response(response)
                .usage(usage)
                .build();
    }

    private ToolCallDetail buildToolDetail(Object span) {
        String toolName = stringValue(getAttribute(span, "name"), "");
        String toolDescription = null;
        Map<String, Object> toolSchema = null;

        if (resourceManager != null && !toolName.isEmpty()) {
            try {
                Object toolInfo = invokeSingleStringArg(
                        resourceManager,
                        toolName,
                        "get_tool_infos",
                        "getToolInfos");
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
            } catch (RuntimeException ex) {
                LOGGER.exception(
                        "[TrajectoryExtractor] failed to get tool info for %s",
                        ex,
                        toolName);
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

        Object agentId = firstNonNull(
                getAttribute(span, "agent_id", "agentId"),
                baseMeta.get("agent_id"));
        if (agentId != null) {
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
        Map<String, Object> reflected = fieldsAsMap(outputs);
        return reflected.isEmpty() ? null : reflected;
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
        Object spanManager =
                getAttribute(tracer, "tracer_agent_span_manager", "tracerAgentSpanManager");
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
        if ("llm".equals(invokeString)
                || "workflow".equals(invokeString)
                || "memory".equals(invokeString)) {
            return invokeString;
        }
        return "agent";
    }

    private Object getOperatorId(Object span, Map<String, Object> meta) {
        return firstNonNull(
                getAttribute(span, "operator_id", "operatorId"),
                getAttribute(span, "llm_call_id", "llmCallId"),
                meta.get("operator_id"),
                getAttribute(span, "name"));
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

    private Map<String, Object> asMap(Object value) {
        Map<String, Object> result = asMapOrNull(value);
        return result != null ? result : new LinkedHashMap<>();
    }

    private Map<String, Object> asMapOrNull(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return null;
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

    private List<Object> objectList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(collection);
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
            Object getterValue = invokeGetter(target, name);
            if (getterValue != Missing.VALUE) {
                return getterValue;
            }
            Object fieldValue = getField(target, name);
            if (fieldValue != Missing.VALUE) {
                return fieldValue;
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

    private Object invokeSingleStringArg(Object target, String arg, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
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
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
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
