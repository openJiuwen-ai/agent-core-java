package com.openjiuwen.agent_evolving.evaluator;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaseAndDefaultEvaluatorTest {

    @Test
    void batchEvaluateRejectsLengthMismatch() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        assertThrows(BaseError.class, () ->
                evaluator.batchEvaluate(List.of(makeCase()), List.of(), 1));
    }

    @Test
    void batchEvaluateRejectsInvalidParallelism() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        assertThrows(ValidationError.class, () ->
                evaluator.batchEvaluate(List.of(makeCase()), List.of(Map.of("output", "x")), 0));
        assertThrows(ValidationError.class, () ->
                evaluator.batchEvaluate(List.of(makeCase()), List.of(Map.of("output", "x")), 100));
    }

    @Test
    void batchEvaluateRejectsEmptyInputLikePythonExecutor() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        assertThrows(IllegalArgumentException.class, () ->
                evaluator.batchEvaluate(List.of(), List.of(), 1));
    }

    @Test
    void batchEvaluateRunsEachPrediction() {
        RecordingEvaluator evaluator = new RecordingEvaluator();

        List<EvaluatedCase> result = evaluator.batchEvaluate(
                List.of(makeCase("case_1"), makeCase("case_2")),
                List.of(Map.of("output", "a"), Map.of("output", "b")),
                2
        );

        assertEquals(2, result.size());
        assertIterableEquals(
            List.of("case_1", "case_2"),
            result.stream().map(evaluatedCase -> evaluatedCase.getCaseData().getCaseId()).toList()
        );
        assertEquals(2, evaluator.seenCaseIds.size());
        assertTrue(evaluator.seenCaseIds.containsAll(List.of("case_1", "case_2")));
    }

    @Test
    void defaultEvaluatorReturnsPassAndFailFromParsedJson() {
        StubDefaultEvaluator evaluator = new StubDefaultEvaluator(
                assistant("```json\n{\"result\": true, \"reason\": \"good\"}\n```"),
                assistant("```json\n{\"result\": false, \"reason\": \"bad\"}\n```")
        );

        EvaluatedCase pass = evaluator.evaluate(makeCase(), Map.of("output", "pred"));
        EvaluatedCase fail = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(1.0, pass.getScore());
        assertEquals("good", pass.getReason());
        assertEquals(0.0, fail.getScore());
        assertEquals("bad", fail.getReason());
    }

    @Test
    void defaultEvaluatorRetriesWhenFirstResponseCannotBeParsed() {
        StubDefaultEvaluator evaluator = new StubDefaultEvaluator(
                assistant("invalid json"),
                assistant("```json\n{\"result\": true, \"reason\": \"retry success\"}\n```")
        );

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(1.0, result.getScore());
        assertEquals("retry success", result.getReason());
    }

    @Test
    void defaultEvaluatorReturnsModelErrorWhenInvokeFails() {
        StubDefaultEvaluator evaluator = new StubDefaultEvaluator(new RuntimeException("boom"));

        EvaluatedCase result = evaluator.evaluate(makeCase(), Map.of("output", "pred"));

        assertEquals(0.0, result.getScore());
        assertTrue(result.getReason().contains("model error"));
    }

    private static Case makeCase() {
        return makeCase("case_1");
    }

    private static Case makeCase(String caseId) {
        return new Case(Map.of("q", "test"), Map.of("ans", "expected"), caseId);
    }

    private static AssistantMessage assistant(String content) {
        return AssistantMessage.builder().content(content).build();
    }

    private static final class RecordingEvaluator extends BaseEvaluator {
        private final List<String> seenCaseIds = Collections.synchronizedList(new ArrayList<>());

        @Override
        public EvaluatedCase evaluate(Case caseData, Map<String, Object> predict) {
            seenCaseIds.add(caseData.getCaseId());
            return EvaluatedCase.builder().caseData(caseData).answer(predict).score(1.0).build();
        }
    }

    private static final class StubDefaultEvaluator extends DefaultEvaluator {
        private final Deque<Object> scriptedResponses = new ArrayDeque<>();

        private StubDefaultEvaluator(Object... scriptedResponses) {
            super(
                    ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("test")
                            .apiBase("https://test.example.com")
                            .build()
            );
            this.scriptedResponses.addAll(List.of(scriptedResponses));
        }

        @Override
        protected AssistantMessage invokeModel(List<?> messages) {
            Object next = scriptedResponses.removeFirst();
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return (AssistantMessage) next;
        }
    }
}
