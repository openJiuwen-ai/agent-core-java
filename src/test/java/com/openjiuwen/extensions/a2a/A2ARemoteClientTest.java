/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientFactory;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import com.openjiuwen.extensions.a2a.A2AAgentCardAdapter.A2aAgentCard;
import com.openjiuwen.extensions.a2a.A2AAgentCardAdapter.AgentInterface;
import com.openjiuwen.extensions.a2a.A2AClient.A2AClientTransport;
import com.openjiuwen.extensions.a2a.A2AClient.A2AEventStream;
import com.openjiuwen.extensions.a2a.A2AClient.CancelTaskRequest;
import com.openjiuwen.extensions.a2a.A2AClient.ClientConfig;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aArtifact;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aMessage;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aPart;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTask;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskState;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskStatus;
import com.openjiuwen.extensions.a2a.A2ATransformer.SendMessageRequest;
import com.openjiuwen.extensions.a2a.A2ATransformer.StreamResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestA2ARemoteClient} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_remote_client.py}.
 */
class A2ARemoteClientTest {

    @AfterEach
    void cleanup() {
        RemoteClientFactory.clearCustomRemoteClientsForTest();
    }

    @Test
    void a2aRemoteClientShouldRequireCard() {
        RemoteClientConfig config = RemoteClientConfig.builder()
                .id("a2a-agent")
                .protocol(ProtocolEnum.A2A)
                .url("http://127.0.0.1:41241")
                .build();

        assertThatThrownBy(() -> new A2ARemoteClient(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("card is required when protocol is A2A");
    }

    @Test
    void a2aRemoteClientShouldPassPollingToA2aClient() {
        RecordingFactory factory = new RecordingFactory(List.of());

        A2ARemoteClient client = new A2ARemoteClient(config(Map.of(
                "card", card(),
                "polling", true,
                "clientFactory", factory)));

        assertThat(client.getClient().isPolling()).isTrue();
        assertThat(factory.config.isPolling()).isTrue();
        assertThat(factory.card).isInstanceOf(A2aAgentCard.class);
        A2aAgentCard a2aCard = (A2aAgentCard) factory.card;
        assertThat(a2aCard.getSupportedInterfaces())
                .extracting(AgentInterface::getUrl)
                .containsExactly("http://127.0.0.1:41241/a2a/jsonrpc/");
    }

    @Test
    void a2aRemoteClientShouldPassCardFromKwargsToA2aClient() {
        RecordingFactory factory = new RecordingFactory(List.of());

        A2ARemoteClient client = new A2ARemoteClient(config(Map.of(
                "card", card(),
                "clientFactory", factory)));

        assertThat(client.getClient().isPolling()).isFalse();
        assertThat(factory.card).isInstanceOf(A2aAgentCard.class);
        assertThat(((A2aAgentCard) factory.card).getName()).isEqualTo("a2a-agent");
    }

    @Test
    void invokeShouldReturnAgentResultFromA2aClient() {
        RecordingFactory factory = new RecordingFactory(List.of(response("task-send-1", "sdk-context-1", "ok")));
        A2ARemoteClient client = new A2ARemoteClient(config(Map.of(
                "card", card(),
                "clientFactory", factory)));

        client.start().toCompletableFuture().join();
        Map<String, Object> response;
        try {
            response = client.invoke(Map.of("query", "hello", "conversation_id", "conv-1"), null)
                    .toCompletableFuture()
                    .join();
        } finally {
            client.stop().toCompletableFuture().join();
        }

        assertThat(factory.transport.lastRequest.getMessage().getParts().get(0).getText()).isEqualTo("hello");
        assertThat(response)
                .containsEntry("task_id", "task-send-1")
                .containsEntry("sessionId", "conv-1")
                .containsEntry("status", "completed");
        assertThat(factory.transport.closed).isTrue();
    }

    @Test
    void cancelTaskShouldDelegateToA2aClient() {
        RecordingFactory factory = new RecordingFactory(List.of());
        A2ARemoteClient client = new A2ARemoteClient(config(Map.of(
                "card", card(),
                "clientFactory", factory)));

        client.start().toCompletableFuture().join();
        AgentResult response;
        try {
            response = client.cancelTask("task-cancel-1", "tenant-1").toCompletableFuture().join();
        } finally {
            client.stop().toCompletableFuture().join();
        }

        assertThat(factory.transport.cancelRequest.getId()).isEqualTo("task-cancel-1");
        assertThat(factory.transport.cancelRequest.getTenant()).isEqualTo("tenant-1");
        assertThat(response.getTaskId()).isEqualTo("task-cancel-1");
        assertThat(response.getStatus()).isEqualTo(TaskStatus.CANCELED);
        assertThat(factory.transport.closed).isTrue();
    }

    @Test
    void remoteAgentInvokeShouldReturnAgentResult() {
        RecordingFactory factory = new RecordingFactory(List.of(response("task-send-1", "sdk-context-1", "invoke ok")));
        RemoteClientFactory.registerRemoteClient("A2A", config -> new A2ARemoteClient(config));
        RemoteAgent agent = new RemoteAgent(
                "a2a-agent",
                "",
                null,
                null,
                ProtocolEnum.A2A,
                Map.of("url", "http://127.0.0.1:41241",
                        "kwargs", Map.of("card", card(), "polling", true, "clientFactory", factory)));

        Map<String, Object> response = agent.invoke(Map.of("query", "hello a2a", "conversation_id", "conv-1"))
                .toCompletableFuture()
                .join();

        assertThat(response).containsEntry("status", "completed").containsEntry("sessionId", "conv-1");
        List<?> artifacts = (List<?>) response.get("artifacts");
        Map<String, Object> firstArtifact = map(artifacts.get(0));
        List<?> parts = (List<?>) firstArtifact.get("parts");
        assertThat(map(parts.get(0))).containsEntry("text", "invoke ok");
        assertThat(factory.config.isPolling()).isTrue();
    }

    @Test
    void remoteAgentShouldBootstrapA2aRegistrationWithoutPreimport() {
        RemoteClientFactory.clearCustomRemoteClientsForTest();
        RecordingFactory factory = new RecordingFactory(List.of(response(
                "task-send-bootstrap-1",
                "sdk-context-bootstrap-1",
                "bootstrap invoke ok")));
        RemoteAgent agent = new RemoteAgent(
                "a2a-agent",
                "",
                null,
                null,
                ProtocolEnum.A2A,
                Map.of("url", "http://127.0.0.1:41241",
                        "kwargs", Map.of("card", card(), "clientFactory", factory)));

        Map<String, Object> response = agent.invoke(Map.of("query", "hello bootstrap",
                        "conversation_id", "conv-bootstrap-1"))
                .toCompletableFuture()
                .join();

        assertThat(factory.transport.lastRequest.getMessage().getParts().get(0).getText())
                .isEqualTo("hello bootstrap");
        assertThat(response)
                .containsEntry("status", "completed")
                .containsEntry("sessionId", "conv-bootstrap-1");
        List<?> artifacts = (List<?>) response.get("artifacts");
        assertThat(map(((List<?>) map(artifacts.get(0)).get("parts")).get(0)))
                .containsEntry("text", "bootstrap invoke ok");
    }

    @Test
    void remoteAgentCancelTaskShouldDelegateForA2aProtocol() {
        RecordingFactory factory = new RecordingFactory(List.of());
        RemoteAgent agent = new RemoteAgent(
                "a2a-agent",
                "",
                null,
                null,
                ProtocolEnum.A2A,
                Map.of("url", "http://127.0.0.1:41241",
                        "kwargs", Map.of("card", card(), "clientFactory", factory)));

        Object response = agent.cancelTask("task-cancel-2", "tenant-2").toCompletableFuture().join();

        assertThat(factory.transport.cancelRequest.getId()).isEqualTo("task-cancel-2");
        assertThat(factory.transport.cancelRequest.getTenant()).isEqualTo("tenant-2");
        assertThat(response).isInstanceOf(AgentResult.class);
        assertThat(((AgentResult) response).getStatus()).isEqualTo(TaskStatus.CANCELED);
    }

    @Test
    void streamShouldPropagateCancelledErrorWithoutCancelTaskSideEffect() {
        RecordingFactory factory = new RecordingFactory(new CancelledAfterFirstChunkStream(workingResponse(
                "task-stream-1",
                "context-stream-1",
                "chunk-1")));
        A2ARemoteClient client = new A2ARemoteClient(config(Map.of(
                "card", card(),
                "clientFactory", factory)));
        client.start().toCompletableFuture().join();

        Iterator<Object> iterator = client.stream(Map.of("query", "stream please"), null);

        assertThat(iterator.hasNext()).isTrue();
        assertThat(map(iterator.next())).containsEntry("status", "working");
        assertThatThrownBy(iterator::hasNext)
                .isInstanceOf(CancellationException.class)
                .hasMessageContaining("stream cancelled");
        assertThat(factory.transport.closed).isTrue();
        assertThat(factory.transport.cancelRequest).isNull();
    }

    @Test
    void streamTimeoutShouldStopClient() {
        RecordingFactory factory = new RecordingFactory(new SlowStream(List.of(response(
                "task-timeout-1",
                "context-timeout-1",
                "slow"))));
        A2ARemoteClient client = new A2ARemoteClient(config(Map.of(
                "card", card(),
                "clientFactory", factory)));
        client.start().toCompletableFuture().join();

        Iterator<Object> iterator = client.stream(Map.of("query", "slow", "task_id", "task-timeout-1"), 0.001d);

        assertThatThrownBy(iterator::hasNext)
                .isInstanceOf(java.util.concurrent.CompletionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(factory.transport.closed).isTrue();
    }

    private static RemoteClientConfig config(Map<String, Object> kwargs) {
        return RemoteClientConfig.builder()
                .id("a2a-agent")
                .protocol(ProtocolEnum.A2A)
                .url("http://127.0.0.1:41241")
                .kwargs(kwargs)
                .build();
    }

    private static AgentCard card() {
        return new AgentCard("a2a-agent", "a2a-agent", "test agent");
    }

    private static StreamResponse response(String taskId, String contextId, String text) {
        return response(taskId, contextId, text, A2aTaskState.TASK_STATE_COMPLETED);
    }

    private static StreamResponse response(String taskId, String contextId, String text, A2aTaskState state) {
        A2aPart part = new A2aPart();
        part.setText(text);
        A2aArtifact artifact = new A2aArtifact();
        artifact.setArtifactId("artifact-1");
        artifact.setParts(List.of(part));
        A2aTask task = new A2aTask();
        task.setId(taskId);
        task.setContextId(contextId);
        task.setStatus(new A2aTaskStatus(state));
        task.setArtifacts(List.of(artifact));
        A2aMessage message = new A2aMessage();
        message.setTaskId(taskId);
        message.setContextId(contextId);
        message.setParts(List.of(part));
        StreamResponse response = new StreamResponse();
        response.setTask(task);
        response.setMessage(message);
        return response;
    }

    private static StreamResponse workingResponse(String taskId, String contextId, String text) {
        A2aPart part = new A2aPart();
        part.setText(text);
        A2aArtifact artifact = new A2aArtifact();
        artifact.setArtifactId("artifact-1");
        artifact.setParts(List.of(part));
        A2aTask task = new A2aTask();
        task.setId(taskId);
        task.setContextId(contextId);
        task.setStatus(new A2aTaskStatus(A2aTaskState.TASK_STATE_WORKING));
        task.setArtifacts(List.of(artifact));
        StreamResponse response = new StreamResponse();
        response.setTask(task);
        return response;
    }

    private static A2aTask canceledTask(String taskId) {
        A2aTask task = new A2aTask();
        task.setId(taskId);
        task.setStatus(new A2aTaskStatus(A2aTaskState.TASK_STATE_CANCELED));
        return task;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static final class RecordingFactory implements A2AClient.A2AClientFactory {
        private final RecordingTransport transport;
        private ClientConfig config;
        private Object card;

        private RecordingFactory(List<?> events) {
            this(new A2AClient.ListEventStream(events));
        }

        private RecordingFactory(A2AEventStream stream) {
            this.transport = new RecordingTransport(stream);
        }

        @Override
        public A2AClientTransport create(ClientConfig config, Object card) {
            this.config = config;
            this.card = card;
            return transport;
        }
    }

    private static final class RecordingTransport implements A2AClientTransport {
        private final A2AEventStream stream;
        private SendMessageRequest lastRequest;
        private CancelTaskRequest cancelRequest;
        private boolean closed;

        private RecordingTransport(A2AEventStream stream) {
            this.stream = stream;
        }

        @Override
        public A2AEventStream sendMessage(SendMessageRequest request) {
            lastRequest = request;
            return stream;
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

    private static final class SlowStream implements A2AEventStream {
        private final Iterator<?> delegate;

        private SlowStream(List<?> events) {
            this.delegate = events.iterator();
        }

        @Override
        public boolean hasNext() {
            sleep(20L);
            return delegate.hasNext();
        }

        @Override
        public Object next() {
            return delegate.next();
        }

        @Override
        public void close() {
        }
    }

    private static final class CancelledAfterFirstChunkStream implements A2AEventStream {
        private final Object firstEvent;
        private boolean firstReturned;
        private boolean postFirstProbeReturned;
        private boolean closed;

        private CancelledAfterFirstChunkStream(Object firstEvent) {
            this.firstEvent = firstEvent;
        }

        @Override
        public boolean hasNext() {
            if (!firstReturned) {
                return true;
            }
            if (!postFirstProbeReturned) {
                postFirstProbeReturned = true;
                return true;
            }
            throw new CancellationException("stream cancelled");
        }

        @Override
        public Object next() {
            firstReturned = true;
            return firstEvent;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
