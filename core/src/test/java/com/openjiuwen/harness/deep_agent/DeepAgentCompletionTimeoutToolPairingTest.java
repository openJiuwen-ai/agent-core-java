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
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies through the public stream API that a task-loop completion timeout
 * cannot leave a running tool or an unanswered tool call behind.
 * The event log records the exact wire order.
 */
class DeepAgentCompletionTimeoutToolPairingTest {

    private static final long DRAIN_TIMEOUT_SECONDS = 15;
    private static final long TOOL_WAIT_SECONDS = 30;
    private static final double COMPLETION_TIMEOUT_SECONDS = 2.0;

    @TempDir
    private Path tempDir;

    @Test
    @Timeout(40)
    void terminalAnswerMustNotLeaveRunningToolWithoutToolOutput() throws Exception {
        String sessionId = "timeout-pairing-" + UUID.randomUUID();
        CountDownLatch toolStarted = new CountDownLatch(1);
        CountDownLatch toolRelease = new CountDownLatch(1);
        AtomicBoolean toolFinished = new AtomicBoolean(false);
        AtomicInteger streamCalls = new AtomicInteger();

        DeepAgent agent = null;
        Iterator<Object> stream = null;
        try {
            AgentCard card = AgentCard.builder()
                    .id("timeout-pairing-" + UUID.randomUUID().toString().replace("-", ""))
                    .name("timeout-pairing")
                    .description("completion timeout tool pairing probe")
                    .build();
            agent = HarnessFactory.createDeepAgent(card, DeepAgentConfig.builder()
                    .workspacePath(tempDir.toString())
                    .enableTaskLoop(true)
                    .enableTaskPlanning(false)
                    .maxIterations(1)
                    .completionTimeout(COMPLETION_TIMEOUT_SECONDS)
                    .build(), null);
            LocalFunction blockingTool = blockingTool(toolStarted, toolRelease, toolFinished);
            agent.getAgent().setLlm(new Model(new ScriptedClient(streamCalls, blockingTool.getCard().getName())));
            agent.registerHarnessTool(blockingTool);

            DeepAgentSession session = new DeepAgentSession(sessionId, null, agent.getCard());
            stream = agent.stream(Map.of("query", "start", "conversation_id", sessionId),
                    session, List.of(StreamMode.OUTPUT));

            Iterator<Object> activeStream = stream;
            CompletableFuture<List<Object>> drained = CompletableFuture.supplyAsync(() -> {
                List<Object> chunks = new ArrayList<>();
                activeStream.forEachRemaining(chunks::add);
                return chunks;
            });

            assertThat(toolStarted.await(10, TimeUnit.SECONDS))
                    .as("the real tool must start before the outer terminal is asserted")
                    .isTrue();
            List<Object> chunks = drained.get(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<OutputSchema> schemas = chunks.stream()
                    .filter(OutputSchema.class::isInstance)
                    .map(OutputSchema.class::cast)
                    .toList();
            String eventLog = describeEvents(schemas);
            System.out.println("TIMEOUT_PAIRING_EVENTS " + eventLog);

            List<String> eventTypes = schemas.stream().map(OutputSchema::getType).toList();
            Set<String> toolCallIds = idsOf(schemas, "tool_call");
            Set<String> toolOutputIds = idsOf(schemas, "tool_output");
            boolean terminalAnswerSeen = schemas.stream()
                    .anyMatch(DeepAgentCompletionTimeoutToolPairingTest::isTopLevelAnswer);

            assertThat(eventTypes)
                    .as("event log: %s", eventLog)
                    .contains("tool_call");
            assertThat(terminalAnswerSeen)
                    .as("event log: %s", eventLog)
                    .isTrue();

            // Contract under test: when the terminal answer is emitted, the tool triggered by
            // the preceding tool_call must have finished, and every tool_call must be paired
            // with a tool_output. This is the desired observable contract regardless of whether
            // the terminal is a normal answer, an error, or a cancellation.
            assertAll(
                    () -> assertThat(toolFinished.get())
                            .as("no running tool may be left behind when the terminal answer is emitted; "
                                    + "event log: %s", eventLog)
                            .isTrue(),
                    () -> assertThat(toolOutputIds)
                            .as("every tool_call must have a matching tool_output before the terminal answer; "
                                    + "event log: %s", eventLog)
                            .containsExactlyInAnyOrderElementsOf(toolCallIds)
            );
        } finally {
            toolRelease.countDown();
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
            if (agent != null) {
                agent.shutdown();
            }
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    @Test
    @Timeout(20)
    void timeoutBeforeInnerStreamRegistrationStillCancelsItsModelRead() throws Exception {
        String sessionId = "timeout-registration-" + UUID.randomUUID();
        CountDownLatch streamEntered = new CountDownLatch(1);
        CountDownLatch allowStream = new CountDownLatch(1);
        AtomicInteger streamCalls = new AtomicInteger();
        DeepAgent agent = HarnessFactory.createDeepAgent(AgentCard.builder()
                        .id("timeout-registration-" + UUID.randomUUID().toString().replace("-", ""))
                        .name("timeout-registration")
                        .description("completion timeout before stream registration")
                        .build(), DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .enableTaskLoop(true)
                        .enableTaskPlanning(false)
                        .maxIterations(1)
                        .completionTimeout(1.0)
                        .build(), null);
        agent.getAgent().setLlm(new Model(new ScriptedClient(streamCalls)));
        ReActAgent delayed = new ReActAgent(agent.getAgent().getCard()) {
            @Override
            public Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.AgentSessionApi session,
                    List<StreamMode> modes) {
                streamEntered.countDown();
                try {
                    allowStream.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return super.stream(inputs, session, modes);
            }
        };
        delayed.setLlm(new Model(new ScriptedClient(streamCalls)));
        agent.setReactAgent(delayed, true);
        DeepAgentSession session = new DeepAgentSession(sessionId, null, agent.getCard());
        Iterator<Object> stream = agent.stream(Map.of("query", "start", "conversation_id", sessionId),
                session, List.of(StreamMode.OUTPUT));
        try {
            assertThat(streamEntered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitCancellationDeadline(agent, sessionId)).isTrue();
            allowStream.countDown();
            List<Object> chunks = new ArrayList<>();
            stream.forEachRemaining(chunks::add);
            assertThat(streamCalls.get()).as("the late stream must observe the pending cancellation").isZero();
            assertThat(chunks).isNotEmpty();
        } finally {
            allowStream.countDown();
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
            agent.shutdown();
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    @Test
    @Timeout(20)
    void userAbortWaitsForToolOutcomeBeforeTerminal() throws Exception {
        String sessionId = "abort-pairing-" + UUID.randomUUID();
        CountDownLatch toolStarted = new CountDownLatch(1);
        CountDownLatch toolRelease = new CountDownLatch(1);
        AtomicBoolean toolFinished = new AtomicBoolean();
        AtomicInteger streamCalls = new AtomicInteger();
        DeepAgent agent = HarnessFactory.createDeepAgent(AgentCard.builder()
                        .id("abort-pairing-" + UUID.randomUUID().toString().replace("-", ""))
                        .name("abort-pairing")
                        .description("user abort tool pairing")
                        .build(), DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .enableTaskLoop(true)
                        .enableTaskPlanning(false)
                        .maxIterations(1)
                        .completionTimeout(30.0)
                        .build(), null);
        LocalFunction blockingTool = blockingTool(toolStarted, toolRelease, toolFinished);
        agent.getAgent().setLlm(new Model(new ScriptedClient(streamCalls, blockingTool.getCard().getName())));
        agent.registerHarnessTool(blockingTool);
        DeepAgentSession session = new DeepAgentSession(sessionId, null, agent.getCard());
        Iterator<Object> stream = agent.stream(Map.of("query", "start", "conversation_id", sessionId),
                session, List.of(StreamMode.OUTPUT));
        try {
            CompletableFuture<List<Object>> drained = CompletableFuture.supplyAsync(() -> {
                List<Object> chunks = new ArrayList<>();
                stream.forEachRemaining(chunks::add);
                return chunks;
            });
            assertThat(toolStarted.await(10, TimeUnit.SECONDS)).isTrue();
            agent.requestAbort(sessionId);
            List<Object> chunks = drained.get(10, TimeUnit.SECONDS);
            List<OutputSchema> schemas = chunks.stream()
                    .filter(OutputSchema.class::isInstance)
                    .map(OutputSchema.class::cast)
                    .toList();
            assertThat(toolFinished).isTrue();
            assertThat(idsOf(schemas, "tool_output")).containsExactlyInAnyOrderElementsOf(idsOf(schemas, "tool_call"));
        } finally {
            toolRelease.countDown();
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
            agent.shutdown();
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    private static boolean awaitCancellationDeadline(DeepAgent agent, String sessionId) throws Exception {
        Field field = DeepAgent.class.getDeclaredField("pendingInnerStreamCancellations");
        field.setAccessible(true);
        Set<?> pending = (Set<?>) field.get(agent);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (pending.contains(sessionId)) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private static LocalFunction blockingTool(CountDownLatch started, CountDownLatch release,
            AtomicBoolean finished) {
        String name = "blocking_tool_" + UUID.randomUUID().toString().replace("-", "");
        ToolCard card = ToolCard.builder()
                .id(name)
                .name(name)
                .description("starts, then blocks until released")
                .inputParams(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .build();
        LocalFunction.ContextFunction function = (inputs, kwargs) -> {
            started.countDown();
            try {
                release.await(TOOL_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                finished.set(true);
            }
            return "blocking-tool-finished";
        };
        return new LocalFunction(card, function);
    }

    private static Set<String> idsOf(List<OutputSchema> schemas, String type) {
        Set<String> ids = new LinkedHashSet<>();
        for (OutputSchema schema : schemas) {
            if (!type.equals(schema.getType()) || !(schema.getPayload() instanceof Map<?, ?> payload)) {
                continue;
            }
            Object id = payload.get("tool_call_id");
            if (id != null) {
                ids.add(String.valueOf(id));
            }
        }
        return ids;
    }

    private static boolean isTopLevelAnswer(OutputSchema schema) {
        if (!"answer".equals(schema.getType()) || !(schema.getPayload() instanceof Map<?, ?> payload)) {
            return false;
        }
        if (!"answer".equals(String.valueOf(payload.get("result_type")))) {
            return false;
        }
        return payload.get("output") instanceof Map<?, ?> output
                && "deep_agent_result".equals(String.valueOf(output.get("type")));
    }

    private static String describeEvents(List<OutputSchema> schemas) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < schemas.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            OutputSchema schema = schemas.get(index);
            builder.append(schema.getType());
            if (schema.getPayload() instanceof Map<?, ?> payload) {
                Object resultType = payload.get("result_type");
                if (resultType != null) {
                    builder.append("(result_type=").append(resultType).append(')');
                }
                Object toolCallId = payload.get("tool_call_id");
                if (toolCallId != null) {
                    builder.append('[').append(toolCallId).append(']');
                }
                if (payload.get("output") instanceof Map<?, ?> output) {
                    builder.append("{output.type=").append(output.get("type"));
                    Object finalResult = output.get("final_result");
                    if (finalResult instanceof Map<?, ?> finalMap) {
                        Object error = finalMap.get("error");
                        Object status = finalMap.get("status");
                        if (error != null) {
                            builder.append(", final_result.error=").append(error);
                        }
                        if (status != null) {
                            builder.append(", final_result.status=").append(status);
                        }
                    }
                    builder.append('}');
                }
            }
        }
        return builder.append(']').toString();
    }

    private static final class ScriptedClient implements Model.ModelClient {
        private final AtomicInteger streamCalls;
        private final String toolName;

        private ScriptedClient(AtomicInteger streamCalls, String toolName) {
            this.streamCalls = streamCalls;
            this.toolName = toolName;
        }

        private ScriptedClient(AtomicInteger streamCalls) {
            this(streamCalls, "unused_tool");
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(AssistantMessage.builder()
                    .content("unexpected non-stream invoke")
                    .build());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            int call = streamCalls.incrementAndGet();
            if (call == 1) {
                return List.of(
                        AssistantMessageChunk.builder().content("working").build(),
                        AssistantMessageChunk.builder()
                                .content("")
                                .toolCalls(List.of(ToolCall.builder()
                                        .id("call-blocking-probe")
                                        .name(toolName)
                                        .arguments("{}")
                                        .index(0)
                                        .build()))
                                .finishReason("tool_calls")
                                .build()
                ).iterator();
            }
            return List.of(AssistantMessageChunk.builder()
                    .content("done")
                    .finishReason("stop")
                    .build()).iterator();
        }
    }
}
