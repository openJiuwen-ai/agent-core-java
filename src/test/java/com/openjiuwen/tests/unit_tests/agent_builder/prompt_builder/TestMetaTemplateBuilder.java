/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_builder.prompt_builder;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.dev_tools.prompt_builder.builder.MetaTemplateBuilder;
import com.openjiuwen.dev_tools.prompt_builder.builder.PromptTemplatesZh;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code test_meta_template_builder.py} in
 * {@code tests.unit_tests.agent_builder.prompt_builder}.
 */
@Tag("unit-test")
class TestMetaTemplateBuilder {

    private static final String META_TEMPLATE_NAME_PREFIX = "META_TEMPLATE_";
    private static final String MOCK_PROVIDER = "MocKMetaTemplateLLM";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    @BeforeAll
    static void registerMockFactory() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new MockMetaTemplateModelFactory());
        }
    }

    private MetaTemplateBuilder createBuilder() {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(MOCK_PROVIDER)
                .apiKey("")
                .apiBase("")
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("")
                .build();
        return new MetaTemplateBuilder(requestConfig, clientConfig);
    }

    @Test
    @DisplayName("Test register custom template")
    void testRegisterCustomTemplate() {
        MetaTemplateBuilder builder = createBuilder();

        String template = "this is a string meta template";
        builder.registerMetaTemplate("custom_general", template);
        PromptTemplate metaTemplate = builder.getMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");
        assertEquals(template, metaTemplate.getContent());
        builder.popMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");

        PromptTemplate promptTemplate = PromptTemplate.builder()
                .content("this is a string meta template")
                .build();
        builder.registerMetaTemplate("custom_general", promptTemplate);
        metaTemplate = builder.getMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");
        assertEquals(promptTemplate.getContent(), metaTemplate.getContent());
        builder.popMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");

        Object invalidTemplate = new Object[] {"this is a invalid tuple meta template"};
        BaseError error = assertThrows(BaseError.class,
                () -> builder.registerMetaTemplate("custom_general", invalidTemplate));
        assertEquals(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR.getCode(), error.getCode());
    }

    @Test
    @DisplayName("Test build with default meta template")
    void testBuildWithDefaultMetaTemplate() throws Exception {
        MetaTemplateBuilder builder = createBuilder();
        String prompt = "你是一个旅行助手";

        String response = builder.build(prompt).get();
        assertEquals(expectedGeneralContent(prompt), response);

        response = builder.build(prompt, null, "general").get();
        assertEquals(expectedGeneralContent(prompt), response);

        response = builder.build(prompt, null, "plan").get();
        assertEquals(expectedPlanContent(prompt), response);
    }

    @Test
    @DisplayName("Test build with custom meta template")
    void testBuildWithCustomMetaTemplate() throws Exception {
        MetaTemplateBuilder builder = createBuilder();
        String prompt = "你是一个旅行助手";

        BaseError missingTemplateType = assertThrows(BaseError.class,
                () -> builder.build(prompt, null, "other").join());
        assertEquals(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR.getCode(), missingTemplateType.getCode());

        String template = "you are a custom meta template";
        builder.registerMetaTemplate("custom_general", template);
        BaseError missingCustomTemplate = assertThrows(BaseError.class,
                () -> builder.build(prompt, null, "other", "not_defined").join());
        assertEquals(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR.getCode(), missingCustomTemplate.getCode());

        String response = builder.build(prompt, null, "other", "custom_general").get();
        assertEquals(template, response);
    }

    private static String expectedGeneralContent(String prompt) {
        List<BaseMessage> messages = new ArrayList<>();
        messages.addAll(PromptTemplatesZh.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE.toMessages());
        messages.addAll(PromptTemplatesZh.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE.format(Map.of(
                "instruction", prompt,
                "tools", "None"
        )).toMessages());
        return concatenateMessages(messages);
    }

    private static String expectedPlanContent(String prompt) {
        List<BaseMessage> messages = new ArrayList<>();
        messages.addAll(PromptTemplatesZh.PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE.toMessages());
        messages.addAll(PromptTemplatesZh.PROMPT_BUILD_PLAN_META_USER_TEMPLATE.format(Map.of(
                "instruction", prompt,
                "tools", "None"
        )).toMessages());
        return concatenateMessages(messages);
    }

    private static String concatenateMessages(List<BaseMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (BaseMessage message : messages) {
            sb.append(message.getContentAsString());
        }
        return sb.toString();
    }

    private static final class MockMetaTemplateModelFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return MOCK_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new MockMetaTemplateModelClient(modelConfig, clientConfig);
        }
    }

    private static final class MockMetaTemplateModelClient extends BaseModelClient {

        MockMetaTemplateModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        protected void validateConfig() {
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser,
                                       Float timeout, Map<String, Object> kwargs) {
            StringBuilder content = new StringBuilder();
            if (messages instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof BaseMessage msg) {
                        content.append(msg.getContentAsString());
                    }
                }
            }
            return new AssistantMessage(content.toString());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            return null;
        }
    }
}
