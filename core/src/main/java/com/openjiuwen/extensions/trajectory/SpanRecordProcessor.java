/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SDK span processor that turns trajectory spans into self-contained records
 * for registered {@link TrajectoryRecordConsumer}s.
 *
 * <p>Mirrors Python Agent Core {@code SpanRecordProcessor}: ended spans become
 * final {@link OtlpSpanRecord}s, started spans emit a running
 * {@link OtlpSpanSnapshotRecord}, and {@link #publishStreamFrame} forwards
 * model streaming frames. Routing hints are extracted from span attributes so
 * consumers never need to parse OTLP JSON.</p>
 *
 * @since 0.1.18
 */
public final class SpanRecordProcessor implements SpanProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(SpanRecordProcessor.class);

    private static final String SNAPSHOT_UPDATE_STARTED = "started";

    private final List<TrajectoryRecordConsumer> consumers = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, AtomicLong> snapshotRevisions = new ConcurrentHashMap<>();
    private final String serviceName;

    /**
     * Create a processor exporting spans with the given resource service name.
     *
     * @param serviceName value stamped into the OTLP resource attributes
     */
    public SpanRecordProcessor(String serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * Register a consumer; registration is identity-deduplicated.
     *
     * @param consumer consumer to register
     */
    public void registerConsumer(TrajectoryRecordConsumer consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (!consumers.contains(consumer)) {
            consumers.add(consumer);
        }
    }

    /**
     * Remove a previously registered consumer.
     *
     * @param consumer consumer to remove
     */
    public void unregisterConsumer(TrajectoryRecordConsumer consumer) {
        consumers.remove(consumer);
    }

    /**
     * Publish one model streaming frame to all registered consumers.
     *
     * @param frame the streaming frame record
     */
    public void publishStreamFrame(StreamFrameRecord frame) {
        for (TrajectoryRecordConsumer consumer : consumers) {
            try {
                consumer.consumeStreamFrame(frame);
            } catch (NullPointerException | IllegalStateException | SecurityException ex) {
                LOG.warn("trajectory consumer rejected stream frame: {}", ex.getMessage());
            }
        }
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        if (consumers.isEmpty()) {
            return;
        }
        try {
            SpanData data = span.toSpanData();
            long revision = snapshotRevision(data.getSpanContext().getSpanId());
            OtlpSpanSnapshotRecord record = new OtlpSpanSnapshotRecord(
                    data.getSpanContext().getTraceId(),
                    data.getSpanContext().getSpanId(),
                    parentSpanId(data).orElse(null),
                    stringValue(data, TrajectorySemConv.GEN_AI_CONVERSATION_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_REQUEST_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_RUN_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_AGENT_MODE).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_DISPLAY_NAME).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_KIND).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_PARENT_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_SESSION_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_TRAJECTORY_RECORD_KIND).orElse(null),
                    revision,
                    schemaVersion(data),
                    SNAPSHOT_UPDATE_STARTED,
                    System.nanoTime(),
                    OtlpJsonCodec.encodeRunningSpan(data, serviceName));
            dispatchSnapshot(record);
        } catch (NullPointerException | IllegalStateException | SecurityException ex) {
            LOG.warn("trajectory snapshot encoding failed: {}", ex.getMessage());
        }
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        snapshotRevisions.remove(span.getSpanContext().getSpanId());
        if (consumers.isEmpty()) {
            return;
        }
        try {
            SpanData data = span.toSpanData();
            OtlpSpanRecord record = new OtlpSpanRecord(
                    data.getSpanContext().getTraceId(),
                    data.getSpanContext().getSpanId(),
                    parentSpanId(data).orElse(null),
                    stringValue(data, TrajectorySemConv.GEN_AI_CONVERSATION_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_REQUEST_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_RUN_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_AGENT_MODE).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_DISPLAY_NAME).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_KIND).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_PARENT_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_EXECUTION_SUBJECT_SESSION_ID).orElse(null),
                    stringValue(data, TrajectorySemConv.OJ_TRAJECTORY_RECORD_KIND).orElse(null),
                    0L,
                    schemaVersion(data),
                    data.getStatus() != null
                            && data.getStatus().getStatusCode() == StatusCode.ERROR,
                    data.getEndEpochNanos(),
                    OtlpJsonCodec.encodeFinalSpan(data, serviceName));
            for (TrajectoryRecordConsumer consumer : consumers) {
                try {
                    consumer.consume(record);
                } catch (NullPointerException | IllegalStateException | SecurityException ex) {
                    LOG.warn("trajectory consumer rejected record: {}", ex.getMessage());
                }
            }
        } catch (NullPointerException | IllegalStateException | SecurityException ex) {
            LOG.warn("trajectory record encoding failed: {}", ex.getMessage());
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    private void dispatchSnapshot(OtlpSpanSnapshotRecord record) {
        for (TrajectoryRecordConsumer consumer : consumers) {
            try {
                consumer.consumeSnapshot(record);
            } catch (NullPointerException | IllegalStateException | SecurityException ex) {
                LOG.warn("trajectory consumer rejected snapshot: {}", ex.getMessage());
            }
        }
    }

    private long snapshotRevision(String spanId) {
        AtomicLong counter = new AtomicLong();
        AtomicLong existing = snapshotRevisions.putIfAbsent(spanId, counter);
        if (existing == null) {
            return 0L;
        }
        return existing.incrementAndGet();
    }

    private static Optional<String> parentSpanId(SpanData span) {
        String parentSpanId = span.getParentSpanContext().getSpanId();
        if (parentSpanId == null || parentSpanId.isEmpty() || "0000000000000000".equals(parentSpanId)) {
            return Optional.empty();
        }
        return Optional.of(parentSpanId);
    }

    private static Optional<String> stringValue(SpanData span, String key) {
        Object value = span.getAttributes().get(AttributeKey.stringKey(key));
        if (value instanceof String text) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private static String schemaVersion(SpanData span) {
        return stringValue(span, TrajectorySemConv.OJ_TRAJECTORY_SCHEMA_VERSION)
                .orElse(TrajectorySemConv.SCHEMA_VERSION_VALUE);
    }
}
