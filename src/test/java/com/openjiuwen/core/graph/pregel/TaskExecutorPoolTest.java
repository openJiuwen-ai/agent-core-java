/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.graph.pregel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TaskExecutorPool} — concurrent task execution with FIRST_EXCEPTION semantics.
 * <p>
 * Ported from Python's {@code test_task.py :: TestTaskExecutorPool}.
 */
class TaskExecutorPoolTest {

    // ---------- Runtime exception test ----------

    @Nested
    @DisplayName("TaskExecutorPool exception handling")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Runtime exception: B fails, A cancelled, C succeeds")
        void testPoolRuntimeException() throws Exception {
            PregelConfig config = new PregelConfig("test_conv_1", "root", 10000);
            config.setParentNs("root");

            // Node A: slow task (1 second)
            Callable<Object> taskASlow = () -> {
                Thread.sleep(1000);
                return null;
            };

            // Node B: fast failure (0.2s then throws)
            Callable<Object> taskBError = () -> {
                Thread.sleep(200);
                throw new RuntimeException("Simulated Runtime Error in B");
            };

            // Node C: fast task
            Runnable taskCFast = () -> {
                // instant
            };

            StaticRouter routerA = new StaticRouter(List.of("Target_A"));
            StaticRouter routerB = new StaticRouter(List.of("Target_B"));
            StaticRouter routerC = new StaticRouter(List.of("Target_C"));

            PregelNode nodeA = new PregelNode("A", taskASlow, List.of(routerA));
            PregelNode nodeB = new PregelNode("B", taskBError, List.of(routerB));
            PregelNode nodeC = new PregelNode("C", taskCFast, List.of(routerC));

            TaskExecutorPool pool = new TaskExecutorPool(config);
            pool.submit(nodeA, 1);
            pool.submit(nodeB, 1);
            pool.submit(nodeC, 1);

            // Verify B's runtime exception is propagated
            Exception thrown = assertThrows(Exception.class, pool::waitAll);
            assertTrue(thrown.getMessage().contains("Simulated Runtime Error in B"),
                    "Expected error message from B, got: " + thrown.getMessage());

            // B fails, recorded as __error__
            assertTrue(pool.getFailed().containsKey("B"));
            assertEquals("__error__", pool.getFailed().get("B").getStatus());

            // C succeeds
            assertFalse(pool.getFailed().containsKey("C"));
            // C's messages should be collected
            boolean cMessageFound = pool.getSucceedMessages().stream()
                    .anyMatch(m -> "C".equals(m.getSender()));
            assertTrue(cMessageFound, "C's success messages should be collected");
        }

        @Test
        @DisplayName("Interrupt exception: B interrupts, A may complete, C succeeds")
        void testPoolInterruptException() throws Exception {
            PregelConfig config = new PregelConfig("test_conv_2", "root", 10000);
            config.setParentNs("root");

            // Node A: slow task (1 second)
            Callable<Object> taskASlow = () -> {
                Thread.sleep(1000);
                return null;
            };

            // Node B: interrupts (0.2s delay)
            Object taskBInterrupt = new Object() {
                public void call(PregelConfig cfg) throws GraphInterrupt {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    throw new GraphInterrupt(new Interrupt("B_Interrupt"));
                }
            };

            // Node C: fast
            Runnable taskCFast = () -> {
                // instant
            };

            StaticRouter routerA = new StaticRouter(List.of("Target_A"));
            StaticRouter routerB = new StaticRouter(List.of("Target_B"));
            StaticRouter routerC = new StaticRouter(List.of("Target_C"));

            PregelNode nodeA = new PregelNode("A", taskASlow, List.of(routerA));
            PregelNode nodeB = new PregelNode("B", taskBInterrupt, List.of(routerB));
            PregelNode nodeC = new PregelNode("C", taskCFast, List.of(routerC));

            TaskExecutorPool pool = new TaskExecutorPool(config);
            pool.submit(nodeA, 1);
            pool.submit(nodeB, 1);
            pool.submit(nodeC, 1);

            // GraphInterrupt is propagated
            assertThrows(GraphInterrupt.class, pool::waitAll);

            // B interrupts, recorded as __interrupt__
            assertTrue(pool.getFailed().containsKey("B"));
            assertEquals("__interrupt__", pool.getFailed().get("B").getStatus());

            // C succeeds
            assertFalse(pool.getFailed().containsKey("C"));
        }
    }

    // ---------- Pool clear and cancel tests ----------

    @Nested
    @DisplayName("TaskExecutorPool clear and cancel")
    class ClearCancelTests {

        @Test
        @DisplayName("clear resets all collections")
        void testClear() throws Exception {
            PregelConfig config = new PregelConfig();
            config.setParentNs("root");

            Runnable taskSimple = () -> {};
            PregelNode node = new PregelNode("A", taskSimple, List.of(new StaticRouter(List.of("B"))));

            TaskExecutorPool pool = new TaskExecutorPool(config);
            pool.submit(node, 1);
            pool.waitAll();

            assertFalse(pool.getSucceedMessages().isEmpty());
            pool.clear();
            assertTrue(pool.getSucceedMessages().isEmpty());
            assertTrue(pool.getFailed().isEmpty());
        }

        @Test
        @DisplayName("waitAll with no submissions is no-op")
        void testWaitAllEmpty() throws Exception {
            PregelConfig config = new PregelConfig();
            TaskExecutorPool pool = new TaskExecutorPool(config);
            assertDoesNotThrow(pool::waitAll);
        }
    }

    // ---------- Node function invocation tests ----------

    @Nested
    @DisplayName("NodeTask invocation")
    class NodeTaskInvocationTests {

        @Test
        @DisplayName("Runnable node function executes successfully")
        void testRunnableNodeFunc() throws Exception {
            AtomicInteger counter = new AtomicInteger(0);
            Runnable fn = counter::incrementAndGet;

            PregelNode node = new PregelNode("test", fn, List.of(new StaticRouter(List.of("next"))));
            NodeTask task = new NodeTask(node, new PregelConfig(), 1);
            Object result = task.call();

            assertInstanceOf(List.class, result);
            @SuppressWarnings("unchecked")
            List<Message> messages = (List<Message>) result;
            assertEquals(1, messages.size());
            assertEquals("test", messages.get(0).getSender());
            assertEquals("next", messages.get(0).getTarget());
            assertEquals(1, counter.get());
        }

        @Test
        @DisplayName("Callable node function executes successfully")
        void testCallableNodeFunc() throws Exception {
            Callable<String> fn = () -> "result";
            PregelNode node = new PregelNode("test", fn, List.of(new StaticRouter(List.of("a", "b"))));
            NodeTask task = new NodeTask(node, new PregelConfig(), 1);
            Object result = task.call();

            assertInstanceOf(List.class, result);
            @SuppressWarnings("unchecked")
            List<Message> messages = (List<Message>) result;
            assertEquals(2, messages.size());
        }

        @Test
        @DisplayName("GraphInterrupt from node is captured as return value")
        void testGraphInterruptCaptured() throws Exception {
            Callable<Object> fn = () -> {
                throw new GraphInterrupt(new Interrupt("test_interrupt"));
            };

            PregelNode node = new PregelNode("test", fn, List.of());
            NodeTask task = new NodeTask(node, new PregelConfig(), 1);
            Object result = task.call();

            assertInstanceOf(GraphInterrupt.class, result);
        }
    }
}
