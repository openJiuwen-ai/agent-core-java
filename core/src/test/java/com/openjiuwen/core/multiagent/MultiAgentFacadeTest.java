/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.multiagent;

import com.openjiuwen.core.session.AgentGroupSession;
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
        assertEquals("facade", session.getEnvs().get("topic"));
        session.updateState(Map.of("topic", "global"));
        assertEquals("global", session.getState("topic"));
    }

    @Test
    void agentGroupSessionApiRetainsAgentSessionHelpers() {
        AgentGroupSession session = new AgentGroupSession("group-session", Map.of("owner", "ops"));

        session.updateState(Map.of("phase", "routing"));

        assertInstanceOf(AgentSessionApi.class, session);
        assertEquals("routing", session.getState("phase"));
        assertEquals("ops", session.getEnvs().get("owner"));
    }
}
