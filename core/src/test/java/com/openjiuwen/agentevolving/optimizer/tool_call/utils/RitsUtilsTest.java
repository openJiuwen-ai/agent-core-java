/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for RITS LLM call helper.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_evolving/optimizer/tool_call/utils/rits.py}.</p>
 */
class RitsUtilsTest {

    @AfterEach
    void resetProvider() {
        RitsUtils.setResponseProviderForTesting(null);
    }

    @Test
    void getRitsResponseReturnsProviderOutputAndBuildsPythonConfigs() {
        RecordingProvider provider = new RecordingProvider("ok");
        RitsUtils.setResponseProviderForTesting(provider);

        Object result = RitsUtils.getRitsResponse("gpt-test", "hello", "key");

        assertEquals("ok", result);
        assertEquals("gpt-test", provider.modelConfig.getModelName());
        assertEquals(1.0d, provider.modelConfig.getTemperature(), 1e-9);
        assertEquals("OpenAI", provider.modelClientConfig.getClientProvider());
        assertEquals("https://api.openai.com/v1", provider.modelClientConfig.getApiBase());
        assertEquals("key", provider.modelClientConfig.getApiKey());
        assertEquals(true, provider.modelClientConfig.isVerifySsl());
        assertEquals("developer", provider.message.getRole());
        assertEquals("hello", provider.message.getContentAsString());
    }

    @Test
    void ritsResponseAppliesVerifyFunction() {
        RitsUtils.setResponseProviderForTesting(new RecordingProvider(" raw "));

        Object result = RitsUtils.ritsResponse(
                "gpt-test",
                "prompt",
                "key",
                value -> Map.of("content", value.strip()),
                false,
                Map.of()
        );

        assertEquals(Map.of("content", "raw"), result);
    }

    @Test
    void getRitsResponseCatchesFailuresAsPythonErrorMap() {
        RitsUtils.setResponseProviderForTesting((model, client, message) -> {
            throw new IllegalStateException("boom");
        });

        Object result = RitsUtils.getRitsResponse("gpt-test", "prompt", "key");

        Map<?, ?> error = assertInstanceOf(Map.class, result);
        assertEquals("Cannot complete LLM call. Error: boom", error.get("error"));
    }

    @Test
    void ritsResponseRetriesTwiceThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        RitsUtils.setResponseProviderForTesting((model, client, message) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("first");
            }
            return "second";
        });

        Object result = RitsUtils.ritsResponse("gpt-test", "prompt", "key", null, false, Map.of());

        assertEquals("second", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void ritsResponseReraisesAfterTwoFailures() {
        AtomicInteger attempts = new AtomicInteger();
        RitsUtils.setResponseProviderForTesting((model, client, message) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("failed");
        });

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> RitsUtils.ritsResponse("gpt-test", "prompt", "key", null, false, Map.of())
        );

        assertEquals("failed", error.getMessage());
        assertEquals(2, attempts.get());
    }

    private static final class RecordingProvider implements RitsUtils.ResponseProvider {

        private final String response;
        private ModelRequestConfig modelConfig;
        private ModelClientConfig modelClientConfig;
        private BaseMessage message;

        private RecordingProvider(String response) {
            this.response = response;
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
            return response;
        }
    }
}
