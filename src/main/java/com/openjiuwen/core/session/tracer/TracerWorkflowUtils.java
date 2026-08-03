/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.WorkflowConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for workflow tracing operations.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.workflow_tracer.TracerWorkflowUtils}.
 * 
 * @since 0.1.7
 */
public final class TracerWorkflowUtils {
    /**
     * TracerWorkflowUtils.
     * 
     * @since 0.1.7
     */
    private TracerWorkflowUtils() {
    }

    /**
     * getWorkflowMetadata.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> getWorkflowMetadata(BaseSession session) {
        String workflowId = "";
        if (session instanceof WorkflowSession) {
            workflowId = ((WorkflowSession) session).workflowId();
        } else if (session instanceof NodeSession) {
            workflowId = ((NodeSession) session).workflowId();
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("workflow_id", workflowId);
        metadata.put("workflow_version", "");
        metadata.put("workflow_name", "");

        if (session.config() != null) {
            Object workflowConfig = session.config().getWorkflowConfig(workflowId);
            if (workflowConfig instanceof WorkflowConfig config && config.getCard() != null) {
                metadata.put("workflow_version", config.getCard().getVersion());
                metadata.put("workflow_name", config.getCard().getName());
            }
        }

        return metadata;
    }

    /**
     * getComponentMetadata.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> getComponentMetadata(BaseSession session) {
        Map<String, Object> metadata = new HashMap<>();

        if (session instanceof NodeSession) {
            NodeSession ns = (NodeSession) session;
            metadata.put("component_id", ns.nodeId());
            metadata.put("component_name", ns.nodeId());
            metadata.put("component_type", ns.nodeType());
            metadata.put("workflow_id", ns.workflowId());
            metadata.put("parent_node_id", ns.parentId());

            // Check loop state
            Object loopId = session.state().getGlobal(Constant.LOOP_ID);
            if (loopId != null) {
                String loopIdStr = loopId.toString();
                Object index =
                    session.state().getGlobal(loopIdStr + SessionUtils.NESTED_PATH_SPLIT + Constant.INDEX);
                metadata.put("loop_node_id", loopIdStr);
                metadata.put("loop_index", index);
            }
        }

        return metadata;
    }

    /**
     * getTracer.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static Tracer getTracer(BaseSession session) {
        Object tracer = session.tracer();
        return tracer instanceof Tracer ? (Tracer) tracer : null;
    }

    /**
     * getExecutableId.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static String getExecutableId(BaseSession session) {
        if (session instanceof NodeSession) {
            return ((NodeSession) session).executableId();
        }
        return session.sessionId();
    }

    /**
     * getParentId.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static String getParentId(BaseSession session) {
        if (session instanceof NodeSession) {
            return ((NodeSession) session).parentId();
        }
        return "";
    }

    /**
     * Trace workflow start event.
     * 
     * @param session session
     * @param inputs inputs
     * @since 0.1.7
     */
    public static void traceWorkflowStart(BaseSession session, Object inputs) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        String workflowId = "";
        if (session instanceof WorkflowSession) {
            workflowId = ((WorkflowSession) session).workflowId();
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", workflowId);
        kwargs.put("parent_node_id", "");
        kwargs.put("metadata", getWorkflowMetadata(session));
        kwargs.put("inputs", inputs);
        kwargs.put("need_send", true);
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_call_start", kwargs);
    }

