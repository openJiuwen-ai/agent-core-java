/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.cli.integration;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.harness.cli.agent.CliAgentFactory;
import com.openjiuwen.harness.cli.rails.TokenTrackingRail;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.cli.integration.test_agent_factory} in
 * {@code tests/cli/integration/test_agent_factory.py}.
 */
class AgentFactoryIntegrationMissingTest {

    @Test
    void createAgentReturnsAgentAndTrackerTuple() {
        CliAgentFactory.AgentBundle bundle = CliAgentFactory.createAgent(Map.of(
                "api_key", "test-key",
                "model", "gpt-4o"));

        assertThat(bundle.agent()).isNotNull();
        assertThat(bundle.tracker()).isInstanceOf(TokenTrackingRail.class);
    }

    @Test
    void createAgentPassesCorrectParams() {
        CliAgentFactory.AgentBundle bundle = CliAgentFactory.createAgent(Map.of(
                "api_key", "key",
                "model", "qwen-max",
                "max_iterations", 50));

        assertThat(bundle.agent().deepConfig().isEnableTaskLoop()).isTrue();
        assertThat(bundle.agent().deepConfig().getMaxIterations()).isEqualTo(50);
        assertThat(bundle.agent().deepConfig().getLanguage()).isEqualTo("en");
        assertThat(bundle.agent().deepConfig().getModel()).isInstanceOf(Model.class);
        Model model = (Model) bundle.agent().deepConfig().getModel();
        assertThat(model.getModelConfig().getModelName()).isEqualTo("qwen-max");
    }
}
