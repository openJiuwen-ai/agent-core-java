/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.session.tracer.Tracer;
import com.openjiuwen.core.session.tracer.TracerWorkflowUtils;

/**
 * Node session implementation for managing node execution context within a workflow.
 * 
 * <p>Each node in a workflow has its own NodeSession that manages node-specific
 * state while delegating common operations to the parent session.
 * 
 * <p>对应 Python: agent-core/openjiuwen/core/session/internal/workflow.py - NodeSession
 *
 * @author OpenJiuwen
 * @since 1.0.0
 */
public class NodeSession implements BaseSession, TracerWorkflowUtils.WorkflowSession {
    
    private final String nodeId;
    private final String nodeType;
    private final String parentId;
    private final String executableId;
    private final State state;
    private final BaseSession session;
    protected String workflowId;
    protected int workflowNestingDepth;
    protected String mainWorkflowId;
    
    /**
     * Creates a new NodeSession.
     * 
     * @param session the parent session (WorkflowSession or NodeSession)
     * @param nodeId the node ID
     */
    public NodeSession(BaseSession session, String nodeId) {
        this(session, nodeId, null);
    }
    
    /**
     * Creates a new NodeSession with a node type.
     * 
     * @param session the parent session (WorkflowSession or NodeSession)
     * @param nodeId the node ID
     * @param nodeType the node type (can be null)
     */
    public NodeSession(BaseSession session, String nodeId, String nodeType) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.parentId = SessionHelper.createParentId(session);
        this.executableId = SessionHelper.createExecutableId(nodeId, this.parentId);
        this.state = session.getState().createNodeState(this.executableId, this.parentId);
        this.session = session;
        
        // Extract workflow information from parent session
        if (session instanceof WorkflowSession ws) {
            this.workflowId = ws.getWorkflowId();
            this.workflowNestingDepth = ws.getWorkflowNestingDepth();
            this.mainWorkflowId = ws.getMainWorkflowId();
        } else if (session instanceof NodeSession ns) {
            this.workflowId = ns.getWorkflowId();
            this.workflowNestingDepth = ns.getWorkflowNestingDepth();
            this.mainWorkflowId = ns.getMainWorkflowId();
        } else {
            this.workflowId = "";
            this.workflowNestingDepth = 0;
            this.mainWorkflowId = "";
        }
    }
    
    /**
     * Gets the node ID.
     * 
     * @return the node ID
     */
    public String getNodeId() {
        return nodeId;
    }
    
    /**
     * Gets the node type.
     * 
     * @return the node type
     */
    public String getNodeType() {
        return nodeType;
    }
    
    /**
     * Gets the executable ID.
     * 
     * @return the executable ID
     */
    public String getExecutableId() {
        return executableId;
    }
    
    /**
     * Gets the parent ID.
     * 
     * @return the parent ID
     */
    public String getParentId() {
        return parentId;
    }
    
    /**
     * Gets the workflow ID.
     * 
     * @return the workflow ID
     */
    public String getWorkflowId() {
        return workflowId;
    }
    
    /**
     * Gets the main workflow ID.
     * 
     * @return the main workflow ID
     */
    public String getMainWorkflowId() {
        return mainWorkflowId;
    }
    
    /**
     * Gets the workflow nesting depth.
     * 
     * @return the workflow nesting depth
     */
    public int getWorkflowNestingDepth() {
        return workflowNestingDepth;
    }
    
    @Override
    public Object getActorManager() {
        if (session instanceof WorkflowSession ws) {
            return ws.getActorManager();
        } else if (session instanceof NodeSession ns) {
            return ns.getActorManager();
        }
        return session.getActorManager();
    }
    
    /**
     * Gets the parent session.
     * 
     * @return the parent session
     */
    public BaseSession getParent() {
        return session;
    }
    
    @Override
    public Tracer getTracer() {
        return session.getTracer();
    }
    
    @Override
    public State getState() {
        return state;
    }
    
    @Override
    public Config getConfig() {
        return session.getConfig();
    }
    
    @Override
    public StreamWriterManager getStreamWriterManager() {
        return session.getStreamWriterManager();
    }
    
    @Override
    public CallbackManager getCallbackManager() {
        return session.getCallbackManager();
    }
    
    @Override
    public String getSessionId() {
        return session.getSessionId();
    }
    
    @Override
    public Checkpointer getCheckpointer() {
        // NodeSession doesn't have its own checkpointer
        return null;
    }
    
    /**
     * Gets the node configuration from the workflow configuration.
     * 
     * @return the node configuration, or null if not found
     */
    public Object getNodeConfig() {
        Object workflowConfig = getConfig().getWorkflowConfig(getWorkflowId());
        if (workflowConfig != null) {
            try {
                // Try to get spec.comp_configs.get(nodeId)
                var specMethod = workflowConfig.getClass().getMethod("getSpec");
                Object spec = specMethod.invoke(workflowConfig);
                if (spec != null) {
                    var compConfigsMethod = spec.getClass().getMethod("getCompConfigs");
                    Object compConfigs = compConfigsMethod.invoke(spec);
                    if (compConfigs instanceof java.util.Map<?, ?> map) {
                        return map.get(nodeId);
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        return null;
    }
    
    // ========== TracerWorkflowUtils.WorkflowSession interface methods ==========
    
    @Override
    public Tracer tracer() {
        return getTracer();
    }
    
    @Override
    public String workflowId() {
        return getWorkflowId();
    }
    
    @Override
    public String executableId() {
        return getExecutableId();
    }
    
    @Override
    public String parentId() {
        return getParentId();
    }
    
    @Override
    public String nodeId() {
        return getNodeId();
    }
    
    @Override
    public String nodeType() {
        return getNodeType();
    }
    
    @Override
    public Object state() {
        return getState();
    }
    
    @Override
    public TracerWorkflowUtils.WorkflowConfig config() {
        return workflowId -> getConfig().getWorkflowConfig(workflowId);
    }
}