    /**
     * Trace component begin event.
     * 
     * @param session session
     * @param sourceIds sourceIds
     * @since 0.1.7
     */
    public static void traceComponentBegin(BaseSession session, java.util.List<String> sourceIds) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("source_ids", sourceIds);
        kwargs.put("metadata", getComponentMetadata(session));
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_call_start", kwargs);
    }

    /**
     * Trace component inputs.
     * 
     * @param session session
     * @param inputs inputs
     * @param send send
     * @since 0.1.7
     */
    public static void traceComponentInputs(BaseSession session, Map<String, Object> inputs, boolean send) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("inputs", inputs);
        kwargs.put("need_send", send);
        kwargs.put("component_metadata", getComponentMetadata(session));
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_pre_invoke", kwargs);
    }

    /**
     * Trace component stream input.
     * 
     * @param session session
     * @param chunk chunk
     * @param send send
     * @since 0.1.7
     */
    public static void traceComponentStreamInput(BaseSession session, Object chunk, boolean send) {
        Tracer tracer = getTracer(session);
        if (tracer == null || chunk instanceof String) {
            return;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("need_send", send);
        kwargs.put("chunk", normalizeStreamChunk(chunk));
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_pre_stream", kwargs);
    }

    /**
     * Trace component outputs.
     * 
     * @param session session
     * @param outputs outputs
     * @since 0.1.7
     */
    public static void traceComponentOutputs(BaseSession session, Object outputs) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("outputs", outputs);
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_post_invoke", kwargs);
    }

    /**
     * Trace component stream output.
     * 
     * @param session session
     * @param chunk chunk
     * @since 0.1.7
     */
    public static void traceComponentStreamOutput(BaseSession session, Object chunk) {
        Tracer tracer = getTracer(session);
        if (tracer == null || chunk instanceof String) {
            return;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("chunk", normalizeStreamChunk(chunk));
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_post_stream", kwargs);
    }

    /**
     * Convert workflow stream schemas to the map representation stored by the
     * Python tracer.
     *
     * @param chunk stream chunk
     * @return trace-compatible chunk
     * @since 0.1.13
     */
    static Object normalizeStreamChunk(Object chunk) {
        if (chunk instanceof OutputSchema output) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("type", output.getType());
            normalized.put("index", output.getIndex());
            normalized.put("payload", output.getPayload());
            return normalized;
        }
        if (chunk instanceof TraceSchema trace) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("type", trace.getType());
            normalized.put("payload", trace.getPayload());
            return normalized;
        }
        if (chunk instanceof CustomSchema custom) {
            return new LinkedHashMap<>(custom.getProperties());
        }
        return chunk;
    }

    /**
     * Trace workflow done event.
     * 
     * @param session session
     * @param outputs outputs
     * @since 0.1.7
     */
    public static void traceWorkflowDone(BaseSession session, Object outputs) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        String workflowId = "";
        if (session instanceof WorkflowSession) {
            workflowId = ((WorkflowSession) session).workflowId();
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", workflowId);
        kwargs.put("parent_node_id", "");
        kwargs.put("outputs", outputs);
        kwargs.put("metadata", getWorkflowMetadata(session));
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_call_done", kwargs);
    }

    /**
     * Trace component done event.
     * 
     * @param session session
     * @since 0.1.7
     */
    public static void traceComponentDone(BaseSession session) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        String executableId = getExecutableId(session);
        String parentId = getParentId(session);
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", executableId);
        kwargs.put("parent_node_id", parentId);
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_call_done", kwargs);

        // Pop span if in a loop
        Object loopId = session.state().getGlobal(Constant.LOOP_ID);
        if (loopId != null) {
            tracer.popWorkflowSpan(executableId, parentId);
        }
    }

    /**
     * General trace data.
     * 
     * @param session session
     * @param data data
     * @since 0.1.7
     */
    public static void trace(BaseSession session, Map<String, Object> data) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("on_invoke_data", data);
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_invoke", kwargs);
    }

    /**
     * Trace an error.
     * 
     * @param session session
     * @param error error
     * @since 0.1.7
     */
    public static void traceError(BaseSession session, Exception error) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        if (error == null) {
            throw new IllegalArgumentException("trace_error's error must not be null");
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("exception", error);
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_invoke", kwargs);
    }

    /**
     * Trace component interactive inputs.
     * 
     * @param session session
     * @param inputs inputs
     * @param send send
     * @since 0.1.7
     */
    public static void traceComponentInteractiveInputs(BaseSession session, Object inputs, boolean send) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("inputs", inputs);
        kwargs.put("need_send", send);
        kwargs.put("component_metadata", getComponentMetadata(session));
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_interact", kwargs);
    }

    /**
     * Register a dedicated workflow span manager for nested workflow tracing.
     * 
     * @param session session
     * @since 0.1.7
     */
    public static void registerWorkflowSpanManager(BaseSession session) {
        Tracer tracer = getTracer(session);
        if (tracer == null) {
            return;
        }
        tracer.registerWorkflowSpanManager(getExecutableId(session));
    }
}
