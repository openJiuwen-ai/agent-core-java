/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.runner.callback.HookType;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

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
    void registerCallbackExecutesByHigherPriorityFirstAndReturnsSelf() {
        InMemoryCallbackFramework framework = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-7", framework);
        List<String> calls = new ArrayList<>();
        AgentCallback lower = context -> {
            calls.add("lower");
            return CompletableFuture.completedFuture(null);
        };
        AgentCallback higher = context -> {
            calls.add("higher");
            return CompletableFuture.completedFuture(null);
        };

        AgentCallbackManager returned = manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, lower, 10)
                .toCompletableFuture().join()
                .registerCallback(AgentCallbackEvent.BEFORE_INVOKE, higher, 100)
                .toCompletableFuture().join();
        AgentCallbackContext context = new AgentCallbackContext();
        AgentCallbackContext executed = manager.execute(AgentCallbackEvent.BEFORE_INVOKE, context)
                .toCompletableFuture().join();

        assertSame(manager, returned);
        assertSame(context, executed);
        assertEquals(List.of("higher", "lower"), calls);
        assertTrue(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertEquals(List.of("agent-7_AgentCallbackEvent.BEFORE_INVOKE"), framework.events());
    }

    @Test
    void instanceRailRegistersOnlyInInstanceFramework() {
        InMemoryCallbackFramework global = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("same-id", global);

        manager.registerInstanceRail(new AgentRail() {
        }, new Object()).toCompletableFuture().join();

        assertFalse(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertTrue(manager.hasInstanceHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertTrue(global.events().isEmpty());
    }

    @Test
    void executeRunsGlobalBeforeInstanceAndSharesContext() {
        InMemoryCallbackFramework global = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-order", global);
        List<String> calls = new ArrayList<>();
        AgentCallbackContext context = new AgentCallbackContext();
        AgentCallback globalCallback = callbackContext -> {
            calls.add("global");
            callbackContext.getExtra().put("global-value", "visible");
            return CompletableFuture.completedFuture(null);
        };
        AgentRail instanceRail = new AgentRail() {
            {
                setPriority(90);
            }

            @Override
            public CompletionStage<Void> beforeInvoke(AgentCallbackContext callbackContext) {
                calls.add("instance:" + callbackContext.getExtra().get("global-value"));
                return CompletableFuture.completedFuture(null);
            }
        };

        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, globalCallback, 1).toCompletableFuture().join();
        manager.registerInstanceRail(instanceRail, new Object()).toCompletableFuture().join();
        AgentCallbackContext returned = manager.execute(AgentCallbackEvent.BEFORE_INVOKE, context)
                .toCompletableFuture().join();

        assertSame(context, returned);
        assertEquals(List.of("global", "instance:visible"), calls);
    }

    @Test
    void instanceRailsAreIsolatedWhenAgentIdsMatch() {
        InMemoryCallbackFramework global = new InMemoryCallbackFramework();
        AgentCallbackManager first = new AgentCallbackManager("same-id", global);
        AgentCallbackManager second = new AgentCallbackManager("same-id", global);
        List<String> calls = new ArrayList<>();

        first.registerInstanceRail(markingRail("first", calls), new Object()).toCompletableFuture().join();
        second.registerInstanceRail(markingRail("second", calls), new Object()).toCompletableFuture().join();
        first.execute(AgentCallbackEvent.BEFORE_INVOKE, new AgentCallbackContext()).toCompletableFuture().join();

        assertEquals(List.of("first"), calls);
    }

    @Test
    void unregisterAndClearInstanceDoNotAffectGlobalCallbacks() {
        InMemoryCallbackFramework global = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-clear-instance", global);
        List<String> calls = new ArrayList<>();
        AgentRail instance = markingRail("instance", calls);
        AgentCallback globalCallback = context -> {
            calls.add("global");
            return CompletableFuture.completedFuture(null);
        };

        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, globalCallback, 100).toCompletableFuture().join();
        manager.registerInstanceRail(instance, new Object()).toCompletableFuture().join();
        manager.clearInstance(AgentCallbackEvent.BEFORE_INVOKE).toCompletableFuture().join();
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, new AgentCallbackContext()).toCompletableFuture().join();

        assertEquals(List.of("global"), calls);
        assertTrue(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertFalse(manager.hasInstanceHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertDoesNotThrow(() -> manager.unregisterInstanceRail(instance, new Object()).toCompletableFuture().join());
    }

    @Test
    void clearInstanceNullRemovesAllInstanceEventsOnly() {
        InMemoryCallbackFramework global = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-clear-all-instance", global);
        List<String> calls = new ArrayList<>();
        AgentRail instance = new AgentRail() {
            @Override
            public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
                calls.add("instance-before");
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> afterInvoke(AgentCallbackContext context) {
                calls.add("instance-after");
                return CompletableFuture.completedFuture(null);
            }
        };
        AgentCallback globalCallback = context -> {
            calls.add("global-before");
            return CompletableFuture.completedFuture(null);
        };

        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, globalCallback, 100).toCompletableFuture().join();
        manager.registerInstanceRail(instance, new Object()).toCompletableFuture().join();
        manager.clearInstance(null).toCompletableFuture().join();
        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, new AgentCallbackContext()).toCompletableFuture().join();
        manager.execute(AgentCallbackEvent.AFTER_INVOKE, new AgentCallbackContext()).toCompletableFuture().join();

        assertEquals(List.of("global-before"), calls);
        assertTrue(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertFalse(manager.hasInstanceHooks(AgentCallbackEvent.BEFORE_INVOKE));
        assertFalse(manager.hasInstanceHooks(AgentCallbackEvent.AFTER_INVOKE));
    }

    @Test
    void globalAbortStopsBeforeInstancePhase() {
        InMemoryCallbackFramework global = new InMemoryCallbackFramework();
        AgentCallbackManager manager = new AgentCallbackManager("agent-global-abort", global);
        AbortError expected = new AbortError("stop before instance callbacks");
        List<String> calls = new ArrayList<>();

        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, context -> {
            throw expected;
        }, 100).toCompletableFuture().join();
        manager.registerInstanceRail(markingRail("instance", calls), new Object()).toCompletableFuture().join();

        AbortError error = assertThrows(AbortError.class,
                () -> manager.execute(AgentCallbackEvent.BEFORE_INVOKE, new AgentCallbackContext())
                        .toCompletableFuture().join());

        assertSame(expected, error);
        assertTrue(calls.isEmpty());
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
    void unregisterNeverRegisteredInstanceRailDoesNotRecreateCallbacks() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-never-registered-instance-rail",
                new InMemoryCallbackFramework());
        int[] getCallbacksCalls = {0};
        AgentRail rail = new AgentRail() {
            @Override
            public Map<AgentCallbackEvent, AgentCallback> getCallbacks() {
                getCallbacksCalls[0]++;
                return super.getCallbacks();
            }
        };

        manager.unregisterInstanceRail(rail, new Object()).toCompletableFuture().join();

        assertEquals(0, getCallbacksCalls[0]);
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
    void nullGlobalCallbackReturnTriggersRunnerErrorHook() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-null-global-callback");
        AgentCallbackEvent event = AgentCallbackEvent.BEFORE_MODEL_CALL;
        int[] errorHookCalls = {0};
        try {
            Runner.getCallbackFramework().addHook(manager.getAgentEvent(event), HookType.ERROR,
                    kwargs -> errorHookCalls[0]++);
            manager.registerCallback(event, context -> null, 100).toCompletableFuture().join();

            manager.execute(event, new AgentCallbackContext()).toCompletableFuture().join();

            assertEquals(1, errorHookCalls[0]);
        } finally {
            manager.clear(event).toCompletableFuture().join();
        }
    }

    @Test
    void executePropagatesAsyncAbortErrorBeforeInstanceCallbacksRun() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-async-abort");
        AbortError expected = new AbortError("async model request headers unavailable");
        List<String> calls = new ArrayList<>();
        AgentRail instanceRail = new AgentRail() {
            @Override
            public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
                calls.add("instance");
                return CompletableFuture.completedFuture(null);
            }
        };
        try {
            manager.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL,
                    context -> CompletableFuture.failedFuture(expected),
                    100).toCompletableFuture().join();
            manager.registerInstanceRail(instanceRail, new Object()).toCompletableFuture().join();

            AbortError error = assertThrows(AbortError.class,
                    () -> manager.execute(AgentCallbackEvent.BEFORE_MODEL_CALL, new AgentCallbackContext())
                            .toCompletableFuture().join());

            assertSame(expected, error);
            assertTrue(calls.isEmpty());
        } finally {
            manager.clear(AgentCallbackEvent.BEFORE_MODEL_CALL).toCompletableFuture().join();
            manager.clearInstance(AgentCallbackEvent.BEFORE_MODEL_CALL).toCompletableFuture().join();
        }
    }

    @Test
    void executeIgnoresAsyncOrdinaryGlobalFailureThenRunsInstanceCallbacks() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-async-ordinary-error");
        List<String> calls = new ArrayList<>();
        AgentRail instanceRail = new AgentRail() {
            @Override
            public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
                calls.add("instance");
                return CompletableFuture.completedFuture(null);
            }
        };
        try {
            manager.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL,
                    context -> CompletableFuture.failedFuture(new IllegalStateException("ordinary async failure")),
                    100).toCompletableFuture().join();
            manager.registerInstanceRail(instanceRail, new Object()).toCompletableFuture().join();

            assertDoesNotThrow(() -> manager.execute(
                    AgentCallbackEvent.BEFORE_MODEL_CALL,
                    new AgentCallbackContext()
            ).toCompletableFuture().join());

            assertEquals(List.of("instance"), calls);
        } finally {
            manager.clear(AgentCallbackEvent.BEFORE_MODEL_CALL).toCompletableFuture().join();
            manager.clearInstance(AgentCallbackEvent.BEFORE_MODEL_CALL).toCompletableFuture().join();
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
    void globalErrorFromRunnerStopsBeforeInstanceCallbacksRun() {
        AgentCallbackManager manager = new AgentCallbackManager("agent-global-error-before-instance");
        AssertionError expected = new AssertionError("fatal global callback failure");
        List<String> calls = new ArrayList<>();
        AgentRail instanceRail = new AgentRail() {
            @Override
            public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
                calls.add("instance");
                return CompletableFuture.completedFuture(null);
            }
        };
        try {
            manager.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, context -> {
                throw expected;
            }, 100).toCompletableFuture().join();
            manager.registerInstanceRail(instanceRail, new Object()).toCompletableFuture().join();

            AssertionError error = assertThrows(AssertionError.class,
                    () -> manager.execute(AgentCallbackEvent.BEFORE_MODEL_CALL, new AgentCallbackContext())
                            .toCompletableFuture().join());

            assertSame(expected, error);
            assertTrue(calls.isEmpty());
        } finally {
            manager.clear(AgentCallbackEvent.BEFORE_MODEL_CALL).toCompletableFuture().join();
            manager.unregisterInstanceRail(instanceRail, new Object()).toCompletableFuture().join();
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
            callbacks.get(event).sort(Comparator.comparingInt(Entry::priority).reversed());
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
            for (Entry entry : List.copyOf(callbacks.getOrDefault(event, List.of()))) {
                try {
                    CompletionStage<Void> result = entry.callback().handle(context);
                    if (result != null) {
                        result.toCompletableFuture().join();
                    }
                } catch (Throwable error) {
                    Throwable normalized = unwrap(error);
                    if (normalized instanceof AbortError abortError) {
                        throw abortError;
                    }
                    if (normalized instanceof Error fatal) {
                        throw fatal;
                    }
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        private static Throwable unwrap(Throwable error) {
            Throwable current = error;
            while ((current instanceof CompletionException || current instanceof ExecutionException)
                    && current.getCause() != null) {
                current = current.getCause();
            }
            return current;
        }

        private List<String> events() {
            return new ArrayList<>(callbacks.keySet());
        }

        private record Entry(AgentCallback callback, int priority) {
        }
    }

    private static AgentRail markingRail(String marker, List<String> calls) {
        return new AgentRail() {
            @Override
            public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
                calls.add(marker);
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
