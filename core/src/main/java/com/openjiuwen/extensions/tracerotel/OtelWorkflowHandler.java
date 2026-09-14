/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.session.tracer.NodeStatus;
import com.openjiuwen.core.session.tracer.TraceExtWorkflowHandler;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workflow-dimension OTel handler.
 *
 * <p>Translates tracer workflow events into OTel spans. Maintains three
 * internal mappings for building a hierarchical span tree:</p>
 * <ul>
 *   <li>{@code spanManager} (invoke_id → {@link OtelSpanState}): lifecycle map</li>
 *   <li>{@code layerRootSpans} (node_id → root {@link OtelSpanState}): each
 *       workflow layer's root span</li>
 *   <li>{@code componentSpans} (node_id → component {@link OtelSpanState}): host
 *       component spans that serve as parents for sub-workflow roots</li>
 * </ul>
 *
 * <p>Every method has try/catch protection so OTel failures never propagate to
 * the business flow.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.handler.OtelWorkflowHandler}.</p>
 *
 * @since 0.1.7
 */
public class OtelWorkflowHandler extends TraceExtWorkflowHandler {
    /** LLM-related component_type substring matches. */
    private static final String[] LLM_SUBSTRINGS = {"LLM", "IntentDetection", "Questioner"};

    /** Tool-related component_type substring matches. */
    private static final String[] TOOL_SUBSTRINGS = {"Tool"};

    private final Tracer otelTracer;
    private final OtelTracerConfig config;
    private final OtelWorkflowSpanManager spanManager = new OtelWorkflowSpanManager();

    /** Mapping 2: parent_node_id → root OtelSpanState for the layer. */
    private final Map<String, OtelSpanState> layerRootSpans = new ConcurrentHashMap<>();

    /** Mapping 3: node_id → host-component OtelSpanState. */
    private final Map<String, OtelSpanState> componentSpans = new ConcurrentHashMap<>();

    /**
     * Construct a workflow-dimension OTel handler.
     *
     * @param otelTracer the OTel tracer returned by {@link OtelTracerSetup#initOtelTracer}
     * @param config     redaction / truncation configuration
     * @param traceId    tracer UUID used to bridge OTel traces with the built-in Tracer's UUID
     * @since 0.1.7
     */
    public OtelWorkflowHandler(Tracer otelTracer, OtelTracerConfig config, String traceId) {
        this.otelTracer = otelTracer;
        this.config = config;
        this.traceId = traceId != null ? traceId : "";
    }

