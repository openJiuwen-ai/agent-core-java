/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 */
class BaseMethodTest {

    @Test
    void parseJsonExtractsHeaderAndPythonLiteralFallback() {
        assertEquals(Map.of("answer", "ok"), BaseMethod.parseJson("prefix {\"answer\":\"ok\"} suffix"));
        assertEquals(Map.of("answer", "yes"), BaseMethod.parseJson("noise {'answer': 'yes'}"));
        assertEquals(Map.of("target", Map.of("x", 1)), BaseMethod.parseJson("xx {\n\"target\":{\"x\":1}}", "target"));
    }

    @Test
    void constructorStoresConfigAndVerboseFlag() {
        BaseMethod method = new BaseMethod(Map.of("verbose", 1, "gen_model_id", "m", "llm_api_key", "k"));

        assertTrue(method.isVerbose());
        assertEquals("m", method.getConfig().get("gen_model_id"));
        assertFalse(new BaseMethod(Map.of()).isVerbose());
    }

    @Test
    void produceAnswerBuildsPromptAndVerifiesAnswer() {
        RecordingBaseMethod method = new RecordingBaseMethod(Map.of(
                "verbose", false,
                "gen_model_id", "model-a",
                "llm_api_key", "key-a"
        ));

        String answer = method.produceAnswerFromApiCall("find status", "doc text", "{\"status\":\"ready\"}");

        assertEquals("Ready", answer);
        assertEquals("model-a", method.modelId);
        assertEquals("key-a", method.llmApiKey);
        assertTrue(method.prompt.contains("doc text"));
        assertTrue(method.prompt.contains("find status"));
        assertTrue(method.prompt.contains("{\"status\":\"ready\"}"));
        assertEquals(15, method.kwargs.get("max_attempts"));
        assertEquals(List.of("<|eot_id|>", "<|end_of_text|>", "<|eom_id|>"), method.kwargs.get("stop_sequences"));
    }

    private static final class RecordingBaseMethod extends BaseMethod {
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
            return verifyFn.apply("{\"answer\":\" Ready \"}");
        }
    }
}
