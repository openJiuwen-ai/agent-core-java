/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Session tracer and workflow trace sink.
 *
 * <p>Mirrors Python's {@code Tracer} in
 * {@code openjiuwen/core/session/tracer/tracer.py}.</p>
 *
 * <p>Also mirrors Python's handler dispatch path in
 * {@code openjiuwen/core/session/tracer/handler.py}.</p>
 *
 * <p>Also mirrors Python's {@code TracerWorkflowUtils} event dispatch surface in
 * {@code openjiuwen/core/session/tracer/workflow_tracer.py}.</p>
 */
public class Tracer implements Vertex.VertexTraceSink {

    public static final String TRACE_AGENT = "tracer_agent";
    public static final String TRACE_WORKFLOW = "tracer_workflow";

    private final String traceId = UUID.randomUUID().toString();
    private final SpanManager tracerAgentSpanManager = new SpanManager(traceId);
    private final Map<String, SpanManager> tracerWorkflowSpanManagerDict = new LinkedHashMap<>();
    private final Map<String, TraceBaseHandler> handlers = new LinkedHashMap<>();
    private StreamWriterManager streamWriterManager;

    public Tracer() {
    }

    public void init(StreamWriterManager streamWriterManager) {
        this.streamWriterManager = streamWriterManager;
        TraceAgentHandler agentHandler = new TraceAgentHandler(streamWriterManager, tracerAgentSpanManager);
        SpanManager parentWorkflowSpanManager = new SpanManager(traceId);
        TraceWorkflowHandler workflowHandler = new TraceWorkflowHandler(streamWriterManager, parentWorkflowSpanManager);
        tracerWorkflowSpanManagerDict.put("", parentWorkflowSpanManager);
        handlers.put(TRACE_AGENT, agentHandler);
        handlers.put(TRACE_WORKFLOW, workflowHandler);
    }

    public void init(StreamWriterManager streamWriterManager, Object ignored) {
        init(streamWriterManager);
    }

    public SpanManager getTracerAgentSpanManager() {
        return tracerAgentSpanManager;
    }

    public Map<String, SpanManager> getTracerWorkflowSpanManagerDict() {
        return tracerWorkflowSpanManagerDict;
    }

    @Override
    public void registerWorkflowSpanManager(String parentNodeId) {
        String key = parentNodeId == null ? "" : parentNodeId;
        SpanManager spanManager = new SpanManager(traceId, key);
        tracerWorkflowSpanManagerDict.put(key, spanManager);
        handlers.put(TRACE_WORKFLOW + "." + key, new TraceWorkflowHandler(streamWriterManager, spanManager));
    }

    public TraceWorkflowSpan getWorkflowSpan(String invokeId, String parentNodeId) {
        SpanManager manager = tracerWorkflowSpanManagerDict.get(parentNodeId == null ? "" : parentNodeId);
        if (manager == null) {
            return null;
        }
        Span span = manager.getSpan(invokeId);
        return span instanceof TraceWorkflowSpan traceWorkflowSpan ? traceWorkflowSpan : null;
    }

