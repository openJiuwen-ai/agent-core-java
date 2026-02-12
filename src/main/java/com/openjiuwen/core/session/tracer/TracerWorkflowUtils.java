/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.tracer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.SessionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for workflow tracing operations.
 * 
 * <p>Provides static methods for tracing workflow lifecycle events including
 * start, component begin/end, inputs/outputs, and errors.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public final class TracerWorkflowUtils {
    
    private TracerWorkflowUtils() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Gets workflow metadata from session.
     * 
     * @param session the workflow session
     * @return the workflow metadata map
     */
    public static Map<String, Object> getWorkflowMetadata(WorkflowSession session) {
        String executableId = session.workflowId();
        Object workflowConfig = session.config() != null ? session.config().getWorkflowConfig(executableId) : null;
        Object workflowMetadata = null;
        if (workflowConfig != null) {
            try {
                // Try to get card from workflow config
                workflowMetadata = workflowConfig.getClass().getMethod("getCard").invoke(workflowConfig);
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("workflow_id", executableId);
        
        String version = "";
        String name = "";
        if (workflowMetadata != null) {
            try {
                Object versionObj = workflowMetadata.getClass().getMethod("getVersion").invoke(workflowMetadata);
                version = versionObj != null ? versionObj.toString() : "";
                Object nameObj = workflowMetadata.getClass().getMethod("getName").invoke(workflowMetadata);
                name = nameObj != null ? nameObj.toString() : "";
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        result.put("workflow_version", version);
        result.put("workflow_name", name);
        
        return result;
    }
    
    /**
     * Gets component metadata from session.
     * 
     * @param session the workflow session
     * @return the component metadata map
     */
    public static Map<String, Object> getComponentMetadata(WorkflowSession session) {
        Map<String, Object> componentMetadata = new HashMap<>();
        componentMetadata.put("component_id", session.nodeId());
        componentMetadata.put("component_name", session.nodeId());
        componentMetadata.put("component_type", session.nodeType());
        componentMetadata.put("workflow_id", session.workflowId());
        
        Object state = session.state();
        String loopId = null;
        if (state != null) {
            try {
                loopId = (String) state.getClass().getMethod("getGlobal", String.class)
                    .invoke(state, Constant.LOOP_ID);
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        
        if (loopId == null) {
            return componentMetadata;
        }
        
        Object index = null;
        try {
            String indexKey = loopId + SessionUtils.NESTED_PATH_SPLIT + Constant.INDEX;
            index = state.getClass().getMethod("getGlobal", String.class).invoke(state, indexKey);
        } catch (Exception e) {
            // Ignore reflection errors
        }
        
        componentMetadata.put("loop_node_id", loopId);
        componentMetadata.put("loop_index", index);
        
        return componentMetadata;
    }
    
    /**
     * Traces workflow start event.
     * 
     * @param session the workflow session
     * @param inputs the workflow inputs
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceWorkflowStart(WorkflowSession session, Map<String, Object> inputs) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", session.workflowId());
        kwargs.put("parent_node_id", "");
        kwargs.put("metadata", getWorkflowMetadata(session));
        kwargs.put("inputs", inputs);
        kwargs.put("need_send", true);
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onCallStart", kwargs);
    }
    
    /**
     * Traces component begin event.
     * 
     * @param session the workflow session
     * @param sourceIds the source node IDs
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceComponentBegin(WorkflowSession session, List<String> sourceIds) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String executableId = session.executableId();
        String parentId = session.parentId();
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", executableId);
        kwargs.put("parent_node_id", parentId);
        kwargs.put("source_ids", sourceIds);
        kwargs.put("metadata", getComponentMetadata(session));
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onCallStart", kwargs);
    }
    
    /**
     * Traces component inputs event.
     * 
     * @param session the workflow session
     * @param inputs the component inputs
     * @param send whether to send the trace data
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceComponentInputs(WorkflowSession session, Map<String, Object> inputs, 
                                                                boolean send) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String executableId = session.executableId();
        String parentId = session.parentId();
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", executableId);
        kwargs.put("parent_node_id", parentId);
        kwargs.put("inputs", inputs);
        kwargs.put("need_send", send);
        kwargs.put("component_metadata", getComponentMetadata(session));
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onPreInvoke", kwargs);
    }
    
    /**
     * Traces component stream input event.
     * 
     * @param session the workflow session
     * @param chunk the input chunk
     * @param send whether to send the trace data
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceComponentStreamInput(WorkflowSession session, Object chunk, 
                                                                     boolean send) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String executableId = session.executableId();
        String parentId = session.parentId();
        
        // Skip if chunk is a string
        if (chunk instanceof String) {
            return CompletableFuture.completedFuture(null);
        }
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", executableId);
        kwargs.put("parent_node_id", parentId);
        kwargs.put("need_send", send);
        kwargs.put("chunk", chunk);
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onPreStream", kwargs);
    }
    
    /**
     * Traces component outputs event.
     * 
     * @param session the workflow session
     * @param outputs the component outputs
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceComponentOutputs(WorkflowSession session, Map<String, Object> outputs) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String executableId = session.executableId();
        String parentId = session.parentId();
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", executableId);
        kwargs.put("parent_node_id", parentId);
        kwargs.put("outputs", outputs);
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onPostInvoke", kwargs);
    }
    
    /**
     * Traces component stream output event.
     * 
     * @param session the workflow session
     * @param chunk the output chunk
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceComponentStreamOutput(WorkflowSession session, Object chunk) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String executableId = session.executableId();
        String parentId = session.parentId();
        
        // Skip if chunk is a string
        if (chunk instanceof String) {
            return CompletableFuture.completedFuture(null);
        }
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", executableId);
        kwargs.put("parent_node_id", parentId);
        kwargs.put("chunk", chunk);
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onPostStream", kwargs);
    }
    
    /**
     * Traces workflow done event.
     * 
     * @param session the workflow session
     * @param outputs the workflow outputs
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceWorkflowDone(WorkflowSession session, Map<String, Object> outputs) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String executableId = session.workflowId();
        String parentId = "";
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", executableId);
        kwargs.put("parent_node_id", parentId);
        kwargs.put("outputs", outputs);
        kwargs.put("metadata", getWorkflowMetadata(session));
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onCallDone", kwargs);
    }
    
    /**
     * Traces component done event.
     * 
     * @param session the workflow session
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceComponentDone(WorkflowSession session) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String executableId = session.executableId();
        String parentId = session.parentId();
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", executableId);
        kwargs.put("parent_node_id", parentId);
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onCallDone", kwargs)
            .thenRun(() -> {
                Object state = session.state();
                String loopId = null;
                if (state != null) {
                    try {
                        loopId = (String) state.getClass().getMethod("getGlobal", String.class)
                            .invoke(state, Constant.LOOP_ID);
                    } catch (Exception e) {
                        // Ignore reflection errors
                    }
                }
                
                if (loopId != null) {
                    tracer.popWorkflowSpan(executableId, parentId);
                }
            });
    }
    
    /**
     * Traces invoke data event.
     * 
     * @param session the workflow session
     * @param data the trace data
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> trace(WorkflowSession session, Map<String, Object> data) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        String invokeId = session.executableId();
        String parentId = session.parentId();
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", invokeId);
        kwargs.put("parent_node_id", parentId);
        kwargs.put("on_invoke_data", data);
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onInvoke", kwargs);
    }
    
    /**
     * Traces error event.
     * 
     * @param session the workflow session
     * @param error the error that occurred
     * @return a CompletableFuture that completes when tracing is done
     */
    public static CompletableFuture<Void> traceError(WorkflowSession session, Exception error) {
        Tracer tracer = session.tracer();
        if (tracer == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (error == null) {
            throw new JiuWenBaseException(
                StatusCode.ERROR.getCode(),
                "trace error failed: error is None");
        }
        
        String invokeId = session.executableId();
        String parentId = session.parentId();
        
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("invoke_id", invokeId);
        kwargs.put("parent_node_id", parentId);
        kwargs.put("exception", error);
        
        return tracer.trigger(TracerHandlerName.TRACER_WORKFLOW.getValue(), "onInvoke", kwargs);
    }
    
    /**
     * Interface for workflow session operations used by tracer utilities.
     * 
     * <p>This interface defines the contract for accessing session information
     * needed for workflow tracing.
     */
    public interface WorkflowSession {
        
        /**
         * Gets the tracer instance.
         * 
         * @return the tracer, or null if tracing is disabled
         */
        Tracer tracer();
        
        /**
         * Gets the workflow ID.
         * 
         * @return the workflow ID
         */
        String workflowId();
        
        /**
         * Gets the executable ID (current node execution ID).
         * 
         * @return the executable ID
         */
        String executableId();
        
        /**
         * Gets the parent node ID.
         * 
         * @return the parent node ID
         */
        String parentId();
        
        /**
         * Gets the current node ID.
         * 
         * @return the node ID
         */
        String nodeId();
        
        /**
         * Gets the current node type.
         * 
         * @return the node type
         */
        String nodeType();
        
        /**
         * Gets the session state.
         * 
         * @return the state object
         */
        Object state();
        
        /**
         * Gets the session configuration.
         * 
         * @return the configuration object
         */
        WorkflowConfig config();
    }
    
    /**
     * Interface for workflow configuration.
     */
    public interface WorkflowConfig {
        
        /**
         * Gets the workflow configuration for the given workflow ID.
         * 
         * @param workflowId the workflow ID
         * @return the workflow configuration object
         */
        Object getWorkflowConfig(String workflowId);
    }
}

