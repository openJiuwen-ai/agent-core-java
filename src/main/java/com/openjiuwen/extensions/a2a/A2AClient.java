/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Minimal A2A (Agent-to-Agent) client wrapper.
 * <p>
 * Mirrors Python's {@code A2AClient} in
 * {@code openjiuwen.extensions.a2a.a2a_client}.
 */
public class A2AClient {

    private static final Logger LOG = LoggerFactory.getLogger(A2AClient.class);

    public interface MessageTransport {
        Iterable<Map<String, Object>> sendMessage(Map<String, Object> request);

        default void close() {
            // Default no-op transport mirrors an SDK client with nothing to release.
        }
    }

    private final Map<String, Object> card;
    private final MessageTransport transport;
    private boolean closed;

    public A2AClient() {
        this(new LinkedHashMap<>());
    }

    public A2AClient(Map<String, Object> card) {
        this(card, request -> List.of());
    }

    public A2AClient(Map<String, Object> card, MessageTransport transport) {
        this.card = card != null ? new LinkedHashMap<>(card) : new LinkedHashMap<>();
        this.transport = transport != null ? transport : request -> List.of();
        LOG.info("[A2AClient] Created with card");
    }

    public Map<String, Object> getCard() {
        return Collections.unmodifiableMap(card);
    }

    /** Invoke the remote agent. */
    public Map<String, Object> invoke(Map<String, Object> inputs) {
        LOG.debug("[A2AClient] Invoking remote agent");
        Map<String, Object> latest = null;
        for (Map<String, Object> event : sendMessage(A2ATransformer.toA2aRequest(inputs))) {
            latest = event;
        }
        return latest == null ? A2ATransformer.fromA2aResponse(new Object()) : A2ATransformer.fromA2aResponse(latest);
    }

    public Iterable<Map<String, Object>> stream(Map<String, Object> inputs) {
        Iterable<Map<String, Object>> events = sendMessage(A2ATransformer.toA2aRequest(inputs));
        return () -> new Iterator<>() {
            private final Iterator<Map<String, Object>> delegate = events.iterator();

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public Map<String, Object> next() {
                return A2ATransformer.fromA2aResponse(delegate.next());
            }
        };
    }

    public Iterable<Map<String, Object>> sendMessage(Map<String, Object> request) {
        return transport.sendMessage(request);
    }

    public Map<String, Object> sendRequest(Map<String, Object> request) {
        return A2ATransformer.toA2aRequest(request);
    }

    public Map<String, Object> receiveResponse(Map<String, Object> response) {
        return A2ATransformer.fromA2aResponse(response);
    }

    /** Close the client. */
    public void close() {
        closed = true;
        transport.close();
        LOG.info("[A2AClient] Closed");
    }

    public void stop() {
        close();
    }

    public boolean isClosed() {
        return closed;
    }
}