    /**
     * Construct with trace_id injected later by {@code Tracer.init()}.
     *
     * @param otelTracer the OTel tracer returned by {@link OtelTracerSetup#initOtelTracer}
     * @param config     redaction / truncation configuration
     * @since 0.1.7
     */
    public OtelWorkflowHandler(Tracer otelTracer, OtelTracerConfig config) {
        this(otelTracer, config, null);
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Derive a parent context from an existing {@link OtelSpanState}.
     *
     * @param state the span state, or {@code null}
     * @return an {@link Optional} containing the parent context, or empty if state is null
     * @since 0.1.7
     */
    private Optional<Context> getParentContext(OtelSpanState state) {
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(Context.current().with(state.getSpan()));
    }

    /**
     * Resolve parent context for a new span based on parent_node_id and metadata.
     *
     * <p>Four-way branching:</p>
     * <ul>
     *   <li>root workflow root (parent_node_id="" + is_workflow_root) → no parent</li>
     *   <li>component in root workflow (parent_node_id="" + !is_workflow_root) → root workflow root</li>
     *   <li>sub-workflow root (parent_node_id!="" + is_workflow_root) → host component span</li>
     *   <li>component in sub-workflow (parent_node_id!="" + !is_workflow_root) → host component span</li>
     * </ul>
     *
     * @param parentNodeId the parent node ID
     * @param metadata     workflow/component metadata map
     * @return an {@link Optional} containing the parent context, or empty if no parent
     * @since 0.1.7
     */
    private Optional<Context> resolveParentContext(String parentNodeId, Map<String, Object> metadata) {
        boolean isWorkflowRoot = isWorkflowRoot(metadata);
        if ((parentNodeId == null || parentNodeId.isEmpty()) && isWorkflowRoot) {
            return Optional.empty();
        }
        if ((parentNodeId == null || parentNodeId.isEmpty()) && !isWorkflowRoot) {
            return getParentContext(layerRootSpans.get(""));
        }
        // sub-workflow root or component in sub-workflow → host component span
        return getParentContext(componentSpans.get(parentNodeId));
    }

    /**
     * Whether metadata represents a workflow root (has workflow_id but no component_id).
     *
     * @param metadata workflow/component metadata map
     * @return {@code true} if the metadata represents a workflow root
     * @since 0.1.7
     */
    private static boolean isWorkflowRoot(Map<String, Object> metadata) {
        return metadata != null && metadata.containsKey("workflow_id") && !metadata.containsKey("component_id");
    }

    /**
     * Extract the parent_node_id from metadata using instanceof for safe casting.
     *
     * @param metadata workflow/component metadata map
     * @return the parent node ID string, or empty string if not present
     * @since 0.1.7
     */
    private static String extractParentNodeId(Map<String, Object> metadata) {
        if (metadata == null) {
            return "";
        }
        Object value = metadata.get("parent_node_id");
        if (value instanceof String s) {
            return s;
        }
        return "";
    }

    /**
     * Set workflow / component attributes on an OTel span.
     *
     * @param otelSpan the OTel span to set attributes on
     * @param metadata workflow/component metadata map
     * @param invokeId the invoke ID for fallback naming
     * @since 0.1.7
     */
    private void setWorkflowAttrs(Span otelSpan, Map<String, Object> metadata, String invokeId) {
        if (metadata == null) {
            return;
        }
        if (isWorkflowRoot(metadata)) {
            otelSpan.setAttribute(SemConv.OJ_WORKFLOW_ID, getStr(metadata, "workflow_id", ""));
            otelSpan.setAttribute(SemConv.OJ_WORKFLOW_NAME, getStr(metadata, "workflow_name", ""));
            otelSpan.setAttribute(SemConv.OJ_WORKFLOW_VERSION, getStr(metadata, "workflow_version", ""));
            otelSpan.setAttribute(SemConv.OJ_WORKFLOW_EXECUTION_ID, getStr(metadata, "workflow_id", invokeId));
        } else {
            otelSpan.setAttribute(SemConv.OJ_WORKFLOW_COMPONENT_ID, getStr(metadata, "component_id", ""));
            otelSpan.setAttribute(SemConv.OJ_WORKFLOW_COMPONENT_TYPE, getStr(metadata, "component_type", ""));
            otelSpan.setAttribute(SemConv.OJ_WORKFLOW_COMPONENT_NAME, getStr(metadata, "component_name", ""));
            otelSpan.setAttribute(SemConv.OJ_WORKFLOW_ID, getStr(metadata, "workflow_id", ""));
            if (metadata.containsKey("loop_node_id")) {
                otelSpan.setAttribute(SemConv.OJ_WORKFLOW_LOOP_NODE_ID,
                        String.valueOf(metadata.get("loop_node_id")));
            }
            if (metadata.containsKey("loop_index")) {
                otelSpan.setAttribute(SemConv.OJ_WORKFLOW_LOOP_INDEX,
                        String.valueOf(metadata.get("loop_index")));
            }
        }
    }

    /**
     * Flush buffered data as span attributes before end.
     *
     * @param invokeId the invoke ID whose buffered data should be flushed
     * @since 0.1.7
     */
    private void flushBufferedData(String invokeId) {
        OtelSpanState state = spanManager.get(invokeId);
        if (state == null) {
            return;
        }
        List<Map<String, Object>> onInvokeData = spanManager.getOnInvokeData(invokeId);
        if (!onInvokeData.isEmpty()) {
            state.getSpan().setAttribute(SemConv.OJ_WORKFLOW_INVOKE_DATA, OtelAgentHandler.serialize(onInvokeData));
        }
        List<Object> streamInputs = spanManager.getStreamInputs(invokeId);
        if (!streamInputs.isEmpty()) {
            state.getSpan().setAttribute(SemConv.OJ_STREAM_INPUTS, OtelAgentHandler.serialize(streamInputs));
        }
        List<Object> streamOutputs = spanManager.getStreamOutputs(invokeId);
        if (!streamOutputs.isEmpty()) {
            state.getSpan().setAttribute(SemConv.OJ_STREAM_OUTPUTS, OtelAgentHandler.serialize(streamOutputs));
        }
    }

    /**
     * Set end_time / elapsed_time before closing a workflow span.
     *
     * @param state the span state to finalize
     * @since 0.1.7
     */
    private void setWorkflowEndAttrs(OtelSpanState state) {
        if (state.getStartTime() == null) {
            return;
        }
        LocalDateTime endTime = LocalDateTime.now();
        state.getSpan().setAttribute(SemConv.OJ_END_TIME, String.valueOf(endTime));
        long elapsedMs = Duration.between(state.getStartTime(), endTime).toMillis();
        state.getSpan().setAttribute(SemConv.OJ_ELAPSED_TIME, OtelAgentHandler.formatElapsed(elapsedMs));
    }

    /**
     * Clean up layer / component mappings for a finished invoke_id.
     *
     * @param invokeId the invoke ID to clean up
     * @since 0.1.7
     */
    private void cleanupMappings(String invokeId) {
        layerRootSpans.entrySet().removeIf(e -> invokeId.equals(e.getValue().getInvokeId()));
        componentSpans.entrySet().removeIf(e -> invokeId.equals(e.getValue().getInvokeId()));
    }

    // ================================================================
    // Lifecycle events
    // ================================================================

    @Override
    public void onCallStart(String invokeId, Map<String, Object> metadata, Object inputs,
                            boolean shouldSend, List<String> sourceIds) {
        try {
            String parent = extractParentNodeId(metadata);
            Optional<Context> parentCtx = resolveParentContext(parent, metadata);
            Map<String, Object> meta = metadata != null ? metadata : new LinkedHashMap<>();
            boolean isWorkflowRoot = isWorkflowRoot(meta);
            String componentType = getStr(meta, "component_type", "");
            boolean isLlmComponent = containsAny(componentType, LLM_SUBSTRINGS);
            SpanKind spanKind = isLlmComponent ? SpanKind.CLIENT : SpanKind.INTERNAL;
            String spanName = isWorkflowRoot ? invokeId : "component." + invokeId;

            io.opentelemetry.api.trace.SpanBuilder builder =
                    otelTracer.spanBuilder(spanName).setSpanKind(spanKind);
            parentCtx.ifPresent(builder::setParent);
            Span otelSpan = builder.startSpan();
            LocalDateTime startTime = LocalDateTime.now();
            otelSpan.setAttribute(SemConv.GEN_AI_SYSTEM, SemConv.GEN_AI_SYSTEM_VALUE);
            otelSpan.setAttribute(SemConv.OJ_TRACE_ID, traceId);
            // Workflow events carry no TraceWorkflowSpan; fall back to tracer-injected session id.
            if (sessionId != null && !sessionId.isBlank()) {
                otelSpan.setAttribute(SemConv.OJ_SESSION_ID, sessionId);
            }
            otelSpan.setAttribute(SemConv.OJ_INVOKE_ID, invokeId);
            otelSpan.setAttribute(SemConv.OJ_PARENT_NODE_ID, parent);
            otelSpan.setAttribute(SemConv.OJ_START_TIME, String.valueOf(startTime));
            if (sourceIds != null) {
                otelSpan.setAttribute(SemConv.OJ_SOURCE_IDS, OtelAgentHandler.serialize(sourceIds));
            }
            resolveOperationName(isLlmComponent, componentType)
                    .ifPresent(name -> otelSpan.setAttribute(SemConv.GEN_AI_OPERATION_NAME, name));
            setWorkflowAttrs(otelSpan, meta, invokeId);
            if (inputs != null) {
                otelSpan.setAttribute(SemConv.OJ_WORKFLOW_INPUTS, RedactionUtils.redact(inputs, config));
            }

            OtelSpanState state = new OtelSpanState(otelSpan, null, invokeId, startTime);
            spanManager.push(invokeId, state);
            registerCallStartMappings(state, isWorkflowRoot, parent, invokeId, meta);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel workflow handler: on_call_start failed: {}", exc.toString());
        }
    }

    /**
     * Resolve the GenAI operation name based on component type.
     *
     * @param isLlmComponent whether the component is LLM
     * @param componentType the component type string
     * @return the operation name, or empty Optional for non-LLM/non-Tool components
     * @since 0.1.7
     */
    private static Optional<String> resolveOperationName(boolean isLlmComponent, String componentType) {
        if (isLlmComponent) {
            return Optional.of("chat");
        }
        if (containsAny(componentType, TOOL_SUBSTRINGS)) {
            return Optional.of("execute_tool");
        }
        // non-LLM, non-Tool component: no operation name attribute
        return Optional.empty();
    }

    /**
     * Register the span state in layer/component mappings.
     *
     * @param state the span state
     * @param isWorkflowRoot whether this is a workflow root call
     * @param parent the parent node id
     * @param invokeId the invoke id
     * @param meta the metadata map
     * @since 0.1.7
     */
    private void registerCallStartMappings(OtelSpanState state, boolean isWorkflowRoot,
            String parent, String invokeId, Map<String, Object> meta) {
        if (isWorkflowRoot) {
            layerRootSpans.put(parent, state);
            // Also register this workflow root as the parent for its children.
            layerRootSpans.put(invokeId, state);
        } else {
            String componentId = getStr(meta, "component_id", "");
            if (!componentId.isEmpty()) {
                componentSpans.put(componentId, state);
            }
        }
    }

    @Override
    public void onCallDone(String invokeId, Object outputs) {
        try {
            flushBufferedData(invokeId);
            OtelSpanState state = spanManager.pop(invokeId);
            if (state == null) {
                return;
            }
            if (outputs != null) {
                state.getSpan().setAttribute(SemConv.OJ_WORKFLOW_OUTPUTS,
                        RedactionUtils.redact(outputs, config));
            }
            setWorkflowEndAttrs(state);
            state.getSpan().setAttribute(SemConv.OJ_STATUS, NodeStatus.FINISH.getValue());
            state.getSpan().setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            state.getSpan().end();
            cleanupMappings(invokeId);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel workflow handler: on_call_done failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Input/output events
    // ================================================================

    @Override
    public void onPreInvoke(String invokeId, Object inputs, Map<String, Object> componentMetadata,
                            boolean shouldSend) {
        try {
            OtelSpanState state = spanManager.get(invokeId);
            if (state == null) {
                return;
            }
            if (inputs != null) {
                state.getSpan().setAttribute(SemConv.OJ_WORKFLOW_INPUTS,
                        RedactionUtils.redact(inputs, config));
            } else {
                state.getSpan().setAttribute(SemConv.OJ_WORKFLOW_INPUTS,
                        RedactionUtils.redact(Collections.emptyMap(), config));
            }
            setWorkflowAttrs(state.getSpan(), componentMetadata, invokeId);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel workflow handler: on_pre_invoke failed: {}", exc.toString());
        }
    }

    @Override
    public void onPreStream(String invokeId, Object chunk, boolean shouldSend) {
        try {
            if (chunk instanceof Map) {
                spanManager.appendStreamInput(invokeId, chunk);
            }
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel workflow handler: on_pre_stream failed: {}", exc.toString());
        }
    }

    @Override
    public void onInvoke(String invokeId, Map<String, Object> onInvokeData, Throwable exception) {
        try {
            if (exception != null) {
                handleInvokeException(invokeId, onInvokeData, exception);
                flushBufferedData(invokeId);
                OtelSpanState popState = spanManager.pop(invokeId);
                if (popState != null) {
                    setWorkflowEndAttrs(popState);
                    popState.getSpan().end();
                    cleanupMappings(invokeId);
                }
                return;
            }

            // Non-exception: buffer on_invoke_data
            if (onInvokeData != null) {
                spanManager.appendOnInvokeData(invokeId, onInvokeData);
            }
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel workflow handler: on_invoke failed: {}", exc.toString());
        }
    }

    /**
     * Handle the exception path of {@link #onInvoke}: set error/interrupted status on the span.
     *
     * <p>Distinguishes GraphInterrupt (control-flow signal) from real errors:
     * <ul>
     *   <li>GraphInterrupt -> status="interrupted", no OTel ERROR, no OJ_ERROR</li>
     *   <li>BaseError      -> status="error", OJ_ERROR with error_code</li>
     *   <li>other Throwable -> status="error", OJ_ERROR with WORKFLOW_EXECUTION_ERROR</li>
     * </ul></p>
     *
     * @param invokeId the invoke id
     * @param onInvokeData the on_invoke_data map (may contain inner_error)
     * @param exception the exception that occurred
     * @since 0.1.7
     */
    private void handleInvokeException(String invokeId, Map<String, Object> onInvokeData, Throwable exception) {
        OtelSpanState state = spanManager.get(invokeId);
        if (state == null) {
            return;
        }
        if (isGraphInterrupt(exception)) {
            state.getSpan().setAttribute(SemConv.OJ_STATUS, NodeStatus.INTERRUPTED.getValue());
            state.getSpan().setAttribute(SemConv.OJ_WORKFLOW_ERROR_MESSAGE,
                    String.valueOf(exception.toString()));
        } else {
            state.getSpan().setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
            state.getSpan().setAttribute(SemConv.OJ_WORKFLOW_ERROR_MESSAGE,
                    getMessage(exception));
            state.getSpan().setAttribute(SemConv.OJ_STATUS, NodeStatus.ERROR.getValue());
            Map<String, Object> errorMap = new LinkedHashMap<>();
            if (exception instanceof BaseError baseError) {
                errorMap.put("error_code", baseError.getStatus().getCode());
                errorMap.put("message", baseError.getMessage());
            } else {
                errorMap.put("error_code", StatusCode.WORKFLOW_EXECUTION_ERROR.getCode());
                errorMap.put("message", getMessage(exception));
            }
            state.getSpan().setAttribute(SemConv.OJ_ERROR, OtelAgentHandler.serialize(errorMap));
            state.getSpan().recordException(exception);
        }
        // inner_error from on_invoke_data (applies to both interrupt and error paths)
        if (onInvokeData != null && onInvokeData.containsKey("inner_error")) {
            state.getSpan().setAttribute(SemConv.OJ_INNER_ERROR,
                    OtelAgentHandler.serialize(onInvokeData.get("inner_error")));
        }
    }

    @Override
    public void onPostInvoke(String invokeId, Object outputs, Object inputs) {
        try {
            OtelSpanState state = spanManager.get(invokeId);
            if (state == null) {
                return;
            }
            if (outputs != null) {
                state.getSpan().setAttribute(SemConv.OJ_WORKFLOW_OUTPUTS,
                        RedactionUtils.redact(outputs, config));
            }
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel workflow handler: on_post_invoke failed: {}", exc.toString());
        }
    }

    @Override
    public void onPostStream(String invokeId, Object chunk) {
        try {
            if (chunk instanceof Map) {
                spanManager.appendStreamOutput(invokeId, chunk);
            }
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel workflow handler: on_post_stream failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Interactive events
    // ================================================================

    @Override
    public void onInteract(String invokeId, Object inputs, Map<String, Object> componentMetadata,
                           boolean shouldSend) {
        try {
            OtelSpanState state = spanManager.get(invokeId);
            if (state == null) {
                return;
            }
            if (inputs != null) {
                state.getSpan().setAttribute(SemConv.OJ_INTERACTIVE_INPUTS,
                        RedactionUtils.redact(inputs, config));
            }
        } catch (NullPointerException | ClassCastException | IllegalArgumentException | IllegalStateException exc) {
            Loggers.SESSION.warn("otel workflow handler: on_interact failed: {}", exc.toString());
        }
    }

    // ================================================================
    // Static helpers
    // ================================================================

    /**
     * Get the error message from a throwable, falling back to its string representation.
     *
     * @param error the throwable to extract the message from
     * @return the error message, never {@code null}
     * @since 0.1.7
     */
    private static String getMessage(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.toString();
    }

    /**
     * Whether the throwable is (or wraps) a {@link GraphInterrupt}.
     *
     * <p>Develop does not expose {@code WorkflowInteraction.GraphInterruptRuntimeWrapper};
     * walk the cause chain instead.</p>
     */
    private static boolean isGraphInterrupt(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof GraphInterrupt) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Get a string value from a map with a default.
     *
     * @param map the map to look up
     * @param key the key to find
     * @param def the default value if key is missing or value is null
     * @return the string value or the default
     * @since 0.1.7
     */
    private static String getStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    /**
     * Whether {@code text} contains any of {@code substrings}.
     *
     * @param text       the text to search
     * @param substrings the substrings to match
     * @return {@code true} if any substring is found in text
     * @since 0.1.7
     */
    private static boolean containsAny(String text, String[] substrings) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String s : substrings) {
            if (text.contains(s)) {
                return true;
            }
        }
        return false;
    }
}
