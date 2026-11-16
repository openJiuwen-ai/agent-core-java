/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session derivation preserves request-local nested environment references.
 */
class SessionEnvironmentSharingTest {
    @Test
    void derivedAgentSessionsShareResponseChannelsButNotTopLevelKeys() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pending", true);
        List<String> events = new ArrayList<>();
        AgentSession caller = new AgentSession("environment-caller",
                Map.of("response", response, "events", events), null);
        AgentSession execution = new AgentSession("environment-execution", caller.getEnvs(), null);
        AgentSession inner = new AgentSession("environment-inner", execution.getEnvs(), null);

        assertSame(response, execution.getEnv("response"));
        assertSame(response, inner.getEnv("response"));
        assertSame(events, inner.getEnvs().get("events"));
        Object channel = inner.getEnv("response");
        if (!(channel instanceof Map<?, ?> values)) {
            throw new AssertionError("Expected the response environment to contain a Map");
        }
        values.remove("pending");
        assertEquals(Map.of(), caller.getEnv("response"));
        response.put("answer", "done");
        events.add("completed");
        assertEquals(Map.of("answer", "done"), caller.getEnv("response"));
        assertEquals(List.of("completed"), execution.getEnv("events"));

        inner.getInner().config().setEnvs(Map.of("inner-only", true));
        assertFalse(caller.getEnvs().containsKey("inner-only"));
        assertFalse(execution.getEnvs().containsKey("inner-only"));
    }

    @Test
    void workflowAndTeamSessionsRetainNestedEnvironmentReferences() {
        Map<String, Object> response = new LinkedHashMap<>();
        AgentSession caller = new AgentSession("workflow-environment", Map.of("response", response), null);
        WorkflowSession workflow = caller.createWorkflowSession();
        AgentTeamSession team = new AgentTeamSession("team-environment", caller.getEnvs(), "team");

        assertSame(response, workflow.getEnvs().get("response"));
        assertSame(response, team.getEnvs().get("response"));
    }

    @Test
    void independentlyAllocatedRequestChannelsRemainIsolated() {
        Map<String, Object> firstResponse = new LinkedHashMap<>();
        Map<String, Object> secondResponse = new LinkedHashMap<>();
        AgentSession first = new AgentSession("first-request", Map.of("response", firstResponse), null);
        AgentSession second = new AgentSession("second-request", Map.of("response", secondResponse), null);

        firstResponse.put("answer", "first");

        assertEquals(Map.of("answer", "first"), first.getEnvs().get("response"));
        assertEquals(Map.of(), second.getEnvs().get("response"));
    }
}
