/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_builder.prompt_builder;

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
import com.openjiuwen.dev_tools.prompt_builder.builder.FeedbackPromptBuilder;
import com.openjiuwen.dev_tools.prompt_builder.builder.PromptTemplateUtils;
import com.openjiuwen.dev_tools.prompt_builder.builder.PromptTemplatesZh;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_builder.prompt_builder.test_feedback_prompt_builder}.
 */
class TestFeedbackPromptBuilder {

    private static final String MOCK_INTENT = "```json{\"intent\": \"true\",\n" +
            "\"optimized_feedback\": \"[优化后的反馈信息]\",\n" +
            "                            \"optimization_directions\": \"[联想并提示其他优化方向的建议]\"}```";

    private static final String MOCK_PROVIDER = "MocKFeedbackLLM";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    @BeforeAll
    static void registerMockFactory() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new MockFeedbackModelFactory());
        }
    }

    @Test
    void testFeedbackPromptBuilderGeneral() throws Exception {
        FeedbackPromptBuilder builder = createBuilder();

        String prompt = "你是一个旅行助手";
        String feedback = "丰富一下";

        String response = builder.build(prompt, feedback, "general").get();

        PromptTemplate template = PromptTemplateUtils.getTemplate(
                PromptTemplatesZh.class, "PROMPT_FEEDBACK_GENERAL_TEMPLATE");
        Map<String, Object> formatParams = new LinkedHashMap<>();
        formatParams.put("original_prompt", prompt);
        formatParams.put("suggestion", feedback);
        List<BaseMessage> expectedMessages = template.format(formatParams).toMessages();

        String expectedContent = buildExpectedContent(expectedMessages);
        assertThat(response).isEqualTo(expectedContent);
    }

    @Test
    void testFeedbackPromptBuilderInsert() throws Exception {
        FeedbackPromptBuilder builder = createBuilder();

        String insertTag = "[用户要插入的位置]";
        String prompt = "你是一个旅行助手";
        String feedback = "丰富一下";

        String response = builder.build(prompt, feedback, "insert", 3).get();

        String taggedPrompt = prompt.substring(0, 3) + insertTag + prompt.substring(3);

        PromptTemplate template = PromptTemplateUtils.getTemplate(
                PromptTemplatesZh.class, "PROMPT_FEEDBACK_INSERT_TEMPLATE");
        Map<String, Object> formatParams = new LinkedHashMap<>();
        formatParams.put("original_prompt", taggedPrompt);
        formatParams.put("suggestion", "[优化后的反馈信息]");
        List<BaseMessage> expectedMessages = template.format(formatParams).toMessages();

        String expectedContent = buildExpectedContent(expectedMessages);
        assertThat(response).isEqualTo(expectedContent);
    }

    @Test
    void testFeedbackPromptBuilderSelect() throws Exception {
        FeedbackPromptBuilder builder = createBuilder();

        String prompt = "你是一个旅行助手";
        String feedback = "丰富一下";

        String response = builder.build(prompt + MOCK_INTENT, feedback, "select", 0, 3).get();

        PromptTemplate template = PromptTemplateUtils.getTemplate(
                PromptTemplatesZh.class, "PROMPT_FEEDBACK_SELECT_TEMPLATE");
        Map<String, Object> formatParams = new LinkedHashMap<>();
        formatParams.put("original_prompt", prompt + MOCK_INTENT);
        formatParams.put("suggestion", "[优化后的反馈信息]");
        formatParams.put("pending_optimized_prompt", prompt.substring(0, 3));
        List<BaseMessage> expectedMessages = template.format(formatParams).toMessages();

        String expectedContent = buildExpectedContent(expectedMessages);
        assertThat(response).isEqualTo(expectedContent);
    }

    private static FeedbackPromptBuilder createBuilder() {
        return new FeedbackPromptBuilder(
                ModelRequestConfig.builder().modelName("").build(),
                ModelClientConfig.builder()
                        .clientProvider(MOCK_PROVIDER)
                        .apiBase("mock")
                        .apiKey("mock")
                        .build()
        );
    }

    private static String buildExpectedContent(List<BaseMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (BaseMessage msg : messages) {
            sb.append(MOCK_INTENT).append(msg.getContentAsString());
        }
        return sb.toString();
    }

    private static final class MockFeedbackModelFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return MOCK_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new MockFeedbackModelClient(modelConfig, clientConfig);
        }
    }

    private static final class MockFeedbackModelClient extends BaseModelClient {

        MockFeedbackModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
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
                        content.append(MOCK_INTENT).append(msg.getContentAsString());
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
