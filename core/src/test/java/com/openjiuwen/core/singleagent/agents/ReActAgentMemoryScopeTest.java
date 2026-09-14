/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

class ReActAgentMemoryScopeTest {

    @Test
    void constructorDoesNotRequireMemoryRuntimeWhenMemScopeIdIsEmpty() {
        assertThatCode(() -> new ReActAgent(agentCard()))
                .doesNotThrowAnyException();
        assertThatCode(() -> new ReActAgentEvolve(agentCard()))
                .doesNotThrowAnyException();
    }

    @Test
    void configureWithoutMemScopeIdDoesNotRequireMemoryRuntime() {
        ReActAgent agent = new ReActAgent(agentCard());

        assertThatCode(() -> agent.configure(new ReActAgentConfig().configureMaxIterations(3)))
                .doesNotThrowAnyException();
    }

    @Test
    void configureWithMemScopeIdRequiresInstalledMemoryRuntime() {
        ReActAgent agent = new ReActAgent(agentCard());
        ReActAgentConfig config = new ReActAgentConfig().configureMemScope("scope-a");

        assertThatThrownBy(() -> agent.configure(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Memory is enabled but agent-core-memory-java is not installed");
    }

    @Test
    void evolveConfigureWithMemScopeIdRequiresInstalledMemoryRuntime() {
        ReActAgentEvolve agent = new ReActAgentEvolve(agentCard());
        ReActAgentConfig config = new ReActAgentConfig().configureMemScope("scope-b");

        assertThatThrownBy(() -> agent.configure(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Memory is enabled but agent-core-memory-java is not installed");
    }

    private static AgentCard agentCard() {
        return new AgentCard("react-memory-scope", "react-memory-scope", "memory scope wiring");
    }
}
