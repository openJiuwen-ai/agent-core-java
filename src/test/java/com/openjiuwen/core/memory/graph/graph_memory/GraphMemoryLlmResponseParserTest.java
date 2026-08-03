/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.GraphStoreUtils;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.ExtractionModels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for graph-memory LLM response parsing.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.parse_llm_response} module in
 * {@code openjiuwen/core/memory/graph/graph_memory/parse_llm_response.py}.</p>
 */
class GraphMemoryLlmResponseParserTest {

    @Test
    void parseIsoNormalizesShortFieldsAndOffset() {
        GraphStoreUtils.TimestampOffset expected = GraphStoreUtils.iso2timestamp("2025-09-01T02:03:04+08:00");

        assertThat(GraphMemoryLlmResponseParser.parseIso("seen at 2025-9-1T2:3:4+8:0"))
                .isEqualTo(expected);
        assertThat(GraphMemoryLlmResponseParser.parseIso(null))
                .isEqualTo(new GraphStoreUtils.TimestampOffset(-1, 0));
        assertThat(GraphMemoryLlmResponseParser.parseIso("not a date"))
                .isEqualTo(new GraphStoreUtils.TimestampOffset(-1, 0));
    }

    @Test
    void dictToRelationUnwrapsSingleNestedResponseAndUsesOneBasedEntityIds() {
        Entity entity = entity("entity-1", "Alice");
        Map<String, Object> inner = mutableMap(
                "source_id", "1",
                "target_id", 1,
                "name", "knows",
                "fact", "Alice knows herself",
                "valid_since", "2025-09-01T02:03:04+08:00"
        );
        Relation relation = GraphMemoryLlmResponseParser.dictToRelation(
                mutableMap("relation", inner),
                List.of(entity),
                mutableMap("user_id", "tester")
        );

        assertThat(relation).isNotNull();
        assertThat(relation.getObjType()).isEqualTo("EntityFact");
        assertThat(relation.getName()).isEqualTo("knows");
        assertThat(relation.getContent()).isEqualTo("Alice knows herself");
        assertThat(relation.getLhs()).isSameAs(entity);
        assertThat(relation.getRhs()).isSameAs(entity);
        assertThat(relation.getUserId()).isEqualTo("tester");
        assertThat(relation.getOffsetSince()).isEqualTo(32);

        assertThat(GraphMemoryLlmResponseParser.dictToRelation(mutableMap("source_id", 0, "target_id", 1),
                List.of(entity))).isNull();
    }

    @Test
    void parseAllRelationsDeclaresEntitiesAndDeduplicatesReturnedEntities() {
        EntityTypeDefinition.HumanEntity human = new EntityTypeDefinition.HumanEntity();
        EntityTypeDefinition.AIEntity ai = new EntityTypeDefinition.AIEntity();
        ExtractionModels.EntityDeclaration alice = new ExtractionModels.EntityDeclaration("Alice", 0);
        ExtractionModels.EntityDeclaration bot = new ExtractionModels.EntityDeclaration("Bot", 1);
        List<Map<String, Object>> relations = new ArrayList<>();
        relations.add(mutableMap("source_id", 1, "target_id", 2, "name", "talks", "fact", "Alice talks", "content",
                "Alice talks to Bot"));
        relations.add(mutableMap("source_id", 1, "target_id", 2, "name", "short", "fact", "Short", "content",
                "talks"));

        GraphMemoryLlmResponseParser.ParseAllRelationsResult parsed =
                GraphMemoryLlmResponseParser.parseAllRelations(relations, List.of(alice, bot), List.of(human, ai));

        assertThat(parsed.relations()).hasSize(2);
        assertThat(parsed.relations().get(0).getObjType()).isEqualTo("Relation");
        assertThat(parsed.entities()).extracting(Entity::getName).containsExactly("Alice", "Bot");
        assertThat(parsed.entities()).extracting(Entity::getObjType).containsExactly("Human", "AI");
        assertThat(relations.get(0).get("content")).isEqualTo("Alice talks to Bot");
        assertThat(relations.get(1).get("content")).isEqualTo("");
    }

    @Test
    void resolveEntitiesKeepsResultLengthAndBuildsMergeArguments() {
        Entity existingAlice = entity("existing-alice", "Alice");
        Entity existingBob = entity("existing-bob", "Bob");
        ExtractionModels.EntityDeclaration newAlice = new ExtractionModels.EntityDeclaration("Alice candidate", 0);
        ExtractionModels.EntityDeclaration newCarol = new ExtractionModels.EntityDeclaration("Carol", 0);
        Map<String, Object> duplicate = mutableMap("id", 1, "duplicate_ids", List.of(2, 3));

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult resolved =
                GraphMemoryLlmResponseParser.resolveEntities(
                        List.of(newAlice, newCarol),
                        List.of(existingAlice, existingBob),
                        List.of(duplicate)
                );

        assertThat(resolved.entities()).containsExactly(existingAlice, newCarol);
        assertThat(resolved.mergingArgs()).hasSize(1);
        assertThat(resolved.mergingArgs().get(0).target()).isSameAs(existingAlice);
        assertThat(resolved.mergingArgs().get(0).sources()).containsExactly(existingBob);
        assertThat(resolved.entityUuidsToRemove()).containsExactly("existing-bob");
    }

    @Test
    void parseRelationMergingUpdatesRelationAndReturnsExistingRelationUuidsToRemove() {
        Relation relation = new Relation();
        relation.setContent("old");
        Map<String, Object> response = mutableMap(
                "need_merging", true,
                "combined_content", " merged content ",
                "duplicate_ids", List.of(1, 3),
                "valid_since", "2025-09-01T02:03:04+08:00",
                "valid_until", "invalid"
        );
        Set<String> toRemove = GraphMemoryLlmResponseParser.parseRelationMerging(
                response,
                relation,
                List.of(mutableMap("uuid", "rel-1"), mutableMap("uuid", "rel-2"))
        );

        assertThat(relation.getContent()).isEqualTo("merged content");
        assertThat(relation.getValidSince()).isGreaterThan(0);
        assertThat(relation.getOffsetSince()).isEqualTo(32);
        assertThat(relation.getValidUntil()).isEqualTo(-1);
        assertThat(toRemove).containsExactly("rel-1");
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        return entity;
    }

    private static Map<String, Object> mutableMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }
}
