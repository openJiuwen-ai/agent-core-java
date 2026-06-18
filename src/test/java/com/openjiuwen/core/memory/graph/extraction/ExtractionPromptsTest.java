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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's prompt assembly helpers in
 * {@code openjiuwen/core/memory/graph/extraction/extraction_prompts.py}.
 */
class ExtractionPromptsTest {

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
        assertTrue(!String.valueOf(request.kwargs().get("new_relation")).startsWith("0. "));
        assertTrue(String.valueOf(request.kwargs().get("existing_relations")).startsWith("1. Alice met Bob"));
        assertEquals("entity_extraction_dedupe_relation_en", request.promptTemplate().getName());
    }
}
