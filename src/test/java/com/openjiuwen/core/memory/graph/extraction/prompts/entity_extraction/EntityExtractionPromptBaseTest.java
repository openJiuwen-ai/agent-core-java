/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's entity extraction prompt base tests in
 * {@code tests/unit_tests/core/memory/graph/extraction/test_entity_extraction_base.py}.
 */
@DisplayName("Entity Extraction Prompt Base Tests")
class EntityExtractionPromptBaseTest {

    @BeforeEach
    void setUp() {
        clearRegistries();
    }

    @AfterEach
    void tearDown() {
        clearRegistries();
    }

    @Test
    @DisplayName("format schema info returns empty for null model")
    void formatSchemaInfoReturnsEmptyForNullModel() {
        assertEquals("", EntityExtractionPromptBase.formatSchemaInfo(null, 2, "cn"));
    }

    @Test
    @DisplayName("format schema info includes references and output schema")
    void formatSchemaInfoIncludesReferencesAndOutputSchema() {
        EntityExtractionPromptBase.REF_JSON_OBJECT_DEF.put("cn", "JSON definitions");
        EntityExtractionPromptBase.OUTPUT_FORMAT.put("cn", "Output format");

        String result = EntityExtractionPromptBase.formatSchemaInfo(new SampleModel(), 2, "cn");

        assertTrue(result.contains("---"));
        assertTrue(result.contains("# JSON definitions"));
        assertTrue(result.contains("## Child"));
        assertTrue(result.contains("payload: array[Child]"));
    }

    @Test
    @DisplayName("format source description renders supplied source text")
    void formatSourceDescriptionRendersSourceText() {
        EntityExtractionPromptBase.SOURCE_DESCRIPTION.put("cn", "Source: {source_description}");

        assertEquals("Source: my source", EntityExtractionPromptBase.formatSourceDescription("my source", "cn"));
        assertEquals("", EntityExtractionPromptBase.formatSourceDescription(null, "cn"));
    }

    @Test
    @DisplayName("get formatting kwargs assembles history content source and schema")
    void getFormattingKwargsAssemblesContext() {
        EntityExtractionPromptBase.MARK_HISTORY_MSG.put("cn", "History: {history}");
        EntityExtractionPromptBase.MARK_CURRENT_MSG.put("cn", "Current: {content}");
        EntityExtractionPromptBase.SOURCE_DESCRIPTION.put("cn", "Desc: {source_description}");
        EntityExtractionPromptBase.REF_JSON_OBJECT_DEF.put("cn", "JSON definitions");
        EntityExtractionPromptBase.OUTPUT_FORMAT.put("cn", "Output format");

        Map<String, String> result = EntityExtractionPromptBase.getFormattingKwargs(
                "src",
                new SampleModel(),
                2,
                "past",
                "now",
                "cn");

        assertTrue(result.get("context").contains("past"));
        assertTrue(result.get("context").contains("now"));
        assertEquals("Desc: src", result.get("source_description"));
        assertTrue(result.get("extra_message").contains("payload: array[Child]"));
    }

    @Test
    @DisplayName("format relation definitions returns no relation text for null or empty input")
    void formatRelationDefinitionsReturnsFallbackForMissingInput() {
        EntityExtractionPromptBase.NO_RELATION_GIVEN.put("cn", "No relations");

        assertEquals("No relations", EntityExtractionPromptBase.formatRelationDefinitions(null, "cn"));
        assertEquals("No relations", EntityExtractionPromptBase.formatRelationDefinitions(List.of(), "cn"));
    }

