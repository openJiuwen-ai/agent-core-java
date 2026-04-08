/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages stream writers for different stream modes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.stream.manager.StreamWriterManager}.
 */
public class StreamWriterManager {

    private static final long DEFAULT_FRAME_TIMEOUT = -1;

    private final StreamEmitter streamEmitter;
    private final List<StreamMode> defaultModes;
    private final Map<StreamMode, StreamWriter<?>> writers = new ConcurrentHashMap<>();

    public StreamWriterManager(StreamEmitter streamEmitter, List<StreamMode> modes) {
        if (streamEmitter == null) {
            throw new IllegalArgumentException("streamEmitter is null");
        }
        this.streamEmitter = streamEmitter;
        this.defaultModes = modes != null ? modes
                : Arrays.asList(StreamMode.OUTPUT, StreamMode.TRACE, StreamMode.CUSTOM);
        addDefaultWriters();
    }

    public StreamWriterManager(StreamEmitter streamEmitter) {
        this(streamEmitter, null);
    }

    /**
     * Factory method.
     */
    public static StreamWriterManager createManager(StreamEmitter streamEmitter, List<StreamMode> modes) {
        return new StreamWriterManager(streamEmitter, modes);
    }

    public static StreamWriterManager createManager(StreamEmitter streamEmitter) {
        return new StreamWriterManager(streamEmitter);
    }

    public StreamEmitter getStreamEmitter() {
        return streamEmitter;
    }

    /**
     * Iterate over stream output synchronously, invoking the consumer for each item.
     * Blocks until END_FRAME is encountered.
     *
     * @param firstFrameTimeoutMs timeout for the first frame in ms, -1 for no timeout
     * @param timeoutMs           timeout for subsequent frames in ms, -1 for no timeout
     * @param needClose           whether to close the queue when done
     * @param consumer            callback for each stream item
     */
    public void streamOutput(long firstFrameTimeoutMs, long timeoutMs, boolean needClose,
                             Consumer<Object> consumer) {
        boolean isFirstFrame = true;
        while (true) {
            Object data;
            if (isFirstFrame) {
                data = streamEmitter.getStreamQueue().receive(firstFrameTimeoutMs);
                if (data == null) {
                    throw ErrorHelper.buildError(StatusCode.STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT,
                            "timeout", formatTimeoutSeconds(firstFrameTimeoutMs),
                            "reason", "");
                }
                isFirstFrame = false;
            } else {
                data = streamEmitter.getStreamQueue().receive(timeoutMs);
                if (data == null) {
                    throw ErrorHelper.buildError(StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT,
                            "timeout", formatTimeoutSeconds(timeoutMs),
                            "reason", "");
                }
            }

            if (StreamEmitter.END_FRAME.equals(data)) {
                if (needClose) {
                    streamEmitter.getStreamQueue().close();
                }
                break;
            } else {
                Loggers.SESSION.debug("Stream data received, dataType={}", data.getClass().getSimpleName());
                consumer.accept(data);
            }
        }
    }

    /**
     * Stream output with default timeouts.
     */
    public void streamOutput(Consumer<Object> consumer) {
        streamOutput(DEFAULT_FRAME_TIMEOUT, DEFAULT_FRAME_TIMEOUT, true, consumer);
    }

    /**
     * Expose stream output as a blocking iterator.
     */
    public Iterator<Object> streamIterator() {
        return streamIterator(DEFAULT_FRAME_TIMEOUT, DEFAULT_FRAME_TIMEOUT, true);
    }

