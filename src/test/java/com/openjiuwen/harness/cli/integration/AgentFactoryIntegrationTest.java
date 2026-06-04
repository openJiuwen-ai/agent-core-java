/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.integration;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.cli.agent.CliAgentFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-01: Agent factory integration tests.
 * <p>
 * Mirrors Python's {@code test_agent_factory} in
 * {@code tests.cli.integration.test_agent_factory}.
 */
class AgentFactoryIntegrationTest {

    @Test
    void createFromConfigReturnsNullPlaceholder() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("api_key", "test-key");
        config.put("model", "gpt-4o");
        Object result = CliAgentFactory.createFromConfig(config);
        assertInstanceOf(CliAgentFactory.AgentAndTracker.class, result);
        CliAgentFactory.AgentAndTracker pair = (CliAgentFactory.AgentAndTracker) result;
        assertNotNull(pair.getAgent());
        assertNotNull(pair.getTracker());
    }

    @Test
    void createFromConfigWithMaxIterations() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("api_key", "key");
        config.put("model", "qwen-max");
        config.put("max_iterations", 50);
        Object result = CliAgentFactory.createFromConfig(config);
        CliAgentFactory.AgentAndTracker pair = assertInstanceOf(CliAgentFactory.AgentAndTracker.class, result);
        DeepAgent agent = assertInstanceOf(DeepAgent.class, pair.getAgent());
        DeepAgentConfig agentConfig = assertInstanceOf(DeepAgentConfig.class, agent.getConfig());
        assertEquals(50, agentConfig.getMaxIterations());
    }

    @Test
    void factoryAcceptsConfigMap() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("api_key", "test");
        config.put("model", "gpt-4o");
        config.put("enable_task_loop", true);
        config.put("max_iterations", 50);
        config.put("language", "en");
        assertDoesNotThrow(() -> CliAgentFactory.createFromConfig(config));
    }
}
