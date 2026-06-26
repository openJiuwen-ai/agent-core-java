/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's English entity extraction language registration in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/en.py}.
 */
class EntityExtractionPromptEnglishTest {

    @BeforeEach
    void setUp() {
        clearRegistries();
    }

    @AfterEach
    void tearDown() {
        clearRegistries();
    }

    @Test
    void registerLanguagePopulatesPromptRegistries() {
        EntityExtractionPromptEnglish.registerLanguage();

        assertTrue(EntityExtractionPromptBase.REGISTERED_LANGUAGE.contains("en"));
        assertEquals("\n<source_description>\nsource\n</source_description>\n",
                EntityExtractionPromptBase.formatSourceDescription("source", "en"));
        assertEquals("Definition for relevant JSON objects", EntityExtractionPromptBase.REF_JSON_OBJECT_DEF.get("en"));
        assertEquals("Output Definition (Final Output NEEDS to be JSON)",
                EntityExtractionPromptBase.OUTPUT_FORMAT.get("en"));
        assertEquals("2. Name:\nContent",
                EntityExtractionPromptBase.formatExistingEntities(
                        java.util.List.of(Map.of("name", "Name", "content", "Content")),
                        2,
                        "en"));
        assertEquals("None", EntityExtractionPromptBase.formatRelationDefinitions(null, "en"));
    }

    @Test
    void registerLanguagePopulatesEntityTypeDescriptions() {
        EntityExtractionPromptEnglish.registerLanguage();

        assertEquals(": Default entity type, pick this if no other option is suitable.",
                EntityTypeDefinition.ENTITY_DEFINITION_DESCRIPTION.get("en"));
        assertEquals(": Represent a human, can either be the user or other people.",
                EntityTypeDefinition.HUMAN_ENTITY_DESCRIPTION.get("en"));
        assertEquals(": Represent an AI assistant, can be a chatbot or other types of AI agents.",
                EntityTypeDefinition.AI_ENTITY_DESCRIPTION.get("en"));
        assertEquals(": Default relation type.", EntityTypeDefinition.RELATION_DEFINITION_DESCRIPTION.get("en"));
    }

    @Test
    void registerLanguagePopulatesMultilingualDescriptions() {
        EntityExtractionPromptEnglish.registerLanguage();

        Map<String, String> descriptions = MultilingualBaseModel.getMultilingualDescription().get("en");
        assertEquals("Name of extracted entity", descriptions.get("{{[ent_def_name]}}"));
        assertEquals("Important information regarding the entity, a short & concise summary within 250 words",
                descriptions.get("{{[ent_summary]}}"));
        assertEquals("List of IDs for existing relations that should be merged within the new relation",
                descriptions.get("{{[rel_dupe_id_list]}}"));
        assertEquals("Offset from UTC (use +HH:MM format)", descriptions.get("{{[tz_offset]}}"));
        assertEquals(":", descriptions.get(":"));
    }

    private static void clearRegistries() {
        EntityExtractionPromptBase.REGISTERED_LANGUAGE.clear();
        EntityExtractionPromptBase.SOURCE_DESCRIPTION.clear();
        EntityExtractionPromptBase.REF_JSON_OBJECT_DEF.clear();
        EntityExtractionPromptBase.OUTPUT_FORMAT.clear();
        EntityExtractionPromptBase.DISPLAY_ENTITY.clear();
        EntityExtractionPromptBase.MARK_CURRENT_MSG.clear();
        EntityExtractionPromptBase.MARK_HISTORY_MSG.clear();
        EntityExtractionPromptBase.RELATION_FORMAT.clear();
        EntityExtractionPromptBase.NO_RELATION_GIVEN.clear();
        EntityTypeDefinition.ENTITY_DEFINITION_DESCRIPTION.clear();
        EntityTypeDefinition.RELATION_DEFINITION_DESCRIPTION.clear();
        EntityTypeDefinition.HUMAN_ENTITY_DESCRIPTION.clear();
        EntityTypeDefinition.AI_ENTITY_DESCRIPTION.clear();
        MultilingualBaseModel.getMultilingualDescription().clear();
    }
}
