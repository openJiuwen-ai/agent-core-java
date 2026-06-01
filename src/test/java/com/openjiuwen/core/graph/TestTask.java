/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.graph;

import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.graph.pregel.PregelConfig;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.pregel.PregelNode;
import com.openjiuwen.core.graph.pregel.StaticRouter;
import com.openjiuwen.core.graph.pregel.TaskExecutorPool;
import com.openjiuwen.core.graph.store.PendingNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TaskExecutorPool.
 * <p>
 * Mirrors Python's {@code test_task.py} from
 * {@code tests/unit_tests/core/graph/test_task.py}.
 */
@DisplayName("Task Tests")
class TestTask {

    @Nested
    @DisplayName("TaskExecutorPool Tests")
    class TestTaskExecutorPool {

        @Test
        @Tag("level0")
        @DisplayName("pool runtime exception")
        void testPoolRuntimeException() throws Exception {
            PregelConfig config = new PregelConfig("test_conv_1", "root", 10000);
            config.setParentNs("root");

            Callable<Object> taskASlow = () -> {
                Thread.sleep(1000);
                return null;
            };
            Callable<Object> taskBError = () -> {
                Thread.sleep(200);
                throw new IllegalArgumentException("Simulated Runtime Error in B");
            };
            Runnable taskCFast = () -> {
            };

            TaskExecutorPool pool = new TaskExecutorPool(config);
            pool.submit(new PregelNode("A", taskASlow, List.of(new StaticRouter(List.of("Target_A")))), 1);
            pool.submit(new PregelNode("B", taskBError, List.of(new StaticRouter(List.of("Target_B")))), 1);
            pool.submit(new PregelNode("C", taskCFast, List.of(new StaticRouter(List.of("Target_C")))), 1);

            Exception thrown = assertThrows(Exception.class, pool::waitAll);

            assertTrue(thrown.getMessage().contains("Simulated Runtime Error in B"));
            assertFailed(pool.getFailed().get("B"), "B", PregelConstants.TASK_STATUS_ERROR,
                    IllegalArgumentException.class);
            assertFailed(pool.getFailed().get("A"), "A", PregelConstants.TASK_STATUS_ERROR,
                    java.util.concurrent.CancellationException.class);
            assertFalse(pool.getFailed().containsKey("C"));
            assertEquals(Set.of("C"), Set.copyOf(pool.getSucceedMessages().stream()
                    .map(message -> message.getSender()).toList()));
            assertEquals("Target_C", pool.getSucceedMessages().get(0).getTarget());
        }

        @Test
        @Tag("level0")
        @DisplayName("pool interrupt exception")
        void testPoolInterruptException() throws Exception {
            PregelConfig config = new PregelConfig("test_conv_2", "root", 10000);
            config.setParentNs("root");

            Callable<Object> taskASlow = () -> {
                Thread.sleep(1000);
                return null;
            };
            Callable<Object> taskBInterrupt = () -> {
                throw new GraphInterrupt(new Interrupt("B_Interrupt"));
            };
            Runnable taskCFast = () -> {
            };

            TaskExecutorPool pool = new TaskExecutorPool(config);
            pool.submit(new PregelNode("A", taskASlow, List.of(new StaticRouter(List.of("Target_A")))), 1);
            pool.submit(new PregelNode("B", taskBInterrupt, List.of(new StaticRouter(List.of("Target_B")))), 1);
            pool.submit(new PregelNode("C", taskCFast, List.of(new StaticRouter(List.of("Target_C")))), 1);

            assertThrows(GraphInterrupt.class, pool::waitAll);

            assertFailed(pool.getFailed().get("B"), "B", PregelConstants.TASK_STATUS_INTERRUPT,
                    GraphInterrupt.class);
            assertFalse(pool.getFailed().containsKey("A"));
            assertFalse(pool.getFailed().containsKey("C"));
            assertEquals(Set.of("A", "C"), Set.copyOf(pool.getSucceedMessages().stream()
                    .map(message -> message.getSender()).toList()));
        }

        private void assertFailed(PendingNode pendingNode, String nodeName, String status,
                                  Class<? extends Exception> exceptionClass) {
            assertEquals(nodeName, pendingNode.getNodeName());
            assertEquals(status, pendingNode.getStatus());
            assertInstanceOf(exceptionClass, pendingNode.getExceptions().get(0));
        }
    }
}
