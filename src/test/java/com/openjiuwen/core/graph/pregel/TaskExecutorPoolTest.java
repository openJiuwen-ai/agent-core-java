/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.WorkflowEvents;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for pregel task execution.
 *
 * <p>Mirrors Python's {@code TaskExecutorPool} and {@code NodeTask} in
 * {@code openjiuwen/core/graph/pregel/task.py}.</p>
 */
class TaskExecutorPoolTest {

    @Test
    void nodeTaskPassesInnerConfigAndRoutesMessages() throws Exception {
        PregelConfig config = new PregelConfig("session-1", "root", 5);
        config.setParentNs("root");
        PregelNode node = new PregelNode("node-a", invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) invocation;
            PregelConfig inner = (PregelConfig) values.get("config");
            assertEquals("root:node-a:3", inner.getNs());
            assertEquals("root:node-a:3", inner.getParentNs());
            assertEquals(3, values.get("version"));
            return "ignored";
        }, List.of(sourceNode -> List.of(new TriggerMessage(sourceNode, "node-b"))));

        Object result = new NodeTask(node, config, 3).run();

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) result;
        assertEquals(1, messages.size());
        assertEquals("node-a", messages.get(0).getSender());
        assertEquals("node-b", messages.get(0).getTarget());
    }

    @Test
    void nodeTaskTriggersEdgeTraversedCallbacks() throws Exception {
        RecordingDecoratorFramework framework = new RecordingDecoratorFramework();
        NodeTask.setCallbackFramework(framework);
        try {
            PregelConfig config = new PregelConfig("session-1", "graph-1", 5);
            PregelNode node = new PregelNode("node-a", ignored -> "ignored",
                    List.of(sourceNode -> List.of(new TriggerMessage(sourceNode, "node-b"))));

            new NodeTask(node, config, 4).run();

            assertEquals(1, framework.events().size());
            Map<String, Object> event = framework.events().get(0);
            assertEquals(WorkflowEvents.EDGE_TRAVERSED, event.get("event"));
            assertEquals("node-a", event.get("source_node"));
            assertEquals("node-b", event.get("target_node"));
            assertEquals("graph-1", event.get("graph_id"));
        } finally {
            NodeTask.clearCallbackFramework();
        }
    }

    @Test
    void waitAllCollectsSuccessMessages() throws Exception {
        TaskExecutorPool pool = new TaskExecutorPool(new PregelConfig("session-1", "graph", 5));
        PregelNode node = new PregelNode("node-a", ignored -> null,
                List.of(sourceNode -> List.of(new TriggerMessage(sourceNode, "node-b"))));

        pool.submit(node, 1);
        pool.waitAll();

        assertEquals(1, pool.getSucceedMessages().size());
        assertEquals("node-b", pool.getSucceedMessages().get(0).getTarget());
        assertTrue(pool.getFailed().isEmpty());
    }

    @Test
    void waitAllRecordsAndRaisesFirstError() {
        TaskExecutorPool pool = new TaskExecutorPool(new PregelConfig("session-1", "graph", 5));
        PregelNode node = new PregelNode("bad-node", ignored -> {
            throw new IllegalStateException("boom");
        }, List.of());

        pool.submit(node, 1);
        IllegalStateException error = assertThrows(IllegalStateException.class, pool::waitAll);

        assertEquals("boom", error.getMessage());
        assertEquals(PregelConstants.TASK_STATUS_ERROR, pool.getFailed().get("bad-node").getStatus());
    }

    @Test
    void waitAllRecordsInterruptAndRaisesIt() {
        TaskExecutorPool pool = new TaskExecutorPool(new PregelConfig("session-1", "graph", 5));
        PregelNode node = new PregelNode("interrupt-node", ignored -> new GraphInterrupt("pause"), List.of());

        pool.submit(node, 1);
        GraphInterrupt interrupt = assertThrows(GraphInterrupt.class, pool::waitAll);

        assertEquals("pause", interrupt.getValue());
        assertEquals(PregelConstants.TASK_STATUS_INTERRUPT, pool.getFailed().get("interrupt-node").getStatus());
    }

    @Test
    void cancelAllCancelsRunningTasks() throws Exception {
        TaskExecutorPool pool = new TaskExecutorPool(new PregelConfig("session-1", "graph", 5));
        PregelNode node = new PregelNode("slow-node", ignored -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return "cancelled";
        }, List.of());

        pool.submit(node, 1);
        pool.cancelAll();

        assertInstanceOf(CancellationException.class,
                pool.getFailed().get("slow-node").getException().get(0));
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
