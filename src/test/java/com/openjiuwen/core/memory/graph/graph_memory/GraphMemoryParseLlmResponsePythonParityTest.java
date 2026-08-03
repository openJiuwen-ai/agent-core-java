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
 * Python parity tests for graph-memory LLM response parsing.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/memory/graph/graph_memory/test_parse_llm_response.py}.</p>
 */
class GraphMemoryParseLlmResponsePythonParityTest {

    @Test
    void testIsoStringReturnsTimestampAndOffset() {
        GraphStoreUtils.TimestampOffset result = GraphMemoryLlmResponseParser.parseIso("2025-01-15T10:00:00Z");

        assertThat(result.timestamp()).isGreaterThanOrEqualTo(-1);
        assertThat(result.offset()).isInstanceOf(Integer.class);
    }

    @Test
    void testNoneReturnsInvalid() {
        assertThat(GraphMemoryLlmResponseParser.parseIso(null))
                .isEqualTo(new GraphStoreUtils.TimestampOffset(-1, 0));
    }

    @Test
    void testInvalidStringReturnsInvalid() {
        assertThat(GraphMemoryLlmResponseParser.parseIso("not a date"))
                .isEqualTo(new GraphStoreUtils.TimestampOffset(-1, 0));
    }

    @Test
    void testIsoWithTimezoneOffset() {
        GraphStoreUtils.TimestampOffset result = GraphMemoryLlmResponseParser.parseIso("2025-06-01T12:00:00+08:00");

        assertThat(result.timestamp()).isGreaterThanOrEqualTo(0);
        assertThat(result.offset()).isInstanceOf(Integer.class);
    }

    @Test
    void testValidResponseReturnsRelation() {
        Entity e1 = entity("e1", "A");
        Entity e2 = entity("e2", "B");
        Relation relation = GraphMemoryLlmResponseParser.dictToRelation(
                mutableMap("source_id", 1, "target_id", 2, "name", "knows", "fact", "A knows B"),
                List.of(e1, e2),
                mutableMap("user_id", "u1")
        );

        assertThat(relation).isNotNull();
        assertThat(relation.getLhs()).isSameAs(e1);
        assertThat(relation.getRhs()).isSameAs(e2);
        assertThat(relation.getContent()).isEqualTo("A knows B");
        assertThat(relation.getObjType()).isEqualTo("Relation");
        assertThat(relation.getUserId()).isEqualTo("u1");
    }

    @Test
    void testSameSourceAndTargetEntityFact() {
        Entity e1 = entity("e1", "A");
        Relation relation = GraphMemoryLlmResponseParser.dictToRelation(
                mutableMap("source_id", 1, "target_id", 1, "name", "fact", "fact", "summary"),
                List.of(e1),
                mutableMap("user_id", "u1")
        );

        assertThat(relation).isNotNull();
        assertThat(relation.getObjType()).isEqualTo("EntityFact");
    }

    @Test
    void testInvalidIndicesReturnNone() {
        Entity entity = entity("e", "E");
        Relation relation = GraphMemoryLlmResponseParser.dictToRelation(
                mutableMap("source_id", 0, "target_id", 2, "name", "R", "fact", "x"),
                List.of(entity),
                mutableMap("user_id", "u1")
        );

        assertThat(relation).isNull();
    }

    @Test
    void testSingleKeyWrapperUnwraps() {
        Entity e1 = entity("e1", "A");
        Entity e2 = entity("e2", "B");
        Relation relation = GraphMemoryLlmResponseParser.dictToRelation(
                mutableMap("relations", mutableMap("source_id", 1, "target_id", 2, "name", "R", "fact", "f")),
                List.of(e1, e2),
                mutableMap("user_id", "u1")
        );

        assertThat(relation).isNotNull();
    }

