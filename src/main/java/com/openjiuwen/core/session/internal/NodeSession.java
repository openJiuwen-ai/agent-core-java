/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.workflow.WorkflowConfig;
import com.openjiuwen.core.workflow.WorkflowSpec;

import java.util.Map;

/**
 * Node session representing a workflow node's scoped session.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.internal.workflow.NodeSession}.
 * 
 * @since 0.1.7
 */
public class NodeSession extends BaseSession {
    private final String nodeId;
    private final String nodeType;
    private final String executableId;
    private final String parentId;
    private final State stateField;
    private final BaseSession parentSession;
    private final String workflowId;
    private final int workflowNestingDepth;
    private final String mainWorkflowId;
    private final boolean skipTrace;

    /**
     * NodeSession.
     * 
     * @param session session
     * @param nodeId nodeId
     * @param nodeType nodeType
     * @param skipTrace skipTrace
     * @since 0.1.7
     */
    public NodeSession(BaseSession session, String nodeId, String nodeType, boolean skipTrace) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.skipTrace = skipTrace;

        String pId = createParentId(session);
        String eId = createExecutableId(nodeId, pId);

        this.parentId = pId;
        this.executableId = eId;
        this.parentSession = session;

        // Create node-scoped state from parent
        if (session.state() instanceof com.openjiuwen.core.session.state.WorkflowStateCollection) {
            this.stateField =
                ((com.openjiuwen.core.session.state.WorkflowStateCollection) session.state()).createNodeState(eId, pId);
        } else {
            this.stateField = session.state();
        }

        if (session instanceof NodeSession nodeSession) {
            this.workflowId = nodeSession.workflowId();
            this.workflowNestingDepth = nodeSession.workflowNestingDepth();
            this.mainWorkflowId = nodeSession.mainWorkflowId();
        } else if (session instanceof WorkflowSession workflowSession) {
            this.workflowId = workflowSession.workflowId();
            this.workflowNestingDepth = workflowSession.workflowNestingDepth();
            this.mainWorkflowId = workflowSession.mainWorkflowId();
        } else {
            this.workflowId = session.sessionId();
            this.workflowNestingDepth = 0;
            this.mainWorkflowId = session.sessionId();
        }
    }

    /**
     * NodeSession.
     * 
     * @param session session
     * @param nodeId nodeId
     * @param nodeType nodeType
     * @since 0.1.7
     */
    public NodeSession(BaseSession session, String nodeId, String nodeType) {
        this(session, nodeId, nodeType, false);
    }

    /**
     * NodeSession.
     * 
     * @param session session
     * @param nodeId nodeId
     * @since 0.1.7
     */
    public NodeSession(BaseSession session, String nodeId) {
        this(session, nodeId, null, false);
    }

    /**
     * nodeId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String nodeId() {
        return nodeId;
    }

    /**
     * nodeType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String nodeType() {
        return nodeType;
    }

    /**
     * executableId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String executableId() {
        return executableId;
    }

    /**
     * parentId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String parentId() {
        return parentId;
    }

    /**
     * workflowId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String workflowId() {
        return workflowId;
    }

    /**
     * mainWorkflowId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String mainWorkflowId() {
        return mainWorkflowId;
    }

    /**
     * workflowNestingDepth.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int workflowNestingDepth() {
        return workflowNestingDepth;
    }

    /**
     * parent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BaseSession parent() {
        return parentSession;
    }

    /**
     * config.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Config config() {
        return parentSession.config();
    }

    /**
     * state.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public State state() {
        return stateField;
    }

    /**
     * tracer.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object tracer() {
        return parentSession.tracer();
    }

    /**
     * streamWriterManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StreamWriterManager streamWriterManager() {
        return parentSession.streamWriterManager();
    }

    /**
     * callbackManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public CallbackManager callbackManager() {
        return parentSession.callbackManager();
    }

    /**
     * sessionId.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String sessionId() {
        return parentSession.sessionId();
    }

    /**
     * checkpointer.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object checkpointer() {
        return parentSession.checkpointer();
    }

    /**
     * Whether this node session should skip trace operations.
     * Mirrors Python's {@code NodeSession.skip_trace()}.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean skipTrace() {
        return skipTrace;
    }

    /**
     * Get the actor manager from the parent session.
     * Mirrors Python's {@code NodeSession.actor_manager()}.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object actorManager() {
        return parentSession.actorManager();
    }

    /**
     * nodeConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Object nodeConfig() {
        Object workflowConfig = config().getWorkflowConfig(workflowId);
        if (workflowConfig instanceof WorkflowConfig typedConfig) {
            WorkflowSpec spec = typedConfig.getSpec();
            return spec != null ? spec.getCompConfigs().get(nodeId) : null;
        }
        if (workflowConfig instanceof Map<?, ?> configMap) {
            Object spec = configMap.get("spec");
            if (spec instanceof WorkflowSpec typedSpec) {
                return typedSpec.getCompConfigs().get(nodeId);
            }
            if (spec instanceof Map<?, ?> specMap) {
                Object compConfigs = specMap.get("compConfigs");
                if (compConfigs == null) {
                    compConfigs = specMap.get("comp_configs");
                }
                if (compConfigs instanceof Map<?, ?> compConfigsMap) {
                    return compConfigsMap.get(nodeId);
                }
            }
        }
        return null;
    }

    // ---- Static Helpers ----

    /**
     * createParentId.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static String createParentId(BaseSession session) {
        if (session instanceof NodeSession) {
            return ((NodeSession) session).executableId();
        }
        return "";
    }

    /**
     * createExecutableId.
     * 
     * @param nodeId nodeId
     * @param parentId parentId
     * @return the result
     * @since 0.1.7
     */
    private static String createExecutableId(String nodeId, String parentId) {
        if (parentId != null && !parentId.isEmpty()) {
            return parentId + "." + nodeId;
        }
        return nodeId;
    }
}
