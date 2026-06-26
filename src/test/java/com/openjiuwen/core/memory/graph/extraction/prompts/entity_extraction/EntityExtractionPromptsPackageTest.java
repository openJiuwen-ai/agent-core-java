/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package initializer in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/__init__.py}.
 */
class EntityExtractionPromptsPackageTest {

    @Test
    void packageExportsHelpersAndRegistersLanguages() {
        assertEquals("openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/__init__.py",
                EntityExtractionPromptsPackage.PYTHON_MODULE);
        assertEquals(List.of("ensure_valid_language", "format_relation_definitions", "get_formatting_kwargs"),
                EntityExtractionPromptsPackage.ALL);

        EntityExtractionPromptsPackage.registerLanguages();

        assertEquals("cn", EntityExtractionPromptsPackage.ensureValidLanguage("cn", 8));
        assertEquals("en", EntityExtractionPromptsPackage.ensureValidLanguage("en", 8));
        assertTrue(EntityExtractionPromptBase.REGISTERED_LANGUAGE.containsAll(List.of("cn", "en")));
        assertEquals("None", EntityExtractionPromptsPackage.formatRelationDefinitions(List.of(), "en"));
    }

    @Test
    void helperDelegatesUseRegisteredEnglishTemplates() {
        EntityExtractionPromptsPackage.registerLanguages();

        Map<String, String> kwargs = EntityExtractionPromptsPackage.getFormattingKwargs(
                "source docs",
                null,
                2,
                "older message",
                "current message",
                "en"
        );

        assertTrue(kwargs.get("source_description").contains("<source_description>"));
        assertTrue(kwargs.get("source_description").contains("source docs"));
        assertTrue(kwargs.get("context").contains("<history_messages>"));
        assertTrue(kwargs.get("context").contains("older message"));
        assertTrue(kwargs.get("context").contains("<current_messages>"));
        assertTrue(kwargs.get("context").contains("current message"));
        assertEquals("", kwargs.get("extra_message"));
    }
}
