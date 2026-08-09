/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import com.openjiuwen.core.runner.callback.EventFilter;
import com.openjiuwen.core.runner.callback.WorkflowEvents;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
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
 *
 * <p>Mirrors Python's {@code TestTaskExecutorPool} in
 * {@code tests/unit_tests/core/graph/test_task.py}.</p>
 */
class TaskExecutorPoolTest {

    private final List<TaskExecutorPool> poolsToClose = new ArrayList<>();

    @AfterEach
    void shutdownPools() {
        for (TaskExecutorPool pool : poolsToClose) {
            pool.shutdown();
        }
        poolsToClose.clear();
    }

    private TaskExecutorPool newPool(PregelConfig config) {
        TaskExecutorPool pool = new TaskExecutorPool(config);
        poolsToClose.add(pool);
        return pool;
    }

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
        TaskExecutorPool pool = newPool(new PregelConfig("session-1", "graph", 5));
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
        TaskExecutorPool pool = newPool(new PregelConfig("session-1", "graph", 5));
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
        TaskExecutorPool pool = newPool(new PregelConfig("session-1", "graph", 5));
        PregelNode node = new PregelNode("interrupt-node", ignored -> new GraphInterrupt("pause"), List.of());

        pool.submit(node, 1);
        GraphInterrupt interrupt = assertThrows(GraphInterrupt.class, pool::waitAll);

        assertEquals("pause", interrupt.getValue());
        assertEquals(PregelConstants.TASK_STATUS_INTERRUPT, pool.getFailed().get("interrupt-node").getStatus());
    }

    @Test
    void cancelAllCancelsRunningTasks() throws Exception {
        TaskExecutorPool pool = newPool(new PregelConfig("session-1", "graph", 5));
        PregelNode node = new PregelNode("slow-node", ignored -> {
            try {
                // Park indefinitely until interrupted by cancelAll. A never-counted-down latch
                // exits immediately on interruption at near-zero cost, replacing a 5s sleep that
                // would waste wall-clock if cancellation failed to interrupt.
                new CountDownLatch(1).await();
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

    @Test
    void poolRuntimeExceptionCancelsSlowSiblingAndKeepsFastSuccess() {
        PregelConfig config = new PregelConfig("test_conv_1", "root", 5);
        config.setParentNs(config.getNs());
        TaskExecutorPool pool = newPool(config);
        pool.submit(routingNode("A", "Target_A", 1_000L, null), 1);
        pool.submit(new PregelNode("B", invocation -> {
            sleep(100L);
            assertInnerNamespace(invocation, "root:B:1");
            throw new IllegalStateException("Simulated Runtime Error in B");
        }, List.of(sourceNode -> List.of(new TriggerMessage(sourceNode, "Target_B")))), 1);
        pool.submit(routingNode("C", "Target_C", 0L, null), 1);

        IllegalStateException error = assertThrows(IllegalStateException.class, pool::waitAll);

        assertEquals("Simulated Runtime Error in B", error.getMessage());
        assertEquals(PregelConstants.TASK_STATUS_ERROR, pool.getFailed().get("B").getStatus());
        assertInstanceOf(IllegalStateException.class, pool.getFailed().get("B").getException().get(0));
        assertEquals(PregelConstants.TASK_STATUS_ERROR, pool.getFailed().get("A").getStatus());
        assertInstanceOf(CancellationException.class, pool.getFailed().get("A").getException().get(0));
        assertTrue(!pool.getFailed().containsKey("C"));
        assertEquals(1, pool.getSucceedMessages().size());
        assertEquals("C", pool.getSucceedMessages().get(0).getSender());
        assertEquals("Target_C", pool.getSucceedMessages().get(0).getTarget());
    }

    @Test
    void poolInterruptRaisesAndKeepsCompletedSiblingMessages() {
        PregelConfig config = new PregelConfig("test_conv_2", "root", 5);
        config.setParentNs(config.getNs());
        TaskExecutorPool pool = newPool(config);
        pool.submit(routingNode("A", "Target_A", 0L, null), 1);
        pool.submit(new PregelNode("B", invocation -> {
            sleep(100L);
            assertInnerNamespace(invocation, "root:B:1");
            return new GraphInterrupt(new Interrupt("B_Interrupt"));
        }, List.of(sourceNode -> List.of(new TriggerMessage(sourceNode, "Target_B")))), 1);
        pool.submit(routingNode("C", "Target_C", 0L, null), 1);

        GraphInterrupt interrupt = assertThrows(GraphInterrupt.class, pool::waitAll);

        assertInstanceOf(Interrupt.class, interrupt.getValue());
        assertEquals(PregelConstants.TASK_STATUS_INTERRUPT, pool.getFailed().get("B").getStatus());
        assertInstanceOf(GraphInterrupt.class, pool.getFailed().get("B").getException().get(0));
        assertTrue(!pool.getFailed().containsKey("A"));
        assertTrue(!pool.getFailed().containsKey("C"));
        assertEquals(Set.of("A", "C"),
                pool.getSucceedMessages().stream().map(Message::getSender).collect(java.util.stream.Collectors.toSet()));
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

    private static PregelNode routingNode(String name, String target, long sleepMillis, Object result) {
        return new PregelNode(name, invocation -> {
            sleep(sleepMillis);
            assertInnerNamespace(invocation, "root:" + name + ":1");
            return result;
        }, List.of(sourceNode -> List.of(new TriggerMessage(sourceNode, target))));
    }

    @SuppressWarnings("unchecked")
    private static void assertInnerNamespace(Object invocation, String expectedNs) {
        Map<String, Object> values = (Map<String, Object>) invocation;
        PregelConfig inner = (PregelConfig) values.get("config");
        assertEquals(expectedNs, inner.getNs());
    }

    private static void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
