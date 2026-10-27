/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.cli.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.harness.cli.agent.CliAgentFactory;
import com.openjiuwen.harness.cli.rails.TokenTrackingRail;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

/**
 * Mirrors Python's {@code tests.cli.integration.test_agent_factory} in
 * {@code tests/cli/integration/test_agent_factory.py}.
 */
class AgentFactoryIntegrationMissingTest {

    private MockedStatic<Runner> runner;

    @BeforeEach
    void setUp() {
        ResourceMgr resourceMgr = new ResourceMgr();
        // Keep real registration checks while isolating built-in tool IDs from other test classes.
        runner = mockStatic(Runner.class, CALLS_REAL_METHODS);
        runner.when(Runner::resourceMgr).thenReturn(resourceMgr);
        runner.when(Runner::getResourceMgr).thenReturn(resourceMgr);
    }

    @AfterEach
    void tearDown() {
        runner.close();
    }

    @Test
    void createAgentReturnsAgentAndTrackerTuple() {
        CliAgentFactory.AgentBundle bundle = CliAgentFactory.createAgent(Map.of(
                "api_key", "test-key",
                "model", "gpt-4o"));

        try (DeepAgent agent = bundle.agent()) {
            assertThat(agent).isNotNull();
            assertThat(bundle.tracker()).isInstanceOf(TokenTrackingRail.class);
        }
    }

    @Test
    void createAgentPassesCorrectParams() {
        CliAgentFactory.AgentBundle bundle = CliAgentFactory.createAgent(Map.of(
                "api_key", "key",
                "model", "qwen-max",
                "max_iterations", 50));

        try (DeepAgent agent = bundle.agent()) {
            assertThat(agent.deepConfig().isEnableTaskLoop()).isTrue();
            assertThat(agent.deepConfig().getMaxIterations()).isEqualTo(50);
            assertThat(agent.deepConfig().getLanguage()).isEqualTo("en");
            if (agent.deepConfig().getModel() instanceof Model model) {
                assertThat(model.getModelConfig().getModelName()).isEqualTo("qwen-max");
            } else {
                throw new AssertionError("CLI agent model must be a Model instance");
            }
        }
    }
}
