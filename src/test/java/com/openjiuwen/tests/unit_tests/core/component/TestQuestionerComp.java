/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
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
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.OutputCache;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;
import com.openjiuwen.core.workflow.component.llm.QuestionerDefaultConfig;
import com.openjiuwen.core.workflow.component.llm.QuestionerExecutable;
import com.openjiuwen.core.workflow.component.llm.QuestionerOutput;
import com.openjiuwen.core.workflow.component.llm.QuestionerUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_questioner_comp} in
 * {@code tests.unit_tests.core.component.test_questioner_comp}.
 */
@Tag("unit-test")
class TestQuestionerComp {

    @Test
    @DisplayName("questioner config and component creation")
    void testComponentCreation() {
        QuestionerComponent component = new QuestionerComponent(config("QuestionerFakeCreate"));

        assertNotNull(component.toExecutable());
        assertInstanceOf(QuestionerExecutable.class, component.toExecutable());
    }

    @Test
    @DisplayName("default prompt template matches accepted language")
    void testDefaultPromptTemplate() {
        assertEquals(2, QuestionerDefaultConfig.fromLanguage("zh").getPromptTemplate().size());
        assertEquals(2, QuestionerDefaultConfig.fromLanguage("en").getPromptTemplate().size());
    }

    @Test
    @DisplayName("continue-ask prompt formatting works in zh and en")
    void testFormatContinueAskQuestion() {
        List<FieldInfo> fields = List.of(
                FieldInfo.builder().fieldName("city").cnFieldName("城市").description("city").build(),
                FieldInfo.builder().fieldName("date").description("date").build());

        assertEquals("请您提供城市, date相关的信息",
                QuestionerUtils.formatContinueAskQuestion(fields, "zh"));
        assertEquals("Please provide information related to: 城市, date",
                QuestionerUtils.formatContinueAskQuestion(fields, "en"));
    }

    @Test
    @DisplayName("value validation matches Python semantics")
    void testIsValidValue() {
        assertFalse(QuestionerUtils.isValidValue(null));
        assertFalse(QuestionerUtils.isValidValue(""));
        assertFalse(QuestionerUtils.isValidValue(List.of()));
        assertFalse(QuestionerUtils.isValidValue(Map.of()));
        assertFalse(QuestionerUtils.isValidValue("null"));
        assertTrue(QuestionerUtils.isValidValue("x"));
    }

    @Test
    @DisplayName("type conversion handles string integer number and boolean")
    void testValidateAndConvertType() {
        assertArrayEquals(new Object[]{"abc", true}, QuestionerUtils.validateAndConvertType("abc", "string"));
        assertArrayEquals(new Object[]{3, true}, QuestionerUtils.validateAndConvertType("3", "integer"));
        assertArrayEquals(new Object[]{3.5, true}, QuestionerUtils.validateAndConvertType("3.5", "number"));
        assertArrayEquals(new Object[]{true, true}, QuestionerUtils.validateAndConvertType("true", "boolean"));
        assertArrayEquals(new Object[]{null, false}, QuestionerUtils.validateAndConvertType("x", "integer"));
    }

    @Test
    @DisplayName("questioner output formatting preserves fields and extras")
    void testFormatQuestionerOutput() {
        OutputCache cache = new OutputCache();
        cache.setUserResponse("yes");
        cache.setQuestion("ask");
        cache.getKeyFields().put("city", "杭州");

        Map<String, Object> output = QuestionerUtils.formatQuestionerOutput(cache);

        assertEquals("杭州", output.get("city"));
        assertEquals("yes", output.get("user_response"));
        assertEquals("ask", output.get("question"));
    }

    @Test
    @DisplayName("config validation rejects invalid response type")
    void testInvalidResponseType() {
        QuestionerConfig config = config("QuestionerFakeInvalidType");
        config.setResponseType("bad");
        assertThrows(BaseError.class, () -> new QuestionerExecutable(config));
    }

    @Test
    @DisplayName("config validation rejects empty field names")
    void testInvalidFieldNames() {
        QuestionerConfig config = config("QuestionerFakeInvalidField");
        config.setFieldNames(List.of(FieldInfo.builder().fieldName("").build()));
        assertThrows(BaseError.class, () -> new QuestionerExecutable(config));
    }

    @Test
    @DisplayName("config validation rejects non-positive max responses")
    void testInvalidMaxResponse() {
        QuestionerConfig config = config("QuestionerFakeInvalidMax");
        config.setMaxResponse(0);
        assertThrows(BaseError.class, () -> new QuestionerExecutable(config));
    }

    @Test
    @DisplayName("questioner executable can be created with a fake model")
    void testExecutableCreation() {
        QuestionerExecutable executable = new QuestionerExecutable(config("QuestionerFakeExecutable"));
        assertNotNull(executable);
    }

    @Test
    @DisplayName("questioner default prompt template contains system and user messages")
    void testDefaultTemplateContainsMessages() {
        assertEquals("system", QuestionerDefaultConfig.getDefaultTemplate("zh").get(0).getRole());
        assertEquals("user", QuestionerDefaultConfig.getDefaultTemplate("zh").get(1).getRole());
    }

    @Test
    @DisplayName("questioner executable can be instantiated repeatedly")
    void testRepeatedExecutableCreation() {
        QuestionerExecutable first = new QuestionerExecutable(config("QuestionerFakeRepeated1"));
        QuestionerExecutable second = new QuestionerExecutable(config("QuestionerFakeRepeated2"));

        assertNotNull(first);
        assertNotNull(second);
    }

    private static QuestionerConfig config(String provider) {
        QuestionerConfig config = new QuestionerConfig();
        config.setFieldNames(List.of(FieldInfo.builder().fieldName("city").build()));
        config.setModelConfig(ModelRequestConfig.builder().modelName("fake-model").build());
        config.setModelClientConfig(ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey("sk-fake")
                .apiBase("mock://api.openai.com/v1")
                .verifySsl(false)
                .build());
        registerProvider(provider);
        return config;
    }

    private static void registerProvider(String provider) {
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
                        return AssistantMessage.builder().role("assistant").content("{\"city\":\"杭州\"}").build();
                    }

                    @Override
                    public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                                                                  Float topP, String model, Integer maxTokens,
                                                                  String stop, BaseOutputParser outputParser,
                                                                  Float timeout, Map<String, Object> kwargs) {
                        return List.<AssistantMessageChunk>of(
                                AssistantMessageChunk.builder().role("assistant").content("ok").build()).iterator();
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
