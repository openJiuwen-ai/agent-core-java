/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mirrors Python's {@code test_3layer_agent_interrupt.py} in
 * {@code tests.unit_tests.agent.react_agent.interrupt}.
 */
class ThreeLayerAgentInterruptTest extends InterruptTestBase {

    @Test
    @DisplayName("three-layer nested agent read interrupt and resume")
    void test3LayerAgentInterrupt() throws Exception {
        ReadTool readTool = new ReadTool();

        NestedAgentConfig subAgent2Config = new NestedAgentConfig(
            "sub_agent_2",
            "sub_agent_2",
            "You are innermost agent. Call read tool when user requests file read."
        );
        subAgent2Config.tools.add(readTool);
        subAgent2Config.railToolNames.add("read");
        ReActAgent subAgent2 = createNestedAgent(subAgent2Config);

        AgentCard subAgent2Card = AgentCard.builder()
            .id("sub_agent_2")
            .name("sub_agent_2")
            .description("Innermost agent for file read tasks")
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of("description", "Task description", "type", "string")
                ),
                "required", List.of("query")
            ))
            .build();
        Runner.resourceMgr().addAgent(subAgent2Card, () -> subAgent2, null);

        NestedAgentConfig subAgent1Config = new NestedAgentConfig(
            "sub_agent_1",
            "sub_agent_1",
            "You are middle agent. Call sub_agent_2 tool for file read tasks."
        );
        subAgent1Config.subAgentCards.add(subAgent2Card);
        ReActAgent subAgent1 = createNestedAgent(subAgent1Config);

        AgentCard subAgent1Card = AgentCard.builder()
            .id("sub_agent_1")
            .name("sub_agent_1")
            .description("Middle agent coordinating sub-tasks")
            .inputParams(Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of("description", "Task description", "type", "string")
                ),
                "required", List.of("query")
            ))
            .build();
        Runner.resourceMgr().addAgent(subAgent1Card, () -> subAgent1, null);

        NestedAgentConfig mainAgentConfig = new NestedAgentConfig(
            "main_agent",
            "main_agent",
            "You are main agent. Call sub_agent_1 tool for tasks."
        );
        mainAgentConfig.subAgentCards.add(subAgent1Card);
        ReActAgent mainAgent = createNestedAgent(mainAgentConfig);
        Runner.resourceMgr().addAgent(mainAgent.getCard(), () -> mainAgent, null);

        assertNotNull(mainAgent);
        assertNotNull(subAgent1);
        assertNotNull(subAgent2);
        assertNotNull(Runner.resourceMgr().getAgent("sub_agent_2"));
        assertNotNull(Runner.resourceMgr().getAgent("sub_agent_1"));
        assertNotNull(Runner.resourceMgr().getAgent("main_agent"));
        assertNotNull(mainAgent.getAbilityManager().get("sub_agent_1"));
        assertNotNull(subAgent1.getAbilityManager().get("sub_agent_2"));
        assertNotNull(subAgent2.getAbilityManager().get("read"));

        AssistantFlow flow = newConfirmFlow(new ConfirmInterruptRail(List.of("read")), readTool);
        Map<String, Object> first = flow.start(
            toolCall("call_read", "read", "{\"filepath\": \"/tmp/test.txt\"}")
        );

        List<String> interruptIds = assertInterruptResult(first);
        assertEquals(List.of("call_read"), interruptIds);
        assertEquals("read", getToolNameFromState(stateList(first).get(0)));

        Map<String, Object> second = flow.resume(confirmInterrupt("call_read"));
        assertAnswerResult(second);
        assertEquals(1, readTool.getInvokeCount());

        Runner.resourceMgr().removeAgent("sub_agent_2", null, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().removeAgent("sub_agent_1", null, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().removeAgent("main_agent", null, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().removeTool(readTool.getCard().getId(), null, TagMatchStrategy.ALL, true);
    }
}
