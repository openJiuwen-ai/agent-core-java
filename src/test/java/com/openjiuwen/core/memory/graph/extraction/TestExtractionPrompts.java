/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Extraction Prompts.
 * <p>
 * Mirrors Python's test_extraction_prompts.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_extraction_prompts.py</code>.
 */
@DisplayName("Extraction Prompts Tests")
class TestExtractionPrompts {

    @Nested
    @DisplayName("FormatNewEntities Tests")
    class TestFormatNewEntities {

        @Test
        @DisplayName("empty entities returns empty string")
        void testEmptyEntitiesReturnsEmptyString() {
            String result = ExtractionPrompts.formatNewEntities(List.of(), "cn");

            assertEquals("", result);
        }

        @Test
        @DisplayName("without entity types lists names with index")
        void testWithoutEntityTypesListsNamesWithIndex() {
            List<ExtractionModels.EntityDeclaration> entities = List.of(
                    new ExtractionModels.EntityDeclaration("Alice", 0),
                    new ExtractionModels.EntityDeclaration("Bob", 0));

            String result = ExtractionPrompts.formatNewEntities(entities, null, 1, "cn");

            assertTrue(result.contains("1. Alice"));
            assertTrue(result.contains("2. Bob"));
        }

        @Test
        @DisplayName("start idx affects numbering")
        void testStartIdxAffectsNumbering() {
            List<ExtractionModels.EntityDeclaration> entities = List.of(
                    new ExtractionModels.EntityDeclaration("X", 0));

            String result = ExtractionPrompts.formatNewEntities(entities, null, 5, "cn");

            assertEquals("5. X", result);
        }

        @Test
        @DisplayName("with entity types includes type and separator")
        void testWithEntityTypesIncludesTypeAndSeparator() {
            MultilingualBaseModel.MULTILINGUAL_DESCRIPTION.put("cn", Map.of(":", ":"));
            EntityTypeDefinition.EntityDef entityType = new EntityTypeDefinition.EntityDef();
            entityType.setName("Person");
            entityType.setDescription(Map.of("cn", "Person", "en", "Person"));
            List<ExtractionModels.EntityDeclaration> entities = List.of(
                    new ExtractionModels.EntityDeclaration("Alice", 0));

            String result = ExtractionPrompts.formatNewEntities(entities, List.of(entityType), 1, "cn");

            assertTrue(result.contains("Person"));
            assertTrue(result.contains("Alice"));
            assertTrue(result.contains("---"));
        }
    }

    @Nested
    @DisplayName("ExtractEntityDeclaration Tests")
    class TestExtractEntityDeclaration {

        @Test
        @DisplayName("returns kwargs template and response format")
        void testReturnsKwargsTemplateAndResponseFormat() {
            ExtractionPrompts.PromptArtifacts artifacts = ExtractionPrompts.extractEntityDeclaration(
                    EpisodeType.CONVERSATION, "Hello", "cn");

            assertTrue(artifacts.kwargs().containsKey("entity_types"));
            assertNotNull(artifacts.template());
            assertEquals("json_schema", artifacts.responseFormat().get("type"));
            assertTrue(artifacts.responseFormat().containsKey("json_schema"));
        }

        @Test
        @DisplayName("entity types default single entity def")
        void testEntityTypesDefaultSingleEntityDef() {
            ExtractionPrompts.PromptArtifacts artifacts = ExtractionPrompts.extractEntityDeclaration(
                    EpisodeType.CONVERSATION, "Hi", "cn");

            assertTrue(artifacts.kwargs().containsKey("entity_types"));
            assertTrue(artifacts.kwargs().get("entity_types").contains("Entity")
                    || artifacts.kwargs().get("entity_types").contains("0."));
        }
    }

    @Nested
    @DisplayName("ExtractEntityAttributes Tests")
    class TestExtractEntityAttributes {

        @Test
        @DisplayName("sets entity name and summary")
        void testSetsEntityNameAndSummary() {
            Entity entity = new Entity("E1", "human", "Summary");

            ExtractionPrompts.PromptArtifacts artifacts =
                    ExtractionPrompts.extractEntityAttributes(entity, "content", "cn");

            assertEquals("E1", artifacts.kwargs().get("entity_name"));
            assertEquals("Summary", artifacts.kwargs().get("entity_summary"));
        }
    }

    @Nested
    @DisplayName("ExtractRelationDeclaration Tests")
    class TestExtractRelationDeclaration {

