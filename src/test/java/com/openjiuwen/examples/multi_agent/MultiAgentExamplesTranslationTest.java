/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

import com.openjiuwen.examples.multi_agent.builtin_teams.HandoffCustomerServiceExample;
import com.openjiuwen.examples.multi_agent.builtin_teams.HierarchicalMsgbusResearchTeamExample;
import com.openjiuwen.examples.multi_agent.builtin_teams.HierarchicalToolsResearchTeamExample;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiAgentExamplesTranslationTest {

    @Test
    void p2pRuntimeRunsPlannerCoderReviewerFlow() {
        Map<String, Object> result = RuntimeP2PExample.runWorkflow("implement quicksort");

        Map<String, Object> plan = map(result.get("plan"));
        Map<String, Object> code = map(result.get("code"));
        Map<String, Object> review = map(result.get("review"));

        assertEquals("implement quicksort", plan.get("task"));
        assertEquals(3, ((List<?>) plan.get("steps")).size());
        assertEquals("completed", code.get("status"));
        assertEquals(3, code.get("step_count"));
        assertEquals(true, review.get("approved"));
    }

    @Test
    void pubSubRuntimeFansOutTaskToWorkersAndMonitor() {
        Map<String, Object> result = RuntimePubSubExample.runWorkflow("process data batch");

        assertEquals("completed", result.get("status"));
        assertEquals(3, result.get("completion_count"));
        assertEquals(3, ((List<?>) result.get("completions")).size());
    }

    @Test
    void hybridRuntimeCombinesP2pAndPubSub() {
        Map<String, Object> result = RuntimeHybridExample.runWorkflow("build new feature");

        assertEquals("broadcast_done", map(result.get("orchestration")).get("status"));
        assertEquals("report_generated", map(result.get("report")).get("status"));
        assertEquals(3, map(result.get("report")).get("total"));
        assertEquals(3, ((List<?>) result.get("results")).size());
    }

    @Test
    void teamHybridAddsDirectPublishAndStreamsExpectedEvents() {
        Map<String, Object> result = TeamHybridExample.runWorkflow("build new feature");
        Map<String, Object> report = map(result.get("report"));

        assertEquals("report_generated", report.get("status"));
        assertEquals(TeamHybridExample.TaskExecutionTeam.expectedResultCount(), report.get("total"));

        List<Map<String, Object>> payloads = TeamHybridStreamCheckExample.runStreamCheck();
        assertTrue(TeamHybridStreamCheckExample.hasEvent(payloads, "team_started", null));
        assertTrue(TeamHybridStreamCheckExample.hasEvent(payloads, "orchestrator_received", "orchestrator"));
        assertTrue(TeamHybridStreamCheckExample.hasEvent(payloads, "aggregator_progress", "aggregator"));
        assertFalse(payloads.isEmpty());
    }

    @Test
    void handoffCustomerServiceRoutesQueries() {
        assertEquals(HandoffCustomerServiceExample.BILLING_SUPPORT,
                HandoffCustomerServiceExample.classifyTarget("I need a refund for this invoice"));
        assertEquals(HandoffCustomerServiceExample.TECHNICAL_SUPPORT,
                HandoffCustomerServiceExample.classifyTarget("The login page shows a network error"));
        assertEquals(HandoffCustomerServiceExample.TRIAGE_AGENT,
                HandoffCustomerServiceExample.classifyTarget("hello and thanks"));
        assertEquals(4, HandoffCustomerServiceExample.handoffRoutes().size());
    }

    @Test
    void hierarchicalExamplesExposeLeafAgentBehavior() {
        Map<String, Object> msgbus = HierarchicalMsgbusResearchTeamExample.runResearchTask("AI diagnosis");
        Map<String, Object> tools = HierarchicalToolsResearchTeamExample.runResearchTask("AI diagnosis");

        assertEquals(HierarchicalMsgbusResearchTeamExample.TEAM_ID, msgbus.get("team_id"));
        assertEquals(HierarchicalToolsResearchTeamExample.TEAM_ID, tools.get("team_id"));
        assertEquals(15, map(msgbus.get("literature")).get("paper_count"));
        assertEquals(0.95, (Double) map(tools.get("analysis")).get("confidence"), 0.0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
