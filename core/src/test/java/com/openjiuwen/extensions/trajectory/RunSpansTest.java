/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tests for run-root span opening, registration and terminal paths.
 *
 * @since 0.1.18
 */
class RunSpansTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SpanRecordProcessor processor;
    private List<OtlpSpanRecord> finals;

    @BeforeEach
    void setUp() {
        TrajectoryObservability.init(new TrajectoryObservabilityConfig(
                true, "run-test-service", 1.0d));
        processor = TrajectoryObservability.active().getSpanRecordProcessor();
        finals = new CopyOnWriteArrayList<>();
        processor.registerConsumer(new TrajectoryRecordConsumer() {
            @Override
            public void consume(OtlpSpanRecord record) {
                finals.add(record);
            }
        });
    }

    @AfterEach
    void tearDown() {
        TrajectoryObservability.shutdown();
        TrajectorySpanContext.clearThreadState();
    }

    @Test
    void openRegistersRunAndStampsRunSpanAttributes() throws Exception {
        RunSpans.RunSpan run = RunSpans.open(runInfo("session-1"));

        assertThat(TrajectorySpanContext.currentRun("session-1")).isPresent();
        assertThat(run.info().sessionId()).isEqualTo("session-1");
        assertThat(run.span().getSpanContext().isValid()).isTrue();

        run.complete();
        assertThat(TrajectorySpanContext.currentRun("session-1")).isEmpty();
        assertThat(finals).hasSize(1);

        JsonNode root = MAPPER.readTree(finals.get(0).otlpJson());
        JsonNode spanNode = root.at("/resourceSpans/0/scopeSpans/0/spans/0");
        assertThat(spanNode.get("name").asText()).isEqualTo("agent.work.session-1");
        assertThat(spanNode.get("kind").asInt()).isEqualTo(2);
        assertThat(attributeValue(spanNode, TrajectorySemConv.OJ_TRACE_ROOT)).hasValue("true");
        assertThat(attributeValue(spanNode, TrajectorySemConv.OJ_TRAJECTORY_RECORD_KIND))
                .hasValue("turn");
        assertThat(attributeValue(spanNode, TrajectorySemConv.GEN_AI_OPERATION_NAME))
                .hasValue("invoke_agent");
        assertThat(attributeValue(spanNode, TrajectorySemConv.OJ_TURN_NUMBER)).hasValue("3");
        assertThat(attributeValue(spanNode, TrajectorySemConv.OJ_EXECUTION_SUBJECT_ID))
                .hasValue("main");
    }

    @Test
    void duplicateOpenForSameSessionIsRejected() {
        RunSpans.RunSpan first = RunSpans.open(runInfo("session-dup"));
        try {
            assertThatThrownBy(() -> RunSpans.open(runInfo("session-dup")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("session-dup");
        } finally {
            first.complete();
        }
    }

    @Test
    void failMarksErrorStatusAndStillUnregisters() {
        RunSpans.RunSpan run = RunSpans.open(runInfo("session-err"));
        run.fail(new IllegalStateException("stream blew up"));

        assertThat(TrajectorySpanContext.currentRun("session-err")).isEmpty();
        assertThat(finals).hasSize(1);
        assertThat(finals.get(0).hasError()).isTrue();
    }

    @Test
    void closeIsIdempotent() {
        RunSpans.RunSpan run = RunSpans.open(runInfo("session-idem"));
        run.complete();
        run.complete();
        run.close();

        assertThat(finals).hasSize(1);
        assertThat(TrajectorySpanContext.currentRun("session-idem")).isEmpty();
    }

    @Test
    void openWithoutRuntimeIsRejected() {
        TrajectoryObservability.shutdown();
        assertThatThrownBy(() -> RunSpans.open(runInfo("session-off")))
                .isInstanceOf(IllegalStateException.class);
        TrajectorySpanContext.unregisterRun("session-off");
    }

    private static TrajectorySpanContext.RunInfo runInfo(String sessionId) {
        return TrajectorySpanContext.RunInfo.builder()
                .sessionId(sessionId)
                .mode("work")
                .requestId("req-" + sessionId)
                .runId("run-" + sessionId)
                .turnId("turn-" + sessionId)
                .turnNumber(3L)
                .requestNumber(7L)
                .subjectId("main")
                .subjectDisplayName("Assistant")
                .subjectKind("main_agent")
                .subjectSessionId(sessionId)
                .build();
    }

    private static Optional<String> attributeValue(JsonNode spanNode, String key) {
        for (JsonNode attribute : spanNode.get("attributes")) {
            if (key.equals(attribute.get("key").asText())) {
                JsonNode value = attribute.get("value");
                if (value.has("stringValue")) {
                    return Optional.of(value.get("stringValue").asText());
                }
                if (value.has("intValue")) {
                    return Optional.of(value.get("intValue").asText());
                }
                if (value.has("boolValue")) {
                    return Optional.of(value.get("boolValue").asText());
                }
                return Optional.of(value.toString());
            }
        }
        return Optional.empty();
    }
}
