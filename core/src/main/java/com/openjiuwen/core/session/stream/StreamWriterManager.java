/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import com.openjiuwen.core.common.constants.TimeoutConstants;
import com.openjiuwen.core.common.exception.BaseError;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code StreamWriterManager} in
 * {@code openjiuwen/core/session/stream/manager.py}.
 */
public class StreamWriterManager {

    private static final long DEFAULT_FRAME_TIMEOUT = TimeoutConstants.BLOCKING_QUEUE_MS;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long MAX_WAIT_SLICE_NANOS = 1_000_000_000L;
    private static final long MODEL_CALL_GRACE_MS = 5000L;

    private final StreamEmitter streamEmitter;
    private final List<Object> defaultModes;
    private final Map<Object, StreamWriter<?>> writers = new ConcurrentHashMap<>();
    private final Map<Long, Long> modelCallDeadlines = new ConcurrentHashMap<>();
    private final AtomicLong modelCallLeaseIds = new AtomicLong();

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

    /**
     * Deliver data frames to the consumer until the terminal frame is received.
     *
     * @param firstFrameTimeoutMs first-frame wait in milliseconds; non-positive values use the configured default
     * @param timeoutMs subsequent-frame wait in milliseconds; non-positive values use the configured default
     * @param shouldClose whether to close the queue after receiving the terminal frame
     * @param consumer recipient of each data frame, excluding the terminal frame
     */
    public void streamOutput(long firstFrameTimeoutMs, long timeoutMs, boolean shouldClose, Consumer<Object> consumer) {
        streamIterator(firstFrameTimeoutMs, timeoutMs, shouldClose).forEachRemaining(consumer);
    }

    public Iterator<Object> streamIterator() {
        return streamIterator(DEFAULT_FRAME_TIMEOUT, DEFAULT_FRAME_TIMEOUT, true);
    }

    /**
     * Drain a completed producer's buffered output before reporting its original failure.
     * No terminal frame is enqueued: the bounded queue may already be full.
     *
     * @param failure original producer failure
     * @return iterator that reports the failure once after buffered output
     */
    public Iterator<Object> streamIterator(RuntimeException failure) {
        List<Object> buffered = streamEmitter.getStreamQueue().closeAndDrain();
        streamEmitter.close();
        buffered.removeIf(StreamEmitter.END_FRAME::equals);
        return streamIterator(DEFAULT_FRAME_TIMEOUT, DEFAULT_FRAME_TIMEOUT, false, buffered.iterator(), failure);
    }

    /**
     * Renew an independent model-call lease using a saturated monotonic deadline.
     *
     * <p>The deadline is the current {@link System#nanoTime()} plus the supplied delay, idle
     * timeout, and five seconds of grace. Active leases are safe to renew and release from
     * different threads; subsequent-frame waits use the latest deadline.</p>
     *
     * @param leaseId zero to allocate a new positive id, or an existing positive id to renew
     * @param idleTimeoutMs model idle timeout in milliseconds; must be positive
     * @param delayMs additional delay in milliseconds; must be non-negative
     * @return the allocated or renewed positive lease id
     * @throws IllegalArgumentException when a duration is invalid or the positive id is unknown
     */
    public long renewModelCallTimeout(long leaseId, long idleTimeoutMs, long delayMs) {
        if (leaseId < 0L || idleTimeoutMs <= 0L || delayMs < 0L) {
            throw new IllegalArgumentException("Invalid model call timeout lease arguments");
        }
        long deadline = modelCallDeadline(idleTimeoutMs, delayMs);
        if (leaseId == 0L) {
            return allocateModelCallLease(deadline);
        }
        if (modelCallDeadlines.replace(leaseId, deadline) == null) {
            throw new IllegalArgumentException("Unknown model call timeout lease id: " + leaseId);
        }
        return leaseId;
    }

    /**
     * Release one model-call lease.
     *
     * <p>Zero and unknown ids are no-ops. Other active leases remain unchanged. This method is
     * safe to call concurrently with renewals and stream waits.</p>
     *
     * @param leaseId lease id to release
     */
    public void endModelCallTimeout(long leaseId) {
        modelCallDeadlines.remove(leaseId);
    }

    /**
     * Iterate over data frames until the terminal frame, throwing on a frame timeout.
     *
     * @param firstFrameTimeoutMs first-frame wait in milliseconds; non-positive values use the configured default
     * @param timeoutMs subsequent-frame wait in milliseconds; non-positive values use the configured default
     * @param shouldClose whether to close the queue after receiving the terminal frame
     * @return iterator over data frames, excluding the terminal frame
     */
    public Iterator<Object> streamIterator(long firstFrameTimeoutMs, long timeoutMs, boolean shouldClose) {
        return streamIterator(firstFrameTimeoutMs, timeoutMs, shouldClose, null, null);
    }

