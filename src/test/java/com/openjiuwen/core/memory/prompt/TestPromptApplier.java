/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory.prompt;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PromptApplier.
 * Mirrors Python's tests/unit_tests/core/memory/prompt/test_prompt_applier.py.
 */
@DisplayName("PromptApplier tests")
class TestPromptApplier {

    @BeforeEach
    void setupMethod() {
        PromptApplier.getInstance().clearCache();
    }

    @AfterEach
    void teardownMethod() {
        PromptApplier.getInstance().clearCache();
    }

    @Test
    void testSingletonInitialization() {
        PromptApplier applier1 = PromptApplier.getInstance();
        PromptApplier applier2 = PromptApplier.getInstance();

        assertSame(applier1, applier2);
    }

    @Test
    void testApplyWithVariableSubstitution() {
        String result = PromptApplier.getInstance().apply(
                "ut_prompt_template",
                Map.of("name", "Alice", "place", "Wonderland"));

        assertTrue(result.contains("Hello Alice"));
        assertTrue(result.contains("welcome to Wonderland"));
        assertEquals("Hello Alice, welcome to Wonderland!", result);
    }

    @Test
    void testApplyWithEmptyVariables() {
        String result = PromptApplier.getInstance().apply("ut_plain_template", Map.of());

        assertEquals("Simple template without variables", result);
    }

    @Test
    void testApplyCachesTemplates() {
        PromptApplier applier = PromptApplier.getInstance();

        String result1 = applier.apply("ut_cached_template", Map.of("var", "value1"));
        PromptTemplate template1 = applier.getTemplate("ut_cached_template");
        String result2 = applier.apply("ut_cached_template", Map.of("var", "value2"));
        PromptTemplate template2 = applier.getTemplate("ut_cached_template");

        assertEquals("Cached template content", result1);
        assertEquals("Cached template content", result2);
        assertSame(template1, template2);
    }

    @Test
    void testApplyFileNotFound() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PromptApplier.getInstance().apply("non_existent_template", Map.of("var", "value")));

        assertTrue(error.getMessage().contains("Prompt file not found"));
    }

    @Test
    void testClearCacheAll() {
        PromptApplier applier = PromptApplier.getInstance();

        PromptTemplate first = applier.getTemplate("ut_cached_template");
        applier.getTemplate("ut_plain_template");

        applier.clearCache();

        PromptTemplate second = applier.getTemplate("ut_cached_template");
        assertNotSame(first, second);
    }

    @Test
    void testGetTemplateReturnsPromptTemplate() {
        PromptTemplate template = PromptApplier.getInstance().getTemplate("ut_prompt_template");

        assertInstanceOf(PromptTemplate.class, template);
    }

    @Test
    void testGetTemplateCachesResult() {
        PromptApplier applier = PromptApplier.getInstance();

        PromptTemplate template1 = applier.getTemplate("ut_cached_template");
        PromptTemplate template2 = applier.getTemplate("ut_cached_template");

        assertSame(template1, template2);
    }

    @Test
    void testGetTemplateFileNotFound() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PromptApplier.getInstance().getTemplate("non_existent_template"));

        assertTrue(error.getMessage().contains("Prompt file not found"));
    }

    @Test
    void testIntegrationWithComplexTemplate() {
        String result = PromptApplier.getInstance().apply("ut_complex_template", Map.of(
                "domain", "Python programming",
                "question", "How do I write unit tests?",
                "topic", "unit testing",
                "context", "Use pytest framework for writing comprehensive tests."));

        assertTrue(result.contains("Python programming"));
        assertTrue(result.contains("How do I write unit tests?"));
        assertTrue(result.contains("unit testing"));
        assertTrue(result.contains("Use pytest framework for writing comprehensive tests."));
    }

    @Test
    void testApplyWithSpecialCharactersInVariables() {
        String result = PromptApplier.getInstance().apply("ut_special_chars_template",
                Map.of("user_input", "Hello, world! How are you? @#$%^&*()"));

        assertTrue(result.contains("Hello, world! How are you? @#$%^&*()"));
    }

    @Test
    void testSingletonBehaviorAcrossMultipleInstances() {
        PromptApplier applier1 = PromptApplier.getInstance();
        PromptApplier applier2 = PromptApplier.getInstance();

        applier1.getTemplate("ut_cached_template");

        assertSame(applier1, applier2);
        assertSame(applier1.getTemplate("ut_cached_template"), applier2.getTemplate("ut_cached_template"));
    }
}
