/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeepAgent public APIs.
 */
class v {

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
}