    public void trigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        String handlerKey = handlerKey(handlerClassName, safeKwargs);
        TraceBaseHandler handler = handlers.get(handlerKey);
        if (handler == null) {
            return;
        }
        if (handler instanceof TraceAgentHandler) {
            handleAgentEvent(eventName, safeKwargs);
            return;
        }
        if (handler instanceof TraceWorkflowHandler) {
            handleWorkflowEvent(handlerKey, eventName, safeKwargs);
        }
    }

    public void syncTrigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        trigger(handlerClassName, eventName, kwargs);
    }

    public void sync_trigger(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        trigger(handlerClassName, eventName, kwargs);
    }

    @Override
    public void traceComponentInputs(Vertex.VertexSession session, Map<String, Object> inputs, boolean send) {
        TraceWorkflowSpan span = workflowSpan(session);
        updateSpan(span, Map.of("inputs", inputs == null ? Map.of() : inputs));
        if (send) {
            emitWorkflow(span);
        }
    }

    @Override
    public void traceComponentOutputs(Vertex.VertexSession session, Map<String, Object> outputs) {
        TraceWorkflowSpan span = workflowSpan(session);
        updateSpan(span, Map.of("outputs", outputs == null ? Map.of() : outputs));
    }

    @Override
    public void traceComponentBegin(Vertex.VertexSession session) {
        TraceWorkflowSpan span = workflowSpan(session);
        Map<String, Object> updates = componentMetadata(session);
        updates.put("startTime", LocalDateTime.now());
        updateSpan(span, updates);
    }

    @Override
    public void traceComponentDone(Vertex.VertexSession session) {
        TraceWorkflowSpan span = workflowSpan(session);
        updateSpan(span, Map.of("endTime", LocalDateTime.now()));
        emitWorkflow(span);
    }

    @Override
    public void traceComponentStreamInput(Vertex.VertexSession session, Object chunk, boolean send) {
        TraceWorkflowSpan span = workflowSpan(session);
        if (chunk != null) {
            span.appendStreamInputs(chunk);
        }
        if (send) {
            emitWorkflow(span);
        }
    }

    @Override
    public void traceComponentStreamOutput(Vertex.VertexSession session, Object chunk) {
        TraceWorkflowSpan span = workflowSpan(session);
        if (chunk != null) {
            span.appendStreamOutput(chunk);
        }
    }

    @Override
    public void traceError(Vertex.VertexSession session, Throwable error) {
        TraceWorkflowSpan span = workflowSpan(session);
        updateSpan(span, Map.of(
                "endTime", LocalDateTime.now(),
                "error", errorMap(error)
        ));
        emitWorkflow(span);
    }

    @Override
    public void trace(Vertex.VertexSession session, Map<String, Object> data) {
        TraceWorkflowSpan span = workflowSpan(session);
        Map<String, Object> safeData = data == null ? Map.of() : data;
        Map<String, Object> metadata = new LinkedHashMap<>();
        copyIfPresent(safeData, metadata, "workflow_id", "workflowId");
        copyIfPresent(safeData, metadata, "workflow_name", "workflowName");
        copyIfPresent(safeData, metadata, "workflow_version", "workflowVersion");
        if (!metadata.isEmpty()) {
            updateSpan(span, metadata);
        }
        span.setOnInvokeData(java.util.List.of(safeData));
        emitWorkflow(span);
    }

    public void trace(BaseSession session, Map<String, Object> data) {
        if (session instanceof Vertex.VertexSession vertexSession) {
            trace(vertexSession, data);
        }
    }

    public void traceError(BaseSession session, Throwable error) {
        if (session instanceof Vertex.VertexSession vertexSession) {
            traceError(vertexSession, error);
        }
    }

    public void traceComponentInteractiveInputs(BaseSession session, Object inputs, boolean send) {
        if (!(session instanceof Vertex.VertexSession vertexSession)) {
            return;
        }
        TraceWorkflowSpan span = workflowSpan(vertexSession);
        span.setInteractiveInputs(inputs);
        if (send) {
            emitWorkflow(span);
        }
    }

    public void popWorkflowSpan(String invokeId, String parentNodeId) {
        SpanManager manager = tracerWorkflowSpanManagerDict.get(parentNodeId == null ? "" : parentNodeId);
        if (manager != null) {
            manager.popSpan(invokeId);
        }
    }

    private void handleAgentEvent(String eventName, Map<String, Object> kwargs) {
        Object spanValue = kwargs.get("span");
        TraceAgentSpan span = spanValue instanceof TraceAgentSpan traceAgentSpan
                ? traceAgentSpan
                : tracerAgentSpanManager.createAgentSpan();
        Map<String, Object> updates = new LinkedHashMap<>();
        if (eventName != null && eventName.endsWith("_start")) {
            updates.put("startTime", LocalDateTime.now());
            updates.put("invokeType", eventName.replace("on_", "").replace("_start", ""));
            Object inputs = kwargs.get("inputs");
            if (inputs instanceof Map<?, ?> map) {
                updates.put("inputs", castMap(map));
            }
            Object info = kwargs.get("instance_info");
            if (info instanceof Map<?, ?> map) {
                updates.put("metaData", castMap(map));
                Object className = map.get("class_name");
                if (className != null) {
                    updates.put("name", String.valueOf(className));
                }
            }
        } else if (eventName != null && eventName.endsWith("_end")) {
            updates.put("endTime", LocalDateTime.now());
            updates.put("outputs", kwargs.get("outputs"));
        } else if (eventName != null && eventName.endsWith("_error")) {
            updates.put("endTime", LocalDateTime.now());
            updates.put("error", errorMap(kwargs.get("error")));
        }
        tracerAgentSpanManager.updateSpan(span, updates);
        emitAgent(span);
    }

    private void handleWorkflowEvent(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        String parentNodeId = extractParentNodeId(handlerClassName, kwargs);
        String invokeId = String.valueOf(kwargs.getOrDefault("invoke_id", kwargs.getOrDefault("invokeId", "")));
        if (invokeId.isBlank()) {
            return;
        }
        TraceWorkflowSpan span = workflowSpan(invokeId, parentNodeId);
        if (eventName != null && (eventName.equals("on_call_start") || eventName.endsWith("_start"))) {
            Map<String, Object> updates = new LinkedHashMap<>(castMapValue(kwargs.get("metadata")));
            updates.put("startTime", LocalDateTime.now());
            updates.put("on_invoke_data", new ArrayList<Map<String, Object>>());
            updates.put("outputs", null);
            updates.put("stream_outputs", new ArrayList<>());
            Object inputs = kwargs.get("inputs");
            if (inputs instanceof Map<?, ?> map) {
                updates.put("inputs", castMap(map));
            }
            if (kwargs.containsKey("source_ids")) {
                updates.put("source_ids", kwargs.get("source_ids"));
            }
            updateSpan(span, updates);
        } else if ("on_pre_invoke".equals(eventName)) {
            Map<String, Object> updates = new LinkedHashMap<>(castMapValue(kwargs.get("component_metadata")));
            updates.put("inputs", kwargs.get("inputs"));
            updateSpan(span, updates);
        } else if ("on_pre_stream".equals(eventName)) {
            Object chunk = kwargs.get("chunk");
            if (chunk instanceof Map<?, ?> map && !map.isEmpty()) {
                span.appendStreamInputs(castMap(map));
            }
        } else if ("on_post_invoke".equals(eventName)) {
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("outputs", kwargs.get("outputs"));
            Object inputs = kwargs.get("inputs");
            if (inputs != null && ("End".equals(span.getComponentType()) || "Message".equals(span.getComponentType()))) {
                updates.put("inputs", inputs);
            }
            updateSpan(span, updates);
        } else if ("on_post_stream".equals(eventName)) {
            span.appendStreamOutput(kwargs.get("chunk"));
        } else if ("on_invoke".equals(eventName)) {
            Object error = kwargs.get("exception");
            if (error != null) {
                updateSpan(span, Map.of("endTime", LocalDateTime.now(), "error", errorMap(error)));
            }
            Object data = kwargs.get("on_invoke_data");
            if (data instanceof Map<?, ?> map) {
                Map<String, Object> invokeData = castMap(map);
                Object innerError = invokeData.get("inner_error");
                if (innerError instanceof Map<?, ?> innerErrorMap) {
                    span.setInnerError(castMap(innerErrorMap));
                }
                List<Map<String, Object>> onInvokeData = span.getOnInvokeData() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(span.getOnInvokeData());
                onInvokeData.add(invokeData);
                span.setOnInvokeData(onInvokeData);
            }
            emitWorkflow(span);
        } else if ("on_call_done".equals(eventName)) {
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("endTime", LocalDateTime.now());
            if (kwargs.containsKey("outputs")) {
                updates.put("outputs", kwargs.get("outputs"));
            }
            updateSpan(span, updates);
            emitWorkflow(span);
        } else if ("on_interact".equals(eventName)) {
            Map<String, Object> updates = new LinkedHashMap<>(castMapValue(kwargs.get("component_metadata")));
            updates.put("interactive_inputs", kwargs.get("inputs"));
            updateSpan(span, updates);
        }
    }

    private TraceWorkflowSpan workflowSpan(Vertex.VertexSession session) {
        String parentNodeId = session == null ? "" : session.parentId();
        String invokeId = session == null ? "" : session.executableId();
        if ((parentNodeId == null || parentNodeId.isEmpty())
                && (invokeId == null || invokeId.isEmpty())
                && session != null
                && session.workflowId() != null
                && !session.workflowId().isEmpty()) {
            invokeId = session.workflowId();
        }
        return workflowSpan(invokeId, parentNodeId);
    }

    private TraceWorkflowSpan workflowSpan(String invokeId, String parentNodeId) {
        String parentKey = parentNodeId == null ? "" : parentNodeId;
        SpanManager manager = tracerWorkflowSpanManagerDict.computeIfAbsent(parentKey,
                key -> new SpanManager(traceId, key));
        Span span = manager.getSpan(invokeId);
        if (span instanceof TraceWorkflowSpan traceWorkflowSpan) {
            return traceWorkflowSpan;
        }
        return manager.createWorkflowSpan(invokeId, manager.getLastSpan() instanceof TraceWorkflowSpan parent
                ? parent
                : null);
    }

    private Map<String, Object> componentMetadata(Vertex.VertexSession session) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String nodeId = session == null ? "" : readString(session, "nodeId");
        String componentId = nodeId == null || nodeId.isBlank()
                ? session == null ? "" : session.executableId()
                : nodeId;
        metadata.put("componentId", componentId);
        metadata.put("componentName", componentId);
        metadata.put("componentType", session == null ? "" : readString(session, "nodeType"));
        metadata.put("workflowId", session == null ? "" : session.workflowId());
        return metadata;
    }

    private void updateSpan(Span span, Map<String, Object> updates) {
        if (span == null || updates == null) {
            return;
        }
        SpanManager manager = span instanceof TraceWorkflowSpan
                ? tracerWorkflowSpanManagerDict.getOrDefault(
                        ((TraceWorkflowSpan) span).getParentNodeId() == null
                                ? ""
                                : ((TraceWorkflowSpan) span).getParentNodeId(),
                        tracerWorkflowSpanManagerDict.get(""))
                : tracerAgentSpanManager;
        manager.updateSpan(span, updates);
    }

    private void emitAgent(TraceAgentSpan span) {
        emit("tracer_agent", span);
    }

    private void emitWorkflow(TraceWorkflowSpan span) {
        emit("tracer_workflow", span);
    }

    private void emit(String type, Object payload) {
        if (streamWriterManager == null || streamWriterManager.getTraceWriter() == null) {
            return;
        }
        try {
            streamWriterManager.getTraceWriter().write(Map.of("type", type, "payload", payload));
        } catch (RuntimeException ignored) {
            // Trace stream emission is best effort and must not alter business behavior.
        }
    }

    private static Map<String, Object> errorMap(Object error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error_code", "WORKFLOW_EXECUTION_ERROR");
        result.put("message", error == null ? "" : String.valueOf(error));
        return result;
    }

    private static String extractParentNodeId(String handlerClassName, Map<String, Object> kwargs) {
        Object parent = kwargs.get("parent_node_id");
        if (parent == null) {
            parent = kwargs.get("parentNodeId");
        }
        if (parent != null) {
            return String.valueOf(parent);
        }
        if (handlerClassName != null && handlerClassName.startsWith(TRACE_WORKFLOW + ".")) {
            return handlerClassName.substring((TRACE_WORKFLOW + ".").length());
        }
        return "";
    }

    private static String handlerKey(String handlerClassName, Map<String, Object> kwargs) {
        Object parent = null;
        boolean hasParent = false;
        if (kwargs.containsKey("parent_node_id")) {
            parent = kwargs.get("parent_node_id");
            hasParent = true;
        } else if (kwargs.containsKey("parentNodeId")) {
            parent = kwargs.get("parentNodeId");
            hasParent = true;
        }
        if (!hasParent || parent == null) {
            return handlerClassName;
        }
        String parentNodeId = String.valueOf(parent);
        if (parentNodeId.isEmpty()) {
            return handlerClassName;
        }
        return handlerClassName + "." + parentNodeId;
    }

    private static Map<String, Object> castMapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return Map.of();
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target,
                                      String sourceKey, String targetKey) {
        if (source.containsKey(sourceKey)) {
            target.put(targetKey, source.get(sourceKey));
        }
    }

    private static String readString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }
}
