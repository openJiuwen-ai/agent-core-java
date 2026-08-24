/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

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

    private static final Logger LOG = LoggerFactory.getLogger(Tracer.class);

    public static final String TRACE_AGENT = "tracer_agent";
    public static final String TRACE_WORKFLOW = "tracer_workflow";

    private final String traceId = UUID.randomUUID().toString();
    private final String sessionId;
    private final SpanManager tracerAgentSpanManager = new SpanManager(traceId);
    private final Map<String, SpanManager> tracerWorkflowSpanManagerDict = new LinkedHashMap<>();
    private final Map<String, TraceBaseHandler> handlers = new LinkedHashMap<>();
    private StreamWriterManager streamWriterManager;

    public Tracer() {
        this(null);
    }

    public Tracer(String sessionId) {
        this.sessionId = sessionId;
    }

    public void init(StreamWriterManager streamWriterManager) {
        this.streamWriterManager = streamWriterManager;
        TraceAgentHandler agentHandler = new TraceAgentHandler(streamWriterManager, tracerAgentSpanManager);
        SpanManager parentWorkflowSpanManager = new SpanManager(traceId);
        TraceWorkflowHandler workflowHandler = new TraceWorkflowHandler(streamWriterManager, parentWorkflowSpanManager);
        tracerWorkflowSpanManagerDict.put("", parentWorkflowSpanManager);
        handlers.put(TRACE_AGENT, agentHandler);
        handlers.put(TRACE_WORKFLOW, workflowHandler);

        // Inject traceId / sessionId into externally registered extension handlers so they can
        // bridge OTel traces with the tracer UUID and session.
        for (TraceExtAgentHandler ext : TracerHandlerRegistry.getAgentHandlers().values()) {
            ext.setTraceId(traceId);
            ext.setSessionId(sessionId);
        }
        for (TraceExtWorkflowHandler ext : TracerHandlerRegistry.getWorkflowHandlers().values()) {
            ext.setTraceId(traceId);
            ext.setSessionId(sessionId);
        }
    }

    /**
     * Update the stream writer manager reference.
     *
     * <p>Called when a session is reused across multiple invocations. The tracer is
     * preserved (maintaining trace_id consistency), but the stream writer manager
     * is recreated. This method updates the tracer's reference so that newly
     * registered handlers use the correct stream writer manager.
     *
     * @param streamWriterManager the new stream writer manager
     */
    public void updateStreamWriterManager(StreamWriterManager streamWriterManager) {
        this.streamWriterManager = streamWriterManager;
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
        if (TRACE_AGENT.equals(handlerClassName)) {
            TraceBaseHandler handler = handlers.get(TRACE_AGENT);
            if (handler instanceof TraceAgentHandler) {
                handleAgentEvent(eventName, safeKwargs); // dispatchExt inside — do not double
            } else {
                dispatchAgentExt(eventName, safeKwargs);
            }
            return;
        }
        if (TRACE_WORKFLOW.equals(handlerClassName)
                || (handlerClassName != null && handlerClassName.startsWith(TRACE_WORKFLOW))) {
            String handlerKey = TRACE_WORKFLOW.equals(handlerClassName)
                    ? handlerKey(TRACE_WORKFLOW, safeKwargs)
                    : handlerKey(handlerClassName, safeKwargs);
            TraceBaseHandler handler = handlers.get(handlerKey);
            if (handler instanceof TraceWorkflowHandler) {
                handleWorkflowEvent(handlerKey, eventName, safeKwargs); // dispatchExt inside — OK
                return;
            }
            // Builtin nested missing: still fan-out to TraceExtWorkflowHandlers (Python parity).
            // Do NOT fall back to root TraceWorkflowHandler for TraceSchema.
            dispatchWorkflowExt(eventName, safeKwargs);
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
        TraceBaseHandler baseHandler = handlers.get(TRACE_AGENT);
        if (!(baseHandler instanceof TraceAgentHandler handler)) {
            return;
        }
        Object spanValue = kwargs.get("span");
        TraceAgentSpan span = spanValue instanceof TraceAgentSpan traceAgentSpan
                ? traceAgentSpan
                : tracerAgentSpanManager.createAgentSpan();
        Map<String, Object> instanceInfo = castMapValue(kwargs.get("instance_info"));
        Object inputs = kwargs.get("inputs");
        Object outputs = kwargs.get("outputs");
        Object error = kwargs.get("error");
        if ("on_chain_start".equals(eventName)) {
            handler.onChainStart(span, inputs, instanceInfo);
        } else if ("on_chain_end".equals(eventName)) {
            handler.onChainEnd(span, outputs);
        } else if ("on_chain_error".equals(eventName)) {
            handler.onChainError(span, error);
        } else if ("on_llm_start".equals(eventName)) {
            handler.onLlmStart(span, inputs, instanceInfo);
        } else if ("on_llm_request".equals(eventName)) {
            handler.onLlmRequest(span, kwargs);
        } else if ("on_llm_end".equals(eventName)) {
            handler.onLlmEnd(span, outputs);
        } else if ("on_llm_error".equals(eventName)) {
            handler.onLlmError(span, error);
        } else if ("on_prompt_start".equals(eventName)) {
            handler.onPromptStart(span, inputs, instanceInfo);
        } else if ("on_prompt_end".equals(eventName)) {
            handler.onPromptEnd(span, outputs);
        } else if ("on_prompt_error".equals(eventName)) {
            handler.onPromptError(span, error);
        } else if ("on_plugin_start".equals(eventName)) {
            handler.onPluginStart(span, inputs, instanceInfo);
        } else if ("on_plugin_end".equals(eventName)) {
            handler.onPluginEnd(span, outputs);
        } else if ("on_plugin_error".equals(eventName)) {
            handler.onPluginError(span, error);
        } else if ("on_retriever_start".equals(eventName)) {
            handler.onRetrieverStart(span, inputs, instanceInfo);
        } else if ("on_retriever_end".equals(eventName)) {
            handler.onRetrieverEnd(span, outputs);
        } else if ("on_retriever_error".equals(eventName)) {
            handler.onRetrieverError(span, error);
        } else if ("on_evaluator_start".equals(eventName)) {
            handler.onEvaluatorStart(span, inputs, instanceInfo);
        } else if ("on_evaluator_end".equals(eventName)) {
            handler.onEvaluatorEnd(span, outputs);
        } else if ("on_evaluator_error".equals(eventName)) {
            handler.onEvaluatorError(span, error);
        } else if ("on_workflow_start".equals(eventName)) {
            handler.onWorkflowStart(span, inputs, instanceInfo);
        } else if ("on_workflow_end".equals(eventName)) {
            handler.onWorkflowEnd(span, outputs);
        } else if ("on_workflow_error".equals(eventName)) {
            handler.onWorkflowError(span, error);
        } else if (eventName != null && eventName.endsWith("_start")) {
            handler.updateStartTraceData(span, eventName.replace("on_", "").replace("_start", ""),
                    inputs, instanceInfo);
            emitAgent(span);
        } else if (eventName != null && eventName.endsWith("_end")) {
            handler.updateEndTraceData(span, outputs);
            emitAgent(span);
        } else if (eventName != null && eventName.endsWith("_error")) {
            handler.updateErrorTraceData(span, error);
            emitAgent(span);
        }
    }

    /**
     * Fan-out agent events to {@link TraceExtAgentHandler}s without updating TraceSchema spans.
     *
     * <p>Used when the built-in {@link TraceAgentHandler} is absent (Python parity).</p>
     */
    private void dispatchAgentExt(String eventName, Map<String, Object> kwargs) {
        Object spanValue = kwargs.get("span");
        TraceAgentSpan span = spanValue instanceof TraceAgentSpan traceAgentSpan
                ? traceAgentSpan
                : tracerAgentSpanManager.createAgentSpan();
        Map<String, Object> instanceInfo = castMapValue(kwargs.get("instance_info"));
        Object inputs = kwargs.get("inputs");
        Object outputs = kwargs.get("outputs");
        Object error = kwargs.get("error");
        Throwable throwable = toThrowable(error);
        if ("on_chain_start".equals(eventName)) {
            forEachAgentExt(h -> h.onChainStart(span, inputs, instanceInfo));
        } else if ("on_chain_end".equals(eventName)) {
            forEachAgentExt(h -> h.onChainEnd(span, outputs));
        } else if ("on_chain_error".equals(eventName)) {
            forEachAgentExt(h -> h.onChainError(span, throwable));
        } else if ("on_llm_start".equals(eventName)) {
            forEachAgentExt(h -> h.onLlmStart(span, inputs, instanceInfo));
        } else if ("on_llm_request".equals(eventName)) {
            forEachAgentExt(h -> h.onLlmRequest(span, kwargs));
        } else if ("on_llm_end".equals(eventName)) {
            forEachAgentExt(h -> h.onLlmEnd(span, outputs));
        } else if ("on_llm_error".equals(eventName)) {
            forEachAgentExt(h -> h.onLlmError(span, throwable));
        } else if ("on_prompt_start".equals(eventName)) {
            forEachAgentExt(h -> h.onPromptStart(span, inputs, instanceInfo));
        } else if ("on_prompt_end".equals(eventName)) {
            forEachAgentExt(h -> h.onPromptEnd(span, outputs));
        } else if ("on_prompt_error".equals(eventName)) {
            forEachAgentExt(h -> h.onPromptError(span, throwable));
        } else if ("on_plugin_start".equals(eventName)) {
            forEachAgentExt(h -> h.onPluginStart(span, inputs, instanceInfo));
        } else if ("on_plugin_end".equals(eventName)) {
            forEachAgentExt(h -> h.onPluginEnd(span, outputs));
        } else if ("on_plugin_error".equals(eventName)) {
            forEachAgentExt(h -> h.onPluginError(span, throwable));
        } else if ("on_retriever_start".equals(eventName)) {
            forEachAgentExt(h -> h.onRetrieverStart(span, inputs, instanceInfo));
        } else if ("on_retriever_end".equals(eventName)) {
            forEachAgentExt(h -> h.onRetrieverEnd(span, outputs));
        } else if ("on_retriever_error".equals(eventName)) {
            forEachAgentExt(h -> h.onRetrieverError(span, throwable));
        } else if ("on_evaluator_start".equals(eventName)) {
            forEachAgentExt(h -> h.onEvaluatorStart(span, inputs, instanceInfo));
        } else if ("on_evaluator_end".equals(eventName)) {
            forEachAgentExt(h -> h.onEvaluatorEnd(span, outputs));
        } else if ("on_evaluator_error".equals(eventName)) {
            forEachAgentExt(h -> h.onEvaluatorError(span, throwable));
        } else if ("on_workflow_start".equals(eventName)) {
            forEachAgentExt(h -> h.onWorkflowStart(span, inputs, instanceInfo));
        } else if ("on_workflow_end".equals(eventName)) {
            forEachAgentExt(h -> h.onWorkflowEnd(span, outputs));
        } else if ("on_workflow_error".equals(eventName)) {
            forEachAgentExt(h -> h.onWorkflowError(span, throwable));
        }
    }

    private void forEachAgentExt(Consumer<TraceExtAgentHandler> action) {
        for (TraceExtAgentHandler ext : TracerHandlerRegistry.getAgentHandlers().values()) {
            try {
                action.accept(ext);
            } catch (NullPointerException | ClassCastException | IllegalArgumentException
                    | IllegalStateException e) {
                LOG.warn("Extension agent handler failed, skipping.", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleWorkflowEvent(String handlerClassName, String eventName, Map<String, Object> kwargs) {
        TraceBaseHandler baseHandler = handlers.get(handlerClassName);
        if (!(baseHandler instanceof TraceWorkflowHandler handler)) {
            return;
        }
        String invokeId = String.valueOf(kwargs.getOrDefault("invoke_id", kwargs.getOrDefault("invokeId", "")));
        if (invokeId.isBlank()) {
            return;
        }
        boolean hasNeedSend = Boolean.TRUE.equals(kwargs.get("need_send")) || Boolean.TRUE.equals(kwargs.get("needSend"));
        Map<String, Object> metadata = castMapValue(kwargs.get("metadata"));
        Map<String, Object> componentMetadata = castMapValue(kwargs.get("component_metadata"));
        Object inputs = kwargs.get("inputs");
        Object outputs = kwargs.get("outputs");
        List<String> sourceIds = null;
        Object sourceIdsValue = kwargs.get("source_ids");
        if (sourceIdsValue instanceof List<?> list) {
            sourceIds = (List<String>) list;
        }
        if ("on_call_start".equals(eventName)) {
            handler.onCallStart(invokeId, metadata, inputs, hasNeedSend, sourceIds);
        } else if ("on_pre_invoke".equals(eventName)) {
            handler.onPreInvoke(invokeId, inputs, componentMetadata, hasNeedSend);
        } else if ("on_pre_stream".equals(eventName)) {
            handler.onPreStream(invokeId, kwargs.get("chunk"), hasNeedSend);
        } else if ("on_post_invoke".equals(eventName)) {
            handler.onPostInvoke(invokeId, outputs, inputs);
        } else if ("on_post_stream".equals(eventName)) {
            handler.onPostStream(invokeId, kwargs.get("chunk"));
        } else if ("on_invoke".equals(eventName)) {
            Exception exception = toException(kwargs.get("exception"));
            Map<String, Object> onInvokeData = null;
            if (kwargs.get("on_invoke_data") instanceof Map<?, ?>) {
                onInvokeData = castMapValue(kwargs.get("on_invoke_data"));
            }
            handler.onInvoke(invokeId, onInvokeData, exception);
        } else if ("on_call_done".equals(eventName)) {
            handler.onCallDone(invokeId, outputs);
        } else if ("on_interact".equals(eventName)) {
            handler.onInteract(invokeId, inputs, componentMetadata, hasNeedSend);
        }
    }

    /**
     * Fan-out workflow events to {@link TraceExtWorkflowHandler}s without updating TraceSchema spans.
     *
     * <p>Used when the nested built-in {@link TraceWorkflowHandler} is absent (Python parity).
     * Does not fall back to the root TraceSchema handler.</p>
     */
    @SuppressWarnings("unchecked")
    private void dispatchWorkflowExt(String eventName, Map<String, Object> kwargs) {
        String invokeId = String.valueOf(kwargs.getOrDefault("invoke_id", kwargs.getOrDefault("invokeId", "")));
        if (invokeId.isBlank()) {
            return;
        }
        boolean hasNeedSend = Boolean.TRUE.equals(kwargs.get("need_send")) || Boolean.TRUE.equals(kwargs.get("needSend"));
        Map<String, Object> metadata = castMapValue(kwargs.get("metadata"));
        Map<String, Object> componentMetadata = castMapValue(kwargs.get("component_metadata"));
        Object inputs = kwargs.get("inputs");
        Object outputs = kwargs.get("outputs");
        List<String> sourceIds = null;
        Object sourceIdsValue = kwargs.get("source_ids");
        if (sourceIdsValue instanceof List<?> list) {
            sourceIds = (List<String>) list;
        }
        if ("on_call_start".equals(eventName)) {
            List<String> finalSourceIds = sourceIds;
            forEachWorkflowExt(h -> h.onCallStart(invokeId, metadata, inputs, hasNeedSend, finalSourceIds));
        } else if ("on_pre_invoke".equals(eventName)) {
            forEachWorkflowExt(h -> h.onPreInvoke(invokeId, inputs, componentMetadata, hasNeedSend));
        } else if ("on_pre_stream".equals(eventName)) {
            Object chunk = kwargs.get("chunk");
            forEachWorkflowExt(h -> h.onPreStream(invokeId, chunk, hasNeedSend));
        } else if ("on_post_invoke".equals(eventName)) {
            forEachWorkflowExt(h -> h.onPostInvoke(invokeId, outputs, inputs));
        } else if ("on_post_stream".equals(eventName)) {
            Object chunk = kwargs.get("chunk");
            forEachWorkflowExt(h -> h.onPostStream(invokeId, chunk));
        } else if ("on_invoke".equals(eventName)) {
            Throwable exception = toThrowable(kwargs.get("exception"));
            Map<String, Object> onInvokeData = null;
            if (kwargs.get("on_invoke_data") instanceof Map<?, ?>) {
                onInvokeData = castMapValue(kwargs.get("on_invoke_data"));
            }
            Map<String, Object> finalOnInvokeData = onInvokeData;
            forEachWorkflowExt(h -> h.onInvoke(invokeId, finalOnInvokeData, exception));
        } else if ("on_call_done".equals(eventName)) {
            forEachWorkflowExt(h -> h.onCallDone(invokeId, outputs));
        } else if ("on_interact".equals(eventName)) {
            forEachWorkflowExt(h -> h.onInteract(invokeId, inputs, componentMetadata, hasNeedSend));
        }
    }

    private void forEachWorkflowExt(Consumer<TraceExtWorkflowHandler> action) {
        for (TraceExtWorkflowHandler ext : TracerHandlerRegistry.getWorkflowHandlers().values()) {
            try {
                action.accept(ext);
            } catch (NullPointerException | ClassCastException | IllegalArgumentException
                    | IllegalStateException e) {
                LOG.warn("Extension workflow handler failed, skipping.", e);
            }
        }
    }

    private static Throwable toThrowable(Object error) {
        if (error == null) {
            return null;
        }
        if (error instanceof Throwable throwable) {
            return throwable;
        }
        return new IllegalStateException("Non-throwable error: " + error);
    }

    private static Exception toException(Object error) {
        if (error == null) {
            return null;
        }
        if (error instanceof Exception exception) {
            return exception;
        }
        if (error instanceof Throwable throwable) {
            return new Exception(throwable);
        }
        return new Exception(String.valueOf(error));
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
