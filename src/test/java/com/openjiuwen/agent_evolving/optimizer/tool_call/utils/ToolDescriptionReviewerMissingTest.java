/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_customized_reviewer} in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_customized_reviewer.py}.
 */
class ToolDescriptionReviewerMissingTest {

    @Test
    void formatCleanCrossCheckTranslate() {
        ScriptedReviewer reviewer = new ScriptedReviewer(
                "{\"name\":\"f\",\"description\":\"d\",\"parameters\":{}}",
                "{\"name\":\"f\",\"description\":\"d2\",\"parameters\":{}}",
                "{\"name\":\"f\",\"description\":\"d3\",\"parameters\":{}}",
                "{\"name\":\"f\",\"description\":\"中文\",\"parameters\":{}}"
        );

        Map<String, Object> schema = map("name", "", "description", "", "parameters", Map.of());
        Object formatted = reviewer.format(schema, "raw desc");
        assertThat(asMap(formatted).get("name")).isEqualTo("f");

        Object cleaned = reviewer.cleanAndDeduplicate(formatted);
        assertThat(asMap(cleaned).get("description")).isEqualTo("d2");

        Object checked = reviewer.crossCheck(cleaned, "ori");
        assertThat(asMap(checked).get("description")).isEqualTo("d3");

        reviewer.setMostlyEnglish(true);
        Object translated = reviewer.translateToChinese(Map.of("text", "hello world"));
        assertThat(asMap(translated).get("description")).isEqualTo("中文");

        reviewer.setMostlyEnglish(false);
        Map<String, Object> chinese = Map.of("text", "你好");
        assertThat(reviewer.translateToChinese(chinese)).isEqualTo(chinese);
    }

    @Test
    void processWithStepsAndUnknownStep() {
        StepReviewer reviewer = new StepReviewer();
        Map<String, Object> source = map("a", 1);

        Object out = reviewer.process(source, "ori", List.of("clean", "translate"));
        assertThat(out).isEqualTo(map("t", map("c", source)));

        Object out2 = reviewer.process(source, "ori", List.of("cross_check"));
        assertThat(asMap(out2).get("ori")).isEqualTo("ori");

        assertThatThrownBy(() -> reviewer.process(source, "ori", List.of("bad")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            result.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return result;
    }

    private static Map<?, ?> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }

    private static final class ScriptedReviewer extends ToolDescriptionReviewer {

        private final Deque<String> responses = new ArrayDeque<>();
        private Boolean mostlyEnglish;

        private ScriptedReviewer(String... responses) {
            super("gpt-eval", "k");
            this.responses.addAll(List.of(responses));
        }

        private void setMostlyEnglish(boolean mostlyEnglish) {
            this.mostlyEnglish = mostlyEnglish;
        }

        @Override
        public boolean isMostlyEnglish(String text) {
            return mostlyEnglish != null ? mostlyEnglish : super.isMostlyEnglish(text);
        }

        @Override
        protected Object invokeRitsResponse(
                String modelId,
                String prompt,
                boolean verbose,
                Map<String, Object> kwargs
        ) {
            String payload = responses.removeFirst();
            Object verifier = kwargs.get("verify_output");
            if (verifier instanceof Function<?, ?> function) {
                @SuppressWarnings("unchecked")
                Function<String, Object> verifyOutput = (Function<String, Object>) function;
                return verifyOutput.apply(payload);
            }
            return payload;
        }
    }

    private static final class StepReviewer extends ToolDescriptionReviewer {

        private StepReviewer() {
            super("gpt-eval", "k");
        }

        @Override
        public Object cleanAndDeduplicate(Object data) {
            return map("c", data);
        }

        @Override
        public Object crossCheck(Object data, String oriTool) {
            return map("x", data, "ori", oriTool);
        }

        @Override
        public Object translateToChinese(Object data) {
            return map("t", data);
        }
    }
}
