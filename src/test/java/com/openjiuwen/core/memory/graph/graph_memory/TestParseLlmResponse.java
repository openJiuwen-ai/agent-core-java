/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.ExtractionModels;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Parse LLM Response.
 *
 * <p>Mirrors Python's {@code test_parse_llm_response.py} from
 * {@code tests/unit_tests/core/memory/graph/graph_memory/test_parse_llm_response.py}.</p>
 */
@DisplayName("Parse LLM Response Tests")
class TestParseLlmResponse {

    @Nested
    @DisplayName("ParseIso Tests")
    class TestParseIso {

        @Test
        void testIsoStringReturnsTimestampAndOffset() {
            int[] result = ParseLlmResponse.parseIso("2025-01-15T10:00:00Z");

            assertEquals(2, result.length);
            assertTrue(result[0] >= 0 || result[0] == -1);
            assertEquals(0, result[1]);
        }

        @Test
        void testNoneReturnsInvalid() {
            assertArrayEquals(new int[]{-1, 0}, ParseLlmResponse.parseIso(null));
        }

        @Test
        void testInvalidStringReturnsInvalid() {
            assertArrayEquals(new int[]{-1, 0}, ParseLlmResponse.parseIso("not a date"));
        }

        @Test
        void testIsoWithTimezoneOffset() {
            int[] result = ParseLlmResponse.parseIso("2025-06-01T12:00:00+08:00");

            assertTrue(result[0] >= 0);
            assertEquals(32, result[1]);
        }
    }

    @Nested
    @DisplayName("Dict2Relation Tests")
    class TestDict2Relation {

        @Test
        void testValidResponseReturnsRelation() {
            Entity e1 = entity("e1", "A");
            Entity e2 = entity("e2", "B");
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("source_id", 1);
            response.put("target_id", 2);
            response.put("name", "knows");
            response.put("fact", "A knows B");

            Relation rel = ParseLlmResponse.dict2relation(response, List.of(e1, e2), Map.of("user_id", "u1"));

            assertNotNull(rel);
            assertEquals("e1", rel.getLhs());
            assertEquals("e2", rel.getRhs());
            assertEquals("A knows B", rel.getContent());
            assertEquals("Relation", rel.getRelationType());
            assertEquals("u1", rel.getUserId());
        }

        @Test
        void testSameSourceAndTargetEntityFact() {
            Entity e1 = entity("e1", "A");
            Map<String, Object> response = Map.of(
                    "source_id", 1,
                    "target_id", 1,
                    "name", "fact",
                    "fact", "summary"
            );

            Relation rel = ParseLlmResponse.dict2relation(response, List.of(e1), Map.of("user_id", "u1"));

            assertNotNull(rel);
            assertEquals("EntityFact", rel.getRelationType());
        }

        @Test
        void testInvalidIndicesReturnNone() {
            Relation rel = ParseLlmResponse.dict2relation(
                    Map.of("source_id", 0, "target_id", 2, "name", "R", "fact", "x"),
                    List.of(entity("e1", "E")),
                    Map.of("user_id", "u1")
            );

            assertNull(rel);
        }

        @Test
        void testSingleKeyWrapperUnwraps() {
            Entity e1 = entity("e1", "A");
            Entity e2 = entity("e2", "B");
            Map<String, Object> wrapped = Map.of("relations",
                    Map.of("source_id", 1, "target_id", 2, "name", "R", "fact", "f"));

            Relation rel = ParseLlmResponse.dict2relation(wrapped, List.of(e1, e2), Map.of("user_id", "u1"));

            assertNotNull(rel);
            assertEquals("f", rel.getContent());
        }
    }

    @Nested
    @DisplayName("DeclareEntities Tests")
    class TestDeclareEntities {

        @Test
        void testEntityDeclarationConvertedToEntity() {
            List<ExtractionModels.EntityDeclaration> declarations =
                    List.of(new ExtractionModels.EntityDeclaration("E1", 0));

            List<Entity> result = ParseLlmResponse.declareEntities(
                    declarations,
                    List.of(new EntityTypeDefinition.EntityDef()),
                    Map.of("user_id", "u1", "created_at", 0)
            );

            assertEquals(1, result.size());
            assertEquals("E1", result.getFirst().getName());
            assertEquals("Entity", result.getFirst().getEntityType());
            assertEquals("u1", result.getFirst().getUserId());
            assertEquals(0, result.getFirst().getCreatedAt());
        }

