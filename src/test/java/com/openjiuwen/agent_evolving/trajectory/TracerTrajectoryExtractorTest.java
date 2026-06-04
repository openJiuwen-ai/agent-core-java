package com.openjiuwen.agent_evolving.trajectory;

import com.openjiuwen.agent_evolving.trajectory.extractor.TrajectoryExtractor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the operation module's backward-compatible extractor alias.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.trajectory.operation},
 * where {@code TracerTrajectoryExtractor} is an alias of
 * {@code TrajectoryExtractor}.</p>
 */
class TracerTrajectoryExtractorTest {

    @Test
    void tracerExtractorIsTrajectoryExtractorAlias() {
        TracerTrajectoryExtractor extractor = new TracerTrajectoryExtractor();

        assertInstanceOf(TrajectoryExtractor.class, extractor);
    }

    @Test
    void inheritedExtractMatchesCanonicalExtractorBehavior() {
        TracerTrajectoryExtractor extractor = new TracerTrajectoryExtractor();
        FakeSpan span = span("llm");
        span.operator_id = null;
        span.llm_call_id = "llm_call_1";
        span.on_invoke_data = List.of(Map.of("llm_params", Map.of(
                "model", "gpt-4",
                "messages", List.of(Map.of("role", "user", "content", "hello"))
        )));
        span.outputs = Map.of("outputs", new LinkedHashMap<>(Map.of(
                "prompt_token_ids", List.of(1, 2),
                "completion_token_ids", List.of(3),
                "logprobs", List.of(0.1),
                "content", "ok"
        )));

        Trajectory result = extractor.extract(session(span), "case_alias");

        assertEquals("case_alias", result.getCaseId());
        assertEquals(1, result.getSteps().size());
        TrajectoryStep step = result.getSteps().getFirst();
        assertEquals("llm", step.getKind());
        assertEquals("llm_call_1", step.getMeta().get("operator_id"));
        assertEquals(List.of(1, 2), step.getPromptTokenIds());
        assertEquals(List.of(3), step.getCompletionTokenIds());
        assertEquals(List.of(0.1), step.getLogprobs());
    }

    @Test
    void executionSpecAdapterUsesCaseIdAndExecutionId() {
        TracerTrajectoryExtractor extractor = new TracerTrajectoryExtractor();
        ExecutionSpec execution = ExecutionSpec.builder()
                .caseId("case_from_spec")
                .executionId("exec_from_spec")
                .build();

        Trajectory result = extractor.extract(session(span("plugin")), execution);

        assertEquals("case_from_spec", result.getCaseId());
        assertEquals("exec_from_spec", result.getExecutionId());
        assertEquals("tool", result.getSteps().getFirst().getKind());
    }

    @Test
    void executionSpecAdapterHandlesNullExecutionLikeMissingCaseId() {
        TracerTrajectoryExtractor extractor = new TracerTrajectoryExtractor();

        Trajectory result = extractor.extract(new FakeSession(null), (ExecutionSpec) null);

        assertEquals("unknown", result.getCaseId());
        assertNotNull(result.getExecutionId());
        assertEquals(List.of(), result.getSteps());
    }

    @Test
    void inheritedTimestampConversionStillWorksThroughAlias() {
        TracerTrajectoryExtractor extractor = new TracerTrajectoryExtractor();
        FakeSpan span = span("memory");
        Instant instant = Instant.parse("2024-01-01T12:00:00Z");
        span.start_time = instant;

        Trajectory result = extractor.extract(session(span), "case_time");

        assertEquals(instant.toEpochMilli(), result.getSteps().getFirst().getStartTimeMs());
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

        private FakeSession(Object tracer) {
            this.tracer = tracer;
        }

        public Object tracer() {
            return tracer;
        }
    }

    public static final class FakeTracer {
        private final Object tracer_agent_span_manager;

        private FakeTracer(Object spanManager) {
            this.tracer_agent_span_manager = spanManager;
        }
    }

    public static final class FakeSpanManager {
        private final List<Object> spans;

        private FakeSpanManager(List<Object> spans) {
            this.spans = spans;
        }

        public List<Object> get_all_spans() {
            return spans;
        }
    }

    public static final class FakeSpan {
        private String invoke_type;
        private String operator_id = "test_op";
        private String llm_call_id;
        private String name = "test_tool";
        private Object inputs = Map.of("inputs", Map.of("arg", "value"));
        private Object outputs = Map.of("outputs", Map.of("result", "success"));
        private Object error;
        private Object start_time;
        private Object end_time;
        private Object on_invoke_data;
        private Map<String, Object> meta_data = new LinkedHashMap<>();
    }
}
