/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Behavior tests for {@link SpanRecordProcessor} record production and
 * consumer dispatch.
 *
 * @since 0.1.18
 */
class SpanRecordProcessorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SpanRecordProcessor processor;
    private SdkTracerProvider provider;
    private Tracer tracer;
    private List<OtlpSpanRecord> finals;
    private List<OtlpSpanSnapshotRecord> snapshots;
    private List<StreamFrameRecord> frames;

    @BeforeEach
    void setUp() {
        processor = new SpanRecordProcessor("test-service");
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(processor)
                .build();
        tracer = provider.get("test", "1");
        finals = new CopyOnWriteArrayList<>();
        snapshots = new CopyOnWriteArrayList<>();
        frames = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        provider.shutdown().join(1, java.util.concurrent.TimeUnit.SECONDS);
        TrajectorySpanContext.clearThreadState();
    }

    @Test
    void emitsFinalRecordWithRoutingHintsAndSingleSpanJson() throws Exception {
        processor.registerConsumer(new RecordingConsumer());
        Span span = tracer.spanBuilder("chat model-a")
                .setAttribute(TrajectorySemConv.GEN_AI_CONVERSATION_ID, "session-1")
                .setAttribute(TrajectorySemConv.OJ_REQUEST_ID, "req-1")
                .setAttribute(TrajectorySemConv.OJ_RUN_ID, "run-1")
                .setAttribute(TrajectorySemConv.OJ_AGENT_MODE, "work")
                .setAttribute(TrajectorySemConv.OJ_TRAJECTORY_RECORD_KIND, "inference")
                .setAttribute(TrajectorySemConv.OJ_EXECUTION_SUBJECT_ID, "main")
                .startSpan();
        span.end();

        assertThat(finals).hasSize(1);
        OtlpSpanRecord record = finals.get(0);
        assertThat(record.recordId()).isEqualTo(record.traceId() + ":" + record.spanId());
        assertThat(record.traceId()).hasSize(32).matches("[0-9a-f]+");
        assertThat(record.spanId()).hasSize(16).matches("[0-9a-f]+");
        assertThat(record.sessionId()).isEqualTo("session-1");
        assertThat(record.requestId()).isEqualTo("req-1");
        assertThat(record.runId()).isEqualTo("run-1");
        assertThat(record.agentMode()).isEqualTo("work");
        assertThat(record.recordKind()).isEqualTo("inference");
        assertThat(record.executionSubjectId()).isEqualTo("main");
        assertThat(record.schemaVersion()).isEqualTo("2");
        assertThat(record.lifecycle()).isEqualTo("final");
        assertThat(record.hasError()).isFalse();

        JsonNode root = MAPPER.readTree(record.otlpJson());
        JsonNode spanNode = root.at("/resourceSpans/0/scopeSpans/0/spans/0");
        assertThat(spanNode.get("spanId").asText()).isEqualTo(record.spanId());
        assertThat(root.at("/resourceSpans/0/scopeSpans/0/spans").size()).isEqualTo(1);
    }

    @Test
    void emitsRunningSnapshotOnStartAndRemovesRevisionStateOnEnd() {
        processor.registerConsumer(new RecordingConsumer());
        Span span = tracer.spanBuilder("chat model-a")
                .setAttribute(TrajectorySemConv.GEN_AI_CONVERSATION_ID, "session-1")
                .startSpan();

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).lifecycle()).isEqualTo("running");
        assertThat(snapshots.get(0).updateKind()).isEqualTo("started");
        assertThat(snapshots.get(0).sessionId()).isEqualTo("session-1");
        assertThat(snapshots.get(0).otlpJson()).doesNotContain("endTimeUnixNano");

        span.end();
        assertThat(finals).hasSize(1);
    }

    @Test
    void deduplicatesConsumerRegistrationByIdentity() {
        RecordingConsumer consumer = new RecordingConsumer();
        processor.registerConsumer(consumer);
        processor.registerConsumer(consumer);

        Span span = tracer.spanBuilder("once").startSpan();
        span.end();

        assertThat(finals).hasSize(1);
    }

    @Test
    void unregisteredConsumerStopsReceivingRecords() {
        RecordingConsumer consumer = new RecordingConsumer();
        processor.registerConsumer(consumer);
        processor.unregisterConsumer(consumer);

        Span span = tracer.spanBuilder("silent").startSpan();
        span.end();

        assertThat(finals).isEmpty();
    }

    @Test
    void publishesStreamFramesToConsumers() {
        processor.registerConsumer(new RecordingConsumer());
        processor.publishStreamFrame(new StreamFrameRecord(
                "a".repeat(32), "b".repeat(16), "main", 1L,
                StreamFrameRecord.KIND_TEXT_DELTA, 42L, "hello", null, null, null));

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).kind()).isEqualTo("text-delta");
        assertThat(frames.get(0).text()).isEqualTo("hello");
    }

    @Test
    void errorSpanFlagsHasError() {
        processor.registerConsumer(new RecordingConsumer());
        Span span = tracer.spanBuilder("failed")
                .setAttribute(TrajectorySemConv.OJ_TRAJECTORY_RECORD_KIND, "tool")
                .startSpan();
        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "boom");
        span.end();

        assertThat(finals).hasSize(1);
        assertThat(finals.get(0).hasError()).isTrue();
    }

    private class RecordingConsumer implements TrajectoryRecordConsumer {
        @Override
        public void consume(OtlpSpanRecord record) {
            finals.add(record);
        }

        @Override
        public void consumeSnapshot(OtlpSpanSnapshotRecord record) {
            snapshots.add(record);
        }

        @Override
        public void consumeStreamFrame(StreamFrameRecord frame) {
            frames.add(frame);
        }
    }
}
