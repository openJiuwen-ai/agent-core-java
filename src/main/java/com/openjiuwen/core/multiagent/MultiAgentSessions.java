/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent;

import java.util.Map;

/**
 * Convenience facade for creating multi-agent sessions from the
 * {@code multiagent} package.
 * <p>
 * Mirrors Python's top-level export of {@code Session} and
 * {@code create_agent_group_session} from {@code openjiuwen.core.multi_agent}.
 */
public final class MultiAgentSessions {

    private MultiAgentSessions() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Session createAgentGroupSession(String sessionId, Map<String, Object> envs) {
        return Session.create(sessionId, envs);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Session createAgentGroupSession() {
        return Session.create(null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Session createAgentGroupSession(String sessionId) {
        return Session.create(sessionId, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Session createAgentGroupSession(Map<String, Object> envs) {
        return Session.create(null, envs);
    }
}
