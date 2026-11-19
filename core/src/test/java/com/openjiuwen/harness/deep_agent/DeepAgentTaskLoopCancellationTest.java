/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.step.StepCheckpointPolicy;
import com.openjiuwen.core.session.checkpointer.step.StepCursor;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.schema.task.TaskPlan;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies task-loop cancellation through the real DeepAgent and ReActAgent streaming path.
 */
class DeepAgentTaskLoopCancellationTest {
    private static final long WAIT_SECONDS = 10;
    private static final long CLOSE_WAIT_SECONDS = 2;

    @TempDir
    private Path tempDir;

    private final List<String> sessionIds = new ArrayList<>();
    private final List<Iterator<Object>> streams = new ArrayList<>();
    private DeepAgent agent;
    private ScriptedClient client;

    @AfterEach
    void cleanup() throws Exception {
        if (client != null) {
            client.closeAll();
        }
        for (Iterator<Object> stream : streams) {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
        if (agent != null) {
            agent.close();
        }
        for (String sessionId : sessionIds) {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    @Test
    void scopedAbortCancelsRoundWithoutCompletionOrPartialHistory() throws Exception {
        String sessionId = newSessionId("single");
        AtomicInteger toolExecutions = new AtomicInteger();
        client = new ScriptedClient("blocked_tool", sessionId);
        agent = newAgent(client, toolExecutions);
        DeepAgentSession session = new DeepAgentSession(sessionId, null, agent.getCard());
        seedPendingPlan(session);
        Iterator<Object> stream = startStream(sessionId, "start-" + sessionId, session);

        RoundObservation cancelled = cancelActiveRound(sessionId, stream);
        StepCursor completedStep = completedStep(sessionId);
        TodoStatus planStatus = agent.loadState(session).getTaskPlan().getTask("task-1").getStatus();
        int toolsAfterCancel = toolExecutions.get();
        Iterator<Object> followUp = startStream(sessionId, "follow-up-" + sessionId, null);
        List<Object> followUpChunks = drain(followUp);
        List<String> followUpAssistantMessages = client.followUpAssistantContents(sessionId);
        Map<String, Object> outerResult = topLevelResult(cancelled.chunks);

        assertAll(
                () -> assertThat(cancelled.transportClosed).as("blocked model transport closes").isTrue(),
                () -> assertThat(lastResultType(cancelled.chunks)).as("outer=%s", outerResult)
                        .isEqualTo("cancelled"),
                () -> assertThat(finalRoundStatus(cancelled.chunks)).isEqualTo("cancelled"),
                () -> assertThat(planStatus).isNotEqualTo(TodoStatus.COMPLETED),
                () -> assertThat(completedStep).isNull(),
                () -> assertThat(cancelled.modelCalls).isEqualTo(1),
                () -> assertThat(cancelled.invokeCalls).isZero(),
                () -> assertThat(toolsAfterCancel).isZero(),
                () -> assertThat(lastResultType(followUpChunks)).isEqualTo("answer"),
                () -> assertThat(client.streamCalls(sessionId)).isEqualTo(2),
                () -> assertThat(followUpAssistantMessages)
                        .noneMatch(content -> content.contains(partialText(sessionId))));
    }

    @Test
    void scopedAbortLeavesOtherSessionRunningUntilItsOwnAbort() throws Exception {
        String firstSession = newSessionId("first");
        String secondSession = newSessionId("second");
        AtomicInteger toolExecutions = new AtomicInteger();
        client = new ScriptedClient("blocked_tool", firstSession, secondSession);
        agent = newAgent(client, toolExecutions);
        Iterator<Object> first = startStream(firstSession, "start-" + firstSession, null);
        Iterator<Object> second = startStream(secondSession, "start-" + secondSession, null);
        assertThat(readOne(first)).satisfies(chunk -> assertChunkContains(chunk, partialText(firstSession)));
        assertThat(readOne(second)).satisfies(chunk -> assertChunkContains(chunk, partialText(secondSession)));
        assertThat(client.iterator(firstSession).awaitBlocked()).isTrue();
        assertThat(client.iterator(secondSession).awaitBlocked()).isTrue();
        CompletableFuture<List<Object>> firstDrained = drainAsync(first);
        CompletableFuture<List<Object>> secondDrained = drainAsync(second);

        agent.requestAbort(firstSession);
        boolean firstClosed = client.iterator(firstSession).awaitClosed();
        if (!firstClosed) {
            client.iterator(firstSession).close();
        }
        List<Object> firstChunks = firstDrained.get(WAIT_SECONDS, TimeUnit.SECONDS);
        boolean secondClosedAfterFirstAbort = client.iterator(secondSession).isClosed();
        boolean secondDoneAfterFirstAbort = secondDrained.isDone();
        agent.requestAbort(secondSession);
        boolean secondClosed = client.iterator(secondSession).awaitClosed();
        if (!secondClosed) {
            client.iterator(secondSession).close();
        }
        List<Object> secondChunks = secondDrained.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertAll(
                () -> assertThat(firstClosed).isTrue(),
                () -> assertThat(lastResultType(firstChunks)).isEqualTo("cancelled"),
                () -> assertThat(secondClosedAfterFirstAbort).isFalse(),
                () -> assertThat(secondDoneAfterFirstAbort).isFalse(),
                () -> assertThat(secondClosed).isTrue(),
                () -> assertThat(lastResultType(secondChunks)).isEqualTo("cancelled"),
                () -> assertThat(client.streamCalls(firstSession)).isEqualTo(1),
                () -> assertThat(client.streamCalls(secondSession)).isEqualTo(1),
                () -> assertThat(toolExecutions.get()).isZero());
    }

    private DeepAgent newAgent(ScriptedClient modelClient, AtomicInteger toolExecutions) {
        AgentCard card = uniqueCard();
        DeepAgent created = HarnessFactory.createDeepAgent(card, DeepAgentConfig.builder()
                .workspacePath(tempDir.toString())
                .enableTaskLoop(true)
                .maxIterations(1)
                .completionTimeout(5.0)
                .stepCheckpointPolicy(StepCheckpointPolicy.STEP_ALWAYS)
                .build(), null);
        created.getAgent().setLlm(new Model(modelClient));
        created.registerHarnessTool(countingTool(modelClient.toolName, toolExecutions));
        return created;
    }

    private Iterator<Object> startStream(String sessionId, String query, DeepAgentSession session) {
        Iterator<Object> stream = agent.stream(Map.of("query", query, "conversation_id", sessionId),
                session, List.of(StreamMode.OUTPUT));
        streams.add(stream);
        return stream;
    }

    private RoundObservation cancelActiveRound(String sessionId, Iterator<Object> stream) throws Exception {
        assertThat(readOne(stream)).satisfies(chunk -> assertChunkContains(chunk, partialText(sessionId)));
        BlockingIterator iterator = client.iterator(sessionId);
        assertThat(iterator.awaitBlocked()).isTrue();
        assertThat(iterator.hasDeliveredToolCall())
                .as("the model must request the tool before cancellation blocks the stream")
                .isTrue();
        CompletableFuture<List<Object>> drained = drainAsync(stream);
        agent.requestAbort(sessionId);
        boolean closed = iterator.awaitClosed();
        if (!closed) {
            iterator.close();
        }
        List<Object> chunks = drained.get(WAIT_SECONDS, TimeUnit.SECONDS);
        return new RoundObservation(chunks, closed, client.streamCalls(sessionId), client.invokeCalls());
    }

    private void seedPendingPlan(DeepAgentSession session) {
        TaskPlan plan = new TaskPlan("cancellation", List.of(new TodoItem("task-1", "run")));
        agent.saveState(session, new DeepAgentState(0, plan, null, List.of(), null));
    }

    private StepCursor completedStep(String sessionId) {
        DeepAgentSession probe = new DeepAgentSession(sessionId, null, agent.getCard());
        probe.preRun(Map.of());
        return CheckpointerFactory.getCheckpointer().lastCompletedStep(probe.getInner());
    }

    private String newSessionId(String prefix) {
        String sessionId = prefix + "-" + UUID.randomUUID();
        sessionIds.add(sessionId);
        return sessionId;
    }

    private static AgentCard uniqueCard() {
        String id = "cancel-" + UUID.randomUUID().toString().replace("-", "");
        return AgentCard.builder().id(id).name(id).description("task loop cancellation").build();
    }

    private static LocalFunction countingTool(String name, AtomicInteger executions) {
        ToolCard card = ToolCard.builder().id(name).name(name).description(name)
                .inputParams(Map.of("type", "object", "properties", Map.of(), "required", List.of())).build();
        LocalFunction.ContextFunction function = (inputs, kwargs) -> {
            executions.incrementAndGet();
            return "unexpected tool result";
        };
        return new LocalFunction(card, function);
    }

    private static Object readOne(Iterator<Object> iterator) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            assertThat(iterator.hasNext()).isTrue();
            return iterator.next();
        }).get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static CompletableFuture<List<Object>> drainAsync(Iterator<Object> iterator) {
        return CompletableFuture.supplyAsync(() -> {
            List<Object> chunks = new ArrayList<>();
            iterator.forEachRemaining(chunks::add);
            return chunks;
        });
    }

    private static List<Object> drain(Iterator<Object> iterator) throws Exception {
        return drainAsync(iterator).get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static String lastResultType(List<Object> chunks) {
        for (int index = chunks.size() - 1; index >= 0; index--) {
            if (chunks.get(index) instanceof OutputSchema schema
                    && schema.getPayload() instanceof Map<?, ?> payload
                    && payload.get("result_type") != null) {
                return String.valueOf(payload.get("result_type"));
            }
        }
        return "";
    }

    private static String finalRoundStatus(List<Object> chunks) {
        Map<String, Object> result = topLevelResult(chunks);
        if (result.get("final_result") instanceof Map<?, ?> finalRound) {
            return String.valueOf(finalRound.get("status"));
        }
        return "";
    }

    private static Map<String, Object> topLevelResult(List<Object> chunks) {
        for (int index = chunks.size() - 1; index >= 0; index--) {
            if (chunks.get(index) instanceof OutputSchema schema
                    && schema.getPayload() instanceof Map<?, ?> payload
                    && payload.get("output") instanceof Map<?, ?> output) {
                Map<String, Object> result = stringKeyedMap(output);
                if ("deep_agent_result".equals(result.get("type"))) {
                    return result;
                }
            }
        }
        return Map.of();
    }

    private static Map<String, Object> stringKeyedMap(Map<?, ?> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static void assertChunkContains(Object chunk, String expected) {
        assertThat(chunk).isInstanceOf(OutputSchema.class);
        Object payload = ((OutputSchema) chunk).getPayload();
        assertThat(payload).isInstanceOf(Map.class);
        Map<?, ?> values = (Map<?, ?>) payload;
        assertThat(String.valueOf(values.get("content")) + String.valueOf(values.get("output")))
                .contains(expected);
    }

    private static String partialText(String sessionId) {
        return "partial-" + sessionId;
    }

    private record RoundObservation(List<Object> chunks, boolean transportClosed, int modelCalls, int invokeCalls) {
    }

    private static final class ScriptedClient implements Model.ModelClient {
        private final String toolName;
        private final List<String> sessionIds;
        private final Map<String, BlockingIterator> iterators = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> streamCalls = new ConcurrentHashMap<>();
        private final Map<String, List<BaseMessage>> followUpMessages = new ConcurrentHashMap<>();
        private final AtomicInteger invokeCalls = new AtomicInteger();

        private ScriptedClient(String toolName, String... sessionIds) {
            this.toolName = toolName;
            this.sessionIds = List.of(sessionIds);
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeCalls.incrementAndGet();
            return CompletableFuture.completedFuture(AssistantMessage.builder().content("unexpected invoke").build());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            String sessionId = sessionId(messages);
            int call = streamCalls.computeIfAbsent(sessionId, ignored -> new AtomicInteger()).incrementAndGet();
            if (call > 1) {
                followUpMessages.put(sessionId, List.copyOf(messages));
                return List.of(chunk("complete-" + sessionId, null, "stop")).iterator();
            }
            BlockingIterator iterator = new BlockingIterator(sessionId);
            iterators.put(sessionId, iterator);
            return iterator;
        }

        private String sessionId(List<BaseMessage> messages) {
            for (String candidate : sessionIds) {
                if (messages.stream().map(BaseMessage::getContent).map(String::valueOf)
                        .anyMatch(content -> content.contains(candidate))) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("No scripted session found in model messages");
        }

        private BlockingIterator iterator(String sessionId) {
            BlockingIterator iterator = iterators.get(sessionId);
            if (iterator == null) {
                throw new IllegalStateException("Model iterator has not been created for " + sessionId);
            }
            return iterator;
        }

        private int streamCalls(String sessionId) {
            return streamCalls.getOrDefault(sessionId, new AtomicInteger()).get();
        }

        private int invokeCalls() {
            return invokeCalls.get();
        }

        private List<String> followUpAssistantContents(String sessionId) {
            return followUpMessages.getOrDefault(sessionId, List.of()).stream()
                    .filter(AssistantMessage.class::isInstance)
                    .map(AssistantMessage.class::cast)
                    .map(message -> String.valueOf(message.getContent()))
                    .toList();
        }

        private void closeAll() {
            iterators.values().forEach(BlockingIterator::close);
        }
    }

    private static final class BlockingIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final String sessionId;
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch closeSignal = new CountDownLatch(1);
        private volatile boolean closed;
        private int position;

        private BlockingIterator(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public boolean hasNext() {
            if (closed) {
                return false;
            }
            if (position < 2) {
                return true;
            }
            blocked.countDown();
            awaitClose();
            return false;
        }

        @Override
        public AssistantMessageChunk next() {
            if (closed || position >= 2) {
                throw new NoSuchElementException();
            }
            position++;
            if (position == 1) {
                return chunk(partialText(sessionId), null, null);
            }
            return chunk("", List.of(ToolCall.builder().id("call-" + sessionId)
                    .name("blocked_tool").arguments("{}").index(0).build()), "tool_calls");
        }

        @Override
        public void close() {
            closed = true;
            closeSignal.countDown();
        }

        private boolean awaitBlocked() throws InterruptedException {
            return blocked.await(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        private boolean awaitClosed() throws InterruptedException {
            return closeSignal.await(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS);
        }

        private boolean isClosed() {
            return closed;
        }

        private boolean hasDeliveredToolCall() {
            return position >= 2;
        }

        private void awaitClose() {
            try {
                closeSignal.await();
            } catch (InterruptedException ignored) {
                close();
            }
        }
    }

    private static AssistantMessageChunk chunk(String content, List<ToolCall> toolCalls, String finishReason) {
        return AssistantMessageChunk.builder().content(content).toolCalls(toolCalls).finishReason(finishReason).build();
    }
}