    /**
     * Expose stream output as a blocking iterator with configurable timeouts.
     */
    public Iterator<Object> streamIterator(long firstFrameTimeoutMs, long timeoutMs, boolean needClose) {
        return new Iterator<>() {
            private boolean firstFrame = true;
            private boolean done = false;
            private Object nextItem;

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (nextItem != null) {
                    return true;
                }
                nextItem = receiveNext(firstFrame ? firstFrameTimeoutMs : timeoutMs);
                firstFrame = false;
                if (StreamEmitter.END_FRAME.equals(nextItem)) {
                    if (needClose) {
                        streamEmitter.getStreamQueue().close();
                    }
                    done = true;
                    nextItem = null;
                    return false;
                }
                Loggers.SESSION.debug("Stream data received, dataType={}", nextItem.getClass().getSimpleName());
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

            private Object receiveNext(long timeoutMs) {
                Object data = streamEmitter.getStreamQueue().receive(timeoutMs);
                if (data != null) {
                    return data;
                }
                if (firstFrame) {
                    throw ErrorHelper.buildError(StatusCode.STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT,
                            "timeout", formatTimeoutSeconds(timeoutMs),
                            "reason", "");
                }
                throw ErrorHelper.buildError(StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT,
                        "timeout", formatTimeoutSeconds(timeoutMs),
                        "reason", "");
            }
        };
    }

    /**
     * Collect all stream items into a list (blocking).
     *
     * @return list of stream items
     */
    public List<Object> collectStreamOutput() {
        List<Object> items = new ArrayList<>();
        streamOutput(items::add);
        return items;
    }

    /**
     * Add a writer for a stream mode.
     */
    public void addWriter(StreamMode key, StreamWriter<?> writer) {
        writers.put(key, writer);
    }

    /**
     * Get writer by mode.
     */
    public StreamWriter<?> getWriter(StreamMode key) {
        return writers.get(key);
    }

    /**
     * Get the output writer.
     */
    public StreamWriter<OutputSchema> getOutputWriter() {
        return castWriter(getWriter(StreamMode.OUTPUT));
    }

    /**
     * Get the trace writer.
     */
    public StreamWriter<TraceSchema> getTraceWriter() {
        return castWriter(getWriter(StreamMode.TRACE));
    }

    /**
     * Get the custom writer.
     */
    public StreamWriter<CustomSchema> getCustomWriter() {
        return castWriter(getWriter(StreamMode.CUSTOM));
    }

    /**
     * Get enabled stream modes in enum declaration order.
     */
    public List<StreamMode> getEnabledModes() {
        List<StreamMode> enabled = new ArrayList<>();
        for (StreamMode mode : StreamMode.values()) {
            if (writers.containsKey(mode)) {
                enabled.add(mode);
            }
        }
        return enabled;
    }

    /**
     * Remove a writer by mode. Cannot remove default writers.
     */
    public StreamWriter<?> removeWriter(StreamMode key) {
        if (defaultModes.contains(key)) {
            throw ErrorHelper.buildError(StatusCode.STREAM_WRITER_MANAGER_REMOVE_WRITER_ERROR,
                    "reason", "Cannot remove default writer for mode " + key);
        }
        return writers.remove(key);
    }

    private void addDefaultWriters() {
        for (StreamMode mode : defaultModes) {
            switch (mode) {
                case OUTPUT:
                    addWriter(mode, new StreamWriter<>(streamEmitter, OutputSchema.class, OutputSchema::fromMap));
                    break;
                case TRACE:
                    addWriter(mode, new StreamWriter<>(streamEmitter, TraceSchema.class, TraceSchema::fromMap));
                    break;
                case CUSTOM:
                    addWriter(mode, new StreamWriter<>(streamEmitter, CustomSchema.class, CustomSchema::fromMap));
                    break;
                default:
                    throw ErrorHelper.buildError(StatusCode.STREAM_WRITER_MANAGER_ADD_WRITER_ERROR,
                            "mode", mode.toString(),
                            "reason", "default modes must be OUTPUT, TRACE, CUSTOM");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends StreamSchema> StreamWriter<T> castWriter(StreamWriter<?> writer) {
        return (StreamWriter<T>) writer;
    }

    private String formatTimeoutSeconds(long timeoutMs) {
        if (timeoutMs < 0) {
            return String.valueOf(timeoutMs);
        }
        return BigDecimal.valueOf(timeoutMs)
                .movePointLeft(3)
                .stripTrailingZeros()
                .toPlainString();
    }
}
