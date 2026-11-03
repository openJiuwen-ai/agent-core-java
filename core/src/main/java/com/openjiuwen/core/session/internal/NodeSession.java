/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.internal;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.state.State;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.workflow.WorkflowConfig;
import com.openjiuwen.core.workflow.WorkflowSpec;

import java.util.Map;

/**
 * Internal node-scoped workflow session.
 *
 * <p>Mirrors Python's {@code NodeSession} in
 * {@code openjiuwen/core/session/internal/workflow.py}.</p>
 */
public class NodeSession extends BaseSession {

    private final BaseSession parentSession;
    private final String nodeId;
    private final String nodeType;
    private final String parentId;
    private final String executableId;
    private final State state;
    private final String workflowId;
    private final String mainWorkflowId;
    private final int workflowNestingDepth;
    private final boolean skipTrace;

    public NodeSession(BaseSession session, String nodeId, String nodeType, boolean skipTrace) {
        this.parentSession = session;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.skipTrace = skipTrace;
        this.parentId = createParentId(session);
        this.executableId = createExecutableId(nodeId, parentId);
        if (session != null && session.state() instanceof WorkflowCommitState workflowState) {
            this.state = workflowState.createNodeState(executableId, parentId);
        } else {
            this.state = session == null ? null : session.state();
        }
        if (session instanceof NodeSession nodeSession) {
            this.workflowId = nodeSession.workflowId();
            this.mainWorkflowId = nodeSession.mainWorkflowId();
            this.workflowNestingDepth = nodeSession.workflowNestingDepth();
        } else if (session instanceof WorkflowSession workflowSession) {
            this.workflowId = workflowSession.workflowId();
            this.mainWorkflowId = workflowSession.mainWorkflowId();
            this.workflowNestingDepth = workflowSession.workflowNestingDepth();
        } else {
            String id = session == null ? "" : session.sessionId();
            this.workflowId = id;
            this.mainWorkflowId = id;
            this.workflowNestingDepth = 0;
        }
    }

    public NodeSession(BaseSession session, String nodeId, String nodeType) {
        this(session, nodeId, nodeType, false);
    }

    public NodeSession(BaseSession session, String nodeId) {
        this(session, nodeId, null, false);
    }

    public String nodeId() {
        return nodeId;
    }

    public String nodeType() {
        return nodeType;
    }

    public String parentId() {
        return parentId;
    }

    public String executableId() {
        return executableId;
    }

    public String workflowId() {
        return workflowId;
    }

    public String mainWorkflowId() {
        return mainWorkflowId;
    }

    public int workflowNestingDepth() {
        return workflowNestingDepth;
    }

    public boolean skipTrace() {
        return skipTrace;
    }

    public BaseSession parent() {
        return parentSession;
    }

    @Override
    public Config config() {
        return parentSession == null ? super.config() : parentSession.config();
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public Object tracer() {
        return parentSession == null ? null : parentSession.tracer();
    }

    @Override
    public StreamWriterManager streamWriterManager() {
        return parentSession == null ? null : parentSession.streamWriterManager();
    }

    @Override
    public String sessionId() {
        return parentSession == null ? "" : parentSession.sessionId();
    }

    @Override
    public Object checkpointer() {
        return parentSession == null ? null : parentSession.checkpointer();
    }

    @Override
    public CallbackManager callbackManager() {
        return parentSession == null ? null : parentSession.callbackManager();
    }

    @Override
    public Object actorManager() {
        return parentSession == null ? null : parentSession.actorManager();
    }

    @SuppressWarnings("unchecked")
    public Object nodeConfig() {
        Object workflowConfig = config().getWorkflowConfig(workflowId);
        if (workflowConfig instanceof WorkflowConfig typedConfig && typedConfig.getSpec() != null) {
            return typedConfig.getSpec().getCompConfigs().get(nodeId);
        }
        if (workflowConfig instanceof Map<?, ?> configMap) {
            Object spec = configMap.get("spec");
            if (spec instanceof WorkflowSpec typedSpec) {
                return typedSpec.getCompConfigs().get(nodeId);
            }
            if (spec instanceof Map<?, ?> specMap) {
                Object compConfigs = specMap.get("comp_configs");
                if (compConfigs == null) {
                    compConfigs = specMap.get("compConfigs");
                }
                if (compConfigs instanceof Map<?, ?> compConfigsMap) {
                    return compConfigsMap.get(nodeId);
                }
            }
        }
        return null;
    }

    private static String createParentId(BaseSession session) {
        return session instanceof NodeSession nodeSession ? nodeSession.executableId() : "";
    }

    private static String createExecutableId(String nodeId, String parentId) {
        if (parentId != null && !parentId.isEmpty()) {
            return parentId + "." + nodeId;
        }
        return nodeId == null ? "" : nodeId;
    }
}
