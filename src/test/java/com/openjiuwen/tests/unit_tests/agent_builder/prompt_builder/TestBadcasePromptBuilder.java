/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_builder.prompt_builder;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.dev_tools.prompt_builder.builder.BadCasePromptBuilder;
import com.openjiuwen.dev_tools.prompt_builder.builder.PromptTemplatesZh;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_builder.prompt_builder.test_badcase_prompt_builder}.
 */
class TestBadcasePromptBuilder {

    private Model mockModel;

    @BeforeEach
    void setUp() throws Exception {
        mockModel = mock(Model.class);
    }

    private BadCasePromptBuilder createBuilderWithMockModel() throws Exception {
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("")
                .build();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("MockModelClient")
                .apiBase("")
                .apiKey("")
                .build();

        BadCasePromptBuilder builder = new BadCasePromptBuilder(requestConfig, clientConfig);

        Field modelField = BadCasePromptBuilder.class.getSuperclass().getDeclaredField("model");
        modelField.setAccessible(true);
        modelField.set(builder, mockModel);
        return builder;
    }

    @SuppressWarnings("unchecked")
    private AssistantMessage concatenateMessagesAndRespond(Object messages) {
        StringBuilder sb = new StringBuilder();
        if (messages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof BaseMessage msg) {
                    if (sb.length() > 0) {
                        sb.append("");
                    }
                    sb.append(msg.getContentAsString());
                }
            }
        }
        return new AssistantMessage(sb.toString());
    }

    @Test
    void testBadCasePromptBuilder() throws Exception {
        BadCasePromptBuilder builder = createBuilderWithMockModel();

        when(mockModel.invoke(
                any(Object.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(invocation -> {
            Object messages = invocation.getArgument(0);
            return concatenateMessagesAndRespond(messages);
        });

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
}
