/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MultiAgentFacadeTest {

    @Test
    void createAgentGroupSessionUsesMultiagentFacade() {
        Session session = MultiAgentSessions.createAgentGroupSession(
                "group-session",
                Map.of("topic", "facade")
        );

        assertEquals("group-session", session.getSessionId());
        assertEquals("facade", session.getEnv("topic", ""));
    }

    @Test
    void agentGroupSessionApiRetainsAgentSessionHelpers() {
        AgentGroupSessionApi session = new AgentGroupSessionApi("group-session", Map.of("owner", "ops"));

        session.updateState(Map.of("phase", "routing"));

        assertInstanceOf(AgentSessionApi.class, session);
        assertEquals("routing", session.getState("phase"));
        assertEquals("ops", session.getEnv("owner", ""));
    }
}
