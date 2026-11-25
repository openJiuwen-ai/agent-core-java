/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that closing the stream iterator during tool execution stops the
 * agent instead of feeding the cancelled tool message back into the model,
 * and that the cancelled result carries the correct result_type and does not
 * poison the session for subsequent invocations.
 */
class ReActAgentStreamCancelTest {

    private static final long TOOL_DURATION_MS = 3000;
    private static final long SETTLE_MS = 8000;

    /**
     * Fake model that records every call and answers with a tool call until the
     * cancelled tool message shows up, then returns a final answer.
     */
    private static final class CountingClient implements Model.ModelClient {
        final AtomicInteger calls = new AtomicInteger();
        /** Signals that the first model response (a tool call) has been produced. */
        final CountDownLatch firstCallDone = new CountDownLatch(1);
        final String toolName;

        private CountingClient(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(policy(messages));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            calls.incrementAndGet();
            AssistantMessage message = policy(messages);
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                    .content(message.getContent())
                    .toolCalls(message.getToolCalls())
                    .finishReason(message.getFinishReason())
                    .build();
            return List.of(chunk).iterator();
        }

        private AssistantMessage policy(List<BaseMessage> messages) {
            BaseMessage last = messages.get(messages.size() - 1);
            if (last instanceof ToolMessage) {
                return textMessage("final answer");
            }
            firstCallDone.countDown();
            return toolCallMessage();
        }

        private AssistantMessage toolCallMessage() {
            ToolCall call = ToolCall.builder()
                    .id("call_" + UUID.randomUUID())
                    .name(toolName)
                    .arguments("{}")
                    .index(0)
                    .build();
            return AssistantMessage.builder()
                    .content("")
                    .finishReason("tool_calls")
                    .toolCalls(List.of(call))
                    .build();
        }
    }

    private static AssistantMessage textMessage(String content) {
        return AssistantMessage.builder()
                .content(content)
                .toolCalls(List.of())
                .finishReason("stop")
                .build();
    }

