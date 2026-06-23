/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_extraction_prompts} in
 * {@code tests/unit_tests/core/memory/graph/extraction/test_extraction_prompts.py}.
 */
class ExtractionPromptsTest {

    @Test
    void formatNewEntitiesEmptyEntitiesReturnsEmptyString() {
        String result = ExtractionPrompts.formatNewEntities(List.of(), null, 1, "en");

        assertEquals("", result);
    }

    @Test
    void formatNewEntitiesWithoutEntityTypesListsNamesWithIndex() {
        List<ExtractionModels.EntityDeclaration> entities = List.of(
                new ExtractionModels.EntityDeclaration("Alice", 0),
                new ExtractionModels.EntityDeclaration("Bob", 0)
        );

        String result = ExtractionPrompts.formatNewEntities(entities, null, 1, "en");

        assertTrue(result.contains("1. Alice"));
        assertTrue(result.contains("2. Bob"));
    }

    @Test
    void formatNewEntitiesStartIndexAffectsNumbering() {
        String result = ExtractionPrompts.formatNewEntities(
                List.of(new ExtractionModels.EntityDeclaration("X", 0)),
                null,
                5,
                "en"
        );

        assertEquals("5. X", result);
    }

    @Test
    void extractEntityDeclarationBuildsConversationPromptAndEntityTypeList() {
        EntityTypeDefinition.EntityDef person = new EntityTypeDefinition.EntityDef();
        person.setName("Person");
        person.setDescription(Map.of("en", ": A person"));

        ExtractionPrompts.PromptRequest request = ExtractionPrompts.extractEntityDeclaration(
                EpisodeType.CONVERSATION,
                "current",
                "history",
                "source docs",
                List.of(person),
                "en",
                Map.of("custom", "value"),
                2
        );

        assertEquals("0. Person: A person", request.kwargs().get("entity_types"));
        assertEquals("value", request.kwargs().get("custom"));
        assertTrue(String.valueOf(request.kwargs().get("context")).contains("current"));
        assertNotNull(request.promptTemplate());
        assertEquals("entity_extraction_conversation_en", request.promptTemplate().getName());
        assertEquals("json_schema", request.responseFormat().get("type"));
        assertTrue(request.responseFormat().containsKey("json_schema"));
    }

    @Test
    void extractEntityDeclarationDefaultsSingleEntityDefinition() {
        ExtractionPrompts.PromptRequest request = ExtractionPrompts.extractEntityDeclaration(
                EpisodeType.CONVERSATION,
                "Hi",
                "",
                "",
                null,
                "en",
                null,
                2
        );

        assertTrue(request.kwargs().containsKey("entity_types"));
        assertTrue(String.valueOf(request.kwargs().get("entity_types")).contains("Entity"));
    }

    @Test
    void extractEntityAttributesDoublesHumanSummaryTargetAndSerializesAttributes() {
        Entity entity = new Entity();
        entity.setObjType("Human");
        entity.setName("Alice");
        entity.setContent("Old summary");
        entity.setAttributes(Map.of("role", "user"));

        ExtractionPrompts.PromptRequest request = ExtractionPrompts.extractEntityAttributes(
                entity,
                "new content",
                "",
                "en",
                Map.of("summary_target", "4"),
                2
        );

        assertEquals("Alice", request.kwargs().get("entity_name"));
        assertEquals("Old summary", request.kwargs().get("entity_summary"));
        assertEquals(8, request.kwargs().get("summary_target"));
        assertTrue(String.valueOf(request.kwargs().get("entity_attribute")).contains("\"role\""));
        assertEquals("entity_extraction_summary_create_en", request.promptTemplate().getName());
    }

    @Test
    void extractRelationDeclarationReturnsEntitiesTimezoneAndRelationTypes() {
        List<ExtractionModels.EntityDeclaration> entities = List.of(
                new ExtractionModels.EntityDeclaration("E1", 0)
        );

        ExtractionPrompts.PromptRequest request = ExtractionPrompts.extractRelationDeclaration(
                null,
                entities,
                0L,
                "UTC",
                "Hi",
                "",
                null,
                "",
                "en",
                2
        );

        assertTrue(request.kwargs().containsKey("tz_info"));
        assertTrue(request.kwargs().containsKey("entities"));
        assertTrue(request.kwargs().containsKey("relation_types"));
        assertTrue(request.kwargs().containsKey("reference_time"));
        assertTrue(request.kwargs().containsKey("id_range"));
        assertEquals("json_schema", request.responseFormat().get("type"));
    }

