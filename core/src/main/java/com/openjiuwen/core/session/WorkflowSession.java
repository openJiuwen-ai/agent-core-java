/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.callback.CallbackManager;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public workflow session facade. Same product layer as {@link AgentSession}.
 */
public class WorkflowSession implements AgentSessionApi {

    private final BaseSession parent;
    private final String sessionId;
    private final CallbackManager callbackManager;
    private Map<String, Object> envs;
    private Object workflowCard;

    public WorkflowSession() {
        this(null, null, null);
    }

    public WorkflowSession(BaseSession parent, String sessionId, Map<String, Object> envs) {
        this.parent = parent;
        this.callbackManager = new CallbackManager();
        if (parent != null) {
            this.sessionId = sessionId;
            this.envs = parent.config() == null ? null : parent.config().getEnvs();
        } else if (sessionId != null) {
            this.sessionId = sessionId;
            this.envs = envs;
        } else {
            this.sessionId = UUID.randomUUID().toString();
            this.envs = envs;
        }
    }

    public WorkflowSession(BaseSession parent, String sessionId) {
        this(parent, sessionId != null ? sessionId : UUID.randomUUID().toString(), null);
    }

    public WorkflowSession(String sessionId) {
        this(null, sessionId, null);
    }

    public static WorkflowSession createWorkflowSession(BaseSession parent, String sessionId,
                                                        Map<String, Object> envs) {
        return new WorkflowSession(parent, sessionId, envs);
    }

    public static WorkflowSession createWorkflowSession(BaseSession parent) {
        return new WorkflowSession(parent, null, null);
    }

    public static WorkflowSession create(BaseSession parent, String sessionId, Map<String, Object> envs) {
        return new WorkflowSession(parent, sessionId, envs);
    }

    public CallbackManager getCallbackManager() {
        return callbackManager;
    }

    public BaseSession getInner() {
        return parent;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public Object getState(String key) {
        return parent == null ? null : parent.getState(key);
    }

    @Override
    public void updateState(Map<String, Object> data) {
        if (parent != null) {
            parent.updateState(data);
        }
    }

    @Override
    public void writeStream(Object data) {
        StreamWriterManager manager = parent == null ? null : parent.streamWriterManager();
        if (manager != null && manager.getOutputWriter() != null) {
            manager.getOutputWriter().write(data instanceof OutputSchema ? data : new OutputSchema("message", 0, data));
        }
    }

    @Override
    public Iterator<Object> streamIterator() {
        StreamWriterManager manager = parent == null ? null : parent.streamWriterManager();
        if (manager == null) {
            return List.of().iterator();
        }
        return manager.streamIterator();
    }

    public Map<String, Object> getEnvs() {
        return envs;
    }

    public BaseSession getParent() {
        return parent;
    }

    public void setWorkflowCard(Object card) {
        this.workflowCard = card;
    }

    public Object getWorkflowCard() {
        return workflowCard;
    }

    public Config asConfig() {
        Config config = new Config();
        config.setEnvs(getEnvs());
        return config;
    }
}
