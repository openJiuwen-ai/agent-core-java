/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Tests for base method helpers.
 *
 * <p>Mirrors Python's {@code BaseMethod} and module helpers in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/base_method.py}.</p>
 *
 * <p>Mirrors Python's {@code test_format_and_base_method} module in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_format_and_base_method.py}.</p>
 */
class BaseMethodTest {

    @Test
    void parseJsonPrefersHeaderAndFallbackLiteralEval() {
        String text = "noise {\"answer\": \"ok\", \"x\": 1} tail";
        assertEquals(Map.of("answer", "ok", "x", 1), FormatUtils.parseJson(text, "answer"));

        String literal = "{'answer': 'ok', 'x': 2}";
        assertEquals(Map.of("answer", "ok", "x", 2), BaseMethod.parseJson(literal));
    }

    @Test
    void formatPromptLlamaAndPrintBoldNoop() {
        assertEquals("sysuser", FormatUtils.formatPromptLlama("sys", "user"));
        assertEquals("ab", BaseMethod.formatPromptLlama("a", "b"));
        assertDoesNotThrow(() -> BaseMethod.printBold("hello"));
    }

    @Test
    void constructorStoresConfigAndVerboseFlag() {
        BaseMethod method = new BaseMethod(Map.of("verbose", 1, "gen_model_id", "m", "llm_api_key", "k"));

        assertTrue(method.isVerbose());
        assertEquals("m", method.getConfig().get("gen_model_id"));
        assertFalse(new BaseMethod(Map.of()).isVerbose());
    }

    @Test
    void baseMethodProduceAnswerFromApiCallSuccess() {
        RecordingBaseMethod method = new SuccessRecordingBaseMethod(Map.of(
                "verbose", false,
                "gen_model_id", "gpt-x",
                "llm_api_key", "k"
        ));

        String answer = method.produceAnswerFromApiCall("inst", "doc", "api_result");

        assertEquals("final answer", answer);
        assertEquals("gpt-x", method.modelId);
        assertEquals("k", method.llmApiKey);
        assertTrue(method.prompt.contains("inst"));
        assertEquals(15, method.kwargs.get("max_attempts"));
        assertEquals(List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"), method.kwargs.get("stop_sequences"));
    }

    @Test
    void baseMethodProduceAnswerFromApiCallVerifyError() {
        RecordingBaseMethod method = new ErrorRecordingBaseMethod(Map.of(
                "verbose", false,
                "gen_model_id", "gpt-x",
                "llm_api_key", "k"
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> method.produceAnswerFromApiCall("inst", "doc", "api_result")
        );
    }

    private abstract static class RecordingBaseMethod extends BaseMethod {
        private String modelId;
        private String prompt;
        private String llmApiKey;
        private Map<String, Object> kwargs;

        private RecordingBaseMethod(Map<String, Object> config) {
            super(config);
        }

        @Override
        protected Object invokeRitsResponse(
                String modelId,
                String prompt,
                String llmApiKey,
                Function<String, Object> verifyFn,
                Map<String, Object> kwargs
        ) {
            this.modelId = modelId;
            this.prompt = prompt;
            this.llmApiKey = llmApiKey;
            this.kwargs = kwargs;
            return verifyFn.apply(response());
        }

        protected abstract String response();
    }

    private static final class SuccessRecordingBaseMethod extends RecordingBaseMethod {
        private SuccessRecordingBaseMethod(Map<String, Object> config) {
            super(config);
        }

        @Override
        protected String response() {
            return "{\"answer\": \"final answer\"}";
        }
    }

    private static final class ErrorRecordingBaseMethod extends RecordingBaseMethod {
        private ErrorRecordingBaseMethod(Map<String, Object> config) {
            super(config);
        }

        @Override
        protected String response() {
            return "{\"error\":\"bad\"}";
        }
    }
}
