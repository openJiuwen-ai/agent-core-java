/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Entity Extraction Base.
 * <p>
 * Mirrors Python's test_entity_extraction_base.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_entity_extraction_base.py</code>.
 */
@DisplayName("Entity Extraction Base Tests")
class TestEntityExtractionBase {

    @Nested
    @DisplayName("FormatSchemaInfo Tests")
    class TestFormatSchemaInfo {

        @Test
        @DisplayName("format schema info null returns empty")
        void testFormatSchemaInfoNoneReturnsEmpty() {
            String result = EntityExtractionBase.formatSchemaInfo(null, "cn");

            assertEquals("", result);
        }

        @Test
        @DisplayName("format schema info with model returns string")
        void testFormatSchemaInfoWithModelReturnsString() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(
                    "{{[ent_ext_list]}}", "entities",
                    "{{[ent_def_name]}}", "name",
                    "{{[ent_def_type]}}", "type"));

            String result = EntityExtractionBase.formatSchemaInfo(
                    new ExtractionModels.EntityExtraction(), 2, "cn");

            assertTrue(result.contains("---"));
            assertTrue(result.contains("extracted_entities") || result.contains("EntityDeclaration"));
        }
    }

    @Nested
    @DisplayName("FormatSourceDescription Tests")
    class TestFormatSourceDescription {

        @Test
        @DisplayName("format source description with text")
        void testFormatSourceDescriptionWithText() {
            String result = EntityExtractionBase.formatSourceDescription("my source", "cn");

            assertTrue(result.contains("my source"));
        }

        @Test
        @DisplayName("format source description none returns empty")
        void testFormatSourceDescriptionNoneReturnsEmpty() {
            String result = EntityExtractionBase.formatSourceDescription(null, "cn");

            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("GetFormattingKwargs Tests")
    class TestGetFormattingKwargs {

        @Test
        @DisplayName("get formatting kwargs with history and content")
        void testGetFormattingKwargsWithHistoryAndContent() {
            Map<String, String> result = EntityExtractionBase.getFormattingKwargs("past", "now", "cn");

            assertTrue(result.containsKey("context"));
            assertTrue(result.get("context").contains("past"));
            assertTrue(result.get("context").contains("now"));
            assertTrue(result.containsKey("source_description"));
            assertTrue(result.containsKey("extra_message"));
        }

        @Test
        @DisplayName("get formatting kwargs with source description")
        void testGetFormattingKwargsWithSourceDescription() {
            Map<String, String> result = EntityExtractionBase.getFormattingKwargs(
                    "src", null, 2, "", "", "cn");

            assertTrue(result.get("source_description").contains("src"));
        }
    }

    @Nested
    @DisplayName("FormatRelationDefinitions Tests")
    class TestFormatRelationDefinitions {

        @Test
        @DisplayName("format relation definitions none returns no relation")
        void testFormatRelationDefinitionsNoneReturnsNoRelation() {
            String result = EntityExtractionBase.formatRelationDefinitions(null, "cn");

            assertEquals("No relations", result);
        }

        @Test
        @DisplayName("format relation definitions with types")
        void testFormatRelationDefinitionsWithTypes() {
            EntityTypeDefinition.RelationDef relation = new EntityTypeDefinition.RelationDef();
            relation.setName("Knows");
            relation.setDescription(Map.of("cn", "knows", "en", "knows"));
            relation.setLhs(EntityTypeDefinition.HumanEntity.class);
            relation.setRhs(EntityTypeDefinition.AIEntity.class);

            String result = EntityExtractionBase.formatRelationDefinitions(List.of(relation), "cn");

            assertTrue(result.contains("Knows"));
            assertTrue(result.contains("Human"));
            assertTrue(result.contains("AI"));
        }
    }

    @Nested
    @DisplayName("FormatExistingRelations Tests")
    class TestFormatExistingRelations {

        @Test
        @DisplayName("format existing relations empty list")
        void testFormatExistingRelationsEmptyList() {
            String result = EntityExtractionBase.formatExistingRelations(List.of());

            assertEquals("", result);
        }

        @Test
        @DisplayName("format existing relations with time")
        void testFormatExistingRelationsWithTime() {
            Map<String, Object> relation = new LinkedHashMap<>();
            relation.put("content", "rel1");
            relation.put("valid_since", 0);
            relation.put("valid_until", 0);
            relation.put("offset_since", 0);
            relation.put("offset_until", 0);

            String result = EntityExtractionBase.formatExistingRelations(List.of(relation), 1, true);

            assertTrue(result.contains("rel1"));
            assertTrue(result.contains("valid_since="));
            assertTrue(result.contains("valid_until="));
        }

        @Test
        @DisplayName("format existing relations include time false")
        void testFormatExistingRelationsIncludeTimeFalse() {
            Map<String, Object> relation = Map.of("content", "r1", "valid_since", -1, "valid_until", -1);

            String result = EntityExtractionBase.formatExistingRelations(List.of(relation), 1, false);

            assertTrue(result.contains("r1"));
            assertTrue(!result.contains("valid_since="));
        }
    }

    @Nested
    @DisplayName("FormatExistingEntities Tests")
    class TestFormatExistingEntities {

        @Test
        @DisplayName("format existing entities")
        void testFormatExistingEntities() {
            Map<String, Object> entity = Map.of("name", "E1", "content", "summary");

            String result = EntityExtractionBase.formatExistingEntities(List.of(entity), 1, "cn");

            assertTrue(result.contains("E1"));
            assertTrue(result.contains("summary"));
        }
    }

    @Nested
    @DisplayName("EnsureValidLanguage Tests")
    class TestEnsureValidLanguage {

        @Test
        @DisplayName("ensure valid language valid returns language")
        void testEnsureValidLanguageValidReturnsLanguage() {
            String result = EntityExtractionBase.ensureValidLanguage("cn", 10);

            assertEquals("cn", result);
        }

        @Test
        @DisplayName("ensure valid language invalid raises")
        void testEnsureValidLanguageInvalidRaises() {
            BaseError error = assertThrows(BaseError.class,
                    () -> EntityExtractionBase.ensureValidLanguage("xx", 10));

            assertTrue(error.getMessage().contains("does not support language"));
        }

        @Test
        @DisplayName("ensure valid language too long raises")
        void testEnsureValidLanguageTooLongRaises() {
            BaseError error = assertThrows(BaseError.class,
                    () -> EntityExtractionBase.ensureValidLanguage("cn", 1));

            assertTrue(error.getMessage().contains("exceeds max length"));
        }

        @Test
        @DisplayName("ensure valid language non str with string convertible")
        void testEnsureValidLanguageNonStrWithStringConvertible() {
            Object language = new Object() {
                @Override
                public String toString() {
                    return "cn";
                }
            };

            String result = EntityExtractionBase.ensureValidLanguage(language, 10);

            assertEquals("cn", result);
        }

        @Test
        @DisplayName("ensure valid language non str raises")
        void testEnsureValidLanguageNonStrRaises() {
            assertThrows(BaseError.class, () -> EntityExtractionBase.ensureValidLanguage(new Object(), 10));
        }
    }
}
