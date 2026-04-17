// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReActAgent}.
 * Translated from Python test_rail.py with additional coverage.
 */
class ReActAgentTest {

    private ReActAgent agent;

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
                .name("test-react-agent")
                .description("Test ReAct Agent")
                .build();
        agent = new ReActAgent(card);
    }

    @AfterEach
    void tearDown() {
        agent.getAgentCallbackManager().clear(null);
    }

    // ========== Construction ==========

    @Test
    void testDefaultConfig() {
        ReActAgentConfig config = (ReActAgentConfig) agent.getConfig();
        assertThat(config).isNotNull();
        assertThat(config.getMaxIterations()).isEqualTo(5);
        assertThat(config.getModelProvider()).isEqualTo("openai");
    }

    @Test
    void testContextEngineCreated() {
        assertThat(agent.getContextEngine()).isNotNull();
    }

    // ========== Configure ==========

    @Test
    void testConfigureWithReActAgentConfig() {
        ReActAgentConfig newConfig = ReActAgentConfig.builder()
                .modelName("gpt-4")
                .maxIterations(10)
                .build();

        agent.configure(newConfig);
        assertThat(((ReActAgentConfig) agent.getConfig()).getModelName()).isEqualTo("gpt-4");
    }

    @Test
    void testConfigureWithWrongTypeThrows() {
        assertThatThrownBy(() -> agent.configure("wrong type"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected ReActAgentConfig");
    }

    @Test
    void testConfigureReturnsAgent() {
        ReActAgentConfig config = ReActAgentConfig.builder().build();
        BaseAgent result = agent.configure(config);
        assertThat(result).isSameAs(agent);
    }

    // ========== Invoke with null/invalid inputs ==========

    @Test
    void testInvokeNullInputThrows() {
        assertThatThrownBy(() -> agent.invoke(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvokeInvalidTypeThrows() {
        assertThatThrownBy(() -> agent.invoke(42, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvokeEmptyQueryThrows() {
        assertThatThrownBy(() -> agent.invoke(Map.of("query", ""), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== Rail Registration on ReActAgent (mirrors Python tests) ==========

    @Test
    void testRailRegistration() {
        List<String> events = new ArrayList<>();
        AgentRail logRail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
                events.add("before_invoke");
            }
            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
                events.add("after_invoke");
            }
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                events.add("before_model_call");
            }
        };

        agent.registerRail(logRail);

        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.AFTER_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_MODEL_CALL)).isTrue();
    }

    @Test
    void testRailAllEightEvents() {
        AgentRail allHooksRail = new AgentRail() {
            @Override public void beforeInvoke(AgentCallbackContext ctx) {}
            @Override public void afterInvoke(AgentCallbackContext ctx) {}
            @Override public void beforeModelCall(AgentCallbackContext ctx) {}
            @Override public void afterModelCall(AgentCallbackContext ctx) {}
            @Override public void onModelException(AgentCallbackContext ctx) {}
            @Override public void beforeToolCall(AgentCallbackContext ctx) {}
            @Override public void afterToolCall(AgentCallbackContext ctx) {}
            @Override public void onToolException(AgentCallbackContext ctx) {}
        };

        agent.registerRail(allHooksRail);

        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            assertThat(agent.getAgentCallbackManager().hasHooks(event))
                    .as("Event %s should have hooks", event)
                    .isTrue();
        }
    }

    // ========== Rail Tools Auto Registration (mirrors Python test_rail_tools_auto_registration) ==========

    @Test
    void testRailToolsAutoRegistration() {
        ToolCard toolCard = ToolCard.builder()
                .name("rail_tool")
                .description("A rail tool")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();

        AgentRail toolRail = new AgentRail(List.of(toolCard)) {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {}
        };

        agent.registerRail(toolRail);

        List<String> names = new ArrayList<>();
        for (Object ability : agent.getAbilityManager().list()) {
            if (ability instanceof ToolCard tc) {
                names.add(tc.getName());
            }
        }
        assertThat(names).contains("rail_tool");
    }

    @Test
    void testRailUnregisterRemovesTools() {
        ToolCard toolCard = ToolCard.builder()
                .name("rail_tool_remove")
                .description("Tool to remove")
                .build();

        AgentRail toolRail = new AgentRail(List.of(toolCard)) {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {}
        };

        agent.registerRail(toolRail);
        assertThat(agent.getAbilityManager().get("rail_tool_remove")).isNotNull();

        agent.unregisterRail(toolRail);
        assertThat(agent.getAbilityManager().get("rail_tool_remove")).isNull();
    }

    // ========== Rail Priority (mirrors Python TestRailPriority) ==========

    @Test
    void testRailPriorityOrdering() {
        List<String> order = new ArrayList<>();

        // Use registerCallback to avoid anonymous class reflection issues
        // Higher priority value runs first in CallbackFramework
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("high"), 90);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("low"), 10);

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(order).containsExactly("high", "low");
    }

    // ========== Rail Extra Communication (mirrors Python TestRailExtra) ==========

    @Test
    void testRailExtraCommunication() {
        final boolean[] sawWriter = {false};

        // Use registerCallback; higher priority runs first
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                ctx -> ctx.getExtra().put("writer_was_here", true), 90);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                ctx -> sawWriter[0] = Boolean.TRUE.equals(ctx.getExtra().get("writer_was_here")), 10);

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(sawWriter[0]).isTrue();
    }

    // ========== Method data visibility (mirrors Python TestMethodSplitDataVisibility) ==========

    @Test
    void testBeforeCallbackSeesInputsData() {
        List<Object> seenMessages = new ArrayList<>();

        // Use registerCallback to avoid reflection access issues
        agent.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx -> {
            if (ctx.getInputs() instanceof ModelCallInputs mci) {
                seenMessages.addAll(mci.getMessages());
            }
        }, 50);

        // Manually fire with some inputs to simulate
        ModelCallInputs inputs = ModelCallInputs.builder()
                .messages(List.of("msg1", "msg2"))
                .build();

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .inputs(inputs)
                .build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx);

        assertThat(seenMessages).hasSize(2);
    }

    // ========== getLlm throws when no config ==========

    @Test
    void testGetLlmThrowsWithoutClientConfig() {
        // Default config has no model_client_config
        assertThatThrownBy(() -> agent.invoke(Map.of("query", "test"), null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testAfterInvokeCallbackSeesInvokeInputsOnFailure() {
        List<EventInputs> seenInputs = new ArrayList<>();
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
        Session session = new TestSession("react-reload-session");

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "reload"), session))
                .isInstanceOf(Exception.class);

        assertThat(agent.getAbilityManager().get("reload_original_context_messages")).isNotNull();
    }
}
