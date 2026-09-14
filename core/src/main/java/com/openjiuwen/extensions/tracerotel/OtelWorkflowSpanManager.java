/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manage invoke_id → {@link OtelSpanState} for workflow handlers plus buffered
 * incremental data.
 *
 * <p>Incremental data ({@code onInvokeData}, {@code streamInputs}, {@code streamOutputs})
 * is buffered as lists and flushed as single OTel span attributes on
 * {@code onCallDone}, avoiding many small individual attributes.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.span_manager.OtelWorkflowSpanManager}.</p>
 *
 * @since 0.1.7
 */
public final class OtelWorkflowSpanManager {
    private final Map<String, OtelSpanState> spans = new ConcurrentHashMap<>();

    /** Buffered incremental data: invoke_id → list. */
    private final Map<String, List<Map<String, Object>>> onInvokeData = new ConcurrentHashMap<>();
    private final Map<String, List<Object>> streamInputs = new ConcurrentHashMap<>();
    private final Map<String, List<Object>> streamOutputs = new ConcurrentHashMap<>();

    /**
     * Register a span state under the given invoke_id and initialize its buffers.
     *
     * @param invokeId the invoke id
     * @param state    the span state
     */
    public void push(String invokeId, OtelSpanState state) {
        spans.put(invokeId, state);
        onInvokeData.put(invokeId, Collections.synchronizedList(new ArrayList<>()));
        streamInputs.put(invokeId, Collections.synchronizedList(new ArrayList<>()));
        streamOutputs.put(invokeId, Collections.synchronizedList(new ArrayList<>()));
    }

    /**
     * Remove and return the span state for the given invoke_id, clearing its buffers.
     *
     * @param invokeId the invoke id
     * @return the removed span state, or {@code null} if not present
     */
    public OtelSpanState pop(String invokeId) {
        onInvokeData.remove(invokeId);
        streamInputs.remove(invokeId);
        streamOutputs.remove(invokeId);
        return spans.remove(invokeId);
    }

    /**
     * Return the span state for the given invoke_id without removing it.
     *
     * @param invokeId the invoke id
     * @return the span state, or {@code null} if not present
     */
    public OtelSpanState get(String invokeId) {
        return spans.get(invokeId);
    }

    // --- Incremental data buffers ---

    /**
     * Append an on_invoke_data entry to the buffer for the given invoke_id.
     *
     * @param invokeId the invoke id
     * @param data     the data entry
     */
    public void appendOnInvokeData(String invokeId, Map<String, Object> data) {
        List<Map<String, Object>> buf = onInvokeData.get(invokeId);
        if (buf != null) {
            buf.add(data);
        }
    }

    /**
     * Return the buffered on_invoke_data for the given invoke_id (empty if absent).
     *
     * @param invokeId the invoke id
     * @return an unmodifiable copy of the buffer
     */
    public List<Map<String, Object>> getOnInvokeData(String invokeId) {
        List<Map<String, Object>> buf = onInvokeData.get(invokeId);
        if (buf == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(buf));
    }

    /**
     * Append a stream-input chunk to the buffer for the given invoke_id.
     *
     * @param invokeId the invoke id
     * @param chunk    the chunk
     */
    public void appendStreamInput(String invokeId, Object chunk) {
        List<Object> buf = streamInputs.get(invokeId);
        if (buf != null) {
            buf.add(chunk);
        }
    }

    /**
     * Return the buffered stream inputs for the given invoke_id (empty if absent).
     *
     * @param invokeId the invoke id
     * @return an unmodifiable copy of the buffer
     */
    public List<Object> getStreamInputs(String invokeId) {
        List<Object> buf = streamInputs.get(invokeId);
        if (buf == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(buf));
    }

    /**
     * Append a stream-output chunk to the buffer for the given invoke_id.
     *
     * @param invokeId the invoke id
     * @param chunk    the chunk
     */
    public void appendStreamOutput(String invokeId, Object chunk) {
        List<Object> buf = streamOutputs.get(invokeId);
        if (buf != null) {
            buf.add(chunk);
        }
    }

    /**
     * Return the buffered stream outputs for the given invoke_id (empty if absent).
     *
     * @param invokeId the invoke id
     * @return an unmodifiable copy of the buffer
     */
    public List<Object> getStreamOutputs(String invokeId) {
        List<Object> buf = streamOutputs.get(invokeId);
        if (buf == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(buf));
    }
}
