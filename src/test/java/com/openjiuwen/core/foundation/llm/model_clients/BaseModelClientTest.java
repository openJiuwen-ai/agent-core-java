/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseModelClientTest {

    @Test
    void buildHttpClientAppliesConfiguredHttpVersion() {
        TestBaseModelClient client = new TestBaseModelClient(ModelHttpVersion.HTTP_1_1, 60.0);

        HttpClient httpClient = client.exposedBuildHttpClient(5.0);

        assertEquals(HttpClient.Version.HTTP_1_1, httpClient.version());
        assertEquals(Duration.ofSeconds(5), httpClient.connectTimeout().orElseThrow());
    }

    @Test
    void buildHttpClientKeepsTimeoutOverrideWhenHttpVersionConfigured() {
        TestBaseModelClient client = new TestBaseModelClient(ModelHttpVersion.HTTP_2, 60.0);

        HttpClient httpClient = client.exposedBuildHttpClient(2.5);

        assertEquals(HttpClient.Version.HTTP_2, httpClient.version());
        assertEquals(Duration.ofMillis(2500), httpClient.connectTimeout().orElseThrow());
    }

    private static final class TestBaseModelClient extends BaseModelClient {
        private TestBaseModelClient(ModelHttpVersion httpVersion, double timeout) {
            super(null,
                    ModelClientConfig.builder()
                            .clientProvider("OpenAI")
                            .apiKey("sk-test")
                            .apiBase("http://localhost")
                            .timeout(timeout)
                            .httpVersion(httpVersion)
                            .build());
        }

        private HttpClient exposedBuildHttpClient(double timeoutSeconds) {
            return buildHttpClient(timeoutSeconds);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }
    }
}
