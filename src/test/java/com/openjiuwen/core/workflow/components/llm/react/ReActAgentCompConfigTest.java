/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm.react;

import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the ReAct workflow component configuration.
 *
 * <p>Mirrors Python's {@code ReActAgentCompConfig} in
 * {@code openjiuwen/core/workflow/components/llm/react/react_config.py}.</p>
 */
class ReActAgentCompConfigTest {

    @Test
    void componentConfigIsAnEmptySubclassOfReactAgentConfig() {
        ReActAgentCompConfig config = new ReActAgentCompConfig();

        assertTrue(config instanceof ReActAgentConfig);
        assertEquals("", config.getModelName());
        assertEquals("openai", config.getModelProvider());
        assertEquals(5, config.getMaxIterations());
    }

    @Test
    void inheritedMutatorsUpdateSameInstance() {
        ReActAgentCompConfig config = new ReActAgentCompConfig();

        ReActAgentConfig returned = config
                .configureModel("qwen")
                .configureModelProvider("openai", "key", "base")
                .configureMaxIterations(3);

        assertSame(config, returned);
        assertEquals("qwen", config.getModelName());
        assertEquals("key", config.getApiKey());
        assertEquals("base", config.getApiBase());
        assertEquals(3, config.getMaxIterations());
    }
}