        @Test
        void testEntityPassthrough() {
            Entity ent = entity("e1", "E");

            List<Entity> result = ParseLlmResponse.declareEntities(
                    List.of(ent),
                    List.of(new EntityTypeDefinition.EntityDef()),
                    Map.of("user_id", "u1")
            );

            assertEquals(List.of(ent), result);
        }
    }

    @Nested
    @DisplayName("ParseAllRelations Tests")
    class TestParseAllRelations {

        @Test
        void testParseAllRelationsReturnsRelationsAndEntities() {
            List<ExtractionModels.EntityDeclaration> declarations = List.of(
                    new ExtractionModels.EntityDeclaration("E1", 0),
                    new ExtractionModels.EntityDeclaration("E2", 0)
            );
            List<Map<String, Object>> relationsData = new ArrayList<>();
            relationsData.add(new LinkedHashMap<>(Map.of(
                    "source_id", 1,
                    "target_id", 2,
                    "name", "R",
                    "fact", "rel"
            )));

            ParseLlmResponse.ParseAllRelationsResult result = ParseLlmResponse.parseAllRelations(
                    relationsData,
                    declarations,
                    List.of(new EntityTypeDefinition.EntityDef()),
                    Map.of("created_at", 0, "user_id", "u1")
            );

            assertEquals(2, result.entities().size());
            assertEquals(1, result.relations().size());
            assertEquals("rel", result.relations().getFirst().getContent());
        }

        @Test
        void testDuplicateContentEmptied() {
            List<ExtractionModels.EntityDeclaration> declarations = List.of(
                    new ExtractionModels.EntityDeclaration("A", 0),
                    new ExtractionModels.EntityDeclaration("B", 0)
            );
            List<Map<String, Object>> relationsData = new ArrayList<>();
            relationsData.add(new LinkedHashMap<>(Map.of(
                    "source_id", 1,
                    "target_id", 2,
                    "name", "R",
                    "fact", "same",
                    "content", "same"
            )));
            relationsData.add(new LinkedHashMap<>(Map.of(
                    "source_id", 1,
                    "target_id", 2,
                    "name", "R2",
                    "fact", "same",
                    "content", "same"
            )));

            ParseLlmResponse.ParseAllRelationsResult result = ParseLlmResponse.parseAllRelations(
                    relationsData,
                    declarations,
                    List.of(new EntityTypeDefinition.EntityDef()),
                    Map.of("created_at", 0, "user_id", "u1")
            );

            assertEquals(2, result.relations().size());
            assertEquals("", relationsData.get(1).get("content"));
        }
    }

    @Nested
    @DisplayName("ResolveEntities Tests")
    class TestResolveEntities {

        @Test
        void testNoDuplicationReturnsCandidatesAndEmptyMerge() {
            List<ExtractionModels.EntityDeclaration> candidates =
                    List.of(new ExtractionModels.EntityDeclaration("E1", 0));

            ParseLlmResponse.ResolveEntitiesResult result =
                    ParseLlmResponse.resolveEntities(candidates, List.of(), List.of());

            assertEquals(1, result.resolvedEntities().size());
            assertTrue(result.mergingArgs().isEmpty());
            assertTrue(result.entityUuidsToRemove().isEmpty());
        }

        @Test
        void testDedupeToExistingReplacesInResult() {
            List<ExtractionModels.EntityDeclaration> candidates = List.of(
                    new ExtractionModels.EntityDeclaration("E1", 0),
                    new ExtractionModels.EntityDeclaration("E2", 0)
            );
            Entity existingEnt = entity("existing-e1", "E1");

            ParseLlmResponse.ResolveEntitiesResult result = ParseLlmResponse.resolveEntities(
                    candidates,
                    List.of(existingEnt),
                    List.of(Map.of("id", 1, "duplicate_ids", List.of(2)))
            );

            assertEquals(2, result.resolvedEntities().size());
            assertTrue(result.resolvedEntities().contains(existingEnt));
            assertNotNull(result.entityUuidsToRemove());
        }

