/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.cli.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    private ResourceMgr resourceMgr;
    private MockedStatic<Runner> runner;

    @BeforeEach
    void isolateResources() {
        resourceMgr = new ResourceMgr();
        // Exercise real duplicate validation without consuming or replacing another fixture's tools.
        runner = mockStatic(Runner.class, CALLS_REAL_METHODS);
        runner.when(Runner::resourceMgr).thenReturn(resourceMgr);
    }

    @AfterEach
    void releaseResources() {
        try {
            resourceMgr.release().toCompletableFuture().join();
        } finally {
            runner.close();
        }
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
            Model model = assertInstanceOf(Model.class, agent.deepConfig().getModel());
            assertThat(model.getModelConfig().getModelName()).isEqualTo("qwen-max");
        }
    }
}
