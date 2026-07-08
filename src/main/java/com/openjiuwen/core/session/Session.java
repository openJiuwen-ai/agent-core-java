/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deprecated compatibility facade for the root session module.
 *
 * <p>Mirrors Python's deprecated {@code Session} in
 * {@code openjiuwen/core/session/session.py}.</p>
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public final class Session implements AgentSessionApi {

    public static final String DEPRECATION_MESSAGE =
            "`openjiuwen.core.session.Session` is deprecated and will be removed in a future release. "
                    + "Use `openjiuwen.core.[module].Session` instead.";

    private final String sessionId = UUID.randomUUID().toString().replace("-", "");
    private final List<Object> streamChunks = new ArrayList<>();

    public Session() {
    }

    public String deprecationMessage() {
        return DEPRECATION_MESSAGE;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void writeStream(Object data) {
        streamChunks.add(data);
    }

    public void write_stream(Object data) {
        writeStream(data);
    }

    public Iterator<Object> streamIterator() {
        return List.copyOf(streamChunks).iterator();
    }

    public Iterator<Object> stream_iterator() {
        return streamIterator();
    }

    public Object getState(String key) {
        return null;
    }

    public void updateState(Map<String, Object> data) {
        // Root Session is a legacy facade with no persistent state backend.
    }
}
