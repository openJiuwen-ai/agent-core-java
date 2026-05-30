// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.tool.ToolCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgentRail}.
 */
class AgentRailTest {

    // ============ Test Rail Subclasses ============

    static class AllHooksRail extends AgentRail {
        final List<String> events = new ArrayList<>();

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            events.add("beforeInvoke");
        }

        @Override
        public void afterInvoke(AgentCallbackContext ctx) {
            events.add("afterInvoke");
        }

        @Override
        public void beforeTaskIteration(AgentCallbackContext ctx) {
            events.add("beforeTaskIteration");
        }

        @Override
        public void afterTaskIteration(AgentCallbackContext ctx) {
            events.add("afterTaskIteration");
        }

        @Override
        public void beforeModelCall(AgentCallbackContext ctx) {
            events.add("beforeModelCall");
        }

        @Override
        public void afterModelCall(AgentCallbackContext ctx) {
            events.add("afterModelCall");
        }

        @Override
        public void onModelException(AgentCallbackContext ctx) {
            events.add("onModelException");
        }

        @Override
        public void beforeToolCall(AgentCallbackContext ctx) {
            events.add("beforeToolCall");
        }

        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            events.add("afterToolCall");
        }

        @Override
        public void onToolException(AgentCallbackContext ctx) {
            events.add("onToolException");
        }
    }

    static class PartialHooksRail extends AgentRail {
        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {}

        @Override
        public void afterModelCall(AgentCallbackContext ctx) {}
    }

    static class EmptyRail extends AgentRail {
        // No hooks overridden
    }

    static class ToolCarryingRail extends AgentRail {
        public ToolCarryingRail(List<ToolCard> tools) {
            super(tools);
        }

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {}
    }

    private static class PrivateHookRail extends AgentRail {
        final List<String> events = new ArrayList<>();

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            events.add("beforeInvoke");
        }
    }

    // ============ Tests ============

    @Test
    void testDefaultPriority() {
        AllHooksRail rail = new AllHooksRail();
        assertThat(rail.getPriority()).isEqualTo(50);
    }

    @Test
    void testSetPriority() {
        AllHooksRail rail = new AllHooksRail();
        rail.setPriority(90);
        assertThat(rail.getPriority()).isEqualTo(90);
    }

    @Test
    void testDefaultToolsEmpty() {
        AllHooksRail rail = new AllHooksRail();
        assertThat(rail.getTools()).isEmpty();
    }

    @Test
    void testToolsProvidedInConstructor() {
        ToolCard tc = ToolCard.builder().name("testTool").description("a tool").build();
        ToolCarryingRail rail = new ToolCarryingRail(List.of(tc));
        assertThat(rail.getTools()).hasSize(1);
        assertThat(rail.getTools().get(0).getName()).isEqualTo("testTool");
    }

    @Test
    void testGetCallbacksAllHooksOverridden() {
        AllHooksRail rail = new AllHooksRail();
        Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks = rail.getCallbacks();
        assertThat(callbacks).hasSize(10);
        assertThat(callbacks).containsKey(AgentCallbackEvent.BEFORE_INVOKE);
        assertThat(callbacks).containsKey(AgentCallbackEvent.AFTER_INVOKE);
        assertThat(callbacks).containsKey(AgentCallbackEvent.BEFORE_TASK_ITERATION);
        assertThat(callbacks).containsKey(AgentCallbackEvent.AFTER_TASK_ITERATION);
        assertThat(callbacks).containsKey(AgentCallbackEvent.BEFORE_MODEL_CALL);
        assertThat(callbacks).containsKey(AgentCallbackEvent.AFTER_MODEL_CALL);
        assertThat(callbacks).containsKey(AgentCallbackEvent.ON_MODEL_EXCEPTION);
        assertThat(callbacks).containsKey(AgentCallbackEvent.BEFORE_TOOL_CALL);
        assertThat(callbacks).containsKey(AgentCallbackEvent.AFTER_TOOL_CALL);
        assertThat(callbacks).containsKey(AgentCallbackEvent.ON_TOOL_EXCEPTION);
    }

    @Test
    void testGetCallbacksPartialHooksOverridden() {
        PartialHooksRail rail = new PartialHooksRail();
        Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks = rail.getCallbacks();
        assertThat(callbacks).hasSize(2);
        assertThat(callbacks).containsKey(AgentCallbackEvent.BEFORE_INVOKE);
        assertThat(callbacks).containsKey(AgentCallbackEvent.AFTER_MODEL_CALL);
    }

    @Test
    void testGetCallbacksEmptyRail() {
        EmptyRail rail = new EmptyRail();
        Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks = rail.getCallbacks();
        assertThat(callbacks).isEmpty();
    }

    @Test
    void testCallbackInvocationForwardsToHookMethod() {
        AllHooksRail rail = new AllHooksRail();
        Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks = rail.getCallbacks();

        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        callbacks.get(AgentCallbackEvent.BEFORE_INVOKE).accept(ctx);
        callbacks.get(AgentCallbackEvent.AFTER_MODEL_CALL).accept(ctx);

        assertThat(rail.events).containsExactly("beforeInvoke", "afterModelCall");
    }

    @Test
    void testCallbackInvocationForPrivateRailSubclass() {
        PrivateHookRail rail = new PrivateHookRail();
        Map<AgentCallbackEvent, Consumer<AgentCallbackContext>> callbacks = rail.getCallbacks();

        callbacks.get(AgentCallbackEvent.BEFORE_INVOKE).accept(AgentCallbackContext.builder().build());

        assertThat(rail.events).containsExactly("beforeInvoke");
    }

    @Test
    void testHookMethodsDoNothingByDefault() {
        EmptyRail rail = new EmptyRail();
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        // These should not throw
        rail.beforeInvoke(ctx);
        rail.afterInvoke(ctx);
        rail.beforeModelCall(ctx);
        rail.afterModelCall(ctx);
        rail.onModelException(ctx);
        rail.beforeToolCall(ctx);
        rail.afterToolCall(ctx);
        rail.onToolException(ctx);
    }
}
