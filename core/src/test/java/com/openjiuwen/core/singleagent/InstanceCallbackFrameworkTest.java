/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstanceCallbackFrameworkTest {
    @Test
    void executesByHigherPriorityThenRegistrationOrder() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AgentCallback lowPriority = recordingCallback("low-priority-10", executionOrder);
        AgentCallback firstHighPriority = recordingCallback("first-high-priority-90", executionOrder);
        AgentCallback secondHighPriority = recordingCallback("second-high-priority-90", executionOrder);

        framework.register("event", lowPriority, 10).toCompletableFuture().join();
        framework.register("event", firstHighPriority, 90).toCompletableFuture().join();
        framework.register("event", secondHighPriority, 90).toCompletableFuture().join();

        framework.trigger("event", new AgentCallbackContext()).toCompletableFuture().join();

        assertEquals(List.of("first-high-priority-90", "second-high-priority-90", "low-priority-10"),
                executionOrder);
        assertEquals(List.of(firstHighPriority, secondHighPriority, lowPriority),
                framework.listCallbacks("event").toCompletableFuture().join());
    }

    @Test
    void unregisterUsesCallbackIdentityAndClearIsEventScoped() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AgentCallback first = recordingCallback("first", executionOrder);
        AgentCallback registered = new EqualLabelCallback("same-label", executionOrder);
        AgentCallback equalButDifferentIdentity = new EqualLabelCallback("same-label", executionOrder);
        AgentCallback other = recordingCallback("other", executionOrder);

        framework.register("event", first, 0).toCompletableFuture().join();
        framework.register("event", registered, 0).toCompletableFuture().join();
        framework.register("other-event", other, 0).toCompletableFuture().join();

        framework.unregister("event", equalButDifferentIdentity).toCompletableFuture().join();
        assertEquals(2, framework.listCallbacks("event").toCompletableFuture().join().size());

        framework.trigger("event", new AgentCallbackContext()).toCompletableFuture().join();
        assertEquals(List.of("first", "same-label"), executionOrder);

        framework.unregister("event", registered).toCompletableFuture().join();
        List<Object> remainingCallbacks = framework.listCallbacks("event").toCompletableFuture().join();
        assertEquals(1, remainingCallbacks.size());
        assertSame(first, remainingCallbacks.get(0));

        framework.unregisterEvent("event").toCompletableFuture().join();

        assertEquals(List.of(), framework.listCallbacks("event").toCompletableFuture().join());
        assertEquals(List.of(other), framework.listCallbacks("other-event").toCompletableFuture().join());
    }

    @Test
    void usesSnapshotWhenCallbackUnregistersItself() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AgentCallback[] self = new AgentCallback[1];
        self[0] = context -> {
            executionOrder.add("self");
            framework.unregister("event", self[0]).toCompletableFuture().join();
            return CompletableFuture.completedFuture(null);
        };
        AgentCallback other = recordingCallback("other", executionOrder);

        framework.register("event", self[0], 0).toCompletableFuture().join();
        framework.register("event", other, 0).toCompletableFuture().join();

        framework.trigger("event", new AgentCallbackContext()).toCompletableFuture().join();
        framework.trigger("event", new AgentCallbackContext()).toCompletableFuture().join();

        assertEquals(List.of("self", "other", "other"), executionOrder);
    }

    @Test
    void ignoresOrdinaryAsyncFailureAndContinues() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AgentCallback failing = context -> {
            executionOrder.add("failing");
            return CompletableFuture.failedFuture(new IllegalStateException("ordinary"));
        };
        AgentCallback following = recordingCallback("following", executionOrder);

        framework.register("event", failing, 0).toCompletableFuture().join();
        framework.register("event", following, 0).toCompletableFuture().join();

        framework.trigger("event", new AgentCallbackContext()).toCompletableFuture().join();

        assertEquals(List.of("failing", "following"), executionOrder);
    }

    @Test
    void propagatesAbortErrorAndStopsFollowingCallbacks() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AbortError expected = new AbortError("abort");
        AgentCallback aborting = context -> {
            executionOrder.add("aborting");
            return CompletableFuture.failedFuture(expected);
        };
        AgentCallback following = recordingCallback("following", executionOrder);

        framework.register("event", aborting, 0).toCompletableFuture().join();
        framework.register("event", following, 0).toCompletableFuture().join();

        Throwable cause = awaitFailure(framework.trigger("event", new AgentCallbackContext()));

        assertSame(expected, cause);
        assertEquals(List.of("aborting"), executionOrder);
    }

    @Test
    void propagatesSynchronousAbortErrorAndStopsFollowingCallbacks() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AbortError expected = new AbortError("synchronous abort");
        AgentCallback aborting = context -> {
            executionOrder.add("aborting");
            throw expected;
        };
        AgentCallback following = recordingCallback("following", executionOrder);

        framework.register("event", aborting, 0).toCompletableFuture().join();
        framework.register("event", following, 0).toCompletableFuture().join();

        Throwable cause = awaitFailure(framework.trigger("event", new AgentCallbackContext()));

        assertSame(expected, cause);
        assertEquals(List.of("aborting"), executionOrder);
    }

    @Test
    void propagatesErrorAndStopsFollowingCallbacks() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AssertionError expected = new AssertionError("fatal");
        AgentCallback failing = context -> {
            executionOrder.add("failing");
            return CompletableFuture.failedFuture(expected);
        };
        AgentCallback following = recordingCallback("following", executionOrder);

        framework.register("event", failing, 0).toCompletableFuture().join();
        framework.register("event", following, 0).toCompletableFuture().join();

        Throwable cause = awaitFailure(framework.trigger("event", new AgentCallbackContext()));

        assertSame(expected, cause);
        assertEquals(List.of("failing"), executionOrder);
    }

    @Test
    void propagatesSynchronousErrorAndStopsFollowingCallbacks() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AssertionError expected = new AssertionError("synchronous fatal");
        AgentCallback failing = context -> {
            executionOrder.add("failing");
            throw expected;
        };
        AgentCallback following = recordingCallback("following", executionOrder);

        framework.register("event", failing, 0).toCompletableFuture().join();
        framework.register("event", following, 0).toCompletableFuture().join();

        Throwable cause = awaitFailure(framework.trigger("event", new AgentCallbackContext()));

        assertSame(expected, cause);
        assertEquals(List.of("failing"), executionOrder);
    }

    @Test
    void ignoresOrdinarySynchronousFailureAndContinues() {
        InstanceCallbackFramework framework = new InstanceCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        AgentCallback failing = context -> {
            executionOrder.add("failing");
            throw new RuntimeException("ordinary");
        };
        AgentCallback following = recordingCallback("following", executionOrder);

        framework.register("event", failing, 0).toCompletableFuture().join();
        framework.register("event", following, 0).toCompletableFuture().join();

        framework.trigger("event", new AgentCallbackContext()).toCompletableFuture().join();

        assertEquals(List.of("failing", "following"), executionOrder);
    }

    private static AgentCallback recordingCallback(String label, List<String> executionOrder) {
        return context -> {
            executionOrder.add(label);
            return CompletableFuture.completedFuture(null);
        };
    }

    private static Throwable awaitFailure(CompletionStage<Void> stage) {
        CompletionException exception = assertThrows(CompletionException.class,
                () -> stage.toCompletableFuture().join());
        return exception.getCause();
    }

    private static final class EqualLabelCallback implements AgentCallback {
        private final String label;
        private final List<String> executionOrder;

        private EqualLabelCallback(String label, List<String> executionOrder) {
            this.label = label;
            this.executionOrder = executionOrder;
        }

        @Override
        public CompletableFuture<Void> handle(AgentCallbackContext context) {
            executionOrder.add(label);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EqualLabelCallback that)) {
                return false;
            }
            return Objects.equals(label, that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label);
        }
    }
}
