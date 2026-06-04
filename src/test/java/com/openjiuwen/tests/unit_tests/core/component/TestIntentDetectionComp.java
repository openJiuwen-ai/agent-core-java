/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.workflow.BranchRouter;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig;
import com.openjiuwen.core.workflow.component.llm.IntentDetectionExecutable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mirrors Python's {@code test_intent_detection_comp} in
 * {@code tests.unit_tests.core.component.test_intent_detection_comp}.
 */
@Tag("unit-test")
class TestIntentDetectionComp {

    @Test
    @DisplayName("LLM JSON result maps zh classification to category name")
    void testInvokeSuccess() {
        registerFakeProvider("IntentFakeZh", "{\"class\": \"分类2\", \"reason\": \"ok\"}");
        IntentDetectionExecutable executable = new IntentDetectionExecutable(config("IntentFakeZh", "zh",
                List.of("name1", "name2", "name3")));
        executable.setRouter(new BranchRouter());

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "你好"), null, null);

        assertEquals("name2", output.get("category_name"));
        assertEquals(2, output.get("classification_id"));
        assertEquals("ok", output.get("reason"));
    }

    @Test
    @DisplayName("English Category2 maps to the second configured category")
    void testInvokeSuccessAcceptLanguageEn() {
        registerFakeProvider("IntentFakeEn", "{\"class\": \"Category2\", \"reason\": \"User asks about travel\"}");
        IntentDetectionExecutable executable = new IntentDetectionExecutable(config("IntentFakeEn", "en",
                List.of("weather", "travel", "other")));

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "I want to travel"), null, null);

        assertEquals("travel", output.get("category_name"));
        assertEquals(2, output.get("classification_id"));
    }

    @Test
    @DisplayName("English category labels are case insensitive")
    void testInvokeSuccessAcceptLanguageEnCaseInsensitive() {
        registerFakeProvider("IntentFakeEnLower", "{\"class\": \"category1\", \"reason\": \"weather query\"}");
        IntentDetectionExecutable executable = new IntentDetectionExecutable(config("IntentFakeEnLower", "en",
                List.of("weather")));

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "What is the weather"), null, null);

        assertEquals("weather", output.get("category_name"));
        assertEquals(1, output.get("classification_id"));
    }

    @Test
    @Disabled("Mirrors Python @unittest.skip(\"skip system test\") for real LLM workflow streaming")
    void testStartIntentEndStream() {
    }

    private static IntentDetectionCompConfig config(String provider, String language, List<String> categories) {
        IntentDetectionCompConfig config = new IntentDetectionCompConfig();
        config.setUserPrompt("Determine user intent");
        config.setCategoryNameList(categories);
        config.setAcceptLanguage(language);
        config.setModelConfig(ModelRequestConfig.builder()
                .modelName("gpt-3.5-turbo")
                .temperature(0.7)
                .topP(0.9)
                .build());
        config.setModelClientConfig(ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey("sk-fake")
                .apiBase("mock://api.openai.com/v1")
                .verifySsl(false)
                .build());
        return config;
    }

    private static void registerFakeProvider(String provider, String content) {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return provider;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return new BaseModelClient(modelConfig, clientConfig) {
                    @Override
                    public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                                                   String model, Integer maxTokens, String stop,
                                                   BaseOutputParser outputParser, Float timeout,
                                                   Map<String, Object> kwargs) {
                        return AssistantMessage.builder().role("assistant").content(content).build();
                    }

                    @Override
                    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                                                                  Float topP, String model, Integer maxTokens,
                                                                  String stop, BaseOutputParser outputParser,
                                                                  Float timeout, Map<String, Object> kwargs) {
                        return List.<AssistantMessageChunk>of(
                                AssistantMessageChunk.builder().role("assistant").content(content).build()).iterator();
                    }

                    @Override
                    public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                                 String negativePrompt, int n, boolean promptExtend,
                                                                 boolean watermark, int seed,
                                                                 Map<String, Object> kwargs) {
                        return null;
                    }

                    @Override
                    public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model,
                                                                  String voice, String languageType,
                                                                  Map<String, Object> kwargs) {
                        return null;
                    }

                    @Override
                    public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                                                                 String audioUrl, String model, String size,
                                                                 String resolution, int duration,
                                                                 boolean promptExtend, boolean watermark,
                                                                 String negativePrompt, Integer seed,
                                                                 Map<String, Object> kwargs) {
                        return null;
                    }
                };
            }
        });
    }
}
