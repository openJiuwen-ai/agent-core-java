/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for the hierarchical message-bus supervisor.
 *
 * <p>Mirrors Python's {@code SupervisorAgent} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/supervisor_agent.py}.</p>
 */
class SupervisorAgentTest {

    @Test
    void constructorInstallsP2PAbilityManagerAndAppliesConfig() {
        AgentCard card = new AgentCard("supervisor", "Supervisor", "Coordinates work");
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureMaxIterations(3);

        SupervisorAgent supervisor = new SupervisorAgent(card, config, 0);

        assertThat(supervisor.getCard()).isSameAs(card);
        assertThat(supervisor.getConfig()).isSameAs(config);
        assertThat(supervisor.getAbilityManager()).isInstanceOf(P2PAbilityManager.class);
        assertThat(supervisor.getP2PAbilityManager().getMaxParallelSubAgents()).isEqualTo(1);
    }

    @Test
    void configureIgnoresNonReactConfigAndReturnsSelf() {
        SupervisorAgent supervisor = new SupervisorAgent(new AgentCard("s", "S", "Supervisor"));
        Object originalConfig = supervisor.getConfig();

        SupervisorAgent result = supervisor.configure("ignored");

        assertThat(result).isSameAs(supervisor);
        assertThat(supervisor.getConfig()).isSameAs(originalConfig);
    }

    @Test
    void registerSubAgentCardExposesAgentCardAsAbility() {
        SupervisorAgent supervisor = new SupervisorAgent(new AgentCard("s", "S", "Supervisor"));
        AgentCard delegate = new AgentCard("agent-a", "delegate", "Delegate");

        supervisor.registerSubAgentCard(delegate);

        assertThat(supervisor.getP2PAbilityManager().getAgents()).containsEntry("delegate", delegate);
    }

    @Test
    void createRejectsEmptyAgentList() {
        assertThatThrownBy(() -> SupervisorAgent.create(
                List.of(),
                null,
                null,
                new AgentCard("s", "S", "Supervisor"),
                "system"
        )).isInstanceOf(BaseError.class)
                .hasMessageContaining("agents list must not be empty");
    }

    @Test
    void createReturnsProviderThatConfiguresSupervisorAndRegistersAgents() {
        AgentCard first = new AgentCard("agent-a", "delegate_a", "Delegate A");
        AgentCard second = new AgentCard("agent-b", "delegate_b", "Delegate B");
        AgentCard supervisorCard = new AgentCard("supervisor", "Supervisor", "Coordinates work");
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("openai")
                .apiKey("key")
                .apiBase("base")
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("model-x").build();

        SupervisorAgent.CreatedSupervisor created = SupervisorAgent.create(
                List.of(first, second),
                clientConfig,
                requestConfig,
                supervisorCard,
                "system prompt",
                7,
                3
        );
        SupervisorAgent supervisor = created.provider().get();

        assertThat(created.agentCard()).isSameAs(supervisorCard);
        assertThat(supervisor.getCard()).isSameAs(supervisorCard);
        assertThat(supervisor.getP2PAbilityManager().getMaxParallelSubAgents()).isEqualTo(3);
        assertThat(supervisor.getP2PAbilityManager().getAgents())
                .containsEntry("delegate_a", first)
                .containsEntry("delegate_b", second);

        ReActAgentConfig config = supervisor.getConfig();
        assertThat(config.getModelClientConfig()).isSameAs(clientConfig);
        assertThat(config.getModelConfigObj()).isSameAs(requestConfig);
        assertThat(config.getModelProvider()).isEqualTo("OpenAI");
        assertThat(config.getApiKey()).isEqualTo("key");
        assertThat(config.getApiBase()).isEqualTo("base");
        assertThat(config.getModelName()).isEqualTo("model-x");
        assertThat(config.getMaxIterations()).isEqualTo(7);
        assertThat(config.getPromptTemplate()).containsExactly(Map.of("role", "system", "content", "system prompt"));
    }
}
