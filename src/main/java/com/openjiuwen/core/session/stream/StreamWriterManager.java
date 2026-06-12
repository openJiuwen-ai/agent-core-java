/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.UserConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code StreamWriterManager} in
 * {@code openjiuwen/core/session/stream/manager.py}.
 */
public class StreamWriterManager {

    private static final long DEFAULT_FRAME_TIMEOUT = -1L;

    private final StreamEmitter streamEmitter;
    private final List<Object> defaultModes;
    private final Map<Object, StreamWriter<?>> writers = new ConcurrentHashMap<>();

    public StreamWriterManager(StreamEmitter streamEmitter, List<? extends Object> modes) {
        if (streamEmitter == null) {
            throw new IllegalArgumentException("stream_emitter is None");
        }
        this.streamEmitter = streamEmitter;
        this.defaultModes = modes != null
                ? new ArrayList<>(modes)
                : new ArrayList<>(Arrays.asList(StreamMode.OUTPUT, StreamMode.TRACE, StreamMode.CUSTOM));
        addDefaultWriters();
    }

    public StreamWriterManager(StreamEmitter streamEmitter) {
        this(streamEmitter, null);
    }

    public static StreamWriterManager createManager(StreamEmitter streamEmitter, List<? extends Object> modes) {
        return new StreamWriterManager(streamEmitter, modes);
    }

    public static StreamWriterManager createManager(StreamEmitter streamEmitter) {
        return new StreamWriterManager(streamEmitter);
    }

    public StreamEmitter streamEmitter() {
        return streamEmitter;
    }

    public StreamEmitter getStreamEmitter() {
        return streamEmitter;
    }

    public void streamOutput(Consumer<Object> consumer) {
        streamOutput(DEFAULT_FRAME_TIMEOUT, DEFAULT_FRAME_TIMEOUT, true, consumer);
    }

    public void streamOutput(long firstFrameTimeoutMs, long timeoutMs, boolean needClose, Consumer<Object> consumer) {
        boolean isFirstFrame = true;
        while (true) {
            Object data;
            if (isFirstFrame) {
                data = receiveOrThrow(firstFrameTimeoutMs, true);
                isFirstFrame = false;
            } else {
                data = receiveOrThrow(timeoutMs, false);
            }

            if (StreamEmitter.END_FRAME.equals(data)) {
                if (needClose) {
                    streamEmitter.getStreamQueue().close(timeoutMs);
                }
                break;
            }

            logStreamData(data);
            consumer.accept(data);
        }
    }

    public Iterator<Object> streamIterator() {
        return streamIterator(DEFAULT_FRAME_TIMEOUT, DEFAULT_FRAME_TIMEOUT, true);
    }

    public Iterator<Object> streamIterator(long firstFrameTimeoutMs, long timeoutMs, boolean needClose) {
        return new Iterator<>() {
            private boolean firstFrame = true;
            private boolean done;
            private Object nextItem;

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (nextItem != null) {
                    return true;
                }
                nextItem = receiveOrThrow(firstFrame ? firstFrameTimeoutMs : timeoutMs, firstFrame);
                firstFrame = false;
                if (StreamEmitter.END_FRAME.equals(nextItem)) {
                    if (needClose) {
                        streamEmitter.getStreamQueue().close(timeoutMs);
                    }
                    done = true;
                    nextItem = null;
                    return false;
                }
                logStreamData(nextItem);
                return true;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Object current = nextItem;
                nextItem = null;
                return current;
            }
        };
    }

    public List<Object> collectStreamOutput() {
        List<Object> items = new ArrayList<>();
        streamOutput(items::add);
        return items;
    }

    public void addWriter(Object key, StreamWriter<?> writer) {
        writers.put(key, writer);
    }

    public StreamWriter<?> getWriter(Object key) {
        return writers.get(key);
    }

    public StreamWriter<OutputSchema> getOutputWriter() {
        return castWriter(getWriter(StreamMode.OUTPUT));
    }

    public StreamWriter<TraceSchema> getTraceWriter() {
        return castWriter(getWriter(StreamMode.TRACE));
    }

    public StreamWriter<CustomSchema> getCustomWriter() {
        return castWriter(getWriter(StreamMode.CUSTOM));
    }

    public List<Object> getEnabledModes() {
        List<Object> enabled = new ArrayList<>();
        for (StreamMode mode : StreamMode.values()) {
            if (writers.containsKey(mode)) {
                enabled.add(mode);
            }
        }
        for (Object key : writers.keySet()) {
            if (!(key instanceof StreamMode) && !enabled.contains(key)) {
                enabled.add(key);
            }
        }
        return enabled;
    }

    public StreamWriter<?> removeWriter(Object key) {
        if (defaultModes.contains(key)) {
            throw ErrorHelper.buildError(
                    StatusCode.STREAM_WRITER_MANAGER_REMOVE_WRITER_ERROR,
                    "mode", String.valueOf(key),
                    "reason", "Can not remove default writer for mode " + key
            );
        }
        return writers.remove(key);
    }

    private Object receiveOrThrow(long timeoutMs, boolean firstFrame) {
        Object data = streamEmitter.getStreamQueue().receive(timeoutMs);
        if (data != null) {
            return data;
        }
        StatusCode status = firstFrame
                ? StatusCode.STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT
                : StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT;
        throw ErrorHelper.buildError(
                status,
                "timeout", formatTimeoutSeconds(timeoutMs),
                "reason", ""
        );
    }

    private void addDefaultWriters() {
        for (Object mode : defaultModes) {
            if (StreamMode.OUTPUT.equals(mode)) {
                addWriter(mode, new StreamWriter<>(streamEmitter, OutputSchema.class, StreamWriterManager::outputFromMap));
            } else if (StreamMode.TRACE.equals(mode)) {
                addWriter(mode, new StreamWriter<>(streamEmitter, TraceSchema.class, StreamWriterManager::traceFromMap));
            } else if (StreamMode.CUSTOM.equals(mode)) {
                addWriter(mode, new StreamWriter<>(streamEmitter, CustomSchema.class, CustomSchema::new));
            } else {
                throw ErrorHelper.buildError(
                        StatusCode.STREAM_WRITER_MANAGER_ADD_WRITER_ERROR,
                        "mode", String.valueOf(mode),
                        "reason", "default modes must be OUTPUT, TRACE, CUSTOM, " + mode + " is not supported."
                );
            }
        }
    }

    private static OutputSchema outputFromMap(Map<String, Object> value) {
        return new OutputSchema(
                stringValue(value.get("type")),
                intValue(value.get("index")),
                value.get("payload")
        );
    }

    private static TraceSchema traceFromMap(Map<String, Object> value) {
        return new TraceSchema(stringValue(value.get("type")), value.get("payload"));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            return Integer.parseInt(String.valueOf(value));
        }
        return 0;
    }

    private void logStreamData(Object data) {
        if (UserConfig.isSensitive()) {
            Loggers.SESSION.debug("Stream data received, sensitiveMode=true");
        } else {
            Loggers.SESSION.debug("Stream data received, dataType={}", data.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> StreamWriter<T> castWriter(StreamWriter<?> writer) {
        return (StreamWriter<T>) writer;
    }

    private static String formatTimeoutSeconds(long timeoutMs) {
        if (timeoutMs < 0) {
            return String.valueOf(timeoutMs);
        }
        return BigDecimal.valueOf(timeoutMs)
                .movePointLeft(3)
                .stripTrailingZeros()
                .toPlainString();
    }
}
