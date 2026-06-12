/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory.extractor;

import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TrajectoryExtractor}.
 *
 * <p>Mirrors Python's
 * {@code tests/unit_tests/agent_evolving/trajectory/test_extractor.py}.</p>
 */
class TrajectoryExtractorTest {

    @Test
    void extractHandlesSessionWithoutTracer() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        TestSession session = new TestSession();

        Trajectory result = extractor.extract(session, "case1");

        assertEquals("case1", result.getCaseId());
        assertTrue(result.getSteps().isEmpty());
        assertNull(result.getTraceId());
        assertNull(result.getEdges());
    }

    @Test
    void extractLlmSpan() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        TestSpan span = makeSpan("llm", "inv1");
        TestSession session = makeSession(List.of(span));

        Trajectory result = extractor.extract(session, "case1");

        assertEquals(1, result.getSteps().size());
        TrajectoryStep step = result.getSteps().getFirst();
        assertEquals("llm", step.getKind());
        assertEquals("test_op", step.getMeta().get("operator_id"));
    }

    @Test
    void extractPluginSpanAsTool() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        TestSpan span = makeSpan("plugin", "inv1");
        TestSession session = makeSession(List.of(span));

        Trajectory result = extractor.extract(session, "case1");

        assertEquals(1, result.getSteps().size());
        assertEquals("tool", result.getSteps().getFirst().getKind());
    }

    @Test
    void extractNestedInputsOutputsIntoLlmDetail() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        TestSpan span = makeSpan("llm", "inv1");
        span.inputs = Map.of("inputs", Map.of("query", "nested"));
        span.outputs = Map.of("outputs", Map.of("response", "nested"));
        span.on_invoke_data = List.of(Map.of(
                "llm_params",
                Map.of(
                        "model", "gpt-4",
                        "messages", List.of(Map.of("role", "user", "content", "nested")))));
        TestSession session = makeSession(List.of(span));

        Trajectory result = extractor.extract(session, "case1");

        TrajectoryStep step = result.getSteps().getFirst();
        assertNotNull(step.getDetail());
        LLMCallDetail detail = assertInstanceOf(LLMCallDetail.class, step.getDetail());
        assertEquals(List.of(Map.of("role", "user", "content", "nested")), detail.getMessages());
    }

    @Test
    void extractUsesLlmCallIdWhenOperatorIdMissing() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        TestSpan span = makeSpan("llm", "inv1");
        span.operator_id = null;
        span.llm_call_id = "llm_call_1";
        TestSession session = makeSession(List.of(span));

        Trajectory result = extractor.extract(session, "case1");

        assertEquals("llm_call_1", result.getSteps().getFirst().getMeta().get("operator_id"));
    }

    @Test
    void extractToolSpanWithDetail() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        TestSpan span = makeSpan("plugin", "inv1");
        span.name = "test_tool";
        span.inputs = Map.of("inputs", Map.of("arg", "value"));
        span.outputs = Map.of("outputs", Map.of("result", "success"));
        TestSession session = makeSession(List.of(span));

        Trajectory result = extractor.extract(session, "case1");

        TrajectoryStep step = result.getSteps().getFirst();
        assertEquals("tool", step.getKind());
        ToolCallDetail detail = assertInstanceOf(ToolCallDetail.class, step.getDetail());
        assertEquals("test_tool", detail.getToolName());
        assertEquals(Map.of("arg", "value"), detail.getCallArgs());
        assertEquals(Map.of("result", "success"), detail.getCallResult());
    }

    @Test
    void extractNonLlmOrToolBacksUpIoIntoMeta() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        TestSpan span = makeSpan("memory", "inv1");
        span.inputs = Map.of("inputs", Map.of("key", "value"));
        span.outputs = Map.of("outputs", Map.of("result", "data"));
        TestSession session = makeSession(List.of(span));

        Trajectory result = extractor.extract(session, "case1");

        TrajectoryStep step = result.getSteps().getFirst();
        assertEquals("memory", step.getKind());
        assertEquals(Map.of("key", "value"), step.getMeta().get("inputs"));
        assertEquals(Map.of("result", "data"), step.getMeta().get("outputs"));
    }

    @Test
    void extractConvertsDatetimeToMilliseconds() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        OffsetDateTime dateTime = OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        TestSpan span = makeSpan("llm", "inv1");
        span.start_time = dateTime;
        span.end_time = null;
        TestSession session = makeSession(List.of(span));

        Trajectory result = extractor.extract(session, "case1");

        assertEquals(dateTime.toInstant().toEpochMilli(), result.getSteps().getFirst().getStartTimeMs());
        assertNull(result.getSteps().getFirst().getEndTimeMs());
    }

    private static TestSession makeSession(List<Object> agentSpans) {
        TestSession session = new TestSession();
        session.tracerValue = new TestTracer(agentSpans);
        return session;
    }

    private static TestSpan makeSpan(String invokeType, String invokeId) {
        TestSpan span = new TestSpan();
        span.invoke_type = invokeType;
        span.invoke_id = invokeId;
        span.inputs = Map.of("inputs", Map.of("query", "test"));
        span.outputs = Map.of("outputs", Map.of("response", "test"));
        span.start_time = Instant.now();
        span.end_time = Instant.now();
        span.meta_data = Map.of();
        span.operator_id = "test_op";
        span.llm_call_id = null;
        span.name = "test_span";
        return span;
    }

    private static final class TestSession {
        private Object tracerValue;

        public Object tracer() {
            return tracerValue;
        }
    }

    private static final class TestTracer {
        private final TestSpanManager tracer_agent_span_manager;

        private TestTracer(List<Object> agentSpans) {
            this.tracer_agent_span_manager = new TestSpanManager(agentSpans);
        }
    }

    private static final class TestSpanManager {
        private final List<Object> spans;

        private TestSpanManager(List<Object> spans) {
            this.spans = spans;
        }

        public List<Object> get_all_spans() {
            return spans;
        }
    }

    private static final class TestSpan {
        private String invoke_type;
        private String invoke_id;
        private Object inputs;
        private Object outputs;
        private Object error;
        private Object start_time;
        private Object end_time;
        private Map<String, Object> meta_data;
        private String operator_id;
        private String llm_call_id;
        private String name;
        private Object parent_invoke_id;
        private Object child_invokes_id;
        private Object agent_id;
        private Object on_invoke_data;
    }
}
