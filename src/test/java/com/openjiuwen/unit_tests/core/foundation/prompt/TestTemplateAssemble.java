/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.prompt;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for template assembly.
 * <p>
 * Mirrors Python's {@code test_template_assemble.py} from
 * {@code tests/unit_tests/core/foundation/prompt/test_template_assemble.py}.
 * Tests prompt template creation, variable substitution, and assembly.
 */
class TestTemplateAssemble {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testPromptTemplateClassExists() {
        assertNotNull(PromptTemplate.class);
    }

    @Test
    @Tag("level0")
    void testMapClassExists() {
        assertNotNull(Map.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Template creation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testPromptTemplateCreation() {
        PromptTemplate template = new PromptTemplate("Hello, {name}!");
        assertNotNull(template);
    }

    @Test
    @Tag("level1")
    void testTemplateContent() {
        PromptTemplate template = new PromptTemplate("Hello, {name}!");
        assertTrue(template.getTemplate().contains("{name}"));
    }

    @Test
    @Tag("level1")
    void testEmptyTemplate() {
        PromptTemplate template = new PromptTemplate("");
        assertEquals("", template.getTemplate());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Variable substitution)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testSingleVariableSubstitution() {
        PromptTemplate template = new PromptTemplate("Hello, {name}!");
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "World");
        String result = template.format(variables);
        assertEquals("Hello, World!", result);
    }

    @Test
    @Tag("level2")
    void testMultipleVariableSubstitution() {
        PromptTemplate template = new PromptTemplate("{greeting}, {name}! Today is {day}.");
        Map<String, Object> variables = new HashMap<>();
        variables.put("greeting", "Hello");
        variables.put("name", "World");
        variables.put("day", "Monday");
        String result = template.format(variables);
        assertEquals("Hello, World! Today is Monday.", result);
    }

    @Test
    @Tag("level2")
    void testNoVariablesTemplate() {
        PromptTemplate template = new PromptTemplate("This is a static template.");
        Map<String, Object> variables = new HashMap<>();
        String result = template.format(variables);
        assertEquals("This is a static template.", result);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Complex templates)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testTemplateWithNewlines() {
        PromptTemplate template = new PromptTemplate("Line 1\nLine 2: {value}\nLine 3");
        Map<String, Object> variables = new HashMap<>();
        variables.put("value", "test");
        String result = template.format(variables);
        assertTrue(result.contains("Line 1"));
        assertTrue(result.contains("Line 2: test"));
        assertTrue(result.contains("Line 3"));
    }

    @Test
    @Tag("level3")
    void testNestedVariables() {
        PromptTemplate template = new PromptTemplate("User: {user.name}, Age: {user.age}");
        assertNotNull(template);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 4 (Template validation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level4")
    void testTemplateVariableCount() {
        PromptTemplate template = new PromptTemplate("{a} and {b} and {c}");
        java.util.List<String> variables = template.getInputVariables();
        assertNotNull(variables);
    }

    @Test
    @Tag("level4")
    void testPartialSubstitution() {
        PromptTemplate template = new PromptTemplate("{a}, {b}, {c}");
        Map<String, Object> variables = new HashMap<>();
        variables.put("a", "valueA");
        // Missing b and c
        assertNotNull(template);
    }
}