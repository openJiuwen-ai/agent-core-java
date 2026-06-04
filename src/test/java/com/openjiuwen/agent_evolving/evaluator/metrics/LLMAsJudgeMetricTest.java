package com.openjiuwen.agent_evolving.evaluator.metrics;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LLM-as-judge metric behavior.
 *
 * <p>Mirrors Python's {@code test_llm_as_judge.py} in
 * {@code tests/unit_tests/agent_evolving/evaluator/test_metrics}.
 */
class LLMAsJudgeMetricTest {

    @Test
    void computeReturnsScoresFromLLMResult() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(
                assistant("```json\n{\"result\": true}\n```"),
                assistant("```json\n{\"result\": \"  false  \"}\n```"),
                assistant("not json")
        );

        assertEquals(1.0, metric.compute("prediction", "label", Map.of("question", "question")));
        assertEquals(0.0, metric.compute("prediction", "label", Map.of()));
        assertEquals(0.0, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeParsesBooleanFalse() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": false}\n```"));

        assertEquals(0.0, metric.compute("prediction", "label", Map.of("question", "question")));
    }

    @Test
    void computeParsesStringTrue() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": \"true\"}\n```"));

        assertEquals(1.0, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeParsesStringFalse() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": \"false\"}\n```"));

        assertEquals(0.0, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeTrimsStringResult() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": \"  true  \"}\n```"));

        assertEquals(1.0, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeReturnsZeroForUnexpectedStringResult() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": \"yes\"}\n```"));

        assertEquals(0.0, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeHandlesNoneQuestionLikePython() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": true}\n```"));

        assertEquals(1.0, metric.compute("prediction", "label", Map.of("question", "")));
    }

    @Test
    void computeFormatsPromptWithPythonStringSemantics() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": true}\n```"));

        assertEquals(1.0, metric.compute(true, null, Map.of("question", false)));

        String prompt = metric.lastPrompt();
        assertTrue(prompt.contains("[Question]: \n\nThe following"));
        assertTrue(prompt.contains("[Expected Answer]: None"));
        assertTrue(prompt.contains("[Model Response]: True"));
    }

    @Test
    void metricExposesStableMetadata() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": true}\n```"));

        assertEquals("llm_as_judge", metric.getName());
        assertTrue(metric.isHigherIsBetter());
    }

    @Test
    void constructorAcceptsDefaultUserMetrics() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": true}\n```"));

        assertEquals("llm_as_judge", metric.getName());
    }

    @Test
    void constructorAcceptsCustomUserMetrics() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric("custom_metric",
                assistant("```json\n{\"result\": true}\n```"));

        assertEquals(1.0, metric.compute("prediction", "label", Map.of()));
    }

    @Test
    void computeReturnsZeroWhenModelInvocationFails() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(new RuntimeException("model error"));

        assertEquals(0.0, metric.compute("prediction", "label", Map.of("question", "question")));
    }

    private static AssistantMessage assistant(String content) {
        return AssistantMessage.builder().content(content).build();
    }

    private static final class StubLLMAsJudgeMetric extends LLMAsJudgeMetric {
        private final Deque<Object> scriptedResponses = new ArrayDeque<>();
        private List<?> lastMessages;

        private StubLLMAsJudgeMetric(Object... scriptedResponses) {
            this("", scriptedResponses);
        }

        private StubLLMAsJudgeMetric(String userMetrics, Object... scriptedResponses) {
            super(
                    ModelRequestConfig.builder().modelName("test-model").build(),
                    ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("test")
                            .apiBase("https://test.example.com")
                            .build(),
                    userMetrics
            );
            this.scriptedResponses.addAll(List.of(scriptedResponses));
        }

        @Override
        protected AssistantMessage invokeModel(List<?> messages) {
            this.lastMessages = messages;
            Object next = scriptedResponses.removeFirst();
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return (AssistantMessage) next;
        }

        private String lastPrompt() {
            StringBuilder builder = new StringBuilder();
            for (Object message : lastMessages) {
                if (message instanceof BaseMessage baseMessage) {
                    builder.append(baseMessage.getContentAsString());
                } else if (message != null) {
                    builder.append(message);
                }
                builder.append('\n');
            }
            return builder.toString();
        }
    }
}
