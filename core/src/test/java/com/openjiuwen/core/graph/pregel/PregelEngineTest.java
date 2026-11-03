/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.WorkflowEvents;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the Pregel graph engine.
 *
 * <p>Mirrors Python's {@code Pregel} and {@code PregelLoop} in
 * {@code openjiuwen/core/graph/pregel/engine.py}.</p>
 */
class PregelEngineTest {

    @Test
    void runExecutesSuperStepsAndTriggersLoopCallbacks() throws Exception {
        RecordingDecoratorFramework framework = new RecordingDecoratorFramework();
        Pregel.setCallbackFramework(framework);
        AtomicInteger afterSteps = new AtomicInteger();
        try {
            Map<String, PregelNode> nodes = new LinkedHashMap<>();
            nodes.put(PregelConstants.START, new PregelNode(PregelConstants.START, ignored -> null,
                    List.of(source -> List.of(new TriggerMessage(source, "worker")))));
            nodes.put("worker", new PregelNode("worker", ignored -> null, List.of()));

            Pregel graph = new Pregel(
                    nodes,
                    List.of(new TriggerChannel(PregelConstants.START), new TriggerChannel("worker")),
                    PregelConstants.START,
                    null,
                    ignored -> afterSteps.incrementAndGet()
            );

            Map<String, Object> result = graph.run(new PregelConfig("session-1", "graph-1", 5));

            assertTrue(result.isEmpty());
            assertEquals(2, afterSteps.get());
            assertEquals(2, framework.events().size());
            assertEquals(WorkflowEvents.LOOP_STARTED, framework.events().get(0).get("event"));
            assertEquals(WorkflowEvents.LOOP_FINISHED, framework.events().get(1).get("event"));
            assertEquals(2, framework.events().get(1).get("total_steps"));
        } finally {
            Pregel.clearCallbackFramework();
        }
    }

    @Test
    void runReturnsTopLevelInterruptPayload() throws Exception {
        Map<String, PregelNode> nodes = Map.of(
                PregelConstants.START,
                new PregelNode(PregelConstants.START, ignored -> new GraphInterrupt("pause"), List.of())
        );
        Pregel graph = new Pregel(nodes, List.of(new TriggerChannel(PregelConstants.START)));

        Map<String, Object> result = graph.run(new PregelConfig("session-1", "graph-1", 5));

        assertEquals("pause", result.get(PregelConstants.TASK_STATUS_INTERRUPT));
    }

    @Test
    void runRethrowsSubgraphInterrupt() {
        Map<String, PregelNode> nodes = Map.of(
                PregelConstants.START,
                new PregelNode(PregelConstants.START, ignored -> new GraphInterrupt("pause"), List.of())
        );
        Pregel graph = new Pregel(nodes, List.of(new TriggerChannel(PregelConstants.START)));
        PregelConfig config = new PregelConfig("session-1", "graph-1", 5);
        config.setParentNs("parent");

        assertThrows(GraphInterrupt.class, () -> graph.run(config));
    }

    @Test
    void runSavesStateOnNodeError() {
        InMemoryStore store = new InMemoryStore();
        Map<String, PregelNode> nodes = Map.of(
                PregelConstants.START,
                new PregelNode(PregelConstants.START, ignored -> {
                    throw new IllegalStateException("boom");
                }, List.of())
        );
        Pregel graph = new Pregel(nodes, List.of(new TriggerChannel(PregelConstants.START)),
                PregelConstants.START, store, null);
        PregelConfig config = new PregelConfig("session-1", "graph-1", 5);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> graph.run(config));

        assertEquals("boom", error.getMessage());
        Optional<GraphStoreState> stored = store.get("session-1", "graph-1").toCompletableFuture().join();
        assertTrue(stored.isPresent());
        assertEquals(0, stored.get().getStep());
        assertEquals(1, stored.get().getNodeVersion().get(PregelConstants.START));
        assertTrue(stored.get().getPendingNode().containsKey(PregelConstants.START));
        assertFalse(stored.get().getPendingNode().get(PregelConstants.START).getException().isEmpty());
    }

    private static final class RecordingDecoratorFramework implements DecoratorFramework {
        private final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public CallbackInfo registerSync(String event, Function<Map<String, Object>, Object> callback, int priority,
                                         boolean once, String namespace, Set<String> tags,
                                         List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler, int maxRetries,
                                         double retryDelay, Double timeout, String callbackType) {
            return CallbackInfo.builder()
                    .callback(callback)
                    .priority(priority)
                    .once(once)
                    .namespace(namespace)
                    .tags(tags)
                    .maxRetries(maxRetries)
                    .retryDelay(retryDelay)
                    .timeout(timeout)
                    .callbackType(callbackType)
                    .build();
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("event", event);
            record.putAll(kwargs);
            events.add(record);
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            return kwargs;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of();
        }

        private List<Map<String, Object>> events() {
            return events;
        }
    }
}
