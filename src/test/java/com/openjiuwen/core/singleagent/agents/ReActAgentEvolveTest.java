// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ReActAgentEvolve}.
 */
class ReActAgentEvolveTest {

    private ReActAgentEvolve agent;

    private static final class TestSession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new HashMap<>();

        private TestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }

    @BeforeEach
    void setUp() {
        AgentCard card = AgentCard.builder()
                .name("test-evolve-agent")
                .description("Test ReActAgentEvolve")
                .build();
        agent = new ReActAgentEvolve(card);
    }

    // ========== Construction ==========

    @Test
    void testConstruction() {
        assertThat(agent.getCard().getName()).isEqualTo("test-evolve-agent");
        assertThat(agent.getContextEngine()).isNotNull();
        assertThat(agent.getConfig()).isInstanceOf(ReActAgentConfig.class);
    }

    @Test
    void testDefaultConfig() {
        ReActAgentConfig config = (ReActAgentConfig) agent.getConfig();
        assertThat(config.getMaxIterations()).isEqualTo(5);
        assertThat(config.getModelProvider()).isEqualTo("openai");
    }

    // ========== Configure ==========

    @Test
    void testConfigureWithReActAgentConfig() {
        ReActAgentConfig newConfig = ReActAgentConfig.builder()
                .modelName("gpt-4-turbo")
                .maxIterations(10)
                .build();

        BaseAgent result = agent.configure(newConfig);

        assertThat(result).isSameAs(agent);
        ReActAgentConfig actual = (ReActAgentConfig) agent.getConfig();
        assertThat(actual.getModelName()).isEqualTo("gpt-4-turbo");
        assertThat(actual.getMaxIterations()).isEqualTo(10);
    }

    @Test
    void testConfigureWithWrongTypeThrows() {
        assertThatThrownBy(() -> agent.configure("wrong type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected ReActAgentConfig");
    }

    @Test
    void testConfigureWithNullThrows() {
        assertThatThrownBy(() -> agent.configure(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConfigureResetsLlmOnProviderChange() {
        ReActAgentConfig config1 = ReActAgentConfig.builder()
                .modelProvider("provider1")
                .build();
        agent.configure(config1);

        ReActAgentConfig config2 = ReActAgentConfig.builder()
                .modelProvider("provider2")
                .build();
        agent.configure(config2);

        // LLM should have been reset; getLlm() will throw since no model config
        assertThatThrownBy(() -> agent.getLlm())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model_client_config is required");
    }

    @Test
    void testConfigurePreservesLlmWhenProviderSame() {
        ReActAgentConfig config1 = ReActAgentConfig.builder()
                .modelProvider("openai")
                .maxIterations(3)
                .build();
        agent.configure(config1);

        ReActAgentConfig config2 = ReActAgentConfig.builder()
                .modelProvider("openai")
                .maxIterations(7)
                .build();
        agent.configure(config2);

        assertThat(((ReActAgentConfig) agent.getConfig()).getMaxIterations()).isEqualTo(7);
    }

    // ========== GetLlm ==========

    @Test
    void testGetLlmThrowsWithoutConfig() {
        assertThatThrownBy(() -> agent.getLlm())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model_client_config is required");
    }

    // ========== GetOperators ==========

    @Test
    void testGetOperators() {
        Map<String, ?> operators = agent.getOperators();
        // toolOp should be present, llmOp may fail (no model config) and be skipped
        assertThat(operators).isNotNull();
        assertThat(operators).containsKey("react_tool");
    }

    @Test
    void testGetOperatorsSkipsLlmOpWhenNotConfigured() {
        Map<String, ?> operators = agent.getOperators();
        // llmOp should be silently skipped since no model config
        assertThat(operators).doesNotContainKey("react_llm");
    }

    // ========== Invoke ==========

    @Test
    void testInvokeNullInputThrows() {
        assertThatThrownBy(() -> agent.invoke(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvokeInvalidTypeThrows() {
        assertThatThrownBy(() -> agent.invoke(42, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Input must be dict with 'query' or String");
    }

    @Test
    void testInvokeMapWithoutQueryThrows() {
        assertThatThrownBy(() -> agent.invoke(Map.of("text", "hello"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Input dict must contain 'query'");
    }

    // ========== Rail Integration ==========

    @Test
    void testRailRegistration() {
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {}
            @Override
            public void afterInvoke(AgentCallbackContext ctx) {}
        };

        agent.registerRail(rail);

        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.AFTER_INVOKE)).isTrue();
    }

    // ========== Inherited ==========

    @Test
    void testAbilityManagerAvailable() {
        assertThat(agent.getAbilityManager()).isNotNull();
    }

    @Test
    void testCallbackManagerAvailable() {
        assertThat(agent.getAgentCallbackManager()).isNotNull();
    }

    @Test
    void testSkillUtilCreated() {
        assertThat(agent.getSkillUtil()).isNotNull();
    }

    @Test
    void testAfterInvokeCallbackSeesInvokeInputsOnFailure() {
        java.util.List<EventInputs> seenInputs = new ArrayList<>();
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE, ctx -> seenInputs.add(ctx.getInputs()), 50);

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "needs-model"), null))
                .isInstanceOf(Exception.class);

        assertThat(seenInputs).hasSize(1);
        assertThat(seenInputs.get(0)).isInstanceOf(InvokeInputs.class);
        assertThat(((InvokeInputs) seenInputs.get(0)).getQuery()).isEqualTo("needs-model");
    }

    @Test
    void testEnableReloadRegistersContextReloaderTool() {
        ReActAgentConfig config = ReActAgentConfig.builder().build()
                .configureContextEngine(200, 10, true);
        agent.configure(config);
        Session session = new TestSession("react-evolve-reload-session");

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "reload"), session))
                .isInstanceOf(Exception.class);

        assertThat(agent.getAbilityManager().get("reload_original_context_messages")).isNotNull();
    }
}
