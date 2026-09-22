/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ReActAgent streaming empty-response retry and non-stream chunk wrapping.
 */
class ReActAgentStreamDegradationTest {

    @Test
    void isEmptyStreamResultDetectsEmptyContentAndNoTools() {
        assertThat(ReActAgent.isEmptyStreamResult(null)).isTrue();
        assertThat(ReActAgent.isEmptyStreamResult(
                AssistantMessage.builder().content("").toolCalls(List.of()).build())).isTrue();
        assertThat(ReActAgent.isEmptyStreamResult(
                AssistantMessage.builder().content("  ").toolCalls(List.of()).build())).isTrue();
        assertThat(ReActAgent.isEmptyStreamResult(
                AssistantMessage.builder().content("hi").toolCalls(List.of()).build())).isFalse();
        assertThat(ReActAgent.isEmptyStreamResult(
                AssistantMessage.builder().content("").toolCalls(List.of(
                        ToolCall.builder().id("1").name("t").arguments("{}").build()
                )).build())).isFalse();
    }

    @Test
    void writeNonStreamAsStreamChunksSplitsContentAndSendsToolCallsAfter() {
        ReActAgent agent = newAgent("chunk-wrap");
        AgentSessionApi session = mock(AgentSessionApi.class);
        List<OutputSchema> written = new ArrayList<>();
        doAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            if (arg instanceof OutputSchema schema) {
                written.add(schema);
            }
            return null;
        }).when(session).writeStream(any());

        AgentCallbackContext ctx = new AgentCallbackContext(agent);
        ctx.setSession(session);

        String content = "a".repeat(250);
        AssistantMessage message = AssistantMessage.builder()
                .content(content)
                .toolCalls(List.of(ToolCall.builder().id("tc1").name("echo").arguments("{}").build()))
                .build();

        agent.writeNonStreamAsStreamChunks(ctx, message, true);

        List<OutputSchema> llmOutputs = written.stream()
                .filter(o -> "llm_output".equals(o.getType()))
                .toList();
        assertThat(llmOutputs.size()).isGreaterThanOrEqualTo(3);

        StringBuilder streamed = new StringBuilder();
        int toolIndex = -1;
        for (int i = 0; i < llmOutputs.size(); i++) {
            Object payload = llmOutputs.get(i).getPayload();
            if (payload instanceof Map<?, ?> map && map.containsKey("tool_calls")) {
                toolIndex = i;
            } else if (payload instanceof Map<?, ?> map && map.get("content") != null) {
                streamed.append(map.get("content"));
            }
        }
        assertThat(streamed.toString()).isEqualTo(content);
        assertThat(toolIndex).isGreaterThan(0);
    }

    @Test
    void streamEmptyThenInvokeFallbackWritesLlmOutputChunks() throws Exception {
        ReActAgent agent = newAgent("empty-fallback");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(2)
                .streamMaxRetries(0)
                .streamRetryDelayMs(0)
                .build());

        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(Collections.emptyIterator());
        when(model.invoke(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        AssistantMessage.builder().content("non-stream content").build()));
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("fallback-session", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        verify(model, times(1)).stream(anyList(), any(ModelInvokeOptions.class));
        verify(model, times(1)).invoke(anyList(), any(ModelInvokeOptions.class));

        boolean hasLlmOutput = collected.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .anyMatch(o -> "llm_output".equals(o.getType())
                        && String.valueOf(o.getPayload()).contains("non-stream content"));
        assertThat(hasLlmOutput).isTrue();
    }

    @Test
    void streamRetryOnEmptyThenSucceedsWithoutInvoke() throws Exception {
        ReActAgent agent = newAgent("retry-then-ok");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(2)
                .streamMaxRetries(1)
                .streamRetryDelayMs(0)
                .build());

        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(Collections.emptyIterator())
                .thenReturn(List.of(
                        AssistantMessageChunk.builder().content("recovered content").build()
                ).iterator());
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("retry-session", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        verify(model, times(2)).stream(anyList(), any(ModelInvokeOptions.class));
        verify(model, never()).invoke(anyList(), any(ModelInvokeOptions.class));
        boolean hasContent = collected.stream()
                .anyMatch(item -> String.valueOf(item).contains("recovered content"));
        assertThat(hasContent).isTrue();
    }

    @Test
    void streamExceptionDoesNotRetryAndDoesNotInvoke() throws Exception {
        ReActAgent agent = newAgent("retries-exhausted");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(2)
                .streamMaxRetries(2)
                .streamRetryDelayMs(0)
                .build());

        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenThrow(new RuntimeException("connection timeout"));
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("exhausted-session", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        boolean hasErrorAnswer = collected.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .anyMatch(output -> "answer".equals(output.getType())
                        && String.valueOf(output.getPayload()).contains("connection timeout")
                        && String.valueOf(output.getPayload()).contains("result_type=error"));
        assertThat(hasErrorAnswer).isTrue();
        verify(model, never()).invoke(anyList(), any(ModelInvokeOptions.class));
        verify(model, times(1)).stream(anyList(), any(ModelInvokeOptions.class));
    }

    @Test
    void streamRetriesIoFailureBeforeFirstChunk() throws Exception {
        ReActAgent agent = newAgent("retry-transport-before-first-chunk");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(2)
                .streamMaxRetries(1)
                .streamRetryDelayMs(0)
                .build());
        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(failingStreamBeforeFirstChunk())
                .thenReturn(List.of(AssistantMessageChunk.builder()
                        .content("recovered after io")
                        .build()).iterator());
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("retry-transport-session", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        assertThat(collected).anyMatch(item -> String.valueOf(item).contains("recovered after io"));
        verify(model, times(2)).stream(anyList(), any(ModelInvokeOptions.class));
        verify(model, never()).invoke(anyList(), any(ModelInvokeOptions.class));
    }

    @Test
    void streamDoesNotRetryIoFailureAfterFirstChunk() throws Exception {
        ReActAgent agent = newAgent("no-retry-after-first-chunk");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(2)
                .streamMaxRetries(2)
                .streamRetryDelayMs(0)
                .build());
        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(failingStreamAfterChunk("partial content"));
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("partial-transport-session", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        assertThat(collected.stream()
                .filter(item -> String.valueOf(item).contains("partial content")))
                .hasSize(1);
        verify(model, times(1)).stream(anyList(), any(ModelInvokeOptions.class));
        verify(model, never()).invoke(anyList(), any(ModelInvokeOptions.class));
    }

    @Test
    void reasoningOnlyStreamFallsBackToNonStreamAnswerWithoutRetry() throws Exception {
        ReActAgent agent = newAgent("reasoning-only-fallback");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(2)
                .streamMaxRetries(2)
                .streamRetryDelayMs(0)
                .build());
        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(List.of(AssistantMessageChunk.builder()
                        .content("")
                        .reasoningContent("thinking")
                        .finishReason("stop")
                        .build()).iterator());
        when(model.invoke(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        AssistantMessage.builder().content("fallback answer").build()));
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("reasoning-only-session", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        assertThat(collected).anyMatch(item -> String.valueOf(item).contains("fallback answer"));
        verify(model, times(1)).stream(anyList(), any(ModelInvokeOptions.class));
        verify(model, times(1)).invoke(anyList(), any(ModelInvokeOptions.class));
    }

    @Test
    void normalStreamNotAffected() throws Exception {
        ReActAgent agent = newAgent("normal-stream");
        Model model = mock(Model.class);
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.buildKvCacheInvokeKwargs(any(), any(Boolean.class))).thenReturn(Map.of());
        when(model.stream(anyList(), any(ModelInvokeOptions.class)))
                .thenReturn(List.of(
                        AssistantMessageChunk.builder().content("Hello ").build(),
                        AssistantMessageChunk.builder().content("World").build()
                ).iterator());
        agent.setLlm(model);

        AgentSessionApi session = new AgentSession("normal-session", null, agent.getCard());
        List<Object> collected = new ArrayList<>();
        var iterator = agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            collected.add(iterator.next());
        }

        String allContent = collected.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .map(output -> String.valueOf(output.getPayload()))
                .reduce("", (a, b) -> a + b);
        assertThat(allContent).contains("Hello");
        assertThat(allContent).contains("World");
        verify(model, never()).invoke(anyList(), any(ModelInvokeOptions.class));
    }

    private static ReActAgent newAgent(String id) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(id)
                .name(id)
                .description(id)
                .build());
        agent.configure(ReActAgentConfig.builder().maxIterations(2).streamRetryDelayMs(0).build());
        return agent;
    }

    private static Iterator<AssistantMessageChunk> failingStreamBeforeFirstChunk() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                throw new UncheckedIOException(new IOException("connection reset before first chunk"));
            }

            @Override
            public AssistantMessageChunk next() {
                throw new NoSuchElementException();
            }
        };
    }

    private static Iterator<AssistantMessageChunk> failingStreamAfterChunk(String content) {
        return new Iterator<>() {
            private boolean emitted;

            @Override
            public boolean hasNext() {
                if (!emitted) {
                    return true;
                }
                throw new UncheckedIOException(new IOException("connection reset after first chunk"));
            }

            @Override
            public AssistantMessageChunk next() {
                if (emitted) {
                    throw new NoSuchElementException();
                }
                emitted = true;
                return AssistantMessageChunk.builder().content(content).build();
            }
        };
    }
}
