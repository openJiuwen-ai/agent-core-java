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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_builder/prompt_builder/test_meta_template_builder.py}.
 */
@DisplayName("MetaTemplateBuilder")
class TestMetaTemplateBuilder {

    private static final String META_TEMPLATE_NAME_PREFIX = "META_TEMPLATE_";
    private static final String MOCK_PROVIDER = "MocKMetaTemplateLLM";

    @BeforeAll
    static void registerMockFactory() {
        Model.registerFactory(ModelClientConfig::new, (modelConfig, clientConfig) ->
                new MockMetaTemplateClient(modelConfig, clientConfig));
    }

    private static MetaTemplateBuilder createBuilder() {
        return new MetaTemplateBuilder(
                ModelRequestConfig.builder().modelName("").build(),
                ModelClientConfig.builder()
                        .clientProvider(MOCK_PROVIDER)
                        .apiBase("mock")
                        .apiKey("mock")
                        .build()
        );
    }

    @Nested
    @DisplayName("register_custom_template")
    class RegisterCustomTemplate {

        @Test
        @DisplayName("register string template and verify content")
        void registerStringTemplate() {
            MetaTemplateBuilder builder = createBuilder();

            String template = "this is a string meta template";
            builder.registerMetaTemplate("custom_general", template);
            PromptTemplate metaTemplate = builder.getMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");
            assertThat(metaTemplate.getContent()).isEqualTo(template);
            builder.popMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");
        }

        @Test
        @DisplayName("register PromptTemplate and verify content")
        void registerPromptTemplateObject() {
            MetaTemplateBuilder builder = createBuilder();

            PromptTemplate template = PromptTemplate.builder().content("this is a string meta template").build();
            builder.registerMetaTemplate("custom_general", template);
            PromptTemplate metaTemplate = builder.getMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");
            assertThat(metaTemplate.getContent()).isEqualTo(template.getContent());
            builder.popMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");
        }

        @Test
        @DisplayName("register invalid type template raises BaseError")
        void registerInvalidTypeTemplate() {
            MetaTemplateBuilder builder = createBuilder();

            assertThatThrownBy(() -> builder.registerMetaTemplate("custom_general", List.of("invalid")))
                    .isInstanceOf(BaseError.class)
                    .satisfies(ex -> assertThat(((BaseError) ex).getCode())
                            .isEqualTo(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR.getCode()));
        }
    }

    @Nested
    @DisplayName("build_with_default_meta_template")
    class BuildWithDefaultMetaTemplate {

        @Test
        @DisplayName("build with default template type uses general")
        void buildDefault() {
            MetaTemplateBuilder builder = createBuilder();
            String prompt = "你是一个旅行助手";

            String response = builder.build(prompt).join();

            String expected = PromptTemplatesZh.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE
                    .toMessages().get(0).getContentAsString()
                    + PromptTemplatesZh.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE
                    .format(Map.of("instruction", prompt))
                    .toMessages().get(0).getContentAsString();
            assertThat(response).isEqualTo(expected);
        }

        @Test
        @DisplayName("build with explicit general template type")
        void buildGeneral() {
            MetaTemplateBuilder builder = createBuilder();
            String prompt = "你是一个旅行助手";

            String response = builder.build(prompt, null, "general").join();

            String expected = PromptTemplatesZh.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE
                    .toMessages().get(0).getContentAsString()
                    + PromptTemplatesZh.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE
                    .format(Map.of("instruction", prompt))
                    .toMessages().get(0).getContentAsString();
            assertThat(response).isEqualTo(expected);
        }

        @Test
        @DisplayName("build with plan template type")
        void buildPlan() {
            MetaTemplateBuilder builder = createBuilder();
            String prompt = "你是一个旅行助手";

            String response = builder.build(prompt, null, "plan").join();

            String expected = PromptTemplatesZh.PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE
                    .toMessages().get(0).getContentAsString()
                    + PromptTemplatesZh.PROMPT_BUILD_PLAN_META_USER_TEMPLATE
                    .format(Map.of("instruction", prompt, "tools", "None"))
                    .toMessages().get(0).getContentAsString();
            assertThat(response).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("build_with_custom_meta_template")
    class BuildWithCustomMetaTemplate {

        @Test
        @DisplayName("build with other template type and no custom name raises error")
        void buildOtherWithoutCustomName() {
            MetaTemplateBuilder builder = createBuilder();

            assertThatThrownBy(() -> builder.build("你是一个旅行助手", null, "other").join())
                    .isInstanceOf(BaseError.class)
                    .satisfies(ex -> assertThat(((BaseError) ex).getCode())
                            .isEqualTo(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR.getCode()));
        }

        @Test
        @DisplayName("build with other template type and undefined custom name raises error")
        void buildOtherWithUndefinedCustomName() {
            MetaTemplateBuilder builder = createBuilder();
            String template = "you are a custom meta template";

            builder.registerMetaTemplate("custom_general", template);

            assertThatThrownBy(() -> builder.build("你是一个旅行助手", null, "other", "not_defined").join())
                    .isInstanceOf(BaseError.class)
                    .satisfies(ex -> assertThat(((BaseError) ex).getCode())
                            .isEqualTo(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR.getCode()));
        }

        @Test
        @DisplayName("build with other template type and registered custom name returns template content")
        void buildOtherWithRegisteredCustomName() {
            MetaTemplateBuilder builder = createBuilder();
            String template = "you are a custom meta template";

            builder.registerMetaTemplate("custom_general", template);
            String response = builder.build("你是一个旅行助手", null, "other", "custom_general").join();

            assertThat(response).isEqualTo(template);
        }
    }

    private static class MockMetaTemplateClient extends BaseModelClient {

        MockMetaTemplateClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        protected void validateConfig() {
        }

        private AssistantMessage concatMessages(Object messages) {
            if (messages instanceof List<?> list) {
                StringBuilder sb = new StringBuilder();
                for (Object item : list) {
                    if (item instanceof BaseMessage msg) {
                        sb.append(msg.getContentAsString());
                    }
                }
                return AssistantMessage.builder().content(sb.toString()).build();
            }
            return AssistantMessage.builder().content("").build();
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature,
                                       Float topP, String model, Integer maxTokens,
                                       String stop, BaseOutputParser outputParser,
                                       Float timeout, Map<String, Object> kwargs) {
            return concatMessages(messages);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools,
                                                      Float temperature, Float topP,
                                                      String model, Integer maxTokens,
                                                      String stop, BaseOutputParser outputParser,
                                                      Float timeout, Map<String, Object> kwargs) {
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                    .content(concatMessages(messages).getContent())
                    .build();
            return List.of(chunk).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model,
                                                     String size, String negativePrompt, int n,
                                                     boolean promptExtend, boolean watermark,
                                                     int seed, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model,
                                                      String voice, String languageType,
                                                      Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String model,
                                                     String imgUrl, String audioUrl, String size,
                                                     String resolution, int duration,
                                                     boolean promptExtend, boolean watermark,
                                                     String negativePrompt, Integer seed,
                                                     Map<String, Object> kwargs) {
            return null;
        }
    }
}
