/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

            // A propagates interruption and remains pending for recovery.
            assertTrue(pool.getFailed().containsKey("A"));

            // C succeeds
            assertFalse(pool.getFailed().containsKey("C"));
            // C's messages should be collected
            boolean cMessageFound = pool.getSucceedMessages().stream().anyMatch(m -> "C".equals(m.getSender()));
            assertTrue(cMessageFound, "C's success messages should be collected");
        }

        @Test
        @DisplayName("Sibling failure preserves a node invocation that returns normally")
        void testSiblingFailurePreservesNormallyReturnedInvocation() throws Exception {
            CountDownLatch invocationStarted = new CountDownLatch(1);
            CountDownLatch invocationBlocker = new CountDownLatch(1);
            AtomicBoolean invocationInterrupted = new AtomicBoolean(false);
            AtomicBoolean routerSawInterruption = new AtomicBoolean(false);
            AtomicInteger invocationCount = new AtomicInteger(0);
            AtomicInteger routingCount = new AtomicInteger(0);
            Callable<Object> successfulTask = () -> {
                int currentInvocation = invocationCount.incrementAndGet();
                if (currentInvocation == 1) {
                    invocationStarted.countDown();
                    try {
                        if (invocationBlocker.await(5L, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Invocation blocker was released unexpectedly");
                        }
                    } catch (InterruptedException exception) {
                        invocationInterrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                }
                return null;
            };
            Callable<Object> failingTask = () -> {
                if (!invocationStarted.await(1L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Successful sibling did not start");
                }
                throw new IllegalStateException("Sibling failed");
            };
            IRouter router = sourceNode -> {
                routingCount.incrementAndGet();
                routerSawInterruption.set(Thread.currentThread().isInterrupted());
                return List.of(new TriggerMessage(sourceNode, "target"));
            };
            PregelNode successfulNode = new PregelNode(
                    "successful",
                    successfulTask,
                    List.of(router));
            PregelNode failingNode = new PregelNode("failing", failingTask, List.of());
            TaskExecutorPool pool = new TaskExecutorPool(new PregelConfig());
            pool.submit(successfulNode, 1);
            pool.submit(failingNode, 1);

            IllegalStateException exception = assertThrows(IllegalStateException.class, pool::waitAll);
            if (pool.getFailed().containsKey("successful")) {
                new NodeTask(successfulNode, new PregelConfig(), 1).call();
            }

            assertEquals("Sibling failed", exception.getMessage());
            assertTrue(invocationInterrupted.get());
            assertFalse(routerSawInterruption.get(), "A sibling-failure interrupt must not leak into routing");
            assertEquals(1, invocationCount.get(), "A normally returned invocation must not execute again");
            assertEquals(1, routingCount.get());
            assertFalse(pool.getFailed().containsKey("successful"));
            assertTrue(pool.getSucceedMessages().stream()
                    .anyMatch(message -> "successful".equals(message.getSender())));
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

            Runnable taskSimple = () -> {
            };
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

        @Test
        @DisplayName("cancelAll interrupts the running node task")
        void testCancelAllInterruptsRunningTask() throws Exception {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch interrupted = new CountDownLatch(1);
            Callable<Object> blockingTask = () -> {
                started.countDown();
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw e;
                }
                return null;
            };
            PregelNode node = new PregelNode("blocking", blockingTask, List.of());
            TaskExecutorPool pool = new TaskExecutorPool(new PregelConfig());
            pool.submit(node, 1);

            assertTrue(started.await(1, TimeUnit.SECONDS));
            pool.cancelAll();

            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("cancelAll interrupts routing after the node invocation returns")
        void testCancelAllInterruptsRouting() throws Exception {
            CountDownLatch routingStarted = new CountDownLatch(1);
            CountDownLatch routingBlocker = new CountDownLatch(1);
            CountDownLatch routingInterrupted = new CountDownLatch(1);
            AtomicInteger invocationCount = new AtomicInteger(0);
            IRouter blockingRouter = sourceNode -> {
                routingStarted.countDown();
                try {
                    routingBlocker.await();
                } catch (InterruptedException exception) {
                    routingInterrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return List.of(new TriggerMessage(sourceNode, "target"));
            };
            PregelNode node = new PregelNode(
                    "routing",
                    (Runnable) invocationCount::incrementAndGet,
                    List.of(blockingRouter));
            TaskExecutorPool pool = new TaskExecutorPool(new PregelConfig());
            pool.submit(node, 1);

            assertTrue(routingStarted.await(1L, TimeUnit.SECONDS));
            pool.cancelAll();

            assertEquals(1, invocationCount.get());
            assertTrue(routingInterrupted.await(1L, TimeUnit.SECONDS));
            assertTrue(pool.getFailed().containsKey("routing"));
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
