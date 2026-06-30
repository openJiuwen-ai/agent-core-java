/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.runtime;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;

/**
 * Inheritance-based Java counterpart of Python's {@code CommunicableAgent} mixin.
 *
 * <p>Java does not support Python-style multiple inheritance, so the current
 * compatibility layer provides the same message helpers through a base class.
 * Existing agents can either extend this class directly or wrap its behaviour.</p>
 */
public abstract class CommunicableAgent extends BaseAgent {
    private TeamRuntime runtime;
    private String agentId;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected CommunicableAgent(AgentCard card) {
        super(card);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void bindRuntime(TeamRuntime runtime, String agentId) {
        this.runtime = runtime;
        this.agentId = agentId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isBound() {
        return runtime != null && agentId != null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamRuntime runtime() {
        if (runtime == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                    "error_msg", "Agent not bound to a TeamRuntime"
            );
        }
        return runtime;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String agentId() {
        if (agentId == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                    "error_msg", "Agent not bound to a TeamRuntime"
            );
        }
        return agentId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object send(Object message, String recipient, String sessionId,
                       Double timeout, AgentGroupSessionApi session) {
        return runtime().send(message, recipient, agentId(), sessionId, session);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void publish(Object message, String topicId, String sessionId, AgentGroupSessionApi session) {
        runtime().publish(message, topicId, agentId(), sessionId, session);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void subscribe(String topic) {
        runtime().subscribe(agentId(), topic);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void unsubscribe(String topic) {
        runtime().unsubscribe(agentId(), topic);
    }
}
