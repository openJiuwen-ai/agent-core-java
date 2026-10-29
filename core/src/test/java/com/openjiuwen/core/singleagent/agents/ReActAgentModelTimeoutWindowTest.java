/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ModelError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.foundation.llm.modelclients.ModelTimeoutResolver;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.AsyncStreamQueue;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.ClassifiedModelFailureRail;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Verifies coordination between model idle windows and session stream waits.
 */
@Timeout(5)
class ReActAgentModelTimeoutWindowTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReActAgentModelTimeoutWindowTest.class);
    private static final long SUBSEQUENT_TIMEOUT_MS = 40L;
    private static final long LATCH_WAIT_SECONDS = 2L;
    private static final long TERMINAL_GRACE_WAIT_MS = 5200L;

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
        ContextCapturingReActAgent agent = agentWith(client, 0.25D);

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

    @Test
    void successReleasesCurrentLeaseAndNextCallAllocatesFreshLease() {
        BlockingModelClient client = new BlockingModelClient();
        ObservedQueue queue = new ObservedQueue(true);
        ShortTimeoutSession session = new ShortTimeoutSession("success-cleanup", queue, true);
        ContextCapturingReActAgent agent = agentWith(client, 0.25D);
        Iterator<Object> stream = agent.stream(Map.of("query", "hello"), session, List.of());

        Object frame = awaitNextFrameAfterBlockedWait(stream, client, queue);
        assertEquals("llm_output", assertInstanceOf(OutputSchema.class, frame).getType());
        assertEquals("answer", assertInstanceOf(OutputSchema.class, stream.next()).getType());
        session.awaitLeaseEnd();
        long firstLease = session.renewals.get(0).renewedLeaseId();
        assertEquals(List.of(firstLease), session.endedLeases);
        assertOriginalWaitIsRestored(stream);

        assertNextCallAllocatesFreshLease(agent, session, firstLease);
    }

    @Test
    void modelFailureReleasesCurrentLeaseAfterPublishingRealError() {
        BlockingModelClient client = new BlockingModelClient(new ModelError(StatusCode.MODEL_CALL_FAILED));
        ObservedQueue queue = new ObservedQueue(true);
        ShortTimeoutSession session = new ShortTimeoutSession("failure-cleanup", queue, true);
        ContextCapturingReActAgent agent = agentWith(client, 0.25D);
        disableGuardrailRetries(agent);
        Iterator<Object> stream = agent.stream(Map.of("query", "hello"), session, List.of());

        Object frame = awaitNextFrameAfterBlockedWait(stream, client, queue);
        assertModelError(frame, StatusCode.MODEL_CALL_FAILED.getCode());
        session.awaitLeaseEnd();
        long firstLease = session.renewals.get(0).renewedLeaseId();
        assertEquals(List.of(firstLease), session.endedLeases);
        assertOriginalWaitIsRestored(stream);

        assertNextCallAllocatesFreshLease(agent, session, firstLease);
    }

    @Test
    void cancellationReleasesCurrentLeaseAndRestoresOriginalWait() throws Exception {
        BlockingModelClient client = new BlockingModelClient();
        ObservedQueue queue = new ObservedQueue(true);
        ShortTimeoutSession session = new ShortTimeoutSession("cancel-cleanup", queue, true);
        ContextCapturingReActAgent agent = agentWith(client, 0.25D);
        Iterator<Object> stream = agent.stream(Map.of("query", "hello"), session, List.of());
        assertEquals("model_retry", assertInstanceOf(OutputSchema.class, stream.next()).getType());
        Future<Object> nextFrame = consumerExecutor.submit(stream::next);
        assertTrue(queue.expiredWait.await(2L, TimeUnit.SECONDS));

        assertInstanceOf(AutoCloseable.class, stream).close();
        client.releaseModel.countDown();
        queue.resumeWait.countDown();
        session.awaitLeaseEnd();

        long firstLease = session.renewals.get(0).renewedLeaseId();
        assertEquals(List.of(firstLease), session.endedLeases);
        assertEquals("answer", assertInstanceOf(OutputSchema.class,
                nextFrame.get(2L, TimeUnit.SECONDS)).getType());
        assertOriginalWaitIsRestored(stream);
        assertNextCallAllocatesFreshLease(agent, session, firstLease);
    }

    @Test
    void sharedCallReleaseIsIsolatedAndRestoresOriginalWait() {
        ObservedQueue queue = new ObservedQueue();
        StreamWriterManager manager = new StreamWriterManager(new StreamEmitter(queue));
        ShortTimeoutSession longSession = new ShortTimeoutSession("shared-long", manager, true);
        ShortTimeoutSession shortSession = new ShortTimeoutSession("shared-short", manager, true);
        BlockingModelClient longClient = new BlockingModelClient();
        BlockingModelClient shortClient = new BlockingModelClient();
        ReActAgent longAgent = agentWith(longClient, 0.25D);
        ReActAgent shortAgent = agentWith(shortClient, 0.05D);
        try {
            longAgent.stream(Map.of("query", "long"), longSession, List.of());
            longClient.awaitModelEntered();
            shortAgent.stream(Map.of("query", "short"), shortSession, List.of());
            shortClient.awaitModelEntered();

            shortClient.releaseModel.countDown();
            shortSession.awaitLeaseEnd();
            shortSession.awaitStreamClosed();
            queue.drain();
            manager.streamEmitter().emit("first");
            Iterator<Object> iterator = manager.streamIterator(1000L, SUBSEQUENT_TIMEOUT_MS, false);
            assertEquals("first", iterator.next());
            queue.arm(3, "long-call-lease");
            assertEquals("long-call-lease", iterator.next());

            longClient.releaseModel.countDown();
            longSession.awaitLeaseEnd();
            longSession.awaitStreamClosed();
            queue.drain();
            assertOriginalWaitIsRestored(iterator);
        } finally {
            longClient.releaseModel.countDown();
            shortClient.releaseModel.countDown();
        }
    }

    @Test
    void railDelayAndActualIdleAreRenewedBeforeRetryEvent() {
        assertSecondRoundModelTimeout(0.12D, 0.08D, "rail-interval");
    }

    @Test
    void idleAboveSessionIntervalStillPublishesSecondRoundTimeout() {
        assertSecondRoundModelTimeout(0.30D, 0.05D, "high-idle");
    }

    @Test
    @Timeout(15)
    void afterModelCallbackPauseBeyondGraceKeepsRealModelError() {
        assertTerminalPauseKeepsModelError(AgentCallbackEvent.AFTER_MODEL_CALL);
    }

    @Test
    @Timeout(15)
    void afterInvokePauseBeyondGraceKeepsRealModelError() {
        assertTerminalPauseKeepsModelError(AgentCallbackEvent.AFTER_INVOKE);
    }

    @Test
    @Timeout(8)
    void queuedErrorWinsSliceBoundaryAndCompletesQueueAccounting() throws Exception {
        BlockingModelClient client = new BlockingModelClient(new ModelError(StatusCode.MODEL_CALL_TIMEOUT));
        SliceWindowQueue queue = new SliceWindowQueue();
        ShortTimeoutSession session = new ShortTimeoutSession("slice-window", queue);
        queue.leaseReleased = session.leaseEndLatch;
        ReActAgent agent = agentWith(client, 0.05D);
        disableGuardrailRetries(agent);
        Iterator<Object> stream = agent.stream(Map.of("query", "hello"), session, List.of());
        assertEquals("model_retry", assertInstanceOf(OutputSchema.class, stream.next()).getType());
        Future<Object> nextFrame = consumerExecutor.submit(stream::next);
        assertTrue(queue.nullReturned.await(2L, TimeUnit.SECONDS));

        client.releaseModel.countDown();

        assertModelError(nextFrame.get(3L, TimeUnit.SECONDS), 181008);
        session.awaitStreamClosed();
        assertFalse(stream.hasNext());
        session.awaitLeaseEnd();
        Future<?> close = consumerExecutor.submit(() -> queue.close(5000L));
        assertDoesNotThrow(() -> close.get(1L, TimeUnit.SECONDS));
    }

    private OutputSchema awaitModelFrameAfterBlockedWait(ReActAgent agent, ShortTimeoutSession session,
            BlockingModelClient client, ObservedQueue queue) {
        Iterator<Object> stream = agent.stream(Map.of("query", "hello"), session, List.of());
        Object frame = awaitNextFrameAfterBlockedWait(stream, client, queue);
        return assertInstanceOf(OutputSchema.class, frame);
    }

    private Object awaitNextFrameAfterBlockedWait(Iterator<Object> stream, BlockingModelClient client,
            ObservedQueue queue) {
        try {
            OutputSchema retry = assertInstanceOf(OutputSchema.class, stream.next());
            assertEquals("model_retry", retry.getType());
            Future<Object> nextFrame = consumerExecutor.submit(stream::next);
            assertTrue(queue.expiredWait.await(2L, TimeUnit.SECONDS));
            client.releaseModel.countDown();
            queue.resumeWait.countDown();
            return assertDoesNotThrow(() -> nextFrame.get(2L, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            throw new AssertionError("Interrupted while coordinating stream consumers", exception);
        } finally {
            client.releaseModel.countDown();
            queue.resumeWait.countDown();
        }
    }

    private void assertNextCallAllocatesFreshLease(ContextCapturingReActAgent agent,
            ShortTimeoutSession session, long oldLease) {
        AgentCallbackContext context = agent.lastContext;
        assertEquals(0L, context.getExtra().get("_model_call_timeout_lease_id"));
        session.resetLeaseEnd();
        int previousRenewals = session.renewals.size();
        BlockingModelClient nextClient = new BlockingModelClient();
        nextClient.releaseModel.countDown();
        agent.setLlm(new Model(nextClient));

        Object result = agent.callModel(context, context.getContext(), List.of());

        assertEquals("ok", assertInstanceOf(AssistantMessage.class, result).getContent());
        TimeoutRenewal renewal = session.renewals.get(previousRenewals);
        assertEquals(0L, renewal.requestedLeaseId());
        assertNotEquals(oldLease, renewal.renewedLeaseId());
        session.awaitLeaseEnd();
        assertEquals(renewal.renewedLeaseId(), session.endedLeases.get(session.endedLeases.size() - 1));
        assertEquals(0L, context.getExtra().get("_model_call_timeout_lease_id"));
    }

    private static void assertOriginalWaitIsRestored(Iterator<Object> stream) {
        BaseError error = assertThrows(BaseError.class, stream::next);
        assertEquals(StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT, error.getStatus());
    }

    private void assertSecondRoundModelTimeout(double idleSeconds, double railDelaySeconds, String sessionId) {
        TwoRoundTimeoutClient client = new TwoRoundTimeoutClient();
        ShortTimeoutSession session = new ShortTimeoutSession(sessionId, new ObservedQueue(), true);
        ReActAgent agent = agentWith(client, idleSeconds);
        useGuardrailRetry(agent, railDelaySeconds);
        Iterator<Object> stream = agent.stream(Map.of("query", "hello"), session, List.of());
        assertEquals("model_retry", assertInstanceOf(OutputSchema.class, stream.next()).getType());
        assertEquals("model_retry", assertInstanceOf(OutputSchema.class, stream.next()).getType());

        OutputSchema railRetry = assertInstanceOf(OutputSchema.class, stream.next());
        Future<Object> finalFrame = consumerExecutor.submit(stream::next);

        assertEquals("model_retry", railRetry.getType());
        long expectedDelayMs = Math.round(railDelaySeconds * 1000.0D);
        long expectedIdleMs = ModelTimeoutResolver.toDuration(client.options.get(0).getTimeout()).toMillis();
        assertEquals(expectedDelayMs, session.railEventDelayMs);
        assertEquals(expectedIdleMs, session.railEventIdleMs);
        Object error = assertDoesNotThrow(() -> finalFrame.get(3L, TimeUnit.SECONDS));
        assertModelError(error, 181008);
        session.awaitLeaseEnd();
        assertEquals(1, session.endedLeases.size());
    }

    private void assertTerminalPauseKeepsModelError(AgentCallbackEvent event) {
        BlockingModelClient client = new BlockingModelClient(new ModelError(StatusCode.MODEL_CALL_TIMEOUT));
        ObservedQueue queue = new ObservedQueue(true);
        ShortTimeoutSession session = new ShortTimeoutSession("terminal-" + event, queue, true);
        ReActAgent agent = agentWith(client, 0.05D);
        disableGuardrailRetries(agent);
        CallbackPause pause = registerPause(agent, event);
        Iterator<Object> stream = agent.stream(Map.of("query", "hello"), session, List.of());
        try {
            assertEquals("model_retry", assertInstanceOf(OutputSchema.class, stream.next()).getType());
            Future<Object> nextFrame = consumerExecutor.submit(stream::next);
            assertTrue(queue.expiredWait.await(2L, TimeUnit.SECONDS));
            client.releaseModel.countDown();
            queue.resumeWait.countDown();
            assertTrue(pause.entered.await(2L, TimeUnit.SECONDS));
            assertFalse(pause.release.await(TERMINAL_GRACE_WAIT_MS, TimeUnit.MILLISECONDS));
            assertFalse(nextFrame.isDone(), "Session wait expired during terminal publication");
            pause.release.countDown();
            Object error = nextFrame.get(2L, TimeUnit.SECONDS);
            assertModelError(error, 181008);
            session.awaitLeaseEnd();
            assertEquals(1, session.endedLeases.size());
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
            throw new AssertionError("Terminal publication coordination failed", exception);
        } finally {
            client.releaseModel.countDown();
            queue.resumeWait.countDown();
            pause.release.countDown();
        }
    }

    private static CallbackPause registerPause(ReActAgent agent, AgentCallbackEvent event) {
        CallbackPause pause = new CallbackPause();
        agent.registerCallback(event, context -> {
            pause.entered.countDown();
            pause.awaitRelease();
            return CompletableFuture.completedFuture(null);
        }, 50).toCompletableFuture().join();
        return pause;
    }

    private static void assertModelError(Object frame, int numericCode) {
        OutputSchema answer = assertInstanceOf(OutputSchema.class, frame);
        assertEquals("answer", answer.getType());
        Map<?, ?> payload = assertInstanceOf(Map.class, answer.getPayload());
        Map<?, ?> failure = assertInstanceOf(Map.class, payload.get("failure"));
        assertEquals(numericCode, failure.get("numericCode"));
    }

    private static void readInto(Iterator<Object> iterator, CompletableFuture<Object> outcome) {
        try {
            outcome.complete(iterator.next());
        } catch (BaseError error) {
            outcome.completeExceptionally(error);
        }
    }

    private static ContextCapturingReActAgent agentWith(Model.ModelClient client, double idleSeconds) {
        ContextCapturingReActAgent agent = new ContextCapturingReActAgent(new AgentCard(
                "stream-window-" + System.nanoTime(), "stream-window", "test agent"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setMaxIterations(1);
        config.setModelStreamIdleSeconds(idleSeconds);
        agent.configure(config);
        agent.setLlm(new Model(client));
        return agent;
    }

    private static void disableGuardrailRetries(ReActAgent agent) {
        agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        agent.registerInstanceRail(new ClassifiedModelFailureRail(0, 0.0D)).toCompletableFuture().join();
    }

    private static void useGuardrailRetry(ReActAgent agent, double delaySeconds) {
        agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        agent.registerInstanceRail(new ClassifiedModelFailureRail(1, delaySeconds)).toCompletableFuture().join();
    }

    private static Thread newConsumerThread(Runnable runnable) {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName("model-window-test-consumer");
        thread.setUncaughtExceptionHandler((worker, error) -> LOGGER.error("Stream consumer failed", error));
        return thread;
    }

    private record TimeoutRenewal(long requestedLeaseId, long renewedLeaseId, long idleMs, long delayMs) {
    }

    private static final class CallbackPause {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private void awaitRelease() {
            try {
                if (!release.await(8L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Callback release was not signalled");
                }
            } catch (InterruptedException exception) {
                throw new IllegalStateException("Callback wait was interrupted", exception);
            }
        }
    }

    private static final class ContextCapturingReActAgent extends ReActAgent {
        private AgentCallbackContext lastContext;

        private ContextCapturingReActAgent(AgentCard card) {
            super(card);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
            lastContext = ctx;
            return super.callModel(ctx, context, tools);
        }
    }

    private static final class ShortTimeoutSession extends AgentSession {
        private final List<TimeoutRenewal> renewals = new CopyOnWriteArrayList<>();
        private final List<Long> endedLeases = new CopyOnWriteArrayList<>();
        private final boolean keepStreamOpen;
        private volatile CountDownLatch leaseEndLatch = new CountDownLatch(1);
        private volatile CountDownLatch streamClosedLatch = new CountDownLatch(1);
        private volatile long renewedIdleMs = -1L;
        private volatile long renewedDelayMs = -1L;
        private volatile long railEventIdleMs = -1L;
        private volatile long railEventDelayMs = -1L;

        private ShortTimeoutSession(String sessionId, AsyncStreamQueue queue) {
            this(sessionId, queue, false);
        }

        private ShortTimeoutSession(String sessionId, AsyncStreamQueue queue, boolean keepStreamOpen) {
            this(sessionId, new StreamWriterManager(new StreamEmitter(queue)), keepStreamOpen);
        }

        private ShortTimeoutSession(String sessionId, StreamWriterManager manager, boolean keepStreamOpen) {
            super(sessionId, null, null, manager, false, null);
            this.keepStreamOpen = keepStreamOpen;
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
            long renewedLease = super.renewModelCallTimeout(leaseId, idleTimeoutMs, delayMs);
            renewals.add(new TimeoutRenewal(leaseId, renewedLease, idleTimeoutMs, delayMs));
            return renewedLease;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void endModelCallTimeout(long leaseId) {
            endedLeases.add(leaseId);
            super.endModelCallTimeout(leaseId);
            leaseEndLatch.countDown();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeStream(Object data) {
            if (data instanceof OutputSchema output && output.getPayload() instanceof Map<?, ?> payload
                    && "rail".equals(payload.get("retry_tier"))) {
                railEventIdleMs = renewedIdleMs;
                railEventDelayMs = renewedDelayMs;
            }
            super.writeStream(data);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void closeStream() {
            if (!keepStreamOpen) {
                super.closeStream();
            }
            streamClosedLatch.countDown();
        }

        private void awaitLeaseEnd() {
            awaitLatch(leaseEndLatch, "Model call lease was not released");
        }

        private void awaitStreamClosed() {
            awaitLatch(streamClosedLatch, "Stream was not closed");
        }

        private void resetLeaseEnd() {
            leaseEndLatch = new CountDownLatch(1);
        }

        private static void awaitLatch(CountDownLatch latch, String message) {
            try {
                assertTrue(latch.await(LATCH_WAIT_SECONDS, TimeUnit.SECONDS), message);
            } catch (InterruptedException exception) {
                throw new AssertionError("Latch wait was interrupted", exception);
            }
        }
    }

    private static class ObservedQueue extends AsyncStreamQueue {
        private final boolean pauseAfterTimeout;
        private final CountDownLatch blockedWait = new CountDownLatch(1);
        private final CountDownLatch expiredWait = new CountDownLatch(1);
        private final CountDownLatch resumeWait = new CountDownLatch(1);
        private int receiveCalls;
        private int frameOnCall = -1;
        private String frame = "";

        private ObservedQueue() {
            this(false);
        }

        private ObservedQueue(boolean pauseAfterTimeout) {
            this.pauseAfterTimeout = pauseAfterTimeout;
        }

        private void arm(int call, String value) {
            frameOnCall = call;
            frame = value;
        }

        private void drain() {
            while (tryReceive() != null) {
                // Remove completed producer output before measuring the next wait.
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object receive(long timeoutMs) {
            receiveCalls++;
            if (receiveCalls == frameOnCall) {
                send(frame);
            }
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

    private static final class SliceWindowQueue extends AsyncStreamQueue {
        private final CountDownLatch nullReturned = new CountDownLatch(1);
        private volatile CountDownLatch leaseReleased;
        private int receiveCalls;

        /**
         * {@inheritDoc}
         */
        @Override
        public Object receive(long timeoutMs) {
            receiveCalls++;
            Object item = super.receive(timeoutMs);
            if (receiveCalls == 2 && item == null) {
                nullReturned.countDown();
                awaitLeaseRelease();
            }
            return item;
        }

        private void awaitLeaseRelease() {
            try {
                if (!leaseReleased.await(3L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Model call lease release was not signalled");
                }
            } catch (InterruptedException exception) {
                throw new IllegalStateException("Slice coordination was interrupted", exception);
            }
        }
    }

    private static final class BlockingModelClient implements Model.ModelClient {
        private final CountDownLatch releaseModel = new CountDownLatch(1);
        private final CountDownLatch modelEntered = new CountDownLatch(1);
        private final RuntimeException failure;
        private volatile ModelInvokeOptions options;

        private BlockingModelClient() {
            this(null);
        }

        private BlockingModelClient(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            this.options = options;
            options.getRetryListener().onRetry(new ModelRetryEvent(1, 2, null, "java.io.IOException",
                    Duration.ZERO, "exponential_backoff", ModelErrorCategory.TEMPORARY));
            modelEntered.countDown();
            awaitModelRelease();
            if (failure != null) {
                throw failure;
            }
            return List.of(AssistantMessageChunk.builder().content("ok").finishReason("stop").build()).iterator();
        }

        private void awaitModelEntered() {
            try {
                assertTrue(modelEntered.await(LATCH_WAIT_SECONDS, TimeUnit.SECONDS),
                        "Model call was not entered");
            } catch (InterruptedException exception) {
                throw new AssertionError("Model entry wait was interrupted", exception);
            }
        }

        private void awaitModelRelease() {
            try {
                if (!releaseModel.await(2L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Model release was not signalled");
                }
            } catch (InterruptedException exception) {
                throw new CancellationException("Model call cancelled");
            }
        }
    }

    private static final class TwoRoundTimeoutClient implements Model.ModelClient {
        private final List<ModelInvokeOptions> options = new CopyOnWriteArrayList<>();
        private int streamCalls;

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            this.options.add(options);
            streamCalls++;
            if (streamCalls == 1) {
                publishInnerRetries(options);
            }
            awaitActualIdle(options);
            throw new ModelError(StatusCode.MODEL_CALL_TIMEOUT);
        }

        private static void publishInnerRetries(ModelInvokeOptions options) {
            for (int retry = 1; retry <= 2; retry++) {
                options.getRetryListener().onRetry(new ModelRetryEvent(retry, 2, null, "java.io.IOException",
                        Duration.ZERO, "exponential_backoff", ModelErrorCategory.TEMPORARY));
            }
        }

        private static void awaitActualIdle(ModelInvokeOptions options) {
            long idleMs = ModelTimeoutResolver.toDuration(options.getTimeout()).toMillis();
            try {
                if (new CountDownLatch(1).await(idleMs, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Unexpected model idle release");
                }
            } catch (InterruptedException exception) {
                throw new CancellationException("Model call cancelled");
            }
        }
    }
}
