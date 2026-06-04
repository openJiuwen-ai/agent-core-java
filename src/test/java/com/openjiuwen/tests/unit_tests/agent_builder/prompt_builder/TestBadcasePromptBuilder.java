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
import com.openjiuwen.dev_tools.prompt_builder.builder.BadCasePromptBuilder;
import com.openjiuwen.dev_tools.prompt_builder.builder.PromptTemplatesZh;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_builder.prompt_builder.test_badcase_prompt_builder}.
 */
class TestBadcasePromptBuilder {

    private static final String MOCK_PROVIDER = "MockModelClient";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    @BeforeAll
    static void registerMockFactory() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new MockBadCaseModelFactory());
        }
    }

    private BadCasePromptBuilder createBuilderWithMockModel() {
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("")
                .build();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(MOCK_PROVIDER)
                .apiBase("")
                .apiKey("")
                .build();

        return new BadCasePromptBuilder(requestConfig, clientConfig);
    }

    @Test
    void testBadCasePromptBuilder() throws Exception {
        BadCasePromptBuilder builder = createBuilderWithMockModel();

        String prompt = "bad_case test prompt";

        List<EvaluatedCase> informationExtractionCases = new ArrayList<>();
        informationExtractionCases.add(new EvaluatedCase(
                new Case(
                        Map.of("query", "test input"),
                        Map.of("label", "test label")
                ),
                Map.of("answer", "test answer")
        ));
        informationExtractionCases.add(new EvaluatedCase(
                new Case(
                        Map.of("query", "test input"),
                        Map.of("label", "test label")
                ),
                Map.of("answer", "test answer")
        ));

        String response = builder.build(prompt, informationExtractionCases).get();

        PromptTemplate analyzeTemplate = PromptTemplatesZh.PROMPT_BAD_CASE_ANALYZE_TEMPLATE;
        String analyzeContent = ((UserMessage) analyzeTemplate.toMessages().get(0)).getContentAsString();
        Pattern summaryPattern = Pattern.compile(
                "<summary>((?:(?!</summary>).)*?)</summary>", Pattern.DOTALL);
        Matcher summaryMatcher = summaryPattern.matcher(analyzeContent);
        String parseStr = "";
        if (summaryMatcher.find()) {
            parseStr = summaryMatcher.group(1);
        }

        PromptTemplate optimizeTemplate = PromptTemplatesZh.PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE;
        Map<String, Object> formatParams = new HashMap<>();
        formatParams.put("original_prompt", prompt);
        formatParams.put("feedback", parseStr);
        PromptTemplate formattedOptimize = optimizeTemplate.format(formatParams);
        String expectedContent = ((UserMessage) formattedOptimize.toMessages().get(0)).getContentAsString();

        assertEquals(expectedContent, response);
    }

    @Test
    void testBadCasePromptBuilderUsesLastSummary() throws Exception {
        BadCasePromptBuilder builder = createBuilderWithMockModel();
        Method method = BadCasePromptBuilder.class.getDeclaredMethod("parseFeedbackSummary", AssistantMessage.class);
        method.setAccessible(true);

        String summary = (String) method.invoke(builder,
                new AssistantMessage("<summary>first</summary><summary>second</summary>"));

        assertEquals("second", summary);
    }

    @Test
    void testBadCaseStringDoesNotAppendTrailingNewline() throws Exception {
        BadCasePromptBuilder builder = createBuilderWithMockModel();
        Method method = BadCasePromptBuilder.class.getDeclaredMethod("buildBadCaseString", List.class);
        method.setAccessible(true);

        List<EvaluatedCase> cases = List.of(
                new EvaluatedCase(new Case(Map.of("query", "q1"), Map.of("label", "l1")), Map.of("answer", "a1")),
                new EvaluatedCase(new Case(Map.of("query", "q2"), Map.of("label", "l2")), Map.of("answer", "a2"))
        );
        String first = (String) method.invoke(builder, cases.subList(0, 1));
        String second = (String) method.invoke(builder, cases.subList(1, 2));
        String rendered = (String) method.invoke(builder, cases);

        assertEquals(first + "\n" + second, rendered);
    }

    private static final class MockBadCaseModelFactory implements Model.ModelClientFactory {
        @Override
        public String providerName() {
            return MOCK_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            return new MockBadCaseModelClient(modelConfig, clientConfig);
        }
    }

    private static final class MockBadCaseModelClient extends BaseModelClient {

        MockBadCaseModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
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
