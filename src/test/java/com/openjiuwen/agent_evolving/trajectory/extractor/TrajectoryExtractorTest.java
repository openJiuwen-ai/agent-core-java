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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for TrajectoryExtractor.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.trajectory.test_extractor}.
 */
class TrajectoryExtractorTest {

    @Test
    void extractNoTracer() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSession session = new FakeSession(null);

        Trajectory result = extractor.extract(session, "case1");

        assertEquals("case1", result.getCaseId());
        assertEquals(List.of(), result.getSteps());
        assertNull(result.getTraceId());
        assertNull(result.getEdges());
    }

    @Test
    void extractTracerNoAgentManager() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeTracer tracer = new FakeTracer(null);

        Trajectory result = extractor.extract(new FakeSession(tracer), "case1");

        assertEquals("case1", result.getCaseId());
        assertEquals(List.of(), result.getSteps());
    }

    @Test
    void extractLlmSpan() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("llm");

        Trajectory result = extractor.extract(session(span), "case1");

        assertEquals(1, result.getSteps().size());
        TrajectoryStep step = result.getSteps().getFirst();
        assertEquals("llm", step.getKind());
        assertEquals("test_op", step.getMeta().get("operator_id"));
    }

    @Test
    void extractPluginSpanAsTool() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("plugin");

        Trajectory result = extractor.extract(session(span), "case1");

        assertEquals(1, result.getSteps().size());
        assertEquals("tool", result.getSteps().getFirst().getKind());
    }

    @Test
    void extractWithError() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("llm");
        span.error = "Test error";

        Trajectory result = extractor.extract(session(span), "case1");

        assertEquals("Test error", result.getSteps().getFirst().getError());
    }

    @Test
    void extractNestedInputsOutputsForLlmDetail() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("llm");
        span.on_invoke_data = List.of(Map.of("llm_params", Map.of(
                "model", "gpt-4",
                "messages", List.of(Map.of("role", "user", "content", "nested"))
        )));
        span.inputs = Map.of("inputs", Map.of("query", "nested"));
        span.outputs = Map.of("outputs", Map.of("response", "nested"));

        Trajectory result = extractor.extract(session(span), "case1");

        Object detail = result.getSteps().getFirst().getDetail();
        LLMCallDetail llm = assertInstanceOf(LLMCallDetail.class, detail);
        assertEquals(List.of(Map.of("role", "user", "content", "nested")), llm.getMessages());
    }

    @Test
    void extractUsesLlmCallIdAsOperatorId() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("llm");
        span.operator_id = null;
        span.llm_call_id = "llm_call_1";

        Trajectory result = extractor.extract(session(span), "case1");

        assertEquals("llm_call_1", result.getSteps().getFirst().getMeta().get("operator_id"));
    }

    @Test
    void extractToolSpanWithDetail() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("plugin");
        span.name = "test_tool";
        span.inputs = Map.of("inputs", Map.of("arg", "value"));
        span.outputs = Map.of("outputs", Map.of("result", "success"));

        Trajectory result = extractor.extract(session(span), "case1");

        TrajectoryStep step = result.getSteps().getFirst();
        assertEquals("tool", step.getKind());
        ToolCallDetail detail = assertInstanceOf(ToolCallDetail.class, step.getDetail());
        assertEquals("test_tool", detail.getToolName());
        assertEquals(Map.of("arg", "value"), detail.getCallArgs());
        assertEquals(Map.of("result", "success"), detail.getCallResult());
    }

    @Test
    void extractMemorySpanWithMetaBackup() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("memory");
        span.inputs = Map.of("inputs", Map.of("key", "value"));
        span.outputs = Map.of("outputs", Map.of("result", "data"));

        Trajectory result = extractor.extract(session(span), "case1");

        TrajectoryStep step = result.getSteps().getFirst();
        assertEquals("memory", step.getKind());
        assertEquals(Map.of("key", "value"), step.getMeta().get("inputs"));
        assertEquals(Map.of("result", "data"), step.getMeta().get("outputs"));
    }

    @Test
    void dtToMsNone() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("llm");
        span.start_time = null;
        span.end_time = null;

        Trajectory result = extractor.extract(session(span), "case1");

        assertNull(result.getSteps().getFirst().getStartTimeMs());
        assertNull(result.getSteps().getFirst().getEndTimeMs());
    }

    @Test
    void dtToMsValidDatetime() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("llm");
        Instant instant = Instant.parse("2024-01-01T12:00:00Z");
        span.start_time = instant;

        Trajectory result = extractor.extract(session(span), "case1");

        assertEquals(instant.toEpochMilli(), result.getSteps().getFirst().getStartTimeMs());
    }

    @Test
    void llmResponseTokenFieldsAreLiftedOut() {
        TrajectoryExtractor extractor = new TrajectoryExtractor();
        FakeSpan span = span("llm");
        span.on_invoke_data = List.of(Map.of("llm_params", Map.of(
                "model", "gpt-4",
                "messages", List.of()
        )));
        span.outputs = Map.of("outputs", new LinkedHashMap<>(Map.of(
                "prompt_token_ids", List.of(1, 2),
                "completion_token_ids", List.of(3),
                "logprobs", List.of(0.1),
                "content", "ok"
        )));

        Trajectory result = extractor.extract(session(span), "case1");

        TrajectoryStep step = result.getSteps().getFirst();
        LLMCallDetail detail = assertInstanceOf(LLMCallDetail.class, step.getDetail());
        assertEquals(List.of(1, 2), step.getPromptTokenIds());
        assertEquals(List.of(3), step.getCompletionTokenIds());
        assertEquals(List.of(0.1), step.getLogprobs());
        assertFalse(detail.getResponse().containsKey("prompt_token_ids"));
        assertEquals("ok", detail.getResponse().get("content"));
    }

    private static FakeSpan span(String invokeType) {
        FakeSpan span = new FakeSpan();
        span.invoke_type = invokeType;
        return span;
    }

    private static FakeSession session(FakeSpan... spans) {
        return new FakeSession(new FakeTracer(new FakeSpanManager(List.of(spans))));
    }

    public static final class FakeSession {
        private final Object tracer;

        FakeSession(Object tracer) {
            this.tracer = tracer;
        }

        public Object tracer() {
            return tracer;
        }
    }

    public static final class FakeTracer {
        public Object tracer_agent_span_manager;

        FakeTracer(Object tracerAgentSpanManager) {
            this.tracer_agent_span_manager = tracerAgentSpanManager;
        }
    }

    public static final class FakeSpanManager {
        private final List<Object> spans;

        FakeSpanManager(List<?> spans) {
            this.spans = new ArrayList<>(spans);
        }

        public List<Object> get_all_spans() {
            return spans;
        }
    }

    public static final class FakeSpan {
        public String invoke_type = "llm";
        public String invoke_id = "inv1";
        public Object inputs = Map.of("inputs", Map.of("query", "test"));
        public Object outputs = Map.of("outputs", Map.of("response", "test"));
        public Object error;
        public Object start_time = Instant.now();
        public Object end_time = Instant.now();
        public Map<String, Object> meta_data = new LinkedHashMap<>();
        public Object operator_id = "test_op";
        public Object llm_call_id;
        public String name = "test_span";
        public Object parent_invoke_id;
        public Object child_invokes_id;
        public Object agent_id;
        public List<?> on_invoke_data = List.of();
    }
}
