/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.single_agent.schema.AgentResult;
import com.openjiuwen.extensions.a2a.A2AClient.A2AClientTransport;
import com.openjiuwen.extensions.a2a.A2AClient.A2AEventStream;
import com.openjiuwen.extensions.a2a.A2AClient.CancelTaskRequest;
import com.openjiuwen.extensions.a2a.A2AClient.ClientConfig;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aMessage;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aPart;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTask;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskState;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskStatus;
import com.openjiuwen.extensions.a2a.A2ATransformer.SendMessageRequest;
import com.openjiuwen.extensions.a2a.A2ATransformer.StreamResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mirrors Python's {@code A2AClient} in
 * {@code openjiuwen/extensions/a2a/a2a_client.py}.
 */
class A2AClientTest {

    @Test
    void constructorPassesPollingAndCardToFactory() {
        Object card = new Object();
        RecordingFactory factory = new RecordingFactory();

        A2AClient client = new A2AClient(card, true, factory);

        assertSame(card, client.getCard());
        assertThat(client.isPolling()).isTrue();
        assertThat(factory.config.isPolling()).isTrue();
        assertSame(card, factory.card);
    }

    @Test
    void constructorWrapsFactoryFailureLikePythonRuntimeError() {
        assertThatThrownBy(() -> new A2AClient("card", false, (config, card) -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create A2A client: boom");
    }

    @Test
    void invokeReturnsFirstEventOnlyAndClosesStreamWithConversationId() {
        RecordingTransport transport = new RecordingTransport(List.of(
                response("task-1", "remote-1", "first"),
                response("task-2", "remote-2", "second")));
        A2AClient client = new A2AClient(null, false, (config, card) -> transport);

        AgentResult result = client.invoke(Map.of("query", "hello", "conversation_id", "local-session"))
                .toCompletableFuture()
                .join();

        assertThat(result.getTaskId()).isEqualTo("task-1");
        assertThat(result.getSessionId()).isEqualTo("local-session");
        assertThat(result.getArtifacts().getFirst().getParts().getFirst().getText()).isEqualTo("first");
        assertThat(transport.lastRequest.getMessage().getContextId()).isEqualTo("local-session");
        assertThat(transport.lastStream.closed).isTrue();
        assertThat(transport.lastStream.nextCount).isEqualTo(1);
    }

    @Test
    void invokeReturnsEmptyResultWhenStreamHasNoEventsAndPreservesSessionId() {
        RecordingTransport transport = new RecordingTransport(List.of());
        A2AClient client = new A2AClient(null, false, (config, card) -> transport);

        AgentResult result = client.invoke(Map.of("query", "hello", "sessionId", "session-fallback"))
                .toCompletableFuture()
                .join();

        assertThat(result.getTaskId()).isNull();
        assertThat(result.getSessionId()).isEqualTo("session-fallback");
        assertThat(transport.lastStream.closed).isTrue();
    }

    @Test
    void streamYieldsAllEventsAndClosesOnExhaustion() {
        RecordingTransport transport = new RecordingTransport(List.of(
                response("task-1", "remote-1", "first"),
                response("task-2", "remote-2", "second")));
        A2AClient client = new A2AClient(null, false, (config, card) -> transport);

        List<AgentResult> results = new ArrayList<>();
        for (AgentResult result : client.stream(Map.of("query", "hello", "sessionId", "stream-session"))) {
            results.add(result);
        }

        assertThat(results).hasSize(2);
        assertThat(results).extracting(AgentResult::getSessionId)
                .containsExactly("stream-session", "stream-session");
        assertThat(results.get(1).getArtifacts().getFirst().getParts().getFirst().getText()).isEqualTo("second");
        assertThat(transport.lastStream.closed).isTrue();
    }

    @Test
    void cancelTaskSetsTenantAndTransformsTaskResponse() {
        RecordingTransport transport = new RecordingTransport(List.of());
        A2AClient client = new A2AClient(null, false, (config, card) -> transport);

        AgentResult result = client.cancel_task("task-cancel", "tenant-a").toCompletableFuture().join();

        assertThat(transport.cancelRequest.getId()).isEqualTo("task-cancel");
        assertThat(transport.cancelRequest.getTenant()).isEqualTo("tenant-a");
        assertThat(result.getTaskId()).isEqualTo("task-cancel");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.CANCELED);
    }

    @Test
    void asyncContextExitStopsClient() {
        RecordingTransport transport = new RecordingTransport(List.of());
        A2AClient client = new A2AClient(null, false, (config, card) -> transport);

        assertSame(client, client.__aenter__().toCompletableFuture().join());
        client.__aexit__(null, null, null).toCompletableFuture().join();

        assertThat(transport.closed).isTrue();
    }

    private static StreamResponse response(String taskId, String contextId, String text) {
        A2aPart part = new A2aPart();
        part.setText(text);
        A2aMessage message = new A2aMessage();
        message.setTaskId(taskId);
        message.setContextId(contextId);
        message.setParts(List.of(part));
        StreamResponse response = new StreamResponse();
        response.setMessage(message);
        return response;
    }

    private static A2aTask canceledTask(String taskId) {
        A2aTask task = new A2aTask();
        task.setId(taskId);
        task.setStatus(new A2aTaskStatus(A2aTaskState.TASK_STATE_CANCELED));
        return task;
    }

    private static final class RecordingFactory implements A2AClient.A2AClientFactory {
        private ClientConfig config;
        private Object card;

        @Override
        public A2AClientTransport create(ClientConfig config, Object card) {
            this.config = config;
            this.card = card;
            return new RecordingTransport(List.of());
        }
    }

    private static final class RecordingTransport implements A2AClientTransport {
        private final List<?> events;
        private SendMessageRequest lastRequest;
        private RecordingStream lastStream;
        private CancelTaskRequest cancelRequest;
        private boolean closed;

        private RecordingTransport(List<?> events) {
            this.events = events;
        }

        @Override
        public A2AEventStream sendMessage(SendMessageRequest request) {
            lastRequest = request;
            lastStream = new RecordingStream(events);
            return lastStream;
        }

        @Override
        public CompletionStage<Object> cancelTask(CancelTaskRequest request) {
            cancelRequest = request;
            return CompletableFuture.completedFuture(canceledTask(request.getId()));
        }

        @Override
        public CompletionStage<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingStream implements A2AEventStream {
        private final Iterator<?> delegate;
        private boolean closed;
        private int nextCount;

        private RecordingStream(List<?> events) {
            this.delegate = events.iterator();
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Object next() {
            nextCount++;
            return delegate.next();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
