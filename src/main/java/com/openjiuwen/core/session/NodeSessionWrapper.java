/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.modelclients.BaseModelClient;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.interaction.WorkflowInteraction;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Node session wrapper for managing node execution context within a workflow.
 * 
 * <p>This class wraps a NodeSession to provide a high-level interface
 * for node operations including state management, tracing, interaction,
 * and stream writing.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/node.py - Session
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class NodeSessionWrapper {
    
    /**
     * The inner node session.
     */
    private final NodeSession inner;
    
    /**
     * The workflow interaction handler.
     */
    private WorkflowInteraction interaction;
    
    /**
     * Whether stream mode is enabled.
     */
    private final boolean streamMode;
    
    /**
     * Creates a new NodeSessionWrapper.
     * 
     * @param session the inner node session
     */
    public NodeSessionWrapper(NodeSession session) {
        this(session, false);
    }
    
    /**
     * Creates a new NodeSessionWrapper with stream mode option.
     * 
     * @param session the inner node session
     * @param streamMode whether stream mode is enabled
     */
    public NodeSessionWrapper(NodeSession session, boolean streamMode) {
        this.inner = session;
        this.interaction = null;
        this.streamMode = streamMode;
    }
    
    /**
     * Gets the workflow ID.
     * 
     * @return the workflow ID
     */
    public String getWorkflowId() {
        return inner.getWorkflowId();
    }
    
    /**
     * Gets the component (node) ID.
     * 
     * @return the component ID
     */
    public String getComponentId() {
        return inner.getNodeId();
    }
    
    /**
     * Gets the component (node) type.
     * 
     * @return the component type
     */
    public String getComponentType() {
        return inner.getNodeType();
    }
    
    /**
     * Traces data for this node.
     * 
     * @param data the data to trace
     * @return a CompletableFuture that completes when tracing is done
     */
    public CompletableFuture<Void> trace(Map<String, Object> data) {
        return TracerWorkflowUtils.trace(inner, data);
    }
    
    /**
     * Traces an error for this node.
     * 
     * @param error the error to trace
     * @return a CompletableFuture that completes when tracing is done
     */
    public CompletableFuture<Void> traceError(Exception error) {
        return TracerWorkflowUtils.traceError(inner, error);
    }
    
    /**
     * Handles user interaction.
     * 
     * <p>This method is not supported in stream mode and will throw an exception.
     * 
     * @param value the interaction value
     * @return a CompletableFuture that completes with the user's input
     * @throws JiuWenBaseException if stream mode is enabled
     */
    public CompletableFuture<Object> interact(Object value) {
        if (streamMode) {
            return CompletableFuture.failedFuture(new JiuWenBaseException(
                StatusCode.WORKFLOW_STREAM_NOT_SUPPORT.getCode(),
                StatusCode.WORKFLOW_STREAM_NOT_SUPPORT.formatMessage(
                    Map.of("error_msg", "streaming process interface(transform or collect)")
                )
            ));
        }
        
        if (interaction == null) {
            interaction = new WorkflowInteraction(inner);
        }
        
        return interaction.waitUserInputs(value);
    }
    
    /**
     * Gets the executable ID.
     * 
     * @return the executable ID
     */
    public String getExecutableId() {
        return inner.getExecutableId();
    }
    
    /**
     * Gets the session ID.
     * 
     * @return the session ID
     */
    public String getSessionId() {
        return inner.getSessionId();
    }
    
    /**
     * Updates the node state.
     * 
     * @param data the data to update
     */
    public void updateState(Map<String, Object> data) {
        inner.getState().update(data);
    }
    
    /**
     * Gets state value by key.
     * 
     * @param key the state key (can be String, List, or Map)
     * @return the state value
     */
    public Object getState(Object key) {
        if (key == null) {
            return inner.getState().get((String) null);
        }
        if (key instanceof String strKey) {
            return inner.getState().get(strKey);
        }
        // For list or dict keys, use reflection to call appropriate method
        try {
            if (key instanceof java.util.List<?>) {
                var method = inner.getState().getClass().getMethod("get", java.util.List.class);
                return method.invoke(inner.getState(), key);
            } else if (key instanceof Map<?, ?>) {
                var method = inner.getState().getClass().getMethod("get", Map.class);
                return method.invoke(inner.getState(), key);
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        return inner.getState().get(key.toString());
    }
    
    /**
     * Gets state value with default key (null).
     * 
     * @return the state value
     */
    public Object getState() {
        return getState(null);
    }
    
    /**
     * Updates the global state.
     * 
     * @param data the data to update
     */
    public void updateGlobalState(Map<String, Object> data) {
        inner.getState().updateGlobal(data);
    }
    
    /**
     * Gets global state value by key.
     * 
     * @param key the state key (can be String, List, or Map)
     * @return the global state value
     */
    public Object getGlobalState(Object key) {
        if (key == null) {
            return inner.getState().getGlobal((String) null);
        }
        if (key instanceof String strKey) {
            return inner.getState().getGlobal(strKey);
        }
        // For list or dict keys, use reflection to call appropriate method
        try {
            if (key instanceof java.util.List<?>) {
                var method = inner.getState().getClass().getMethod("getGlobal", java.util.List.class);
                return method.invoke(inner.getState(), key);
            } else if (key instanceof Map<?, ?>) {
                var method = inner.getState().getClass().getMethod("getGlobal", Map.class);
                return method.invoke(inner.getState(), key);
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        return inner.getState().getGlobal(key.toString());
    }
    
    /**
     * Gets global state value with default key (null).
     * 
     * @return the global state value
     */
    public Object getGlobalState() {
        return getGlobalState(null);
    }
    
    /**
     * Writes data to the output stream.
     * 
     * @param data the data to write (can be a Map or OutputSchema)
     * @return a CompletableFuture that completes when writing is done
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> writeStream(Object data) {
        StreamWriter<Map<String, Object>, OutputSchema> writer = streamWriter();
        if (writer != null) {
            return writer.write((Map<String, Object>) data);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Writes data to the custom stream.
     * 
     * @param data the data to write
     * @return a CompletableFuture that completes when writing is done
     */
    public CompletableFuture<Void> writeCustomStream(Map<String, Object> data) {
        StreamWriter<Map<String, Object>, ?> writer = customWriter();
        if (writer != null) {
            return writer.write(data);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Gets the callback manager.
     * 
     * @return the callback manager
     */
    public CallbackManager getCallbackManager() {
        return inner.getCallbackManager();
    }
    
    /**
     * Gets an environment variable value.
     * 
     * @param key the environment variable key
     * @return the value, or null if not found
     */
    public Object getEnv(String key) {
        return inner.getConfig().getEnv(key);
    }
    
    /**
     * Gets the output stream writer.
     * 
     * @return the output stream writer, or null if not available
     */
    @SuppressWarnings("unchecked")
    private StreamWriter<Map<String, Object>, OutputSchema> streamWriter() {
        StreamWriterManager manager = inner.getStreamWriterManager();
        if (manager != null) {
            return (StreamWriter<Map<String, Object>, OutputSchema>) (Object) manager.getOutputWriter();
        }
        return null;
    }
    
    /**
     * Gets the custom stream writer.
     * 
     * @return the custom stream writer, or null if not available
     */
    @SuppressWarnings("unchecked")
    private StreamWriter<Map<String, Object>, ?> customWriter() {
        StreamWriterManager manager = inner.getStreamWriterManager();
        if (manager != null) {
            return (StreamWriter<Map<String, Object>, ?>) (Object) manager.getCustomWriter();
        }
        return null;
    }
    
    // ========== Resource interface methods ==========
    // Note: These will be deleted when resource_mgr supports tag feature
    
    /**
     * Gets a prompt template by ID.
     * 
     * @param templateId the template ID
     * @return the prompt template
     */
    public PromptTemplate getPrompt(String templateId) {
        Object resourceManager = getResourceManager();
        if (resourceManager != null) {
            try {
                var registryMethod = resourceManager.getClass().getMethod("getResourceRegistry");
                Object registry = registryMethod.invoke(resourceManager);
                if (registry != null) {
                    var promptMethod = registry.getClass().getMethod("prompt");
                    Object promptRegistry = promptMethod.invoke(registry);
                    if (promptRegistry != null) {
                        var getPromptMethod = promptRegistry.getClass().getMethod("getPrompt", String.class);
                        return (PromptTemplate) getPromptMethod.invoke(promptRegistry, templateId);
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return null;
    }
    
    /**
     * Gets a model client by ID.
     * 
     * @param modelId the model ID
     * @return the model client
     */
    public BaseModelClient getModel(String modelId) {
        Object resourceManager = getResourceManager();
        if (resourceManager != null) {
            try {
                var registryMethod = resourceManager.getClass().getMethod("getResourceRegistry");
                Object registry = registryMethod.invoke(resourceManager);
                if (registry != null) {
                    var modelMethod = registry.getClass().getMethod("model");
                    Object modelRegistry = modelMethod.invoke(registry);
                    if (modelRegistry != null) {
                        var getModelMethod = modelRegistry.getClass().getMethod("getModel", String.class);
                        return (BaseModelClient) getModelMethod.invoke(modelRegistry, modelId);
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return null;
    }
    
    /**
     * Gets a workflow by ID asynchronously.
     * 
     * @param workflowId the workflow ID
     * @return a CompletableFuture that completes with the workflow
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Object> getWorkflow(String workflowId) {
        Object resourceManager = getResourceManager();
        if (resourceManager != null) {
            try {
                var registryMethod = resourceManager.getClass().getMethod("getResourceRegistry");
                Object registry = registryMethod.invoke(resourceManager);
                if (registry != null) {
                    var workflowMethod = registry.getClass().getMethod("workflow");
                    Object workflowRegistry = workflowMethod.invoke(registry);
                    if (workflowRegistry != null) {
                        var getWorkflowMethod = workflowRegistry.getClass().getMethod("getWorkflow", String.class);
                        Object result = getWorkflowMethod.invoke(workflowRegistry, workflowId);
                        if (result instanceof CompletableFuture<?> future) {
                            return (CompletableFuture<Object>) future;
                        }
                        return CompletableFuture.completedFuture(result);
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Gets a workflow by ID synchronously.
     * 
     * @param workflowId the workflow ID
     * @return the workflow
     */
    public Object getWorkflowSync(String workflowId) {
        Object resourceManager = getResourceManager();
        if (resourceManager != null) {
            try {
                var registryMethod = resourceManager.getClass().getMethod("getResourceRegistry");
                Object registry = registryMethod.invoke(resourceManager);
                if (registry != null) {
                    var workflowMethod = registry.getClass().getMethod("workflow");
                    Object workflowRegistry = workflowMethod.invoke(registry);
                    if (workflowRegistry != null) {
                        var getWorkflowSyncMethod = workflowRegistry.getClass().getMethod("getWorkflowSync", String.class);
                        return getWorkflowSyncMethod.invoke(workflowRegistry, workflowId);
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return null;
    }
    
    /**
     * Gets a tool by ID.
     * 
     * @param toolId the tool ID
     * @return the tool
     */
    @SuppressWarnings("rawtypes")
    public Tool getTool(String toolId) {
        Object resourceManager = getResourceManager();
        if (resourceManager != null) {
            try {
                var registryMethod = resourceManager.getClass().getMethod("getResourceRegistry");
                Object registry = registryMethod.invoke(resourceManager);
                if (registry != null) {
                    var toolMethod = registry.getClass().getMethod("tool");
                    Object toolRegistry = toolMethod.invoke(registry);
                    if (toolRegistry != null) {
                        var getToolMethod = toolRegistry.getClass().getMethod("getTool", String.class);
                        return (Tool) getToolMethod.invoke(toolRegistry, toolId);
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return null;
    }
    
    /**
     * Gets the resource manager from the inner session.
     * 
     * @return the resource manager, or null if not available
     */
    private Object getResourceManager() {
        try {
            var method = inner.getClass().getMethod("resourceManager");
            return method.invoke(inner);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets the inner node session.
     * 
     * @return the inner node session
     */
    public NodeSession getInner() {
        return inner;
    }
}

