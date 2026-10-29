/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.foundation.llm.modelclients.ModelTimeoutResolver;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.AsyncStreamQueue;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.singleagent.runbudget.RunBudgetConfig;
import com.openjiuwen.core.singleagent.runbudget.RunBudgetRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Verifies coordination between model idle windows and session stream waits.
 */
@Timeout(5)
class ReActAgentModelTimeoutWindowTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReActAgentModelTimeoutWindowTest.class);
    private static final long SUBSEQUENT_TIMEOUT_MS = 40L;

    private final ThreadPoolExecutor consumerExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(8),
            ReActAgentModelTimeoutWindowTest::newConsumerThread);

    @AfterEach
    void stopConsumerExecutor() {
        consumerExecutor.shutdownNow();
        try {
            if (!consumerExecutor.awaitTermination(2L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Stream consumer did not stop");
            }
        } catch (InterruptedException exception) {
            throw new IllegalStateException("Consumer executor shutdown was interrupted", exception);
        }
    }

    @Test
    void blockedSubsequentWaitIsRearmedByModelIdle() {
        BlockingModelClient client = new BlockingModelClient();
        ObservedQueue queue = new ObservedQueue(true);
        ShortTimeoutSession session = new ShortTimeoutSession("rearmed-wait", queue);
        ReActAgent agent = agentWith(client, 0.25D);

        OutputSchema output = awaitModelFrameAfterBlockedWait(agent, session, client, queue);

        assertEquals("llm_output", output.getType());
    }

    @Test
    void budgetClampedOptionsIdleControlsSubsequentWait() {
        BlockingModelClient client = new BlockingModelClient();
        ObservedQueue queue = new ObservedQueue(true);
        ShortTimeoutSession session = new ShortTimeoutSession("budget-clamped-wait", queue);
        ReActAgent agent = agentWith(client, 30.0D);
        agent.registerRail(new RunBudgetRail(RunBudgetConfig.builder()
                .turnEnabled(false)
                .totalBudgetSeconds(1)
                .nearDeadlineThresholdSeconds(1)
                .build())).toCompletableFuture().join();

        OutputSchema output = awaitModelFrameAfterBlockedWait(agent, session, client, queue);

        assertEquals("llm_output", output.getType());
        float actualIdleSeconds = client.options.getTimeout();
        assertTrue(actualIdleSeconds > 0.0F && actualIdleSeconds < 1.0F);
        long expectedIdleMs = ModelTimeoutResolver.toDuration(actualIdleSeconds).toMillis();
        assertEquals(expectedIdleMs, session.renewedIdleMs);
        assertEquals(0L, session.renewedDelayMs);
    }

    @Test
    void positiveSubMillisecondOptionsIdleControlsSubsequentWait() {
        BlockingModelClient client = new BlockingModelClient();
        ObservedQueue queue = new ObservedQueue(true);
        ShortTimeoutSession session = new ShortTimeoutSession("sub-millisecond-wait", queue);
        ReActAgent agent = agentWith(client, 0.0005D);

        OutputSchema output = awaitModelFrameAfterBlockedWait(agent, session, client, queue);

        assertEquals("llm_output", output.getType());
        assertTrue(client.options.getTimeout() > 0.0F && client.options.getTimeout() < 0.001F);
        assertEquals(1L, session.renewedIdleMs);
        assertEquals(0L, session.renewedDelayMs);
    }

    @Test
    void blockedSubsequentWaitKeepsTimeoutAfterInterruption() throws Exception {
        ObservedQueue queue = new ObservedQueue();
        StreamEmitter emitter = new StreamEmitter(queue);
        StreamWriterManager manager = new StreamWriterManager(emitter);
        emitter.emit("first");
        Iterator<Object> iterator = manager.streamIterator(1000L, 5000L, false);
        assertEquals("first", iterator.next());
        CompletableFuture<Object> outcome = new CompletableFuture<>();
        Future<?> consumer = consumerExecutor.submit(() -> readInto(iterator, outcome));
        assertTrue(queue.blockedWait.await(2L, TimeUnit.SECONDS));

        consumer.cancel(true);

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> outcome.get(2L, TimeUnit.SECONDS));
        BaseError cause = assertInstanceOf(BaseError.class, error.getCause());
        assertEquals(StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT, cause.getStatus());
    }

    private OutputSchema awaitModelFrameAfterBlockedWait(ReActAgent agent, ShortTimeoutSession session,
            BlockingModelClient client, ObservedQueue queue) {
        Iterator<Object> stream = agent.stream(Map.of("query", "hello"), session, List.of());
        try {
            OutputSchema retry = assertInstanceOf(OutputSchema.class, stream.next());
            assertEquals("model_retry", retry.getType());
            Future<Object> nextFrame = consumerExecutor.submit(stream::next);
            assertTrue(queue.expiredWait.await(2L, TimeUnit.SECONDS));
            client.releaseModel.countDown();
            queue.resumeWait.countDown();
            Object frame = assertDoesNotThrow(() -> nextFrame.get(2L, TimeUnit.SECONDS));
            return assertInstanceOf(OutputSchema.class, frame);
        } catch (InterruptedException exception) {
            throw new AssertionError("Interrupted while coordinating stream consumers", exception);
        } finally {
            client.releaseModel.countDown();
            queue.resumeWait.countDown();
        }
    }

    private static void readInto(Iterator<Object> iterator, CompletableFuture<Object> outcome) {
        try {
            outcome.complete(iterator.next());
        } catch (BaseError error) {
            outcome.completeExceptionally(error);
        }
    }

    private static ReActAgent agentWith(BlockingModelClient client, double idleSeconds) {
        ReActAgent agent = new ReActAgent(new AgentCard(
                "stream-window-" + System.nanoTime(), "stream-window", "test agent"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setMaxIterations(1);
        config.setModelStreamIdleSeconds(idleSeconds);
        agent.configure(config);
        agent.setLlm(new Model(client));
        return agent;
    }

    private static Thread newConsumerThread(Runnable runnable) {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName("model-window-test-consumer");
        thread.setUncaughtExceptionHandler((worker, error) -> LOGGER.error("Stream consumer failed", error));
        return thread;
    }

    private static final class ShortTimeoutSession extends AgentSession {
        private volatile long renewedIdleMs = -1L;
        private volatile long renewedDelayMs = -1L;

        private ShortTimeoutSession(String sessionId, AsyncStreamQueue queue) {
            super(sessionId, null, null, new StreamWriterManager(new StreamEmitter(queue)), false, null);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Iterator<Object> streamIterator() {
            return getInner().streamWriterManager().streamIterator(2000L, SUBSEQUENT_TIMEOUT_MS, false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long renewModelCallTimeout(long leaseId, long idleTimeoutMs, long delayMs) {
            renewedIdleMs = idleTimeoutMs;
            renewedDelayMs = delayMs;
            return super.renewModelCallTimeout(leaseId, idleTimeoutMs, delayMs);
        }
    }

    private static final class ObservedQueue extends AsyncStreamQueue {
        private final boolean pauseAfterTimeout;
        private final CountDownLatch blockedWait = new CountDownLatch(1);
        private final CountDownLatch expiredWait = new CountDownLatch(1);
        private final CountDownLatch resumeWait = new CountDownLatch(1);
        private int receiveCalls;

        private ObservedQueue() {
            this(false);
        }

        private ObservedQueue(boolean pauseAfterTimeout) {
            this.pauseAfterTimeout = pauseAfterTimeout;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object receive(long timeoutMs) {
            receiveCalls++;
            boolean isFirstSubsequentWait = receiveCalls == 2;
            if (isFirstSubsequentWait) {
                blockedWait.countDown();
            }
            Object item = super.receive(timeoutMs);
            if (pauseAfterTimeout && isFirstSubsequentWait && item == null) {
                expiredWait.countDown();
                awaitResume();
            }
            return item;
        }

        private void awaitResume() {
            try {
                if (!resumeWait.await(2L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Queue resume was not signalled");
                }
            } catch (InterruptedException exception) {
                throw new IllegalStateException("Queue wait was interrupted", exception);
            }
        }
    }

    private static final class BlockingModelClient implements Model.ModelClient {
        private final CountDownLatch releaseModel = new CountDownLatch(1);
        private volatile ModelInvokeOptions options;

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            this.options = options;
            options.getRetryListener().onRetry(new ModelRetryEvent(1, 2, null, "java.io.IOException",
                    Duration.ZERO, "exponential_backoff", ModelErrorCategory.TEMPORARY));
            awaitModelRelease();
            return List.of(AssistantMessageChunk.builder().content("ok").finishReason("stop").build()).iterator();
        }

        private void awaitModelRelease() {
            try {
                if (!releaseModel.await(2L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Model release was not signalled");
                }
            } catch (InterruptedException exception) {
                throw new IllegalStateException("Model wait was interrupted", exception);
            }
        }
    }
}
