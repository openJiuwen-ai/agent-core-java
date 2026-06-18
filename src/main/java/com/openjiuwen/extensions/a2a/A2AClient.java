/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.single_agent.schema.AgentResult;
import com.openjiuwen.extensions.a2a.A2ATransformer.SendMessageRequest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Minimal A2A SDK wrapper for openjiuwen.
 *
 * <p>Mirrors Python's {@code A2AClient} in
 * {@code openjiuwen/extensions/a2a/a2a_client.py}.</p>
 */
public class A2AClient implements AutoCloseable {

    private final Object card;
    private final boolean polling;
    private final A2AClientTransport client;

    public A2AClient() {
        this(null, false);
    }

    public A2AClient(Object card) {
        this(card, false);
    }

    public A2AClient(Object card, boolean polling) {
        this(card, polling, new DefaultClientFactory());
    }

    A2AClient(Object card, boolean polling, A2AClientFactory factory) {
        this.card = card;
        this.polling = polling;
        try {
            ClientConfig config = new ClientConfig();
            config.setPolling(polling);
            this.client = Objects.requireNonNull(factory, "factory").create(config, card);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to create A2A client: " + exception.getMessage(), exception);
        }
    }

    public Object getCard() {
        return card;
    }

    public boolean isPolling() {
        return polling;
    }

    public CompletionStage<Void> stop() {
        return client.close();
    }

    public A2AEventStream sendMessage(SendMessageRequest request) {
        return client.sendMessage(request);
    }

    public A2AEventStream _send_message(SendMessageRequest request) {
        return sendMessage(request);
    }

    public CompletionStage<AgentResult> invoke(Map<String, Object> inputs) {
        SendMessageRequest request = A2ATransformer.toA2aRequest(inputs);
        String sessionId = resolveSessionId(inputs);
        A2AEventStream eventStream = sendMessage(request);
        try {
            if (eventStream.hasNext()) {
                AgentResult result = A2ATransformer.fromA2aResponse(eventStream.next());
                return CompletableFuture.completedFuture(withSessionId(result, sessionId));
            }
            return CompletableFuture.completedFuture(withSessionId(new AgentResult(), sessionId));
        } finally {
            closeStream(eventStream);
        }
    }

    public Iterable<AgentResult> stream(Map<String, Object> inputs) {
        SendMessageRequest request = A2ATransformer.toA2aRequest(inputs);
        String sessionId = resolveSessionId(inputs);
        A2AEventStream eventStream = sendMessage(request);
        return () -> new Iterator<>() {
            private boolean closed;

            @Override
            public boolean hasNext() {
                boolean hasNext = eventStream.hasNext();
                if (!hasNext) {
                    closeOnce();
                }
                return hasNext;
            }

            @Override
            public AgentResult next() {
                AgentResult result = A2ATransformer.fromA2aResponse(eventStream.next());
                if (!eventStream.hasNext()) {
                    closeOnce();
                }
                return withSessionId(result, sessionId);
            }

            private void closeOnce() {
                if (!closed) {
                    closed = true;
                    closeStream(eventStream);
                }
            }
        };
    }

    public CompletionStage<AgentResult> cancelTask(String taskId) {
        return cancelTask(taskId, null);
    }

    public CompletionStage<AgentResult> cancelTask(String taskId, String tenant) {
        CancelTaskRequest request = new CancelTaskRequest(taskId);
        if (tenant != null) {
            request.setTenant(tenant);
        }
        return client.cancelTask(request).thenApply(A2ATransformer::fromA2aResponse);
    }

    public CompletionStage<AgentResult> cancel_task(String taskId) {
        return cancelTask(taskId);
    }

    public CompletionStage<AgentResult> cancel_task(String taskId, String tenant) {
        return cancelTask(taskId, tenant);
    }

    public CompletionStage<A2AClient> aenter() {
        return CompletableFuture.completedFuture(this);
    }

    public CompletionStage<Void> aexit(Throwable excType, Throwable excVal, Throwable excTb) {
        return stop();
    }

    public CompletionStage<A2AClient> __aenter__() {
        return aenter();
    }

    public CompletionStage<Void> __aexit__(Throwable excType, Throwable excVal, Throwable excTb) {
        return aexit(excType, excVal, excTb);
    }

    @Override
    public void close() {
        stop().toCompletableFuture().join();
    }

    static String resolveSessionId(Map<String, Object> inputs) {
        if (inputs == null) {
            return null;
        }
        Object sessionId = inputs.get("conversation_id");
        if (sessionId == null) {
            sessionId = inputs.get("sessionId");
        }
        return sessionId == null ? null : String.valueOf(sessionId);
    }

    static AgentResult withSessionId(AgentResult result, String sessionId) {
        AgentResult safeResult = result == null ? new AgentResult() : result;
        if (sessionId == null) {
            return safeResult;
        }
        AgentResult copy = new AgentResult();
        copy.setTaskId(safeResult.getTaskId());
        copy.setSessionId(sessionId);
        copy.setStatus(safeResult.getStatus());
        copy.setArtifacts(safeResult.getArtifacts());
        copy.setMetadata(safeResult.getMetadata());
        return copy;
    }

    private static void closeStream(A2AEventStream eventStream) {
        eventStream.close();
    }

    public interface A2AClientFactory {
        A2AClientTransport create(ClientConfig config, Object card);
    }

    public interface A2AClientTransport {
        A2AEventStream sendMessage(SendMessageRequest request);

        CompletionStage<Object> cancelTask(CancelTaskRequest request);

        CompletionStage<Void> close();
    }

    public interface A2AEventStream extends Iterator<Object>, AutoCloseable {
        @Override
        void close();
    }

    /**
     * Mirrors Python's {@code ClientConfig} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_client.py}.
     */
    public static final class ClientConfig {
        private boolean polling;

        public boolean isPolling() {
            return polling;
        }

        public void setPolling(boolean polling) {
            this.polling = polling;
        }
    }

    /**
     * Mirrors Python's {@code CancelTaskRequest} A2A SDK boundary in
     * {@code openjiuwen/extensions/a2a/a2a_client.py}.
     */
    public static final class CancelTaskRequest {
        private final String id;
        private String tenant;

        public CancelTaskRequest(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }

    public static final class ListEventStream implements A2AEventStream {
        private final Iterator<?> iterator;

        public ListEventStream(List<?> events) {
            this.iterator = new ArrayList<>(events == null ? List.of() : events).iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Object next() {
            return iterator.next();
        }

        @Override
        public void close() {
        }
    }

    private static final class DefaultClientFactory implements A2AClientFactory {
        @Override
        public A2AClientTransport create(ClientConfig config, Object card) {
            throw new UnsupportedOperationException("A2A Java SDK client factory is not configured");
        }
    }
}
