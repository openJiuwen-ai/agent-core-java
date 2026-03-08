/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.utils.SessionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for workflow tracing operations.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.tracer.workflow_tracer.TracerWorkflowUtils}.
 */
public final class TracerWorkflowUtils {

    private TracerWorkflowUtils() {
    }

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

        // Attempt to get workflow config for richer metadata
        if (session.config() != null) {
            Object workflowConfig = session.config().getWorkflowConfig(workflowId);
            // Placeholder: in full implementation, extract version/name from workflow config card
        }

        return metadata;
    }

    private static Map<String, Object> getComponentMetadata(BaseSession session) {
        Map<String, Object> metadata = new HashMap<>();

        if (session instanceof NodeSession) {
            NodeSession ns = (NodeSession) session;
            metadata.put("component_id", ns.nodeId());
            metadata.put("component_name", ns.nodeId());
            metadata.put("component_type", ns.nodeType());
            metadata.put("workflow_id", ns.workflowId());

            // Check loop state
            Object loopId = session.state().getGlobal(SessionConstants.LOOP_ID);
            if (loopId != null) {
                String loopIdStr = loopId.toString();
                Object index = session.state().getGlobal(
                        loopIdStr + SessionUtils.NESTED_PATH_SPLIT + SessionConstants.INDEX);
                metadata.put("loop_node_id", loopIdStr);
                metadata.put("loop_index", index);
            }
        }

        return metadata;
    }

    private static Tracer getTracer(BaseSession session) {
        Object tracer = session.tracer();
        return tracer instanceof Tracer ? (Tracer) tracer : null;
    }

    private static String getExecutableId(BaseSession session) {
        if (session instanceof NodeSession) {
            return ((NodeSession) session).executableId();
        }
        return session.sessionId();
    }

    private static String getParentId(BaseSession session) {
        if (session instanceof NodeSession) {
            return ((NodeSession) session).parentId();
        }
        return "";
    }

    /**
     * Trace workflow start event.
     */
    public static void traceWorkflowStart(BaseSession session, Map<String, Object> inputs) {
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
        kwargs.put("chunk", chunk);
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_pre_stream", kwargs);
    }

    /**
     * Trace component outputs.
     */
    public static void traceComponentOutputs(BaseSession session, Map<String, Object> outputs) {
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
     */
    public static void traceComponentStreamOutput(BaseSession session, Object chunk) {
        Tracer tracer = getTracer(session);
        if (tracer == null || chunk instanceof String) {
            return;
        }
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", getExecutableId(session));
        kwargs.put("parent_node_id", getParentId(session));
        kwargs.put("chunk", chunk);
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_post_stream", kwargs);
    }

    /**
     * Trace workflow done event.
     */
    public static void traceWorkflowDone(BaseSession session, Map<String, Object> outputs) {
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
        Object loopId = session.state().getGlobal(SessionConstants.LOOP_ID);
        if (loopId != null) {
            tracer.popWorkflowSpan(executableId, parentId);
        }
    }

    /**
     * General trace data.
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
     */
    public static void traceComponentInteractiveInputs(BaseSession session, Map<String, Object> inputs,
                                                        boolean send) {
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
}
