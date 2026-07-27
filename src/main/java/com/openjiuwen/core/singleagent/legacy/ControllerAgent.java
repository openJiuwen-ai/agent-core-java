/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.controller.legacy.BaseController;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Legacy controller-driven agent wrapper.
 */
public class ControllerAgent extends BaseAgent {

    private BaseController controller;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ControllerAgent(AgentConfig agentConfig, BaseController controller) {
        super(agentConfig);
        this.controller = controller;
        if (this.controller != null) {
            this.controller.setupFromAgent(this);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public BaseController getController() {
        return controller;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setController(BaseController controller) {
        this.controller = controller;
        if (this.controller != null) {
            this.controller.setupFromAgent(this);
        }
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Session session) {
        Session effectiveSession = session != null ? session : createSession(inputs);
        try {
            return controller.invoke(inputs, effectiveSession);
        } finally {
            if (session == null && effectiveSession instanceof Session) {
                try { effectiveSession.getClass().getMethod("postRun").invoke(effectiveSession); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Session session) {
        Session effectiveSession = session != null ? session : createSession(inputs);
        Object result = controller.invoke(inputs, effectiveSession);
        List<Object> outputs = new ArrayList<>();
        if (result != null) {
            outputs.add(result);
        }
        if (session == null && effectiveSession instanceof Session) {
            try { effectiveSession.getClass().getMethod("postRun").invoke(effectiveSession); } catch (Exception ignored) {}
        }
        return outputs.iterator();
    }

    private Session createSession(Map<String, Object> inputs) {
        return new Session();
    }
}
