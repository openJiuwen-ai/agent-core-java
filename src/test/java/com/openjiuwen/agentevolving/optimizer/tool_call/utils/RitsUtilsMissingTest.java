/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_rits} in
 * {@code tests/unit_tests/agent_evolving/optimizer/tool_call/test_rits.py}.
 */
class RitsUtilsMissingTest {

    @AfterEach
    void resetProvider() {
        RitsUtils.setResponseProviderForTesting(null);
    }

    @Test
    void ritsResponseWithAndWithoutVerify() {
        RecordingProvider provider = new RecordingProvider("raw-output");
        RitsUtils.setResponseProviderForTesting(provider);

        Object out = RitsUtils.ritsResponse("gpt-test", "hello", "key", String::toUpperCase, false, Map.of());
        Object out2 = RitsUtils.ritsResponse("gpt-test", "hello", "key", null, false, Map.of());

        assertThat(out).isEqualTo("RAW-OUTPUT");
        assertThat(out2).isEqualTo("raw-output");
        assertThat(provider.message.getRole()).isEqualTo("developer");
        assertThat(provider.message.getContentAsString()).isEqualTo("hello");
        assertThat(provider.modelConfig.getModelName()).isEqualTo("gpt-test");
        assertThat(provider.modelClientConfig.getApiKey()).isEqualTo("key");
    }

    @Test
    void getRitsResponseWrapsException() {
        RitsUtils.setResponseProviderForTesting((modelConfig, modelClientConfig, message) -> {
            throw new RuntimeException("x");
        });

        Object out = RitsUtils.getRitsResponse("m", "p", "k");

        assertThat(out).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) out).get("error"))
                .asString()
                .contains("Cannot complete LLM call")
                .contains("x");
    }

    private static final class RecordingProvider implements RitsUtils.ResponseProvider {

        private final String content;
        private ModelRequestConfig modelConfig;
        private ModelClientConfig modelClientConfig;
        private BaseMessage message;

        private RecordingProvider(String content) {
            this.content = content;
        }

        @Override
        public String invoke(
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                BaseMessage message
        ) {
            this.modelConfig = modelConfig;
            this.modelClientConfig = modelClientConfig;
            this.message = message;
            return content;
        }
    }
}
