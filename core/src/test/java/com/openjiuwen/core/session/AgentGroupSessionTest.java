/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentGroupSessionTest {

    @Test
    void teamAndMemberWritesIncludeSourceMetadata() {
        AgentGroupSession teamSession = AgentGroupSession.create("group-session", Map.of("env", "test"));
        teamSession.setTeamId("team-a");
        AgentSession memberSession = teamSession.createAgentSession(
                new AgentCard("worker-a", "worker", "worker"),
                "worker-a"
        );

        memberSession.writeStream(Map.of("kind", "member"));
        teamSession.writeStream(Map.of("kind", "team"));

        OutputSchema memberOutput = nextOutput(teamSession);
        Map<?, ?> memberPayload = assertInstanceOf(Map.class, memberOutput.getPayload());
        assertEquals("member", memberPayload.get("kind"));
        assertEquals("worker-a", memberPayload.get("source_agent_id"));
        assertEquals("team-a", memberPayload.get("source_team_id"));

        OutputSchema teamOutput = nextOutput(teamSession);
        Map<?, ?> teamPayload = assertInstanceOf(Map.class, teamOutput.getPayload());
        assertEquals("team", teamPayload.get("kind"));
        assertEquals("team-a", teamPayload.get("source_team_id"));
    }

    private static OutputSchema nextOutput(AgentGroupSession session) {
        Object emitted = session.getInner().streamWriterManager().streamEmitter().getStreamQueue().receive(100);
        assertNotNull(emitted);
        return assertInstanceOf(OutputSchema.class, emitted);
    }
}
