/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.single_agent.legacy.config.ConstrainConfig;
import com.openjiuwen.core.single_agent.legacy.config.IntentDetectionConfig;
import com.openjiuwen.core.single_agent.legacy.config.LegacyReActAgentConfig;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.core.application.llm_agent} in
 * {@code openjiuwen/core/application/llm_agent/__init__.py}.
 */
class LLMAgentPackageTest {

    @Test
    void exportsMirrorPythonAllOrderAndAliases() {
        assertEquals("openjiuwen/core/application/llm_agent/__init__.py", LLMAgentPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "create_llm_agent_config",
                "create_llm_agent",
                "LLMAgent",
                "ConstrainConfig",
                "IntentDetectionConfig",
                "ReActAgentConfig"
        ), LLMAgentPackage.ALL);
        assertSame(LLMAgent.class, LLMAgentPackage.LLM_AGENT);
        assertSame(ConstrainConfig.class, LLMAgentPackage.CONSTRAIN_CONFIG);
        assertSame(IntentDetectionConfig.class, LLMAgentPackage.INTENT_DETECTION_CONFIG);
        assertSame(LegacyReActAgentConfig.class, LLMAgentPackage.REACT_AGENT_CONFIG);
    }

    @Test
    void createLlmAgentConfigDelegatesToFactory() {
        ModelConfig model = ModelConfig.builder().modelProvider("mock").build();
        List<Map<String, Object>> prompt = List.of(Map.of("role", "system", "content", "exact"));

        LegacyReActAgentConfig config = LLMAgentPackage.createLlmAgentConfig(
                "agent-id",
                "v1",
                "desc",
                List.of(),
                List.of(),
                model,
                prompt,
                List.of("search")
        );

        assertEquals("agent-id", config.getId());
        assertEquals("v1", config.getVersion());
        assertEquals("desc", config.getDescription());
        assertEquals(model, config.getModel());
        assertEquals(prompt, config.getPromptTemplate());
        assertEquals(List.of("search"), config.getTools());
    }

    @Test
    void createLlmAgentDelegatesToFactory() {
        LegacyReActAgentConfig config = LLMAgentPackage.createLlmAgentConfig(
                "agent-id",
                "v1",
                "desc",
                List.of(),
                List.of(),
                new ModelConfig(),
                List.of()
        );

        LLMAgent agent = LLMAgentPackage.createLlmAgent(config);

        assertSame(config, agent.getTypedAgentConfig());
        assertTrue(agent.getWorkflows().isEmpty());
    }
}