    @Test
    void testEntityDeclarationConvertedToEntity() {
        List<EntityTypeDefinition.EntityDef> types = List.of(new EntityTypeDefinition.EntityDef());
        List<ExtractionModels.EntityDeclaration> declarations =
                List.of(new ExtractionModels.EntityDeclaration("E1", 0));

        List<Entity> result = GraphMemoryLlmResponseParser.declareEntities(
                declarations,
                types,
                mutableMap("user_id", "u1", "created_at", 0)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(Entity.class);
        assertThat(result.get(0).getName()).isEqualTo("E1");
        assertThat(result.get(0).getUserId()).isEqualTo("u1");
        assertThat(result.get(0).getCreatedAt()).isZero();
    }

    @Test
    void testEntityPassthrough() {
        List<EntityTypeDefinition.EntityDef> types = List.of(new EntityTypeDefinition.EntityDef());
        Entity entity = entity("e", "E");

        List<Entity> result = GraphMemoryLlmResponseParser.declareEntities(List.of(entity), types);

        assertThat(result).containsExactly(entity);
    }

    @Test
    void testParseAllRelationsReturnsRelationsAndEntities() {
        List<ExtractionModels.EntityDeclaration> declarations = List.of(
                new ExtractionModels.EntityDeclaration("E1", 0),
                new ExtractionModels.EntityDeclaration("E2", 0)
        );
        List<EntityTypeDefinition.EntityDef> types = List.of(new EntityTypeDefinition.EntityDef());
        List<Map<String, Object>> relations = new ArrayList<>();
        relations.add(mutableMap("source_id", 1, "target_id", 2, "name", "R", "fact", "rel"));

        GraphMemoryLlmResponseParser.ParseAllRelationsResult result =
                GraphMemoryLlmResponseParser.parseAllRelations(
                        relations,
                        declarations,
                        types,
                        mutableMap("created_at", 0, "user_id", "u1")
                );

        assertThat(result.entities()).hasSize(2);
        assertThat(result.relations()).hasSize(1);
        assertThat(result.relations().get(0).getContent()).isEqualTo("rel");
    }

    @Test
    void testDuplicateContentEmptied() {
        List<ExtractionModels.EntityDeclaration> declarations = List.of(
                new ExtractionModels.EntityDeclaration("A", 0),
                new ExtractionModels.EntityDeclaration("B", 0)
        );
        List<EntityTypeDefinition.EntityDef> types = List.of(new EntityTypeDefinition.EntityDef());
        List<Map<String, Object>> relations = new ArrayList<>();
        relations.add(mutableMap("source_id", 1, "target_id", 2, "name", "R", "fact", "same", "content", "same"));
        relations.add(mutableMap("source_id", 1, "target_id", 2, "name", "R2", "fact", "same", "content", "same"));

        GraphMemoryLlmResponseParser.ParseAllRelationsResult result =
                GraphMemoryLlmResponseParser.parseAllRelations(
                        relations,
                        declarations,
                        types,
                        mutableMap("created_at", 0, "user_id", "u1")
                );

        assertThat(result.relations()).hasSize(2);
        assertThat(relations.get(1).get("content")).isEqualTo("");
    }

    @Test
    void testNoDuplicationReturnsCandidatesAndEmptyMerge() {
        List<ExtractionModels.EntityDeclaration> candidates =
                List.of(new ExtractionModels.EntityDeclaration("E1", 0));

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(candidates, List.of(), List.of());

        assertThat(result.entities()).hasSize(1);
        assertThat(result.mergingArgs()).isEmpty();
        assertThat(result.entityUuidsToRemove()).isEmpty();
    }

    @Test
    void testDedupeToExistingReplacesInResult() {
        List<ExtractionModels.EntityDeclaration> candidates = List.of(
                new ExtractionModels.EntityDeclaration("E1", 0),
                new ExtractionModels.EntityDeclaration("E2", 0)
        );
        Entity existing = entity("existing-e1", "E1");
        List<Map<String, Object>> duplication = List.of(mutableMap("id", 1, "duplicate_ids", List.of(2)));

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(candidates, List.of(existing), duplication);

        assertThat(result.entities()).hasSize(2);
        assertThat(result.entities()).contains(existing);
        assertThat(result.entityUuidsToRemove()).isInstanceOf(Set.class);
    }

    @Test
    void testResolveEntitiesDupByNameUsesNameLookup() {
        Entity existing = entity("existing-alpha", "Alpha");
        List<ExtractionModels.EntityDeclaration> candidates =
                List.of(new ExtractionModels.EntityDeclaration("Beta", 0));
        List<Map<String, Object>> duplication =
                List.of(mutableMap("id", "x", "name", "Alpha", "duplicate_ids", List.of(2)));

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(candidates, List.of(existing), duplication);

        assertThat(result.entities()).hasSize(1);
        assertThat(result.entities().get(0)).isSameAs(existing);
    }

    @Test
    void testResolveEntitiesExistingMergeProducesToRemove() {
        Entity e0 = entity("u0", "E0");
        Entity e1 = entity("u1", "E1");
        List<Map<String, Object>> duplication = List.of(mutableMap("id", 1, "duplicate_ids", List.of(2)));

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(List.of(), List.of(e0, e1), duplication);

        assertThat(result.entityUuidsToRemove()).contains("u1");
        assertThat(result.entityUuidsToRemove()).doesNotContain("u0");
        assertThat(result.mergingArgs()).hasSize(1);
        assertThat(result.mergingArgs().get(0).target().getUuid()).isEqualTo("u0");
        assertThat(result.mergingArgs().get(0).sources()).containsExactly(e1);
    }

    @Test
    void testResolveMergeDictTargetNotInResult() {
        Entity source = entity("src-uuid", "Src");
        Entity target = entity("tgt-uuid", "Tgt");
        List<ExtractionModels.EntityDeclaration> candidates =
                List.of(new ExtractionModels.EntityDeclaration("Candidate", 0));
        List<Map<String, Object>> duplication = List.of(
                mutableMap("id", 1, "duplicate_ids", List.of(3)),
                mutableMap("id", 2, "duplicate_ids", List.of(1))
        );

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(candidates, List.of(source, target), duplication);

        assertThat(result.entities().get(0)).isSameAs(source);
        assertThat(result.mergingArgs()).hasSize(1);
        assertThat(result.mergingArgs().get(0).target()).isSameAs(source);
        assertThat(result.mergingArgs().get(0).sources()).containsExactly(target);
    }

    @Test
    void testResolveMergeDictTargetInResult() {
        Entity target = entity("tgt-uuid", "Tgt");
        Entity source = entity("src-uuid", "Src");
        List<ExtractionModels.EntityDeclaration> candidates = List.of(
                new ExtractionModels.EntityDeclaration("TargetCandidate", 0),
                new ExtractionModels.EntityDeclaration("SourceCandidate", 0)
        );
        List<Map<String, Object>> duplication = List.of(
                mutableMap("id", 1, "duplicate_ids", List.of(3)),
                mutableMap("id", 2, "duplicate_ids", List.of(4)),
                mutableMap("id", 1, "duplicate_ids", List.of(2))
        );

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(candidates, List.of(target, source), duplication);

        assertThat(result.entities()).containsExactly(target, target);
        assertThat(result.mergingArgs()).hasSize(1);
        assertThat(result.mergingArgs().get(0).target()).isSameAs(target);
        assertThat(result.mergingArgs().get(0).sources()).containsExactly(source);
    }

    @Test
    void testResolveEntitiesParseEntityMergingTgtInMergeMap() {
        Entity e0 = entity("u0", "E0");
        Entity e1 = entity("u1", "E1");
        Entity e2 = entity("u2", "E2");
        List<Map<String, Object>> duplication = List.of(
                mutableMap("id", 1, "duplicate_ids", List.of(2)),
                mutableMap("id", 1, "duplicate_ids", List.of(3))
        );

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(List.of(), List.of(e0, e1, e2), duplication);

        assertThat(result.entityUuidsToRemove()).contains("u1", "u2");
    }

    @Test
    void testResolveEntitiesParseEntityMergingSrcInMergeMap() {
        Entity e0 = entity("u0", "E0");
        Entity e1 = entity("u1", "E1");
        Entity e2 = entity("u2", "E2");
        List<Map<String, Object>> duplication = List.of(
                mutableMap("id", 1, "duplicate_ids", List.of(2)),
                mutableMap("id", 2, "duplicate_ids", List.of(1))
        );

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(List.of(), List.of(e0, e1, e2), duplication);

        assertThat(result.entityUuidsToRemove()).containsAnyOf("u0", "u1");
        assertThat(result.mergingArgs().isEmpty() && result.entityUuidsToRemove().isEmpty()).isFalse();
    }

    @Test
    void testResolveEntitiesParseEntityMergingElseBranch() {
        Entity e0 = entity("u0", "E0");
        Entity e1 = entity("u1", "E1");
        List<Map<String, Object>> duplication = List.of(mutableMap("id", 1, "duplicate_ids", List.of(2)));

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(List.of(), List.of(e0, e1), duplication);

        assertThat(result.entityUuidsToRemove()).contains("u1");
        assertThat(result.mergingArgs()).hasSize(1);
        assertThat(result.mergingArgs().get(0).target().getUuid()).isEqualTo("u0");
    }

    @Test
    void testResolveEntitiesParseEntityMergingTgtEqSrcContinues() {
        Entity e0 = entity("u0", "E0");
        List<Map<String, Object>> duplication = List.of(mutableMap("id", 1, "duplicate_ids", List.of(1)));

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(List.of(), List.of(e0), duplication);

        assertThat(result.mergingArgs()).isEmpty();
        assertThat(result.entityUuidsToRemove()).isEmpty();
    }

    @Test
    void testResolveEntitiesParseEntityMergingElseAddToExistingSet() {
        Entity e0 = entity("u0", "E0");
        Entity e1 = entity("u1", "E1");
        Entity e2 = entity("u2", "E2");
        List<Map<String, Object>> duplication = List.of(
                mutableMap("id", 1, "duplicate_ids", List.of(2)),
                mutableMap("id", 2, "duplicate_ids", List.of(3))
        );

        GraphMemoryLlmResponseParser.ResolvedEntitiesResult result =
                GraphMemoryLlmResponseParser.resolveEntities(List.of(), List.of(e0, e1, e2), duplication);

        assertThat(result.entityUuidsToRemove()).contains("u1", "u2");
        assertThat(result.mergingArgs()).hasSize(1);
        assertThat(result.mergingArgs().get(0).target().getUuid()).isEqualTo("u0");
        assertThat(result.mergingArgs().get(0).sources()).hasSize(2);
    }

    @Test
    void testNeedMergingAndContentUpdatesRelation() {
        Relation relation = relation("R", "old", "e1", "e2");
        List<Map<String, Object>> existing = List.of(mutableMap("uuid", "r1"), mutableMap("uuid", "r2"));
        Map<String, Object> response = mutableMap(
                "need_merging", true,
                "combined_content", "merged",
                "duplicate_ids", List.of(1)
        );

        Set<String> toRemove = GraphMemoryLlmResponseParser.parseRelationMerging(response, relation, existing);

        assertThat(relation.getContent()).isEqualTo("merged");
        assertThat(toRemove).contains("r1");
    }

    @Test
    void testNoMergeReturnsEmpty() {
        Relation relation = relation("R", "c", "e1", "e2");

        Set<String> toRemove = GraphMemoryLlmResponseParser.parseRelationMerging(
                mutableMap("need_merging", false),
                relation,
                List.of()
        );

        assertThat(toRemove).isEmpty();
    }

    @Test
    void testParseRelationMergingUpdatesValidSinceUntil() {
        Relation relation = relation("R", "c", "e1", "e2");
        relation.setValidSince(0);
        relation.setValidUntil(-1);
        relation.setOffsetSince(0);
        relation.setOffsetUntil(0);
        Map<String, Object> response = mutableMap(
                "need_merging", true,
                "combined_content", "merged",
                "valid_since", "2025-01-01T00:00:00Z",
                "valid_until", "2025-12-31T23:59:59Z",
                "duplicate_ids", List.of()
        );

        GraphMemoryLlmResponseParser.parseRelationMerging(response, relation, List.of(mutableMap("uuid", "r1")));

        assertThat(relation.getContent()).isEqualTo("merged");
        assertThat(relation.getValidSince()).isGreaterThanOrEqualTo(0);
        assertThat(relation.getValidUntil()).isGreaterThanOrEqualTo(0);
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setContent("");
        entity.setObjType("Entity");
        return entity;
    }

    private static Relation relation(String name, String content, Object lhs, Object rhs) {
        Relation relation = new Relation(lhs, rhs);
        relation.setName(name);
        relation.setContent(content);
        relation.setObjType("Relation");
        relation.setValidSince(0);
        relation.setValidUntil(-1);
        return relation;
    }

    private static Map<String, Object> mutableMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }
}
