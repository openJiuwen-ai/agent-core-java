package com.openjiuwen.agentevolving.evaluator.metrics;

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
    void metricExposesStableMetadata() {
        StubLLMAsJudgeMetric metric = new StubLLMAsJudgeMetric(assistant("```json\n{\"result\": true}\n```"));

        assertEquals("llm_as_judge", metric.getName());
        assertTrue(metric.isHigherIsBetter());
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

        private StubLLMAsJudgeMetric(Object... scriptedResponses) {
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
        protected AssistantMessage invokeModel(List<BaseMessage> messages) throws Exception {
            Object next = scriptedResponses.removeFirst();
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return (AssistantMessage) next;
        }
    }
}
