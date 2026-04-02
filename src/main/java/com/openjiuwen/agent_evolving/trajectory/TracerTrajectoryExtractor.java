// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.trajectory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extract Trajectory from Session.tracer() spans.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.trajectory.operation.TracerTrajectoryExtractor}.
 */
public class TracerTrajectoryExtractor {

    /**
     * Extract trajectory from session tracer.
     *
     * @param session   Agent session with tracer attribute
     * @param execution Execution specification for this trajectory
     * @return Trajectory containing all steps and their dependencies
     */
    public Trajectory extract(Object session, ExecutionSpec execution) {
        Object tracer = getTracer(session);
        List<Object> agentSpans = getAgentSpans(tracer);
        List<Object> workflowSpans = getWorkflowSpans(tracer);

        List<TrajectoryStep> steps = new ArrayList<>();
        Map<String, Integer> invokeIndex = new LinkedHashMap<>();

        for (Object span : agentSpans) {
            StepResult result = buildAgentStep(span);
            steps.add(result.step());
            if (result.invokeId() != null) {
                invokeIndex.put(result.invokeId(), steps.size() - 1);
            }
        }

        for (Object span : workflowSpans) {
            steps.add(buildWorkflowStep(span));
        }

        List<int[]> edges = buildEdges(steps, invokeIndex);

        return Trajectory.builder()
                .caseId(execution.getCaseId())
                .executionId(execution.getExecutionId())
                .traceId(getTraceId(tracer))
                .steps(steps)
                .edges(edges.isEmpty() ? null : edges)
                .build();
    }

