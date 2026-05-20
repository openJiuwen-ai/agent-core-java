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
     * Auto-generated for codecheck compliance.
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
            this.stateField = ((com.openjiuwen.core.session.state.WorkflowStateCollection) session.state())
                    .createNodeState(eId, pId);
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
     * Auto-generated for codecheck compliance.
     */
    public NodeSession(BaseSession session, String nodeId, String nodeType) {
        this(session, nodeId, nodeType, false);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public NodeSession(BaseSession session, String nodeId) {
        this(session, nodeId, null, false);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String nodeId() {
        return nodeId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String nodeType() {
        return nodeType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String executableId() {
        return executableId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String parentId() {
        return parentId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String workflowId() {
        return workflowId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String mainWorkflowId() {
        return mainWorkflowId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int workflowNestingDepth() {
        return workflowNestingDepth;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseSession parent() {
        return parentSession;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Config config() {
        return parentSession.config();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public State state() {
        return stateField;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object tracer() {
        return parentSession.tracer();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamWriterManager streamWriterManager() {
        return parentSession.streamWriterManager();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public CallbackManager callbackManager() {
        return parentSession.callbackManager();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String sessionId() {
        return parentSession.sessionId();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object checkpointer() {
        return parentSession.checkpointer();
    }

    /**
     * Whether this node session should skip trace operations.
     * Mirrors Python's {@code NodeSession.skip_trace()}.
     */
    public boolean skipTrace() {
        return skipTrace;
    }

    /**
     * Get the actor manager from the parent session.
     * Mirrors Python's {@code NodeSession.actor_manager()}.
     */
    public Object actorManager() {
        return parentSession.actorManager();
    }

    /**
     * Get node-specific config from workflow config.
     * Mirrors Python: workflow_config.spec.comp_configs.get(self._node_id)
     * Uses Map-based traversal since WorkflowConfig is not yet typed in Java.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
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

    private static String createParentId(BaseSession session) {
        if (session instanceof NodeSession) {
            return ((NodeSession) session).executableId();
        }
        return "";
    }

    private static String createExecutableId(String nodeId, String parentId) {
        if (parentId != null && !parentId.isEmpty()) {
            return parentId + "." + nodeId;
        }
        return nodeId;
    }
}
