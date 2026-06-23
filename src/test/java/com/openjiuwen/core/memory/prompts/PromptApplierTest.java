/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestPromptApplier} in
 * {@code tests/unit_tests/core/memory/prompt/test_prompt_applier.py}.
 */
public final class PromptApplierTest {

    @BeforeEach
    void setUp() {
        PromptApplier.getInstance().clearCache();
    }

    @AfterEach
    void tearDown() {
        PromptApplier.getInstance().clearCache();
    }

    @Test
    void singletonInitializationUsesSharedInstance() {
        PromptApplier applier1 = PromptApplier.getInstance();
        PromptApplier applier2 = PromptApplier.getInstance();

        assertThat(applier1).isSameAs(applier2);
    }

    @Test
    void applyWithVariableSubstitution() {
        PromptApplier applier = new PromptApplier();

        String result = applier.apply("test_template", Map.of("name", "Alice", "place", "Wonderland"));

        assertThat(result).contains("Hello Alice");
        assertThat(result).contains("welcome to Wonderland");
        assertThat(result.stripTrailing()).isEqualTo("Hello Alice, welcome to Wonderland!");
    }

    @Test
    void applyWithEmptyVariables() {
        PromptApplier applier = new PromptApplier();

        String result = applier.apply("simple_template", Map.of());

        assertThat(result.stripTrailing()).isEqualTo("Simple template without variables");
    }

    @Test
    void applyCachesTemplates() {
        PromptApplier applier = new PromptApplier();

        String result1 = applier.apply("cached_template", Map.of("var", "value1"));
        PromptTemplate cached = applier.getTemplate("cached_template");
        String result2 = applier.apply("cached_template", Map.of("var", "value2"));

        assertThat(result1.stripTrailing()).isEqualTo("Cached template content");
        assertThat(result2.stripTrailing()).isEqualTo("Cached template content");
        assertThat(applier.getTemplate("cached_template")).isSameAs(cached);
    }

    @Test
    void applyFileNotFound() {
        PromptApplier applier = new PromptApplier();

        assertThatThrownBy(() -> applier.apply("non_existent_template", Map.of("var", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt file not found");
    }

    @Test
    void clearCacheAll() {
        PromptApplier applier = new PromptApplier();
        PromptTemplate template1 = applier.getTemplate("template1");
        PromptTemplate template2 = applier.getTemplate("template2");

        applier.clearCache();

        assertThat(applier.getTemplate("template1")).isNotSameAs(template1);
        assertThat(applier.getTemplate("template2")).isNotSameAs(template2);
    }

    @Test
    void getTemplateReturnsPromptTemplate() {
        PromptApplier applier = new PromptApplier();

        PromptTemplate template = applier.getTemplate("get_template");

        assertThat(template).isInstanceOf(PromptTemplate.class);
    }

    @Test
    void getTemplateCachesResult() {
        PromptApplier applier = new PromptApplier();

        PromptTemplate template1 = applier.getTemplate("cached_template");
        PromptTemplate template2 = applier.getTemplate("cached_template");

        assertThat(template2).isSameAs(template1);
    }

    @Test
    void getTemplateFileNotFound() {
        PromptApplier applier = new PromptApplier();

        assertThatThrownBy(() -> applier.getTemplate("non_existent_template"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt file not found");
    }

    @Test
    void integrationWithComplexTemplate() {
        PromptApplier applier = new PromptApplier();
        Map<String, String> variables = Map.of(
                "domain", "Python programming",
                "question", "How do I write unit tests?",
                "topic", "unit testing",
                "context", "Use pytest framework for writing comprehensive tests."
        );

        String result = applier.apply("complex_template", variables);

        assertThat(result).contains("Python programming");
        assertThat(result).contains("How do I write unit tests?");
        assertThat(result).contains("unit testing");
        assertThat(result).contains("Use pytest framework for writing comprehensive tests.");
    }

    @Test
    void applyWithSpecialCharactersInVariables() {
        PromptApplier applier = new PromptApplier();

        String result = applier.apply(
                "special_chars_template",
                Map.of("user_input", "Hello, world! How are you? @#$%^&*()")
        );

        assertThat(result).contains("Hello, world! How are you? @#$%^&*()");
    }

    @Test
    void singletonBehaviorAcrossMultipleInstancesSharesCache() {
        PromptApplier applier1 = new PromptApplier();
        PromptApplier applier2 = new PromptApplier();

        PromptTemplate template1 = applier1.getTemplate("shared_template");
        String first = applier1.apply("shared_template", Map.of());
        String second = applier2.apply("shared_template", Map.of());

        assertThat(applier2.getTemplate("shared_template")).isSameAs(template1);
        assertThat(first.stripTrailing()).isEqualTo("test");
        assertThat(second.stripTrailing()).isEqualTo("test");
    }
}