    @Test
    void extractTimezoneReturnsContextAndResponseFormat() {
        ExtractionPrompts.PromptRequest request = ExtractionPrompts.extractTimezone("content", "", "", "en", 2);

        assertTrue(request.kwargs().containsKey("context"));
        assertNotNull(request.promptTemplate());
        assertEquals("json_schema", request.responseFormat().get("type"));
    }

    @Test
    void mergeExistingEntitiesReturnsEntitiesToMerge() {
        Entity target = new Entity();
        target.setName("T");
        target.setContent("");
        target.setObjType("human");
        Entity source = new Entity();
        source.setName("S1");
        source.setContent("");
        source.setObjType("human");

        ExtractionPrompts.PromptRequest request = ExtractionPrompts.mergeExistingEntities(
                target,
                List.of(source),
                "en",
                null,
                2
        );

        assertEquals("T", request.kwargs().get("entity_name"));
        assertTrue(request.kwargs().containsKey("entities_to_merge"));
    }

    @Test
    void filterRelationsForMergeReturnsExistingRelations() {
        Entity target = new Entity();
        target.setName("T");
        target.setContent("");
        target.setObjType("human");
        Relation relation = new Relation();
        relation.setContent("r1");
        relation.setName("Relation");

        ExtractionPrompts.PromptRequest request = ExtractionPrompts.filterRelationsForMerge(
                target,
                List.of(relation),
                "en",
                null,
                2
        );

        assertEquals("T", request.kwargs().get("entity_name"));
        assertTrue(request.kwargs().containsKey("existing_relations"));
    }

    @Test
    void dedupeEntityListReturnsEntitiesAndCandidates() {
        List<ExtractionModels.EntityDeclaration> candidates = List.of(
                new ExtractionModels.EntityDeclaration("C1", 0)
        );

        ExtractionPrompts.PromptRequest request = ExtractionPrompts.dedupeEntityList(
                "content",
                candidates,
                List.of(),
                null,
                "",
                "",
                "en",
                2
        );

        assertTrue(request.kwargs().containsKey("entities"));
        assertTrue(request.kwargs().containsKey("candidate_entities"));
        assertTrue(String.valueOf(request.kwargs().get("candidate_entities")).contains("C1"));
    }

    @Test
    void formatNewEntitiesIncludesTypeDefinitionsAndCandidateNames() {
        EntityTypeDefinition.EntityDef person = new EntityTypeDefinition.EntityDef();
        person.setName("Person");
        person.setDescription(Map.of("en", "A person"));
        ExtractionModels.EntityDeclaration alice = new ExtractionModels.EntityDeclaration("Alice", 0);

        String result = ExtractionPrompts.formatNewEntities(List.of(alice), List.of(person), 3, "en");

        assertEquals("Person:A person\n---\n3. Alice (Person)", result);
    }

    @Test
    void dedupeRelationListFormatsNewRelationWithoutZeroPrefix() {
        Entity alice = new Entity();
        alice.setName("Alice");
        alice.setContent("person");

        Relation relation = new Relation();
        relation.setName("knows");
        relation.setContent("Alice knows Bob");
        relation.setValidSince(-1);
        relation.setValidUntil(-1);

        Map<String, Object> existingRelation = new LinkedHashMap<>();
        existingRelation.put("content", "Alice met Bob");
        existingRelation.put("valid_since", -1);
        existingRelation.put("valid_until", -1);

        ExtractionPrompts.PromptRequest request = ExtractionPrompts.dedupeRelationList(
                "content",
                relation,
                List.of(existingRelation),
                List.of(alice),
                "history",
                "source",
                "en",
                2
        );

        assertTrue(String.valueOf(request.kwargs().get("new_relation")).startsWith("Alice knows Bob"));
        assertFalse(String.valueOf(request.kwargs().get("new_relation")).startsWith("0. "));
        assertTrue(String.valueOf(request.kwargs().get("existing_relations")).startsWith("1. Alice met Bob"));
        assertEquals("entity_extraction_dedupe_relation_en", request.promptTemplate().getName());
    }
}
