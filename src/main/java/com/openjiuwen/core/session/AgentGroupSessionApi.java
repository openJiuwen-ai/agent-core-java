/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session;

import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;

import java.util.Map;
import java.util.UUID;

/**
 * User-facing agent group session.
 * <p>
 * Provides a simplified session API for multi-agent groups,
 * wrapping an internal {@link AgentSession}.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.agent_group.Session}.
 */
public class AgentGroupSessionApi {

    private final String sessionId;
    private final AgentSession inner;

    /**
     * Create a new agent group session.
     *
     * @param sessionId session ID (nullable, auto-generated if absent)
     * @param envs      environment variables (nullable)
     */
    public AgentGroupSessionApi(String sessionId, Map<String, Object> envs) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        this.sessionId = sessionId;

        Config config = new Config();
        if (envs != null) {
            config.setEnvs(envs);
        }
        this.inner = new AgentSession(sessionId, config, null, null);
    }

    public AgentGroupSessionApi(String sessionId) {
        this(sessionId, null);
    }

    public AgentGroupSessionApi() {
        this(null, null);
    }

    public String getSessionId() {
        return sessionId;
    }

    /**
     * Get an environment variable.
     *
     * @param key          the environment key
     * @param defaultValue default value if not found
     * @return the value, or defaultValue
     */
    public Object getEnv(String key, Object defaultValue) {
        if (inner.config() != null) {
            Object val = inner.config().getEnv(key);
            return val != null ? val : defaultValue;
        }
        return defaultValue;
    }

    /**
     * Get the underlying internal AgentSession.
     */
    public AgentSession getInner() {
        return inner;
    }

    /**
     * Factory method to create an agent group session.
     *
     * @param sessionId session ID (nullable, auto-generated if absent)
     * @param envs      environment variables (nullable)
     * @return a new AgentGroupSessionApi
     */
    public static AgentGroupSessionApi create(String sessionId, Map<String, Object> envs) {
        return new AgentGroupSessionApi(sessionId, envs);
    }
}