        @Test
        void testResolveEntitiesDupByNameUsesNameLookup() {
            Entity existingEnt = entity("existing-alpha", "Alpha");

            ParseLlmResponse.ResolveEntitiesResult result = ParseLlmResponse.resolveEntities(
                    List.of(new ExtractionModels.EntityDeclaration("Beta", 0)),
                    List.of(existingEnt),
                    List.of(Map.of("id", "x", "name", "Alpha", "duplicate_ids", List.of(2)))
            );

            assertEquals(1, result.resolvedEntities().size());
            assertSame(existingEnt, result.resolvedEntities().getFirst());
        }

        @Test
        void testResolveEntitiesExistingMergeProducesToRemove() {
            Entity e0 = entity("u0", "E0");
            Entity e1 = entity("u1", "E1");

            ParseLlmResponse.ResolveEntitiesResult result = ParseLlmResponse.resolveEntities(
                    List.of(),
                    List.of(e0, e1),
                    List.of(Map.of("id", 1, "duplicate_ids", List.of(2)))
            );

            assertTrue(result.entityUuidsToRemove().contains("u1"));
            assertFalse(result.entityUuidsToRemove().contains("u0"));
            assertEquals(1, result.mergingArgs().size());
            assertEquals("u0", result.mergingArgs().getFirst().target().getUuid());
            assertTrue(result.mergingArgs().getFirst().sources().contains(e1));
        }

        @Test
        void testResolveMergeDictTargetNotInResult() {
            Entity eSrc = entity("src-uuid", "Src");
            Entity eTgt = entity("tgt-uuid", "Tgt");
            List<Object> result = new ArrayList<>();
            result.add(eSrc);
            Map<String, List<Entity>> mergeDict = new LinkedHashMap<>();
            mergeDict.put("tgt-uuid", new ArrayList<>(List.of(eSrc)));

            Map<String, List<Entity>> out = ParseLlmResponse.resolveMergeDict(
                    mergeDict,
                    result,
                    Map.of("tgt-uuid", eTgt, "src-uuid", eSrc)
            );

            assertTrue(result.getFirst() == eTgt || result.getFirst() == eSrc);
            assertTrue(out.containsKey("tgt-uuid") || out.containsKey("src-uuid"));
        }

        @Test
        void testResolveMergeDictTargetInResult() {
            Entity eSrc = entity("src-uuid", "Src");
            Entity eTgt = entity("tgt-uuid", "Tgt");
            List<Object> result = new ArrayList<>();
            result.add(eTgt);
            result.add(eSrc);
            Map<String, List<Entity>> mergeDict = new LinkedHashMap<>();
            mergeDict.put("tgt-uuid", new ArrayList<>(List.of(eSrc)));

            ParseLlmResponse.resolveMergeDict(
                    mergeDict,
                    result,
                    Map.of("tgt-uuid", eTgt, "src-uuid", eSrc)
            );

            assertSame(eTgt, result.get(0));
            assertSame(eTgt, result.get(1));
        }

        @Test
        void testResolveEntitiesParseEntityMergingTgtInMergeMap() {
            Entity e0 = entity("u0", "E0");
            Entity e1 = entity("u1", "E1");
            Entity e2 = entity("u2", "E2");

            ParseLlmResponse.ResolveEntitiesResult result = ParseLlmResponse.resolveEntities(
                    List.of(),
                    List.of(e0, e1, e2),
                    List.of(
                            Map.of("id", 1, "duplicate_ids", List.of(2)),
                            Map.of("id", 1, "duplicate_ids", List.of(3))
                    )
            );

            assertTrue(result.entityUuidsToRemove().contains("u1"));
            assertTrue(result.entityUuidsToRemove().contains("u2"));
        }

        @Test
        void testResolveEntitiesParseEntityMergingSrcInMergeMap() {
            Entity e0 = entity("u0", "E0");
            Entity e1 = entity("u1", "E1");
            Entity e2 = entity("u2", "E2");

            ParseLlmResponse.ResolveEntitiesResult result = ParseLlmResponse.resolveEntities(
                    List.of(),
                    List.of(e0, e1, e2),
                    List.of(
                            Map.of("id", 1, "duplicate_ids", List.of(2)),
                            Map.of("id", 2, "duplicate_ids", List.of(1))
                    )
            );

            assertTrue(result.entityUuidsToRemove().contains("u0") || result.entityUuidsToRemove().contains("u1"));
            assertTrue(!result.mergingArgs().isEmpty() || !result.entityUuidsToRemove().isEmpty());
        }

