/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt.InterruptTestBase.NestedAgentConfig;
import com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt.InterruptTestBase.ReadTool;
import com.openjiuwen.tests.unit_tests.fixtures.MockLLMModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.react_agent.interrupt.test_3layer_agent_interrupt}.
 */
@Tag("system-test")
class ThreeLayerAgentInterruptTest {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    void test_3layer_agent_interrupt() throws Exception {
        ReadTool readTool = new ReadTool();
        Runner.resourceMgr().addTool(readTool, null);

        ReActAgent subAgent2 = InterruptTestBase.createNestedAgent(
                new NestedAgentConfig("sub_agent_2", "sub_agent_2",
                        "You are innermost agent. Call read tool when user requests file read.")
        );
        subAgent2.getAbilityManager().add(readTool.getCard());
        ConfirmInterruptRail rail2 = new ConfirmInterruptRail(List.of("read"));
        subAgent2.registerRail(rail2);

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

        ReActAgent subAgent1 = InterruptTestBase.createNestedAgent(
                new NestedAgentConfig("sub_agent_1", "sub_agent_1",
                        "You are middle agent. Call sub_agent_2 tool for file read tasks.")
        );
        subAgent1.getAbilityManager().add(subAgent2Card);

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

        ReActAgent mainAgent = InterruptTestBase.createNestedAgent(
                new NestedAgentConfig("main_agent", "main_agent",
                        "You are main agent. Call sub_agent_1 tool for tasks.")
        );
        mainAgent.getAbilityManager().add(subAgent1Card);

        MockLLMModel mockLlm = new MockLLMModel();
        mockLlm.setResponses(List.of(
                MockLLMModel.createToolCallResponse("sub_agent_1", "{\"query\": \"read file\"}"),
                MockLLMModel.createToolCallResponse("sub_agent_2", "{\"query\": \"read file\"}"),
                MockLLMModel.createToolCallResponse("read", "{\"filepath\": \"/tmp/test.txt\"}"),
                MockLLMModel.createTextResponse("File read complete")
        ));

        Model mockModel = mock(Model.class);
        when(mockModel.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Object messages = inv.getArgument(0);
                    return mockLlm.invoke(messages);
                });
        when(mockModel.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Object messages = inv.getArgument(0);
                    List<AssistantMessageChunk> chunks = new ArrayList<>();
                    for (AssistantMessage msg : mockLlm.stream(messages)) {
                        chunks.add(AssistantMessageChunk.builder()
                                .content(msg.getContent())
                                .toolCalls(msg.getToolCalls())
                                .usageMetadata(msg.getUsageMetadata())
                                .build());
                    }
                    return chunks.iterator();
                });

        ReActAgent spyMain = spy(mainAgent);
        doReturn(mockModel).when(spyMain).getLlm();
        ReActAgent spySub1 = spy(subAgent1);
        doReturn(mockModel).when(spySub1).getLlm();
        ReActAgent spySub2 = spy(subAgent2);
        doReturn(mockModel).when(spySub2).getLlm();

        Runner.resourceMgr().removeAgent("sub_agent_2", null, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addAgent(subAgent2Card, () -> spySub2, null);
        Runner.resourceMgr().removeAgent("sub_agent_1", null, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addAgent(subAgent1Card, () -> spySub1, null);

        try {
            Object result1 = Runner.runAgent(spyMain,
                    Map.of("query", "Please read file /tmp/test.txt", "conversation_id", "494"),
                    null, null);

            Map<String, Object> result1Map = (Map<String, Object>) result1;
            InterruptTestBase.assertInterruptResult(result1Map, 1);
            List<String> interruptIds = (List<String>) result1Map.get("interrupt_ids");
            String innerToolCallId = interruptIds.get(0);
            List<?> stateList = (List<?>) result1Map.get("state");
            String toolName = InterruptTestBase.getToolNameFromState(stateList.get(0));
            assertEquals("read", toolName, "Expected tool_name 'read', got '" + toolName + "'");

            Object result2 = Runner.runAgent(spyMain,
                    Map.of("query", InterruptTestBase.confirmInterrupt(innerToolCallId),
                            "conversation_id", "494"),
                    null, null);

            Map<String, Object> result2Map = (Map<String, Object>) result2;
            InterruptTestBase.assertAnswerResult(result2Map);
            assertEquals(1, readTool.invokeCount, "Expected read invokeCount=1, got " + readTool.invokeCount);
        } finally {
            Runner.resourceMgr().removeAgent("sub_agent_2", null, TagMatchStrategy.ALL, true);
            Runner.resourceMgr().removeAgent("sub_agent_1", null, TagMatchStrategy.ALL, true);
            Runner.resourceMgr().removeAgent("main_agent", null, TagMatchStrategy.ALL, true);
            Runner.resourceMgr().removeTool("read", null, TagMatchStrategy.ALL, true);
        }
    }
}
