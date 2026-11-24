/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/**
 * Checks workflow trace snapshots taken while multiple component events append stream frames.
 *
 * @since 2026-11-24
 */
class WorkflowStreamSpanConcurrencyTest {
    private static final int WRITERS = 8;
    private static final int FRAMES_PER_WRITER = 128;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parallelAppendsAndSnapshotsRetainEveryStreamFrame()
            throws InterruptedException, ExecutionException, TimeoutException {
        TraceWorkflowSpan span = new TraceWorkflowSpan();
        CyclicBarrier start = new CyclicBarrier(WRITERS);
        ExecutorService pool = OpenJiuwenExecutors.newFixedThreadPool("workflow-trace-writer", WRITERS, true);
        try {
            List<Future<Void>> writers = new ArrayList<>();
            for (int worker = 0; worker < WRITERS; worker++) {
                int firstFrame = worker * FRAMES_PER_WRITER;
                writers.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    appendFrames(span, firstFrame);
                    return null;
                }));
            }
            for (Future<Void> writer : writers) {
                writer.get(10, TimeUnit.SECONDS);
            }
        } finally {
            OpenJiuwenExecutors.shutdown(pool);
        }

        List<Integer> expected = IntStream.range(0, WRITERS * FRAMES_PER_WRITER).boxed().toList();
        assertThat(span.getStreamInputs()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(span.getStreamOutputs()).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static void appendFrames(TraceWorkflowSpan span, int firstFrame) {
        for (int offset = 0; offset < FRAMES_PER_WRITER; offset++) {
            int frame = firstFrame + offset;
            span.appendStreamInput(frame);
            span.appendStreamOutput(frame);
            if (offset % 16 == 0) {
                TraceWorkflowSpan snapshot = span.snapshot();
                assertThat(snapshot.getStreamInputs()).doesNotContainNull().containsAll(snapshot.getStreamOutputs());
            }
        }
    }

    @Test
    void streamListAccessIsIsolatedAndSnapshotsCopyNestedFrames() {
        TraceWorkflowSpan span = new TraceWorkflowSpan();
        List<String> frame = new ArrayList<>(List.of("original"));
        List<Object> supplied = new ArrayList<>(List.of(frame));
        span.setStreamInputs(supplied);
        span.update(Map.of("stream_outputs", supplied));
        supplied.clear();
        span.getStreamInputs().clear();
        span.getStreamOutputs().clear();
        assertThat(span.getStreamInputs()).containsExactly(frame);
        assertThat(span.getStreamOutputs()).containsExactly(frame);

        TraceWorkflowSpan snapshot = span.snapshot();
        frame.add("later");
        span.appendStreamInputs(null);
        span.appendStreamOutput(null);
        assertThat(snapshot.getStreamInputs()).containsExactly(List.of("original"));
        assertThat(snapshot.getStreamOutputs()).containsExactly(List.of("original"));
        assertThat(span.getStreamInputs()).containsExactly(frame, null);
        assertThat(span.getStreamOutputs()).containsExactly(frame, null);
    }

    @Test
    void emptyAndAbsentStreamFieldsKeepTheirJsonRepresentations() {
        TraceWorkflowSpan span = new TraceWorkflowSpan();
        span.update(Map.of("stream_inputs", List.of(), "streamOutputs", List.of()));
        JsonNode empty = MAPPER.valueToTree(span);
        assertThat(empty.get("streamInputs").isArray()).isTrue();
        assertThat(empty.get("streamOutputs").isArray()).isTrue();
        span.appendStreamInput("in");
        span.appendStreamOutput("out");

        span.setStreamInputs(null);
        span.setStreamOutputs(null);
        JsonNode absent = MAPPER.valueToTree(span.snapshot());
        assertThat(absent.has("streamInputs")).isFalse();
        assertThat(absent.has("streamOutputs")).isFalse();
    }
}
