/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.config.AgentRuntimeConfig;
import com.openjiuwen.agent_evolving.agent_rl.offline.runtime.AgentFactory;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RLTask;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AgentFactory.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/agent_rl/offline/runtime/test_agent_factory.py}.
 */
@DisplayName("AgentFactory Tests")
class TestAgentFactory {

    @Test
    @DisplayName("build agent factory returns factory with unset proxy")
    void testBuildAgentFactoryReturnsFactory() {
        AgentRuntimeConfig cfg = new AgentRuntimeConfig();
        cfg.setSystemPrompt("You are a helpful assistant.");
        cfg.setTemperature(0.7d);
        cfg.setTopP(0.9d);
        cfg.setMaxNewTokens(512);

        AgentFactory factory = AgentFactory.buildAgentFactory(cfg, List.of(), List.of());

        assertThat(factory).isInstanceOf(AgentFactory.class);
        assertThat(factory.getProxyUrl()).isNull();
        assertThat(factory.getSystemPrompt()).isEqualTo("You are a helpful assistant.");
        assertThat(factory.getTemperature()).isEqualTo(0.7d);
        assertThat(factory.getTopP()).isEqualTo(0.9d);
        assertThat(factory.getMaxNewTokens()).isEqualTo(512);
    }

    @Test
    @DisplayName("create agent without proxy url raises")
    void testCallWithoutProxyUrlRaises() {
        AgentFactory factory = new AgentFactory(
                "test",
                List.of(),
                List.of(),
                0.7d,
                128,
                0.9d,
                0.0d,
                0.0d);
        RLTask task = new RLTask("t1", "o1", Map.of(), 0);

        assertThatThrownBy(() -> factory.createAgent(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proxy_url");
    }

    @Test
    @DisplayName("create agent configures DeepAgent from runtime parameters")
    void testCreateAgentConfiguresDeepAgent() {
        AgentRuntimeConfig cfg = new AgentRuntimeConfig();
        cfg.setSystemPrompt("system prompt");
        cfg.setTemperature(0.2d);
        cfg.setTopP(0.8d);
        cfg.setMaxNewTokens(256);
        cfg.setPresencePenalty(1.25d);
        cfg.setFrequencyPenalty(0.5d);
        ToolCard toolCard = ToolCard.builder()
                .id("lookup")
                .name("lookup")
                .description("Lookup data")
                .build();

        AgentFactory factory = AgentFactory.buildAgentFactory(cfg, List.of(toolCard), List.of("lookup"));
        factory.setProxyUrl("http://127.0.0.1:8000/");
        DeepAgent agent = factory.createAgent(new RLTask("task-1", "origin-1", Map.of(), 0));

        assertThat(agent.getCard().getId()).isEqualTo("rl_agent_task-1");
        assertThat(agent.getCard().getName()).isEqualTo("RLTrainingAgent");
        assertThat(agent.getCard().getDescription()).isEqualTo("RL training agent based on DeepAgent");

        DeepAgentConfig deepAgentConfig = (DeepAgentConfig) agent.getConfig();
        assertThat(deepAgentConfig.getSystemPrompt()).isEqualTo("system prompt");
        assertThat(deepAgentConfig.getMaxIterations()).isEqualTo(10);
        assertThat(deepAgentConfig.getTools()).extracting(ToolCard::getName).containsExactly("lookup");
        assertThat(agent.getAbilityManager().get("lookup")).isSameAs(toolCard);
        assertThat(agent.getDelegate().getAbilityManager().get("lookup")).isSameAs(toolCard);

        ModelClientConfig clientConfig = deepAgentConfig.getModelClientConfig();
        assertThat(clientConfig.getClientProvider()).isEqualTo("OpenAI");
        assertThat(clientConfig.getApiKey()).isEqualTo("EMPTY");
        assertThat(clientConfig.getApiBase()).isEqualTo("http://127.0.0.1:8000/v1");
        assertThat(clientConfig.getTimeout()).isEqualTo(300.0d);
        assertThat(clientConfig.isVerifySsl()).isFalse();

        ModelRequestConfig requestConfig = deepAgentConfig.getModelRequestConfig();
        assertThat(requestConfig.getModelName()).isEqualTo("agentrl");
        assertThat(requestConfig.getTemperature()).isEqualTo(0.2d);
        assertThat(requestConfig.getTopP()).isEqualTo(0.8d);
        assertThat(requestConfig.getMaxTokens()).isEqualTo(256);
        assertThat(requestConfig.getExtraFields()).containsEntry("presencePenalty", 1.25d);
        assertThat(requestConfig.getExtraFields()).containsEntry("frequencyPenalty", 0.5d);
    }

    @Test
    @DisplayName("compatibility constructor and string createAgent create DeepAgent")
    void testCompatibilityCreateAgentFromConfig() {
        AgentFactory factory = new AgentFactory(Map.of(
                "system_prompt", "compat prompt",
                "temperature", 0.1d,
                "top_p", 0.6d,
                "max_new_tokens", 64
        ), null);
        factory.setProxy_url("http://localhost:9000");

        Object agent = factory.createAgent("compat-task");

        assertThat(agent).isInstanceOf(DeepAgent.class);
        DeepAgent deepAgent = (DeepAgent) agent;
        DeepAgentConfig config = (DeepAgentConfig) deepAgent.getConfig();
        assertThat(deepAgent.getCard().getId()).isEqualTo("rl_agent_compat-task");
        assertThat(config.getSystemPrompt()).isEqualTo("compat prompt");
        assertThat(config.getModelClientConfig().getApiBase()).isEqualTo("http://localhost:9000/v1");
        assertThat(config.getModelRequestConfig().getMaxTokens()).isEqualTo(64);
    }
}
