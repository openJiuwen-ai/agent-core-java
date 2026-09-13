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
 * <p>
 * Java does not support Python-style multiple inheritance, so the current
 * compatibility layer provides the same message helpers through a base class.
 * Existing agents can either extend this class directly or wrap its behaviour.
 * </p>
 * 
 * @since 0.1.7
 */
public abstract class CommunicableAgent extends BaseAgent {
    private TeamRuntime runtime;
    private String agentId;

    /**
     * CommunicableAgent.
     * 
     * @param card card
     * @since 0.1.7
     */
    protected CommunicableAgent(AgentCard card) {
        super(card);
    }

    /**
     * bindRuntime.
     * 
     * @param runtime runtime
     * @param agentId agentId
     * @since 0.1.7
     */
    public void bindRuntime(TeamRuntime runtime, String agentId) {
        this.runtime = runtime;
        this.agentId = agentId;
    }

    /**
     * isBound.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isBound() {
        return runtime != null && agentId != null;
    }

    /**
     * runtime.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamRuntime runtime() {
        if (runtime == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg",
                    "Agent not bound to a TeamRuntime");
        }
        return runtime;
    }

    /**
     * agentId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String agentId() {
        if (agentId == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg",
                    "Agent not bound to a TeamRuntime");
        }
        return agentId;
    }

    /**
     * send.
     * 
     * @param message message
     * @param recipient recipient
     * @param sessionId sessionId
     * @param timeout timeout
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public Object send(Object message, String recipient, String sessionId, Double timeout,
            AgentGroupSessionApi session) {
        return runtime().send(message, recipient, agentId(), sessionId, session);
    }

    /**
     * publish.
     * 
     * @param message message
     * @param topicId topicId
     * @param sessionId sessionId
     * @param session session
     * @since 0.1.7
     */
    public void publish(Object message, String topicId, String sessionId, AgentGroupSessionApi session) {
        runtime().publish(message, topicId, agentId(), sessionId, session);
    }

    /**
     * subscribe.
     * 
     * @param topic topic
     * @since 0.1.7
     */
    public void subscribe(String topic) {
        runtime().subscribe(agentId(), topic);
    }

    /**
     * unsubscribe.
     * 
     * @param topic topic
     * @since 0.1.7
     */
    public void unsubscribe(String topic) {
        runtime().unsubscribe(agentId(), topic);
    }
}