    private Iterator<Object> streamIterator(long firstFrameTimeoutMs, long timeoutMs, boolean shouldClose,
                                            Iterator<Object> buffered, RuntimeException failure) {
        return new Iterator<>() {
            private boolean isFirstFrame = true;
            private boolean isDone;
            private Object nextItem;

            @Override
            public boolean hasNext() {
                if (isDone) {
                    return false;
                }
                if (nextItem != null) {
                    return true;
                }
                if (buffered == null) {
                    nextItem = receiveOrThrow(isFirstFrame ? firstFrameTimeoutMs : timeoutMs, isFirstFrame);
                } else {
                    nextItem = buffered.hasNext() ? buffered.next() : StreamEmitter.END_FRAME;
                }
                isFirstFrame = false;
                if (StreamEmitter.END_FRAME.equals(nextItem)) {
                    if (shouldClose) {
                        // Draining an empty queue must not inherit the frame-interval timeout.
                        streamEmitter.getStreamQueue().close(AsyncStreamQueue.DEFAULT_CLOSE_TIMEOUT_MS);
                    }
                    isDone = true;
                    nextItem = null;
                    if (failure != null) {
                        throw failure;
                    }
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

    private Object receiveOrThrow(long timeoutMs, boolean isFirstFrame) {
        long effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_FRAME_TIMEOUT;
        if (!isFirstFrame) {
            return receiveSubsequentOrThrow(effectiveTimeoutMs);
        }
        Object data = streamEmitter.getStreamQueue().receive(effectiveTimeoutMs);
        if (data != null) {
            return data;
        }
        throw frameTimeout(true, effectiveTimeoutMs);
    }

    private Object receiveSubsequentOrThrow(long originalTimeoutMs) {
        long originalDeadline = saturatedAdd(System.nanoTime(), millisecondsToNanos(originalTimeoutMs));
        while (true) {
            long effectiveDeadline = Math.max(originalDeadline, latestModelCallDeadline());
            long remainingNanos = saturatedSubtract(effectiveDeadline, System.nanoTime());
            if (remainingNanos <= 0L) {
                Object queued = streamEmitter.getStreamQueue().tryReceive();
                if (queued != null) {
                    return queued;
                }
                throw frameTimeout(false, originalTimeoutMs);
            }
            if (Thread.currentThread().isInterrupted()) {
                throw frameTimeout(false, originalTimeoutMs);
            }
            Object data = streamEmitter.getStreamQueue().receive(waitSliceMillis(remainingNanos));
            if (data != null) {
                return data;
            }
            if (Thread.currentThread().isInterrupted()) {
                throw frameTimeout(false, originalTimeoutMs);
            }
        }
    }

    private long allocateModelCallLease(long deadline) {
        while (true) {
            long leaseId = modelCallLeaseIds.updateAndGet(
                    current -> current == Long.MAX_VALUE ? 1L : current + 1L);
            if (modelCallDeadlines.putIfAbsent(leaseId, deadline) == null) {
                return leaseId;
            }
        }
    }

    private long latestModelCallDeadline() {
        long latest = Long.MIN_VALUE;
        for (Long deadline : modelCallDeadlines.values()) {
            latest = Math.max(latest, deadline);
        }
        return latest;
    }

    private static long modelCallDeadline(long idleTimeoutMs, long delayMs) {
        long deadline = saturatedAdd(System.nanoTime(), millisecondsToNanos(delayMs));
        deadline = saturatedAdd(deadline, millisecondsToNanos(idleTimeoutMs));
        return saturatedAdd(deadline, millisecondsToNanos(MODEL_CALL_GRACE_MS));
    }

    private static long millisecondsToNanos(long timeoutMs) {
        if (timeoutMs > Long.MAX_VALUE / NANOS_PER_MILLISECOND) {
            return Long.MAX_VALUE;
        }
        return timeoutMs * NANOS_PER_MILLISECOND;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    private static long saturatedSubtract(long left, long right) {
        if (right > 0L && left < Long.MIN_VALUE + right) {
            return Long.MIN_VALUE;
        }
        if (right < 0L && left > Long.MAX_VALUE + right) {
            return Long.MAX_VALUE;
        }
        return left - right;
    }

    private static long waitSliceMillis(long remainingNanos) {
        long sliceNanos = Math.min(remainingNanos, MAX_WAIT_SLICE_NANOS);
        long wholeMillis = sliceNanos / NANOS_PER_MILLISECOND;
        long remainderNanos = sliceNanos % NANOS_PER_MILLISECOND;
        return Math.max(1L, wholeMillis + (remainderNanos > 0L ? 1L : 0L));
    }

    private static BaseError frameTimeout(boolean isFirstFrame, long effectiveTimeoutMs) {
        StatusCode status = isFirstFrame
                ? StatusCode.STREAM_OUTPUT_FIRST_CHUNK_INTERVAL_TIMEOUT
                : StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT;
        return ErrorHelper.buildError(
                status,
                "timeout", formatTimeoutSeconds(effectiveTimeoutMs),
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
