/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_builder.prompt_builder;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.dev_tools.prompt_builder.builder.MetaTemplateBuilder;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_meta_template_builder.py} in 
 * {@code tests.unit_tests.agent_builder.prompt_builder}.
 */
@Tag("unit-test")
class TestMetaTemplateBuilder {

    private static final String META_TEMPLATE_NAME_PREFIX = "META_TEMPLATE_";

    @Test
    @DisplayName("Test register custom template")
    void testRegisterCustomTemplate() throws Exception {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
            .clientProvider("mock")
            .apiKey("test-key")
            .apiBase("https://api.example.com")
            .build();
        ModelRequestConfig requestConfig = new ModelRequestConfig();
        
        MetaTemplateBuilder builder = new MetaTemplateBuilder(requestConfig, clientConfig);

        // Register string template
        String template = "this is a string meta template";
        builder.registerMetaTemplate("custom_general", template);
        PromptTemplate metaTemplate = builder.getMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");
        assertEquals(template, metaTemplate.getContent());
        builder.popMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");

        // Register PromptTemplate object
        PromptTemplate promptTemplate = PromptTemplate.builder()
            .content("this is a string meta template")
            .build();
        builder.registerMetaTemplate("custom_general", promptTemplate);
        metaTemplate = builder.getMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");
        assertEquals(promptTemplate.getContent(), metaTemplate.getContent());
        builder.popMetaTemplate(META_TEMPLATE_NAME_PREFIX + "custom_general");

        // Register invalid type template
        final Object invalidTemplate = new Object[]{ "this is a invalid tuple meta template" };
        assertThrows(Exception.class, () -> {
            builder.registerMetaTemplate("custom_general", invalidTemplate);
        });
    }

    @Test
    @DisplayName("Test build with default meta template")
    @Disabled("Requires mock LLM client")
    void testBuildWithDefaultMetaTemplate() throws Exception {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
            .clientProvider("mock")
            .apiKey("test-key")
            .apiBase("https://api.example.com")
            .build();
        ModelRequestConfig requestConfig = new ModelRequestConfig();
        
        MetaTemplateBuilder builder = new MetaTemplateBuilder(requestConfig, clientConfig);

        // Build with default template
        String response = builder.build("你是一个旅行助手").get();
        assertNotNull(response);

        // Build with general template type
        response = builder.build("你是一个旅行助手", "general").get();
        assertNotNull(response);

        // Build with plan template type
        response = builder.build("你是一个旅行助手", "plan").get();
        assertNotNull(response);
    }

    @Test
    @DisplayName("Test build with custom meta template")
    @Disabled("Requires mock LLM client")
    void testBuildWithCustomMetaTemplate() throws Exception {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
            .clientProvider("mock")
            .apiKey("test-key")
            .apiBase("https://api.example.com")
            .build();
        ModelRequestConfig requestConfig = new ModelRequestConfig();
        
        MetaTemplateBuilder builder = new MetaTemplateBuilder(requestConfig, clientConfig);

        // Build with invalid template type
        assertThrows(Exception.class, () -> {
            builder.build("你是一个旅行助手", "other").get();
        });

        // Build with custom template
        String template = "you are a custom meta template";
        builder.registerMetaTemplate("custom_general", template);
        
        String response = builder.build("你是一个旅行助手", "other", "custom_general").get();
        assertEquals(template, response);
    }

    @Test
    @DisplayName("Placeholder test")
    @Tag("level0")
    void testPlaceholder() {
        assertTrue(true);
    }
}