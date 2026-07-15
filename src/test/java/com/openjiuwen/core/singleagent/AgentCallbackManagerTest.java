/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for agent callback registration and event-name scoping.
 *
 * <p>Mirrors Python's {@code AgentCallbackManager} in
 * {@code openjiuwen/core/single_agent/agent_callback_manager.py}.</p>
 */
class AgentCallbackManagerTest {

    @Test
    void eventNameUsesAgentIdPrefixAndEnumValue() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-7", new InMemoryCallbackFramework());

        assertEquals("agent-7_AgentCallbackEvent.BEFORE_TOOL_CALL",
                manager.getAgentEvent(AgentCallbackEvent.BEFORE_TOOL_CALL));
        assertEquals("agent-7_AgentCallbackEvent.BEFORE_TOOL_CALL",
                manager._get_agent_event(AgentCallbackEvent.BEFORE_TOOL_CALL));
    }

    @Test
    void registerCallbackExecutesByLowerPriorityFirstAndReturnsSelf() {
        InMemoryCallbackFramework framework = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-7", framework);
        List<String> calls = new ArrayList<>();
        AgentCallback later = context -> {
            calls.add("later");
            return CompletableFuture.completedFuture(null);
        };
        AgentCallback earlier = context -> {
            calls.add("earlier");
            return CompletableFuture.completedFuture(null);
        };

        AgentCallbackManager returned = manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, later, 100)
                .toCompletableFuture().join()
                .registerCallback(AgentCallbackEvent.BEFORE_INVOKE, earlier, 10)
                .toCompletableFuture().join();
        AgentCallbackContext context = new AgentCallbackContext();
        AgentCallbackContext executed = manager.execute(AgentCallbackEvent.BEFORE_INVOKE, context)
                .toCompletableFuture().join();

        assertSame(manager, returned);
        assertSame(context, executed);
        assertEquals(List.of("earlier", "later"), calls);
        assertTrue(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertEquals(List.of("agent-7_AgentCallbackEvent.BEFORE_INVOKE"), framework.events());
    }

    @Test
    void unregisterAndClearUseScopedAgentEventNames() {
        InMemoryCallbackFramework framework = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-9", framework);
        AgentCallback callback = context -> CompletableFuture.completedFuture(null);
        manager.registerCallback(AgentCallbackEvent.AFTER_INVOKE, callback, 100).toCompletableFuture().join();

        manager.unregister(AgentCallbackEvent.AFTER_INVOKE, callback).toCompletableFuture().join();

        assertFalse(manager.hasHooks(AgentCallbackEvent.AFTER_INVOKE));
        manager.registerCallback(AgentCallbackEvent.AFTER_INVOKE, callback, 100).toCompletableFuture().join();
        manager.clear(AgentCallbackEvent.AFTER_INVOKE).toCompletableFuture().join();
        assertFalse(manager.hasHooks(AgentCallbackEvent.AFTER_INVOKE));
    }

    @Test
    void clearWithoutEventClearsEveryAgentScopedEvent() {
        InMemoryCallbackFramework framework = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-11", framework);
        AgentCallback callback = context -> CompletableFuture.completedFuture(null);
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, callback, 100).toCompletableFuture().join();
        manager.registerCallback(AgentCallbackEvent.AFTER_INVOKE, callback, 100).toCompletableFuture().join();

        manager.clear(null).toCompletableFuture().join();

        assertFalse(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertFalse(manager.hasHooks(AgentCallbackEvent.AFTER_INVOKE));
    }

    @Test
    void registerAndUnregisterRailDelegateThroughCallbacks() {
        InMemoryCallbackFramework framework = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-rail", framework);
        List<String> calls = new ArrayList<>();
        AgentRail rail = new AgentRail() {
            {
                setPriority(5);
            }

            @Override
            public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
                calls.add("rail-before");
                return CompletableFuture.completedFuture(null);
            }
        };

        manager.registerRail(rail, new Object()).toCompletableFuture().join();
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, new AgentCallbackContext()).toCompletableFuture().join();
        manager.unregisterRail(rail, new Object()).toCompletableFuture().join();
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, new AgentCallbackContext()).toCompletableFuture().join();

        assertEquals(List.of("rail-before"), calls);
    }

    @Test
    void executePropagatesAbortErrorFromRunnerCallbackFramework() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-abort");
        AbortError expected = new AbortError("model request headers unavailable");
        try {
            manager.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, context -> {
                throw expected;
            }, 100).toCompletableFuture().join();

            AbortError error = assertThrows(AbortError.class,
                    () -> manager.execute(AgentCallbackEvent.BEFORE_MODEL_CALL, new AgentCallbackContext())
                            .toCompletableFuture().join());

            assertSame(expected, error);
            assertEquals("model request headers unavailable", error.getReason());
        } finally {
            manager.clear(AgentCallbackEvent.BEFORE_MODEL_CALL).toCompletableFuture().join();
        }
    }

    @Test
    void executePropagatesErrorFromRunnerCallbackFramework() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-error");
        AssertionError expected = new AssertionError("fatal callback failure");
        try {
            manager.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, context -> {
                throw expected;
            }, 100).toCompletableFuture().join();

            AssertionError error = assertThrows(AssertionError.class,
                    () -> manager.execute(AgentCallbackEvent.BEFORE_MODEL_CALL, new AgentCallbackContext())
                            .toCompletableFuture().join());

            assertSame(expected, error);
        } finally {
            manager.clear(AgentCallbackEvent.BEFORE_MODEL_CALL).toCompletableFuture().join();
        }
    }

    @Test
    void executeDoesNotPropagateOrdinaryCallbackException() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-ordinary-error");
        try {
            manager.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, context -> {
                throw new IllegalStateException("ordinary callback failure");
            }, 100).toCompletableFuture().join();

            assertDoesNotThrow(() -> manager.execute(
                    AgentCallbackEvent.BEFORE_MODEL_CALL,
                    new AgentCallbackContext()
            ).toCompletableFuture().join());
        } finally {
            manager.clear(AgentCallbackEvent.BEFORE_MODEL_CALL).toCompletableFuture().join();
        }
    }

    private static final class InMemoryCallbackFramework implements AgentCallbackManager.CallbackFramework {
        private final Map<String, List<Entry>> callbacks = new LinkedHashMap<>();

        @Override
        public CompletionStage<Void> register(String event, AgentCallback callback, int priority) {
            callbacks.computeIfAbsent(event, ignored -> new ArrayList<>()).add(new Entry(callback, priority));
            callbacks.get(event).sort(Comparator.comparingInt(Entry::priority));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregister(String event, AgentCallback callback) {
            callbacks.computeIfPresent(event, (ignored, entries) -> {
                entries.removeIf(entry -> entry.callback() == callback);
                return entries.isEmpty() ? null : entries;
            });
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterEvent(String event) {
            callbacks.remove(event);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<Object>> listCallbacks(String event) {
            List<Entry> entries = callbacks.getOrDefault(event, List.of());
            return CompletableFuture.completedFuture(new ArrayList<>(entries));
        }

        @Override
        public CompletionStage<Void> trigger(String event, AgentCallbackContext context) {
            for (Entry entry : callbacks.getOrDefault(event, List.of())) {
                entry.callback().handle(context).toCompletableFuture().join();
            }
            return CompletableFuture.completedFuture(null);
        }

        private List<String> events() {
            return new ArrayList<>(callbacks.keySet());
        }

        private record Entry(AgentCallback callback, int priority) {
        }
    }
}