    private Object getTracer(Object session) {
        if (session == null) {
            return null;
        }
        try {
            Method method = session.getClass().getMethod("tracer");
            return method.invoke(session);
        } catch (Exception e) {
            try {
                Method getter = session.getClass().getMethod("getTracer");
                return getter.invoke(session);
            } catch (Exception ignore) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> getAgentSpans(Object tracer) {
        Object spanManager = getAttribute(tracer, List.of("tracerAgentSpanManager"), Object.class, null);
        return collectSpansFromManager(spanManager);
    }

    @SuppressWarnings("unchecked")
    private List<Object> getWorkflowSpans(Object tracer) {
        Map<String, Object> managers = getAttribute(tracer, List.of("tracerWorkflowSpanManagerDict"), Map.class, Map.of());
        List<Object> spans = new ArrayList<>();
        for (Object spanManager : managers.values()) {
            spans.addAll(collectSpansFromManager(spanManager));
        }
        return spans;
    }

    @SuppressWarnings("unchecked")
    private List<Object> collectSpansFromManager(Object spanManager) {
        if (spanManager == null) {
            return List.of();
        }
        try {
            Field orderField = spanManager.getClass().getDeclaredField("order");
            orderField.setAccessible(true);
            List<String> order = (List<String>) orderField.get(spanManager);

            Field spanMapField = spanManager.getClass().getDeclaredField("sessionSpans");
            spanMapField.setAccessible(true);
            Map<String, Object> spanMap = (Map<String, Object>) spanMapField.get(spanManager);

            List<Object> spans = new ArrayList<>();
            for (String invokeId : order != null ? order : List.<String>of()) {
                Object span = spanMap.get(invokeId);
                if (span != null) {
                    spans.add(snapshotSpan(span));
                }
            }
            return spans;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Object snapshotSpan(Object span) {
        try {
            Method method = span.getClass().getMethod("snapshot");
            return method.invoke(span);
        } catch (Exception e) {
            return span;
        }
    }

    private String getTraceId(Object tracer) {
        return getAttribute(tracer, List.of("traceId"), String.class, null);
    }

    @SuppressWarnings("unchecked")
    private StepResult buildAgentStep(Object span) {
        Map<String, Object> baseMeta = getAttribute(span, List.of("metaData"), Map.class, new LinkedHashMap<>());
        String operatorId = getOperatorId(span, baseMeta);
        String nodeId = getStringFromMap(baseMeta, "node_id", "component_id");

        TrajectoryStep step = TrajectoryStep.builder()
                .kind(classifySpanKind(span))
                .operatorId(operatorId)
                .agentId(getStringFromMap(baseMeta, "agent_id"))
                .role(getStringFromMap(baseMeta, "role"))
                .nodeId(nodeId)
                .inputs(extractInputs(span))
                .outputs(extractOutputs(span))
                .error(getAttribute(span, List.of("error"), Map.class, null))
                .startTimeMs(getTimeMs(span, "startTime"))
                .endTimeMs(getTimeMs(span, "endTime"))
                .meta(buildAgentMeta(span, baseMeta))
                .build();

        String invokeId = getAttribute(span, List.of("invokeId"), String.class, null);
        return new StepResult(step, invokeId);
    }

    private TrajectoryStep buildWorkflowStep(Object span) {
        String nodeId = firstNonBlank(
                getAttribute(span, List.of("componentId"), String.class, null),
                getAttribute(span, List.of("componentName"), String.class, null),
                getAttribute(span, List.of("workflowName"), String.class, null)
        );

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("workflow_id", getAttribute(span, List.of("workflowId"), String.class, null));
        meta.put("workflow_name", getAttribute(span, List.of("workflowName"), String.class, null));
        meta.put("component_id", getAttribute(span, List.of("componentId"), String.class, null));
        meta.put("component_name", getAttribute(span, List.of("componentName"), String.class, null));
        meta.put("component_type", getAttribute(span, List.of("componentType"), String.class, null));
        meta.put("loop_node_id", getAttribute(span, List.of("loopNodeId"), String.class, null));
        meta.put("loop_index", getAttribute(span, List.of("loopIndex"), Object.class, null));
        meta.put("parent_node_id", getAttribute(span, List.of("parentNodeId"), String.class, null));

        return TrajectoryStep.builder()
                .kind(StepKind.WORKFLOW)
                .operatorId(null)
                .agentId(null)
                .role(null)
                .nodeId(nodeId)
                .inputs(extractInputs(span))
                .outputs(extractOutputs(span))
                .error(getAttribute(span, List.of("error"), Map.class, null))
                .startTimeMs(getTimeMs(span, "startTime"))
                .endTimeMs(getTimeMs(span, "endTime"))
                .meta(meta)
                .build();
    }

    private StepKind classifySpanKind(Object span) {
        String invokeType = getAttribute(span, List.of("invokeType"), String.class, "");
        if ("plugin".equalsIgnoreCase(invokeType)) {
            return StepKind.TOOL;
        }
        if ("llm".equalsIgnoreCase(invokeType)) {
            return StepKind.LLM;
        }
        if ("workflow".equalsIgnoreCase(invokeType)) {
            return StepKind.WORKFLOW;
        }
        if ("memory".equalsIgnoreCase(invokeType)) {
            return StepKind.MEMORY;
        }
        return StepKind.AGENT;
    }

    private String getOperatorId(Object span, Map<String, Object> meta) {
        return firstNonBlank(
                getAttribute(span, List.of("operatorId"), String.class, null),
                getAttribute(span, List.of("llmCallId"), String.class, null),
                getStringFromMap(meta, "operator_id"),
                getAttribute(span, List.of("name"), String.class, null)
        );
    }

    @SuppressWarnings("unchecked")
    private Object extractInputs(Object span) {
        Object raw = getAttribute(span, List.of("inputs"), Object.class, null);
        if (raw instanceof Map<?, ?> map && map.containsKey("inputs")) {
            return ((Map<String, Object>) map).get("inputs");
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private Object extractOutputs(Object span) {
        Object raw = getAttribute(span, List.of("outputs"), Object.class, null);
        if (raw instanceof Map<?, ?> map && map.containsKey("outputs")) {
            return ((Map<String, Object>) map).get("outputs");
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildAgentMeta(Object span, Map<String, Object> baseMeta) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (baseMeta != null) {
            meta.putAll(baseMeta);
        }
        meta.put("invoke_id", getAttribute(span, List.of("invokeId"), String.class, null));
        meta.put("parent_invoke_id", getAttribute(span, List.of("parentInvokeId"), String.class, null));
        meta.put("child_invokes", getAttribute(span, List.of("childInvokesId"), List.class, null));
        return meta;
    }

    private List<int[]> buildEdges(List<TrajectoryStep> steps, Map<String, Integer> invokeIndex) {
        List<int[]> edges = new ArrayList<>();
        for (int idx = 0; idx < steps.size(); idx++) {
            TrajectoryStep step = steps.get(idx);
            Map<String, Object> meta = step.getMeta() != null ? step.getMeta() : Map.of();

            Object parentId = meta.get("parent_invoke_id");
            if (parentId instanceof String parentInvokeId && invokeIndex.containsKey(parentInvokeId)) {
                edges.add(new int[]{invokeIndex.get(parentInvokeId), idx});
            }

            Object childIds = meta.get("child_invokes");
            if (childIds instanceof List<?> list) {
                for (Object childId : list) {
                    if (childId instanceof String childInvokeId && invokeIndex.containsKey(childInvokeId)) {
                        edges.add(new int[]{idx, invokeIndex.get(childInvokeId)});
                    }
                }
            }
        }
        return edges;
    }

    @SuppressWarnings("unchecked")
    private <T> T getAttribute(Object obj, List<String> names, Class<T> type, T defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        for (String name : names) {
            try {
                Method getter = obj.getClass().getMethod("get" + capitalize(name));
                Object value = getter.invoke(obj);
                if (value == null) {
                    continue;
                }
                if (type == Object.class || type.isInstance(value)) {
                    return (T) value;
                }
            } catch (Exception ignored) {
            }
            try {
                Field field = obj.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value == null) {
                    continue;
                }
                if (type == Object.class || type.isInstance(value)) {
                    return (T) value;
                }
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    private String getStringFromMap(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    private Long getTimeMs(Object obj, String fieldName) {
        Object value = getAttribute(obj, List.of(fieldName), Object.class, null);
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof java.util.Date date) {
            return date.getTime();
        }
        return null;
    }

    private String capitalize(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record StepResult(TrajectoryStep step, String invokeId) {
    }
}
