/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.controller.legacy.BaseController;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Legacy controller-driven agent wrapper.
 * 
 * @since 0.1.7
 */
public class ControllerAgent extends BaseAgent {
    private BaseController controller;

    /**
     * ControllerAgent.
     * 
     * @param agentConfig agentConfig
     * @param controller controller
     * @since 0.1.7
     */
    public ControllerAgent(AgentConfig agentConfig, BaseController controller) {
        super(agentConfig);
        this.controller = controller;
        if (this.controller != null) {
            this.controller.setupFromAgent(this);
        }
    }

    /**
     * getController.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BaseController getController() {
        return controller;
    }

    /**
     * setController.
     * 
     * @param controller controller
     * @since 0.1.7
     */
    public void setController(BaseController controller) {
        this.controller = controller;
        if (this.controller != null) {
            this.controller.setupFromAgent(this);
        }
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Map<String, Object> inputs, Session session) {
        AgentSessionApi effectiveSession = toAgentSession(inputs, session);
        try {
            return controller.invoke(inputs, effectiveSession);
        } finally {
            if (session == null) {
                effectiveSession.postRun();
            }
        }
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Session session) {
        AgentSessionApi effectiveSession = toAgentSession(inputs, session);
        Object result = controller.invoke(inputs, effectiveSession);
        List<Object> outputs = new java.util.ArrayList<>();
        effectiveSession.streamOutput(outputs::add);
        if (outputs.isEmpty() && result != null) {
            outputs.add(result);
        }
        if (session == null) {
            effectiveSession.postRun();
        }
        return outputs.iterator();
    }

    /**
     * toAgentSession.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private AgentSessionApi toAgentSession(Map<String, Object> inputs, Session session) {
        if (session instanceof AgentSessionApi agentSessionApi) {
            return agentSessionApi;
        }
        String sessionId = String.valueOf(inputs.getOrDefault("conversation_id", "default_session"));
        AgentSessionApi agentSessionApi = AgentSessionApi.create(sessionId, null, null);
        agentSessionApi.preRun(inputs);
        return agentSessionApi;
    }
}