    @Test
    @DisplayName("format relation definitions renders relation name endpoints and description")
    void formatRelationDefinitionsRendersRelations() {
        EntityExtractionPromptBase.RELATION_FORMAT.put("cn", "{name}: {description} ({lhs}-{rhs})");
        EntityTypeDefinition.RelationDef relation = new EntityTypeDefinition.RelationDef();
        relation.setName("Knows");
        relation.setDescription(Map.of("cn", "knows"));
        relation.setLhs(EntityTypeDefinition.HumanEntity.class);
        relation.setRhs(EntityTypeDefinition.AIEntity.class);

        String result = EntityExtractionPromptBase.formatRelationDefinitions(List.of(relation), "cn");

        assertEquals("Knows: knows (Human-AI)", result);
    }

    @Test
    @DisplayName("format existing relations preserves index content and time fields")
    void formatExistingRelationsPreservesContentAndTimes() {
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("content", "rel1");
        relation.put("valid_since", 0);
        relation.put("valid_until", -1);
        relation.put("offset_since", 0);

        String result = EntityExtractionPromptBase.formatExistingRelations(List.of(relation), 2, true);

        assertTrue(result.startsWith("2. rel1"));
        assertTrue(result.contains("valid_since=1970-01-01T00:00:00+00:00"));
    }

    @Test
    @DisplayName("format existing relations skips time when requested")
    void formatExistingRelationsSkipsTimeWhenRequested() {
        Map<String, Object> relation = Map.of("content", "rel1", "valid_since", 0, "valid_until", 0);

        assertEquals("1. rel1", EntityExtractionPromptBase.formatExistingRelations(List.of(relation), 1, false));
        assertEquals("", EntityExtractionPromptBase.formatExistingRelations(List.of(), 1, true));
    }

    @Test
    @DisplayName("format existing entities renders template for every entity")
    void formatExistingEntitiesRendersTemplate() {
        EntityExtractionPromptBase.DISPLAY_ENTITY.put("cn", "{i}. {name}: {content}");
        Map<String, Object> entity = Map.of("name", "E1", "content", "summary");

        assertEquals("3. E1: summary",
                EntityExtractionPromptBase.formatExistingEntities(List.of(entity), 3, "cn"));
    }

    @Test
    @DisplayName("ensure valid language returns strings and converts non-string values")
    void ensureValidLanguageConvertsAndValidates() {
        EntityExtractionPromptBase.REGISTERED_LANGUAGE.add("cn");

        assertEquals("cn", EntityExtractionPromptBase.ensureValidLanguage("cn", 10));
        assertEquals("cn", EntityExtractionPromptBase.ensureValidLanguage(new LanguageValue("cn"), 10));
    }

    @Test
    @DisplayName("ensure valid language raises BaseError for invalid or too long values")
    void ensureValidLanguageRaisesBaseError() {
        EntityExtractionPromptBase.REGISTERED_LANGUAGE.add("cn");

        BaseError invalid = assertThrows(BaseError.class,
                () -> EntityExtractionPromptBase.ensureValidLanguage("xx", 10));
        BaseError tooLong = assertThrows(BaseError.class,
                () -> EntityExtractionPromptBase.ensureValidLanguage("cn", 1));

        assertEquals(StatusCode.MEMORY_GRAPH_LANGUAGE_INVALID, invalid.getStatus());
        assertTrue(invalid.getMessage().contains("does not support language"));
        assertEquals(StatusCode.MEMORY_GRAPH_LANGUAGE_INVALID, tooLong.getStatus());
        assertTrue(tooLong.getMessage().contains("exceeds max length"));
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
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> objectSchema(String title, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", title);
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private static final class SampleModel extends MultilingualBaseModel {

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> child = objectSchema("Child", Map.of("id", property("integer", "child id")));

            Map<String, Object> itemRef = new LinkedHashMap<>();
            itemRef.put("$ref", "#/$defs/Child");
            Map<String, Object> payload = property("array", "payload list");
            payload.put("items", itemRef);

            Map<String, Object> schema = objectSchema("SampleModel", Map.of("payload", payload));
            schema.put("$defs", Map.of("Child", child));
            return schema;
        }
    }

    private record LanguageValue(String value) {
        @Override
        public String toString() {
            return value;
        }
    }
}
