/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.WorkflowConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow tracing helpers for node and workflow sessions.
 *
 * <p>Mirrors Python's {@code TracerWorkflowUtils} in
 * {@code openjiuwen/core/session/tracer/workflow_tracer.py}.</p>
 */
public final class TracerWorkflowUtils {

    private TracerWorkflowUtils() {
    }

    public static void traceWorkflowStart(BaseSession session, Map<String, Object> inputs) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_call_start",
                payload(
                        "invoke_id", workflowId(session),
                        "parent_node_id", "",
                        "metadata", workflowMetadata(session),
                        "inputs", inputs,
                        "need_send", true
                ));
    }

    public static void traceComponentBegin(BaseSession session) {
        traceComponentBegin(session, null);
    }

    public static void traceComponentBegin(BaseSession session, java.util.List<String> sourceIds) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoke_id", executableId(session));
        payload.put("parent_node_id", parentId(session));
        payload.put("source_ids", sourceIds);
        payload.put("metadata", componentMetadata(session));
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_call_start", payload);
    }

    public static void traceComponentInputs(BaseSession session, Map<String, Object> inputs, boolean send) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_pre_invoke",
                payload(
                        "invoke_id", executableId(session),
                        "parent_node_id", parentId(session),
                        "inputs", inputs,
                        "need_send", send,
                        "component_metadata", componentMetadata(session)
                ));
    }

    public static void traceComponentStreamInput(BaseSession session, Object chunk, boolean send) {
        Tracer tracer = tracer(session);
        if (tracer == null || chunk instanceof String) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_pre_stream",
                payload(
                        "invoke_id", executableId(session),
                        "parent_node_id", parentId(session),
                        "need_send", send,
                        "chunk", copyMappingChunk(chunk)
                ));
    }

    public static void traceComponentOutputs(BaseSession session, Map<String, Object> outputs) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_post_invoke",
                payload(
                        "invoke_id", executableId(session),
                        "parent_node_id", parentId(session),
                        "outputs", outputs
                ));
    }

    public static void traceComponentStreamOutput(BaseSession session, Object chunk) {
        Tracer tracer = tracer(session);
        if (tracer == null || chunk instanceof String) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_post_stream",
                payload(
                        "invoke_id", executableId(session),
                        "parent_node_id", parentId(session),
                        "chunk", copyMappingChunk(chunk)
                ));
    }

    public static void traceWorkflowDone(BaseSession session, Map<String, Object> outputs) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_call_done",
                payload(
                        "invoke_id", workflowId(session),
                        "parent_node_id", "",
                        "outputs", outputs,
                        "metadata", workflowMetadata(session)
                ));
    }

    public static void traceComponentDone(BaseSession session) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_call_done",
                payload(
                        "invoke_id", executableId(session),
                        "parent_node_id", parentId(session)
                ));
        SessionStateAccess state = state(session);
        Object loopId = state == null ? null : state.getGlobal(Constant.LOOP_ID);
        if (loopId != null) {
            tracer.popWorkflowSpan(executableId(session), parentId(session));
        }
    }

    public static void trace(BaseSession session, Map<String, Object> data) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_invoke",
                payload(
                        "invoke_id", executableId(session),
                        "parent_node_id", parentId(session),
                        "on_invoke_data", data
                ));
    }

    public static void traceError(BaseSession session, Throwable error) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        if (error == null) {
            throw ErrorHelper.buildError(StatusCode.TRACER_WORKFLOW_TRACE_ERROR,
                    "reason", "'trace_error''s error is None");
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_invoke",
                payload(
                        "invoke_id", executableId(session),
                        "parent_node_id", parentId(session),
                        "exception", error
                ));
    }

    public static void traceComponentInteractiveInputs(BaseSession session, Object inputs, boolean send) {
        Tracer tracer = tracer(session);
        if (tracer == null) {
            return;
        }
        tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "on_interact",
                payload(
                        "invoke_id", executableId(session),
                        "parent_node_id", parentId(session),
                        "inputs", inputs,
                        "need_send", send,
                        "component_metadata", componentMetadata(session)
                ));
    }

    private static Tracer tracer(BaseSession session) {
        return session != null && session.tracer() instanceof Tracer tracer ? tracer : null;
    }

    private static Map<String, Object> workflowMetadata(BaseSession session) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflow_id", workflowId(session));
        Object workflowCard = workflowCard(session);
        metadata.put("workflow_version", stringProperty(workflowCard, "version"));
        metadata.put("workflow_name", stringProperty(workflowCard, "name"));
        return metadata;
    }

    private static Map<String, Object> componentMetadata(BaseSession session) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String nodeId = stringMethod(session, "nodeId");
        metadata.put("component_id", nodeId);
        metadata.put("component_name", nodeId);
        metadata.put("component_type", stringMethod(session, "nodeType"));
        metadata.put("workflow_id", workflowId(session));
        SessionStateAccess state = state(session);
        Object loopId = state == null ? null : state.getGlobal(Constant.LOOP_ID);
        if (loopId != null) {
            metadata.put("loop_node_id", loopId);
            metadata.put("loop_index", state.getGlobal(String.valueOf(loopId) + SessionUtils.NESTED_PATH_SPLIT
                    + Constant.INDEX));
        }
        return metadata;
    }

    private static Object workflowCard(BaseSession session) {
        SessionConfigAccess config = session == null ? null : session.config();
        Object workflowConfig = config == null ? null : config.getWorkflowConfig(workflowId(session));
        if (workflowConfig instanceof WorkflowConfig typedConfig) {
            return typedConfig.getCard();
        }
        if (workflowConfig instanceof Map<?, ?> mapConfig) {
            Object card = mapConfig.get("card");
            return card == null ? mapConfig.get("workflow_metadata") : card;
        }
        return invokeNoArg(workflowConfig, "getCard");
    }

    private static SessionStateAccess state(BaseSession session) {
        return session == null ? null : session.state();
    }

    private static String workflowId(BaseSession session) {
        return stringMethod(session, "workflowId");
    }

    private static String executableId(BaseSession session) {
        return stringMethod(session, "executableId");
    }

    private static String parentId(BaseSession session) {
        return stringMethod(session, "parentId");
    }

    private static String stringMethod(BaseSession session, String methodName) {
        if (session == null) {
            return "";
        }
        Object value = invokeNoArg(session, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringProperty(Object target, String propertyName) {
        if (target == null) {
            return "";
        }
        if (target instanceof Map<?, ?> map) {
            Object value = map.get(propertyName);
            return value == null ? "" : String.valueOf(value);
        }
        String accessor = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        Object value = invokeNoArg(target, accessor);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }

    private static Object copyMappingChunk(Object chunk) {
        if (chunk instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return copy;
        }
        return chunk;
    }
}
