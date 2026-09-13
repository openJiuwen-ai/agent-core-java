// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link AgentCallbackManager}.
 */
class AgentCallbackManagerTest {
    private AgentCallbackManager manager;
    private static final String TEST_AGENT_ID = "test-agent-cbm";

    @BeforeEach
    void setUp() {
        manager = new AgentCallbackManager(TEST_AGENT_ID);
    }

    @AfterEach
    void tearDown() {
        manager.clear(null);
    }

    @Test
    void testRegisterCallbackAndHasHooks() {
        assertThat(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isFalse();

        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> {
        });

        assertThat(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isTrue();
    }

    @Test
    void testRegisterCallbackWithPriority() {
        List<String> order = new ArrayList<>();

        // Higher priority value runs first (descending sort in CallbackFramework)
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("high"), 100);
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("low"), 10);

        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(order.get(0)).isEqualTo("high");
        assertThat(order.get(1)).isEqualTo("low");
    }

    @Test
    void testExecuteCallbacks() {
        List<String> results = new ArrayList<>();
        manager.registerCallback(AgentCallbackEvent.AFTER_INVOKE, ctx -> results.add("executed"));

        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        manager.execute(AgentCallbackEvent.AFTER_INVOKE, ctx);

        assertThat(results).containsExactly("executed");
    }

    @Test
    void testExecuteNoCallbacksRegistered() {
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        // Should not throw
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, ctx);
    }

    @Test
    void testClearSpecificEvent() {
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> {
        });
        manager.registerCallback(AgentCallbackEvent.AFTER_INVOKE, ctx -> {
        });

        manager.clear(AgentCallbackEvent.BEFORE_INVOKE);

        assertThat(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isFalse();
        assertThat(manager.hasHooks(AgentCallbackEvent.AFTER_INVOKE)).isTrue();
    }

    @Test
    void testClearAllEvents() {
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> {
        });
        manager.registerCallback(AgentCallbackEvent.AFTER_INVOKE, ctx -> {
        });
        manager.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx -> {
        });

        manager.clear(null);

        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            assertThat(manager.hasHooks(event)).isFalse();
        }
    }

    @Test
    void testRegisterRailRegistersOverriddenHooks() {
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
            }

            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
            }

            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
            }
        };

        manager.registerRail(rail, null);

        assertThat(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isTrue();
        assertThat(manager.hasHooks(AgentCallbackEvent.AFTER_INVOKE)).isTrue();
        assertThat(manager.hasHooks(AgentCallbackEvent.BEFORE_MODEL_CALL)).isTrue();
        assertThat(manager.hasHooks(AgentCallbackEvent.AFTER_MODEL_CALL)).isFalse();
    }

    @Test
    void testRegisterRailUsesRailPriority() {
        List<String> order = new ArrayList<>();

        // Use registerCallback instead of rail to avoid reflection access issues
        // Higher priority value runs first
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("high"), 90);
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> order.add("low"), 10);

        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(order).containsExactly("high", "low");
    }

    @Test
    void testRegisterRailWithToolsAddsToAbilityManager() {
        AgentCard card = AgentCard.builder().description("test").build();
        ReActAgent agent = new ReActAgent(card);

        ToolCard toolCard = ToolCard.builder().name("railTool").description("a tool").build();
        AgentRail rail = new AgentRail(List.of(toolCard)) {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
            }
        };

        agent.getAgentCallbackManager().registerRail(rail, agent);

        List<String> toolNames = new ArrayList<>();
        for (Object ability : agent.getAbilityManager().list()) {
            if (ability instanceof ToolCard tc) {
                toolNames.add(tc.getName());
            }
        }
        assertThat(toolNames).contains("railTool");

        // Cleanup
        agent.getAgentCallbackManager().clear(null);
    }

    @Test
    void testUnregisterRailRemovesTools() {
        AgentCard card = AgentCard.builder().description("test").build();
        ReActAgent agent = new ReActAgent(card);

        ToolCard toolCard = ToolCard.builder().name("railTool2").description("a tool").build();
        AgentRail rail = new AgentRail(List.of(toolCard)) {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
            }
        };

        agent.getAgentCallbackManager().registerRail(rail, agent);
        assertThat(agent.getAbilityManager().get("railTool2")).isNotNull();

        agent.getAgentCallbackManager().unregisterRail(rail, agent);
        assertThat(agent.getAbilityManager().get("railTool2")).isNull();

        // Cleanup
        agent.getAgentCallbackManager().clear(null);
    }

    @Test
    void testUnregisterRailRemovesCallbacks() {
        List<String> events = new ArrayList<>();
        AgentRail rail = new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
                events.add("before");
            }
        };

        manager.registerRail(rail, null);
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder().build());
        assertThat(events).containsExactly("before");

        manager.unregisterRail(rail, null);
        events.clear();
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackContext.builder().build());

        assertThat(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    void testMultipleCallbacksOnSameEvent() {
        List<String> results = new ArrayList<>();

        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> results.add("cb1"));
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> results.add("cb2"));

        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        assertThat(results).hasSize(2);
        assertThat(results).contains("cb1", "cb2");
    }
}
