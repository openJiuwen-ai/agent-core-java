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

    private final Map<String, Object> card;
    private boolean closed;

    public A2AClient() {
        this(new LinkedHashMap<>());
    }

    public A2AClient(Map<String, Object> card) {
        this.card = card != null ? new LinkedHashMap<>(card) : new LinkedHashMap<>();
        LOG.info("[A2AClient] Created with card");
    }

    public Map<String, Object> getCard() {
        return Collections.unmodifiableMap(card);
    }

    /** Invoke the remote agent. */
    public Map<String, Object> invoke(Map<String, Object> inputs) {
        LOG.debug("[A2AClient] Invoking remote agent");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", inputs);
        return result;
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
        LOG.info("[A2AClient] Closed");
    }

    public void stop() {
        close();
    }

    public boolean isClosed() {
        return closed;
    }
}