        @Test
        @DisplayName("returns kwargs with entities tz and relation types")
        void testReturnsKwargsWithEntitiesTzAndRelationTypes() {
            List<ExtractionModels.EntityDeclaration> entities = List.of(
                    new ExtractionModels.EntityDeclaration("E1", 0));

            ExtractionPrompts.PromptArtifacts artifacts = ExtractionPrompts.extractRelationDeclaration(
                    null, entities, 0, "UTC", "Hi", "cn");

            assertTrue(artifacts.kwargs().containsKey("tz_info"));
            assertTrue(artifacts.kwargs().containsKey("entities"));
            assertTrue(artifacts.kwargs().containsKey("relation_types"));
            assertTrue(artifacts.kwargs().containsKey("reference_time"));
            assertTrue(artifacts.kwargs().containsKey("id_range"));
            assertEquals("json_schema", artifacts.responseFormat().get("type"));
        }
    }

    @Nested
    @DisplayName("ExtractTimezone Tests")
    class TestExtractTimezone {

        @Test
        @DisplayName("returns kwargs template and response format")
        void testReturnsKwargsTemplateAndResponseFormat() {
            ExtractionPrompts.PromptArtifacts artifacts = ExtractionPrompts.extractTimezone("content", "cn");

            assertTrue(artifacts.kwargs().containsKey("context"));
            assertNotNull(artifacts.template());
            assertEquals("json_schema", artifacts.responseFormat().get("type"));
        }
    }

    @Nested
    @DisplayName("MergeExistingEntities Tests")
    class TestMergeExistingEntities {

        @Test
        @DisplayName("returns kwargs with entities to merge")
        void testReturnsKwargsWithEntitiesToMerge() {
            Entity target = new Entity("T", "human", "");
            Entity source = new Entity("S1", "human", "");

            ExtractionPrompts.PromptArtifacts artifacts =
                    ExtractionPrompts.mergeExistingEntities(target, List.of(source), "cn");

            assertEquals("T", artifacts.kwargs().get("entity_name"));
            assertTrue(artifacts.kwargs().containsKey("entities_to_merge"));
        }
    }

    @Nested
    @DisplayName("FilterRelationsForMerge Tests")
    class TestFilterRelationsForMerge {

        @Test
        @DisplayName("returns kwargs with existing relations")
        void testReturnsKwargsWithExistingRelations() {
            Entity target = new Entity("T", "human", "");
            Relation relation = new Relation("r1", "Relation", "e1", "e2");
            relation.setContent("r1");

            ExtractionPrompts.PromptArtifacts artifacts =
                    ExtractionPrompts.filterRelationsForMerge(target, List.of(relation), "cn");

            assertEquals("T", artifacts.kwargs().get("entity_name"));
            assertTrue(artifacts.kwargs().containsKey("existing_relations"));
        }
    }

    @Nested
    @DisplayName("DedupeEntityList Tests")
    class TestDedupeEntityList {

        @Test
        @DisplayName("returns kwargs with entities and candidates")
        void testReturnsKwargsWithEntitiesAndCandidates() {
            Map<String, Object> existing = new LinkedHashMap<>();
            existing.put("name", "Existing");
            existing.put("content", "summary");
            List<ExtractionModels.EntityDeclaration> candidates = List.of(
                    new ExtractionModels.EntityDeclaration("C1", 0));

            ExtractionPrompts.PromptArtifacts artifacts =
                    ExtractionPrompts.dedupeEntityList("content", candidates, List.of(existing), "cn");

            assertTrue(artifacts.kwargs().containsKey("entities"));
            assertTrue(artifacts.kwargs().containsKey("candidate_entities"));
        }
    }

    @Nested
    @DisplayName("DedupeRelationList Tests")
    class TestDedupeRelationList {

        @Test
        @DisplayName("returns kwargs with relations and new relation")
        void testReturnsKwargsWithRelationsAndNewRelation() {
            Entity entity = new Entity("E1", "human", "");
            Relation relation = new Relation("r1", "Relation", "e1", "e2");
            relation.setContent("r1");

            ExtractionPrompts.PromptArtifacts artifacts = ExtractionPrompts.dedupeRelationList(
                    "content", relation, List.of(), List.of(entity), "cn");

            assertTrue(artifacts.kwargs().containsKey("entities"));
            assertTrue(artifacts.kwargs().containsKey("existing_relations"));
            assertTrue(artifacts.kwargs().containsKey("new_relation"));
        }
    }
}
