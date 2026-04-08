// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BaseAgent} — rail/callback integration via ReActAgent.
 * Mirrors Python TestRailRegistration, TestRailPriority, TestRailExtra, etc.
 */
class BaseAgentTest {

    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        AgentCard card = AgentCard.builder()
                .name("test-agent")
                .description("Test agent")
                .build();
        agent = new ReActAgent(card);
    }

    @AfterEach
    void tearDown() {
        agent.getAgentCallbackManager().clear(null);
    }

    // ============ Rail Registration ============

    @Test
    void testRegisterRailRegistersHooks() {
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {}

            @Override
            public void afterInvoke(AgentCallbackContext ctx) {}

            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {}
        };

        agent.registerRail(rail);

        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.AFTER_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_MODEL_CALL)).isTrue();
    }

    @Test
    void testRegisterRailAllEightHooks() {
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {}
            @Override
            public void afterInvoke(AgentCallbackContext ctx) {}
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {}
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {}
            @Override
            public void onModelException(AgentCallbackContext ctx) {}
            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {}
            @Override
            public void afterToolCall(AgentCallbackContext ctx) {}
            @Override
            public void onToolException(AgentCallbackContext ctx) {}
        };

        agent.registerRail(rail);

        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            assertThat(agent.getAgentCallbackManager().hasHooks(event)).isTrue();
        }
    }

    // ============ Rail Priority ============

    @Test
    void testRailPriorityOrdering() {
        List<String> order = new ArrayList<>();

        // Higher priority value runs first in CallbackFramework (descending sort)
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("high"), 90);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("low"), 10);

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(order).containsExactly("high", "low");
    }

    // ============ Rail Extra Communication ============

    @Test
    void testRailExtraCommunication() {
        final boolean[] sawWriter = {false};

        // Higher priority value runs first: writer (90) runs before reader (10)
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                ctx -> ctx.getExtra().put("writer_was_here", true), 90);
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                ctx -> sawWriter[0] = Boolean.TRUE.equals(ctx.getExtra().get("writer_was_here")), 10);

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(sawWriter[0]).isTrue();
    }

    // ============ Tool Registration via Rail ============

    @Test
    void testRailToolsAutoRegistration() {
        ToolCard toolCard = ToolCard.builder()
                .name("rail_tool")
                .description("A rail tool")
                .build();

        AgentRail rail = new AgentRail(List.of(toolCard)) {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {}
        };

        agent.registerRail(rail);

        List<String> names = new ArrayList<>();
        for (Object ability : agent.getAbilityManager().list()) {
            if (ability instanceof ToolCard tc) {
                names.add(tc.getName());
            }
        }
        assertThat(names).contains("rail_tool");
    }

    @Test
    void testUnregisterRailRemovesTools() {
        ToolCard toolCard = ToolCard.builder()
                .name("rail_tool_unreg")
                .description("Tool to unregister")
                .build();

        AgentRail rail = new AgentRail(List.of(toolCard)) {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {}
        };

        agent.registerRail(rail);
        assertThat(agent.getAbilityManager().get("rail_tool_unreg")).isNotNull();

        agent.unregisterRail(rail);
        assertThat(agent.getAbilityManager().get("rail_tool_unreg")).isNull();
    }

    @Test
    void testUnregisterRailRemovesHooks() {
        List<String> fired = new ArrayList<>();

        AgentRail rail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
                fired.add("before");
            }
        };

        agent.registerRail(rail);
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder().agent(agent).build());
        assertThat(fired).containsExactly("before");

        agent.unregisterRail(rail);
        fired.clear();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder().agent(agent).build());

        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isFalse();
        assertThat(fired).isEmpty();
    }

    // ============ Register Callback ============

    @Test
    void testRegisterCallback() {
        List<String> fired = new ArrayList<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> fired.add("manual"), 50);

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(fired).containsExactly("manual");
    }

    // ============ FireCallbackEvent ============

    @Test
    void testFireCallbackEventTriggersRegisteredCallbacks() {
        List<AgentCallbackEvent> firedEvents = new ArrayList<>();

        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE,
                ctx -> firedEvents.add(AgentCallbackEvent.BEFORE_INVOKE), 50);
        agent.registerCallback(AgentCallbackEvent.AFTER_INVOKE,
                ctx -> firedEvents.add(AgentCallbackEvent.AFTER_INVOKE), 50);

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(agent)
                .build();
        agent.fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);
        agent.fireCallbackEvent(AgentCallbackEvent.AFTER_INVOKE, ctx);

        assertThat(firedEvents).containsExactly(
                AgentCallbackEvent.BEFORE_INVOKE,
                AgentCallbackEvent.AFTER_INVOKE
        );
    }

    // ============ Agent Card ============

    @Test
    void testGetCard() {
        assertThat(agent.getCard().getName()).isEqualTo("test-agent");
        assertThat(agent.getCard().getDescription()).isEqualTo("Test agent");
    }

    @Test
    void testGetAbilityManager() {
        assertThat(agent.getAbilityManager()).isNotNull();
    }

    @Test
    void testGetAgentCallbackManager() {
        assertThat(agent.getAgentCallbackManager()).isNotNull();
    }

    // ============ Configure ============

    @Test
    void testConfigure() {
        ReActAgentConfig config = ReActAgentConfig.builder()
                .modelName("gpt-4")
                .maxIterations(10)
                .build();

        agent.configure(config);
        assertThat(((ReActAgentConfig) agent.getConfig()).getModelName()).isEqualTo("gpt-4");
        assertThat(((ReActAgentConfig) agent.getConfig()).getMaxIterations()).isEqualTo(10);
    }

    // ============ Chaining ============

    @Test
    void testRegisterRailReturnsAgent() {
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {}
        };
        BaseAgent result = agent.registerRail(rail);
        assertThat(result).isSameAs(agent);
    }

    @Test
    void testRegisterCallbackReturnsAgent() {
        BaseAgent result = agent.registerCallback(
                AgentCallbackEvent.BEFORE_INVOKE, ctx -> {}, 50);
        assertThat(result).isSameAs(agent);
    }
}
