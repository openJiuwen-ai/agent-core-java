/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.subagents.PlanAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for plan-agent configuration helpers.
 *
 * <p>Mirrors Python's {@code test_plan_agent} in
 * {@code tests.unit_tests.harness.test_plan_agent}.
 */
class TestPlanAgent {

    @TempDir
    Path tempDir;

    @Test
    @Tag("level0")
    @DisplayName("PlanAgent exposes bilingual descriptions and guarded prompts")
    void testPlanAgentExposesBilingualDescriptionsAndPrompts() {
        assertEquals("plan_agent", PlanAgent.FACTORY_NAME);

        String cnDescription = PlanAgent.getDescription("cn");
        String enDescription = PlanAgent.getDescription("en");
        String cnPrompt = PlanAgent.getSystemPrompt("cn");
        String enPrompt = PlanAgent.getSystemPrompt("en");

        assertFalse(cnDescription.isBlank());
        assertFalse(enDescription.isBlank());
        assertTrue(cnPrompt.contains("Critical Files for Implementation"));
        assertTrue(enPrompt.contains("Critical Files for Implementation"));
        assertTrue(cnPrompt.contains("只读模式") || cnPrompt.contains("READ-ONLY"));
        assertTrue(enPrompt.contains("READ-ONLY"));
    }

    @Test
    @Tag("level0")
    @DisplayName("buildPlanAgentConfig uses Python-aligned defaults")
    void testBuildPlanAgentConfigUsesDefaults() {
        DeepAgentConfig config = PlanAgent.buildPlanAgentConfig("en");

        assertEquals("plan_agent", config.getCard().getName());
        assertEquals(PlanAgent.getDescription("en"), config.getCard().getDescription());
        assertEquals(PlanAgent.getSystemPrompt("en"), config.getSystemPrompt());
        assertEquals(1, config.getRails().size());
        assertInstanceOf(SysOperationRail.class, config.getRails().get(0));
        assertFalse(config.getEnableTaskLoop());
        assertEquals(25, config.getMaxIterations());
    }

    @Test
    @Tag("level0")
    @DisplayName("buildPlanAgentConfig respects explicit overrides")
    void testBuildPlanAgentConfigRespectsOverrides() {
        AgentCard customCard = AgentCard.builder()
                .name("custom_plan")
                .description("custom description")
                .build();
        List<AgentRail> customRails = List.of();

        DeepAgentConfig config = PlanAgent.buildPlanAgentConfig(
                null,
                customCard,
                "custom prompt",
                null,
                customRails,
                true,
                10,
                List.of(),
                null,
                null,
                "en"
        );

        assertEquals("custom_plan", config.getCard().getName());
        assertEquals("custom description", config.getCard().getDescription());
        assertEquals("custom prompt", config.getSystemPrompt());
        assertTrue(config.getRails().isEmpty());
        assertTrue(config.getEnableTaskLoop());
        assertEquals(10, config.getMaxIterations());
    }

    @Test
    @Tag("level0")
    @DisplayName("createPlanAgent builds a DeepAgent with resolved workspace")
    void testCreatePlanAgentBuildsDeepAgent() {
        DeepAgent agent = PlanAgent.createPlanAgent(createDummyModel(), tempDir.toString(), "en");

        assertNotNull(agent);
        assertEquals("plan_agent", agent.getCard().getName());
        assertEquals(PlanAgent.getSystemPrompt("en"), ((DeepAgentConfig) agent.getConfig()).getSystemPrompt());
        assertNotNull(agent.getWorkspace());
        assertEquals(tempDir.toString(), agent.getWorkspace().getRootPath());
    }

    private static Model createDummyModel() {
        return new Model(
                ModelClientConfig.builder()
                        .clientProvider("OpenAI")
                        .apiKey("test-key")
                        .apiBase("http://test-base")
                        .verifySsl(false)
                        .build(),
                ModelRequestConfig.builder()
                        .modelName("test-model")
                        .build()
        );
    }
}
