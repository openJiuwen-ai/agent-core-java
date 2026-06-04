/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeepAgent public APIs.
 */
class TestDeepAgent {

    @Test
    @Tag("level0")
    @DisplayName("DeepAgent can be created with AgentCard")
    void testDeepAgentCanBeCreated() {
        AgentCard card = AgentCard.builder()
            .id("test-agent")
            .name("Test Agent")
            .description("Test agent")
            .build();
        
        DeepAgent agent = new DeepAgent(card);
        assertNotNull(agent);
        assertEquals("test-agent", agent.getCard().getId());
    }

    @Test
    @Tag("level0")
    @DisplayName("DeepAgent initializes with config")
    void testDeepAgentInitializesWithConfig() {
        AgentCard card = AgentCard.builder()
            .id("config-test")
            .name("Config Test")
            .description("Config test")
            .build();
        
        DeepAgent agent = new DeepAgent(card);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        
        agent.configure(config);
        assertNotNull(agent);
    }

    @Test
    @Tag("level0")
    @DisplayName("DeepAgent registers rails correctly")
    void testDeepAgentRegistersRailsCorrectly() {
        AgentCard card = AgentCard.builder()
            .id("rail-test")
            .name("Rail Test")
            .description("Rail test")
            .build();
        
        DeepAgent agent = new DeepAgent(card);
        assertNotNull(agent.getAbilityManager());
    }

    @Test
    @Tag("level0")
    @DisplayName("DeepAgent configures subagents")
    void testDeepAgentConfiguresSubagents() {
        AgentCard card = AgentCard.builder()
            .id("subagent-test")
            .name("Subagent Test")
            .description("Subagent test")
            .build();
        
        DeepAgent agent = new DeepAgent(card);
        assertTrue(agent instanceof com.openjiuwen.core.singleagent.BaseAgent);
    }

    @Test
    @Tag("level0")
    @DisplayName("DeepAgent registers tools")
    void testDeepAgentRegistersTools() {
        AgentCard card = AgentCard.builder()
            .id("tool-test")
            .name("Tool Test")
            .description("Tool test")
            .build();
        
        DeepAgent agent = new DeepAgent(card);
        assertNotNull(agent.getAbilityManager());
    }

    @Test
    @Tag("level0")
    @DisplayName("DeepAgent fires task iteration callbacks")
    void testDeepAgentFiresTaskIterationCallbacks() {
        AgentCard card = AgentCard.builder()
            .id("callback-test")
            .name("Callback Test")
            .description("Callback test")
            .build();
        DeepAgent agent = new DeepAgent(card);
        List<Object> seen = new ArrayList<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_TASK_ITERATION, ctx -> {
            TaskIterationInputs inputs = (TaskIterationInputs) ctx.getInputs();
            seen.add(inputs.getIteration());
            seen.add(inputs.getQuery());
            seen.add(ctx.getExtra().get("task_id"));
        }, 10);

        agent.fireCallback("before_task_iteration", Map.of(
            "iteration", 2,
            "query", "continue",
            "task_id", "task-1"
        ));

        assertEquals(List.of(2, "continue", "task-1"), seen);
    }

    @Test
    @Tag("level0")
    @DisplayName("DeepAgent cancels tracked session task")
    void testDeepAgentCancelsTrackedSessionTask() {
        AgentCard card = AgentCard.builder()
            .id("cancel-test")
            .name("Cancel Test")
            .description("Cancel test")
            .build();
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
        toolkit.upsertTask("task-1", "sub-1", "work", "running");
        config.setSessionToolkit(toolkit);
        DeepAgent agent = new DeepAgent(card);
        agent.configure(config);

        agent.cancelTask("task-1");

        assertEquals("canceled", toolkit.listTasks().get(0).get("status"));
    }

    @Test
    @Tag("level0")
    @DisplayName("DeepAgent records spawned session task")
    void testDeepAgentRecordsSpawnedSessionTask() {
        AgentCard card = AgentCard.builder()
            .id("spawn-test")
            .name("Spawn Test")
            .description("Spawn test")
            .build();
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        DeepAgentConfig.SessionToolkit toolkit = new DeepAgentConfig.SessionToolkit();
        config.setSessionToolkit(toolkit);
        DeepAgent agent = new DeepAgent(card);
        agent.configure(config);

        agent.spawnSubagentTask("task-1", "general-purpose", "do work", "sub-1");

        Map<String, Object> row = toolkit.listTasks().get(0);
        assertEquals("task-1", row.get("task_id"));
        assertEquals("sub-1", row.get("sub_session_id"));
        assertEquals("do work", row.get("description"));
        assertEquals("running", row.get("status"));
    }
}