    private static LocalFunction slowTool(String name, boolean parallelSafe) {
        ToolCard card = ToolCard.builder()
                .id(name)
                .name(name)
                .description(name)
                .parallelSafe(parallelSafe)
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()))
                .build();
        LocalFunction.ContextFunction fn = (inputs, kwargs) -> {
            try {
                Thread.sleep(TOOL_DURATION_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("tool interrupted", e);
            }
            return "slow tool finished";
        };
        return new LocalFunction(card, fn);
    }

    private static ReActAgent newAgent(String id, Model.ModelClient client) {
        ReActAgent agent = new ReActAgent(AgentCard.builder().id(id).name(id).description("stream cancel").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(4)
                .streamMaxRetries(2)
                .streamRetryDelayMs(0)
                .promptTemplate(List.of(Map.of("role", "system", "content", "sys")))
                .build());
        agent.setLlm(new Model(client));
        return agent;
    }

    private static void registerTool(ReActAgent agent, LocalFunction tool) {
        Runner.resourceMgr().addTool(tool, null);
        agent.getAbilityManager().add(List.of(tool.getCard()));
    }

    /**
     * Runs a streaming invocation, cancels by closing the iterator during tool
     * execution, then drains remaining stream chunks and returns the collected
     * result along with the model call count.
     */
    private static CancelResult cancelDuringTool(boolean parallelSafe) throws Exception {
        String toolName = "slow_tool_" + parallelSafe + "_" + UUID.randomUUID();
        CountingClient client = new CountingClient(toolName);
        ReActAgent agent = newAgent("cancel_agent_" + UUID.randomUUID(), client);
        registerTool(agent, slowTool(toolName, parallelSafe));

        Map<String, Object> inputs = Map.of(
                "query", "go",
                "conversation_id", "s-cancel-" + UUID.randomUUID());
        AgentSession session = AgentSession.createAgentSession(
                String.valueOf(inputs.get("conversation_id")), null, agent.getCard());
        session.preRun(Map.of("inputs", inputs));

        Iterator<Object> iterator = agent.stream(inputs, session, List.of());
        assertThat(client.firstCallDone.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);
        if (iterator instanceof AutoCloseable closeable) {
            closeable.close();
        }
        Thread.sleep(SETTLE_MS);

        List<Object> drained = new ArrayList<>();
        while (iterator.hasNext()) {
            drained.add(iterator.next());
        }
        return new CancelResult(client.calls.get(), drained, session);
    }

    /**
     * Closing the stream while a parallel-safe tool runs must stop the loop:
     * the cancelled tool result must not trigger another model call.
     */
    @Test
    void streamCloseDuringParallelSafeToolStopsModelCalls() throws Exception {
        CancelResult result = cancelDuringTool(true);
        assertThat(result.modelCalls)
                .as("parallelSafe=true: LLM calls after cancel (1 = stopped, 2 = kept running) = %s", result.modelCalls)
                .isEqualTo(1);
    }

    /**
     * Same expectation for a non-parallel-safe tool: cancel must stop the loop.
     */
    @Test
    void streamCloseDuringSerialToolStopsModelCalls() throws Exception {
        CancelResult result = cancelDuringTool(false);
        assertThat(result.modelCalls)
                .as("parallelSafe=false: LLM calls after cancel (1 = stopped, 2 = kept running) = %s", result.modelCalls)
                .isEqualTo(1);
    }

    /**
     * The final streamed chunk after cancellation must carry
     * result_type=cancelled, not result_type=error.
     */
    @Test
    void streamCancelProducesCancelledResultType() throws Exception {
        CancelResult result = cancelDuringTool(true);
        String lastResultType = result.lastResultType();
        assertThat(lastResultType)
                .as("result_type after cancel (expected 'cancelled', got '%s')", lastResultType)
                .isEqualTo("cancelled");
    }

    @Test
    void requestCancelInterruptsRunningToolAndPublishesItsOutcome() throws Exception {
        String toolName = "interruptible_tool_" + UUID.randomUUID();
        CountDownLatch toolStarted = new CountDownLatch(1);
        CountDownLatch toolRelease = new CountDownLatch(1);
        AtomicBoolean toolInterrupted = new AtomicBoolean();
        ToolCard card = ToolCard.builder()
                .id(toolName)
                .name(toolName)
                .description(toolName)
                .inputParams(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .build();
        LocalFunction tool = new LocalFunction(card, (inputs, kwargs) -> {
            toolStarted.countDown();
            try {
                toolRelease.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                toolInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
            return "interrupted tool finished";
        });
        ReActAgent agent = newAgent("interrupt_cancel_" + UUID.randomUUID(), new CountingClient(toolName));
        registerTool(agent, tool);
        AgentSession session = AgentSession.createAgentSession(
                "interrupt-cancel-" + UUID.randomUUID(), null, agent.getCard());
        Map<String, Object> inputs = Map.of("query", "go", "conversation_id", session.getSessionId());
        session.preRun(Map.of("inputs", inputs));
        Iterator<Object> stream = agent.stream(inputs, session, List.of());
        List<Object> chunks = new ArrayList<>();
        try {
            assertThat(readUntil(stream, "tool_call", chunks)).isTrue();
            assertThat(toolStarted.await(10, TimeUnit.SECONDS)).isTrue();
            ReActAgent.requestCancel(session);
            stream.forEachRemaining(chunks::add);
            List<OutputSchema> schemas = chunks.stream()
                    .filter(OutputSchema.class::isInstance)
                    .map(OutputSchema.class::cast)
                    .toList();
            assertThat(toolInterrupted).as("the running tool must receive cooperative interruption").isTrue();
            assertThat(toolEventIds(schemas, "tool_output"))
                    .containsExactlyInAnyOrderElementsOf(toolEventIds(schemas, "tool_call"));
            assertThat(schemas).anySatisfy(schema -> {
                if ("tool_output".equals(schema.getType()) && schema.getPayload() instanceof Map<?, ?> payload) {
                    assertThat(payload.get("result")).isEqualTo("interrupted tool finished");
                }
            });
        } finally {
            toolRelease.countDown();
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    void normalStreamExhaustionDoesNotPublishCancelledResult() throws Exception {
        ReActAgent agent = newAgent("normal_finish_" + UUID.randomUUID(), new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.completedFuture(textMessage("complete"));
            }

            @Override
            public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
                return List.of(chunk("complete", List.of(), "stop")).iterator();
            }
        });
        AgentSession session = newSession(agent, "normal-finish-" + UUID.randomUUID());
        Iterator<Object> stream = agent.stream(Map.of("query", "go"), session, List.of());
        try {
            List<Object> chunks = drain(stream);
            assertThat(resultType(chunks)).isEqualTo("answer");
        } finally {
            closeStream(stream);
        }
    }

    private static boolean readUntil(Iterator<Object> stream, String type, List<Object> chunks) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (!stream.hasNext()) {
                return false;
            }
            Object chunk = stream.next();
            chunks.add(chunk);
            if (chunk instanceof OutputSchema schema && type.equals(schema.getType())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> toolEventIds(List<OutputSchema> schemas, String type) {
        return schemas.stream()
                .filter(schema -> type.equals(schema.getType()))
                .map(OutputSchema::getPayload)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(payload -> String.valueOf(payload.get("tool_call_id")))
                .toList();
    }

    /**
     * After a cancelled invocation, the same session must be reusable: a new
     * stream() call must not be permanently poisoned by the previous cancel flag.
     */
    @Test
    void sessionReusableAfterCancel() throws Exception {
        String toolName = "reuse_tool_" + UUID.randomUUID();
        CountingClient client = new CountingClient(toolName);
        ReActAgent agent = newAgent("reuse_agent_" + UUID.randomUUID(), client);
        registerTool(agent, slowTool(toolName, true));

        Map<String, Object> inputs1 = Map.of(
                "query", "go",
                "conversation_id", "reuse-cancel-" + UUID.randomUUID());
        AgentSession session = AgentSession.createAgentSession(
                String.valueOf(inputs1.get("conversation_id")), null, agent.getCard());
        session.preRun(Map.of("inputs", inputs1));

        Iterator<Object> iterator1 = agent.stream(inputs1, session, List.of());
        assertThat(client.firstCallDone.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);
        if (iterator1 instanceof AutoCloseable closeable) {
            closeable.close();
        }
        Thread.sleep(SETTLE_MS);
        int callsAfterFirstCancel = client.calls.get();
        assertThat(callsAfterFirstCancel).isEqualTo(1);

        // Second invocation on the same session must not be poisoned.
        CountingClient client2 = new CountingClient(toolName);
        agent.setLlm(new Model(client2));
        Map<String, Object> inputs2 = Map.of(
                "query", "again",
                "conversation_id", inputs1.get("conversation_id"));
        Iterator<Object> iterator2 = agent.stream(inputs2, session, List.of());
        Thread.sleep(SETTLE_MS);
        assertThat(client2.calls.get())
                .as("session must not be poisoned: second invocation should call the model at least once")
                .isGreaterThanOrEqualTo(1);
    }

    private record CancelResult(int modelCalls, List<Object> drainedChunks, AgentSession session) {
        String lastResultType() {
            for (int i = drainedChunks.size() - 1; i >= 0; i--) {
                Object chunk = drainedChunks.get(i);
                if (chunk instanceof OutputSchema outputSchema) {
                    Object payload = outputSchema.getPayload();
                    if (payload instanceof Map<?, ?> map) {
                        Object rt = map.get("result_type");
                        if (rt != null) {
                            return String.valueOf(rt);
                        }
                    }
                }
            }
            return null;
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class BlockingClient implements Model.ModelClient {
        private final AtomicInteger streamCalls = new AtomicInteger();
        private final AtomicInteger invokeCalls = new AtomicInteger();
        private final AtomicInteger readAttempts = new AtomicInteger();
        private final CountDownLatch readBlocked = new CountDownLatch(1);
        private final CountDownLatch iteratorClosed = new CountDownLatch(1);
        private final CountDownLatch streamEntered = new CountDownLatch(1);
        private final CountDownLatch streamReturn = new CountDownLatch(1);
        private final CountDownLatch reuseCalled = new CountDownLatch(1);
        private final boolean delayIteratorReturn;
        private volatile BlockingIterator currentIterator;

        private BlockingClient(boolean delayIteratorReturn) {
            this.delayIteratorReturn = delayIteratorReturn;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCalls.incrementAndGet();
            return CompletableFuture.completedFuture(textMessage("non-stream fallback"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            if (streamCalls.incrementAndGet() > 1) {
                reuseCalled.countDown();
                return List.of(chunk("reuse-complete", null, "stop")).iterator();
            }
            if (delayIteratorReturn) {
                streamEntered.countDown();
                awaitUninterruptibly(streamReturn);
            }
            currentIterator = new BlockingIterator();
            return currentIterator;
        }

        private void allowIteratorReturn() {
            streamReturn.countDown();
        }

        private void closeCurrentIterator() {
            BlockingIterator iterator = currentIterator;
            if (iterator != null) {
                iterator.close();
            }
        }

        private final class BlockingIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
            private final CountDownLatch closeSignal = new CountDownLatch(1);
            private volatile boolean closed;
            private int position;

            @Override
            public boolean hasNext() {
                if (position == 0) {
                    return true;
                }
                if (position == 1) {
                    readAttempts.incrementAndGet();
                    readBlocked.countDown();
                    return awaitClose();
                }
                return position < 3;
            }

            @Override
            public AssistantMessageChunk next() {
                if (position == 0) {
                    position++;
                    return chunk("partial-token", null, null);
                }
                if (position == 1) {
                    position++;
                    return chunk("", toolCall("blocked_tool"), "tool_calls");
                }
                if (position == 2) {
                    position++;
                    return chunk("uncancelled-final", null, "stop");
                }
                throw new NoSuchElementException();
            }

            @Override
            public void close() {
                closed = true;
                closeSignal.countDown();
                iteratorClosed.countDown();
            }

            private boolean awaitClose() {
                try {
                    closeSignal.await();
                    return !closed;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    private static final class InterruptSensitiveClient implements Model.ModelClient {
        private final AtomicInteger streamCalls = new AtomicInteger();
        private final AtomicInteger invokeCalls = new AtomicInteger();
        private final CountDownLatch readBlocked = new CountDownLatch(1);
        private final CountDownLatch interruptionObserved = new CountDownLatch(1);
        private final CountDownLatch closeCalled = new CountDownLatch(1);
        private final CountDownLatch iteratorClosed = new CountDownLatch(1);
        private final CountDownLatch blockRead = new CountDownLatch(1);

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCalls.incrementAndGet();
            return CompletableFuture.completedFuture(textMessage("non-stream fallback"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            streamCalls.incrementAndGet();
            return new InterruptSensitiveIterator();
        }

        private void releaseClose() {
            interruptionObserved.countDown();
        }

        private final class InterruptSensitiveIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
            private int position;

            @Override
            public boolean hasNext() {
                if (position == 0) {
                    return true;
                }
                readBlocked.countDown();
                try {
                    blockRead.await();
                    return false;
                } catch (InterruptedException exception) {
                    interruptionObserved.countDown();
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            @Override
            public AssistantMessageChunk next() {
                if (position != 0) {
                    throw new NoSuchElementException();
                }
                position++;
                return chunk("partial-token", null, null);
            }

            @Override
            public void close() {
                closeCalled.countDown();
                awaitUninterruptibly(interruptionObserved);
                iteratorClosed.countDown();
            }
        }
    }

    private static AssistantMessageChunk chunk(String content, List<ToolCall> toolCalls, String finishReason) {
        return AssistantMessageChunk.builder()
                .content(content)
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .build();
    }

    private static List<ToolCall> toolCall(String toolName) {
        return List.of(ToolCall.builder()
                .id("blocked_call")
                .name(toolName)
                .arguments("{}")
                .index(0)
                .build());
    }

    private static LocalFunction countingTool(String name, AtomicInteger executions) {
        ToolCard card = ToolCard.builder()
                .id(name)
                .name(name)
                .description(name)
                .inputParams(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .build();
        LocalFunction.ContextFunction function = (inputs, kwargs) -> {
            executions.incrementAndGet();
            return "unexpected tool result";
        };
        return new LocalFunction(card, function);
    }

    private static AgentSession newSession(ReActAgent agent, String conversationId) {
        return AgentSession.createAgentSession(conversationId, null, agent.getCard());
    }

    private static Object readOne(Iterator<Object> iterator) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            assertThat(iterator.hasNext()).isTrue();
            return iterator.next();
        }).get(10, TimeUnit.SECONDS);
    }

    private static CompletableFuture<List<Object>> drainAsync(Iterator<Object> iterator) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object> chunks = new ArrayList<>();
            iterator.forEachRemaining(chunks::add);
            return chunks;
        });
    }

    private static List<Object> drain(Iterator<Object> iterator) throws Exception {
        return drainAsync(iterator).get(10, TimeUnit.SECONDS);
    }

    private static String resultType(List<Object> chunks) {
        return new CancelResult(0, chunks, null).lastResultType();
    }

    private static void closeStream(Iterator<Object> iterator) throws Exception {
        if (iterator instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Test
    void requestCancelClosesBlockedModelStreamAndAllowsSessionReuse() throws Exception {
        BlockingClient client = new BlockingClient(false);
        ReActAgent agent = newAgent("blocking_cancel_" + UUID.randomUUID(), client);
        AtomicInteger toolExecutions = new AtomicInteger();
        registerTool(agent, countingTool("blocked_tool", toolExecutions));
        String conversationId = "blocked-cancel-" + UUID.randomUUID();
        AgentSession session = newSession(agent, conversationId);
        Iterator<Object> iterator = agent.stream(Map.of("query", "go"), session, List.of());
        try {
            Object partial = readOne(iterator);
            assertThat(partial).isInstanceOf(OutputSchema.class);
            assertThat(((OutputSchema) partial).getPayload())
                    .isEqualTo(Map.of("content", "partial-token", "result_type", "answer"));
            assertThat(client.readBlocked.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<List<Object>> drained = drainAsync(iterator);
            ReActAgent.requestCancel(session);
            assertThat(client.iteratorClosed.await(5, TimeUnit.SECONDS))
                    .as("requestCancel must close the blocked model iterator")
                    .isTrue();
            List<Object> cancelledChunks = drained.get(10, TimeUnit.SECONDS);
            assertThat(resultType(cancelledChunks)).isEqualTo("cancelled");
            assertThat(client.streamCalls.get()).isEqualTo(1);
            assertThat(client.invokeCalls.get()).isZero();
            assertThat(toolExecutions.get()).isZero();

            Iterator<Object> reused = agent.stream(Map.of("query", "again"), session, List.of());
            try {
                assertThat(client.reuseCalled.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(client.streamCalls.get()).isEqualTo(2);
                assertThat(client.invokeCalls.get()).isZero();
            } finally {
                closeStream(reused);
            }
        } finally {
            client.closeCurrentIterator();
            closeStream(iterator);
        }
    }

    @Test
    void requestCancelBeforeIteratorRegistrationClosesWithoutReading() throws Exception {
        BlockingClient client = new BlockingClient(true);
        ReActAgent agent = newAgent("registration_cancel_" + UUID.randomUUID(), client);
        AtomicInteger toolExecutions = new AtomicInteger();
        registerTool(agent, countingTool("blocked_tool", toolExecutions));
        AgentSession session = newSession(agent, "registration-cancel-" + UUID.randomUUID());
        Iterator<Object> iterator = agent.stream(Map.of("query", "go"), session, List.of());
        try {
            assertThat(client.streamEntered.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<List<Object>> drained = drainAsync(iterator);
            ReActAgent.requestCancel(session);
            client.allowIteratorReturn();
            assertThat(client.iteratorClosed.await(5, TimeUnit.SECONDS))
                    .as("an iterator returned after cancellation must be closed on registration")
                    .isTrue();
            assertThat(resultType(drained.get(10, TimeUnit.SECONDS))).isEqualTo("cancelled");
            assertThat(client.readAttempts.get()).isZero();
            assertThat(client.streamCalls.get()).isEqualTo(1);
            assertThat(client.invokeCalls.get()).isZero();
            assertThat(toolExecutions.get()).isZero();
        } finally {
            client.allowIteratorReturn();
            client.closeCurrentIterator();
            closeStream(iterator);
        }
    }

    private static FutureTask<?> cancellationFuture(Runnable task, Runnable cleanup) throws Exception {
        Class<?> futureType = null;
        for (Class<?> nestedType : ReActAgent.class.getDeclaredClasses()) {
            if ("CancellationFuture".equals(nestedType.getSimpleName())) {
                futureType = nestedType;
            }
        }
        if (futureType == null) {
            throw new AssertionError("Cancellation-aware worker future is missing");
        }
        Constructor<?> constructor = futureType.getDeclaredConstructor(Runnable.class, Runnable.class);
        constructor.setAccessible(true);
        Object instance = constructor.newInstance(task, cleanup);
        if (instance instanceof FutureTask<?> future) {
            return future;
        }
        throw new AssertionError("Cancellation worker must be a FutureTask");
    }

    @Test
    void cancellationBeforeWorkerStartRunsRegistrationCleanup() throws Exception {
        AtomicInteger taskRuns = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        FutureTask<?> future = cancellationFuture(taskRuns::incrementAndGet, cleanups::incrementAndGet);

        assertThat(future.cancel(true)).isTrue();
        future.run();

        assertThat(taskRuns.get()).isZero();
        assertThat(cleanups.get()).isOne();
    }

    @Test
    void cancellationDuringWorkerRunDefersRegistrationCleanup() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        CountDownLatch cleaned = new CountDownLatch(1);
        Phaser release = new Phaser(2);
        FutureTask<?> future = cancellationFuture(() -> {
            entered.countDown();
            try {
                release.arriveAndAwaitAdvance();
            } finally {
                exited.countDown();
            }
        }, cleaned::countDown);
        CompletableFuture.runAsync(future);
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(future.cancel(true)).isTrue();
            assertThat(cleaned.getCount()).isOne();
        } finally {
            release.arrive();
        }
        assertThat(exited.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(cleaned.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void requestCancelInterruptsWorkerBeforeClosingInterruptSensitiveIterator() throws Exception {
        InterruptSensitiveClient client = new InterruptSensitiveClient();
        ReActAgent agent = newAgent("interrupt_cancel_" + UUID.randomUUID(), client);
        AgentSession session = newSession(agent, "interrupt-cancel-" + UUID.randomUUID());
        Iterator<Object> iterator = agent.stream(Map.of("query", "go"), session, List.of());
        CompletableFuture<Void> cancellation = CompletableFuture.completedFuture(null);
        assertThat(cancellation).as("an unsubmitted cancellation must not block cleanup").isDone();
        try {
            Object partial = readOne(iterator);
            assertThat(((OutputSchema) partial).getPayload())
                    .isEqualTo(Map.of("content", "partial-token", "result_type", "answer"));
            assertThat(client.readBlocked.await(10, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<List<Object>> drained = drainAsync(iterator);
            cancellation = CompletableFuture.runAsync(() -> ReActAgent.requestCancel(session));
            assertThat(client.closeCalled.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(client.interruptionObserved.await(1, TimeUnit.SECONDS))
                    .as("the worker must be interrupted before iterator close waits for it")
                    .isTrue();
            cancellation.get(5, TimeUnit.SECONDS);
            assertThat(client.iteratorClosed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(resultType(drained.get(10, TimeUnit.SECONDS))).isEqualTo("cancelled");
            assertThat(client.streamCalls.get()).isEqualTo(1);
            assertThat(client.invokeCalls.get()).isZero();
        } finally {
            client.releaseClose();
            cancellation.get(5, TimeUnit.SECONDS);
            closeStream(iterator);
        }
    }
}