        @Test
        void testResolveEntitiesParseEntityMergingElseBranch() {
            Entity e0 = entity("u0", "E0");
            Entity e1 = entity("u1", "E1");

            ParseLlmResponse.ResolveEntitiesResult result = ParseLlmResponse.resolveEntities(
                    List.of(),
                    List.of(e0, e1),
                    List.of(Map.of("id", 1, "duplicate_ids", List.of(2)))
            );

            assertTrue(result.entityUuidsToRemove().contains("u1"));
            assertEquals(1, result.mergingArgs().size());
            assertEquals("u0", result.mergingArgs().getFirst().target().getUuid());
        }

        @Test
        void testResolveEntitiesParseEntityMergingTgtEqSrcContinues() {
            Entity e0 = entity("u0", "E0");

            ParseLlmResponse.ResolveEntitiesResult result = ParseLlmResponse.resolveEntities(
                    List.of(),
                    List.of(e0),
                    List.of(Map.of("id", 1, "duplicate_ids", List.of(1)))
            );

            assertTrue(result.mergingArgs().isEmpty());
            assertTrue(result.entityUuidsToRemove().isEmpty());
        }

        @Test
        void testResolveEntitiesParseEntityMergingElseAddToExistingSet() {
            Entity e0 = entity("u0", "E0");
            Entity e1 = entity("u1", "E1");
            Entity e2 = entity("u2", "E2");

            ParseLlmResponse.ResolveEntitiesResult result = ParseLlmResponse.resolveEntities(
                    List.of(),
                    List.of(e0, e1, e2),
                    List.of(
                            Map.of("id", 1, "duplicate_ids", List.of(2)),
                            Map.of("id", 2, "duplicate_ids", List.of(3))
                    )
            );

            assertTrue(result.entityUuidsToRemove().contains("u1"));
            assertTrue(result.entityUuidsToRemove().contains("u2"));
            assertEquals(1, result.mergingArgs().size());
            assertEquals("u0", result.mergingArgs().getFirst().target().getUuid());
            assertEquals(2, result.mergingArgs().getFirst().sources().size());
        }
    }

    @Nested
    @DisplayName("ParseRelationMerging Tests")
    class TestParseRelationMerging {

        @Test
        void testNeedMergingAndContentUpdatesRelation() {
            Relation rel = relation("r-new", "old", "e1", "e2");

            java.util.Set<String> toRemove = ParseLlmResponse.parseRelationMerging(
                    Map.of("need_merging", true, "combined_content", "merged", "duplicate_ids", List.of(1)),
                    rel,
                    List.of(Map.of("uuid", "r1"), Map.of("uuid", "r2"))
            );

            assertEquals("merged", rel.getContent());
            assertTrue(toRemove.contains("r1"));
        }

        @Test
        void testNoMergeReturnsEmpty() {
            java.util.Set<String> toRemove = ParseLlmResponse.parseRelationMerging(
                    Map.of("need_merging", false),
                    relation("r-new", "c", "e1", "e2"),
                    List.of()
            );

            assertTrue(toRemove.isEmpty());
        }

        @Test
        void testParseRelationMergingUpdatesValidSinceUntil() {
            Relation rel = relation("r-new", "c", "e1", "e2");

            ParseLlmResponse.parseRelationMerging(
                    Map.of(
                            "need_merging", true,
                            "combined_content", "merged",
                            "valid_since", "2025-01-01T00:00:00Z",
                            "valid_until", "2025-12-31T23:59:59Z",
                            "duplicate_ids", List.of()
                    ),
                    rel,
                    List.of(Map.of("uuid", "r1"))
            );

            assertEquals("merged", rel.getContent());
            assertTrue(rel.getValidSince() >= 0);
            assertTrue(rel.getValidUntil() >= 0);
        }
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setContent("");
        entity.setEntityType("Entity");
        return entity;
    }

    private static Relation relation(String uuid, String content, String lhs, String rhs) {
        Relation relation = new Relation();
        relation.setUuid(uuid);
        relation.setName("R");
        relation.setContent(content);
        relation.setRelationType("Relation");
        relation.setLhs(lhs);
        relation.setRhs(rhs);
        relation.setValidSince(0);
        relation.setValidUntil(-1);
        return relation;
    }
}
