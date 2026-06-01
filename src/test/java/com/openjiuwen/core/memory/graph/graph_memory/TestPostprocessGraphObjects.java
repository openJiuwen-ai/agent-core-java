/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.spi.store.query.QueryExpr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_postprocess_graph_objects.py} in
 * {@code tests/unit_tests/core/memory/graph/graph_memory/test_postprocess_graph_objects.py}.
 */
class TestPostprocessGraphObjects {

    @Test
    void testCurrentEpisodeEntitiesUnion() {
        Episode episode = episode("ep1", "c");
        episode.setEntities(new ArrayList<>(List.of("e1")));
        Entity e1 = entity("e1", "E1");
        Entity e2 = entity("e2", "E2");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(e1, e2), episode, state);

        assertEquals(List.of("e1", "e2"), episode.getEntities());
    }

    @Test
    void testValidateEntitiesEpisodesMergeInfosUpdatesEpisodes() {
        Episode episode = episode("ep1", "c");
        episode.setEntities(new ArrayList<>(List.of("src-uuid")));
        Entity target = entity("tgt-uuid", "Tgt");
        Entity source = entity("src-uuid", "Src");
        source.setEpisodes(new ArrayList<>(List.of("ep1")));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getLookupTable().getEpisodes().put("ep1", episode);
        state.getMergeInfos().put("tgt-uuid", new GraphMemoryStates.EntityMerge(target));
        state.getMergeInfos().get("tgt-uuid").getSource().put("src-uuid", source);

        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(target), episode, state);

        assertFalse(episode.getEntities().contains("src-uuid"));
        assertTrue(episode.getEntities().contains("tgt-uuid"));
    }

    @Test
    void testValidateEntitiesEpisodesMergeEpisodeNotInLookupSkipped() {
        Episode currentEpisode = episode("cur-ep", "cur");
        Entity target = entity("tgt-uuid", "Tgt");
        Entity source = entity("src-uuid", "Src");
        source.setEpisodes(new ArrayList<>(List.of("missing-ep")));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMergeInfos().put("tgt-uuid", new GraphMemoryStates.EntityMerge(target));
        state.getMergeInfos().get("tgt-uuid").getSource().put("src-uuid", source);

        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(target), currentEpisode, state);

        assertTrue(currentEpisode.getEntities().contains("tgt-uuid"));
    }

    @Test
    void testValidateEntitiesEpisodesSyncEp2eNotE2epRemovesFromEpisode() {
        Episode episode = episode("ep1", "c");
        episode.setEntities(new ArrayList<>(List.of("e1")));
        Entity entity = entity("e1", "E1");
        entity.setEpisodes(new ArrayList<>());
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);

        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(entity), episode("cur", "cur"), state);

        assertFalse(episode.getEntities().contains("e1"));
    }

    @Test
    void testValidateEntitiesEpisodesSyncEpisodeEntityLists() {
        Episode episode = episode("ep1", "c");
        episode.setEntities(new ArrayList<>(List.of("e1")));
        Entity entity = entity("e1", "E1");
        entity.setEpisodes(new ArrayList<>(List.of("ep1")));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);

        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(entity), episode, state);

        assertTrue(episode.getEntities().contains("e1"));
        assertTrue(entity.getEpisodes().contains("ep1"));
    }

    @Test
    void testValidateEntitiesEpisodesSyncE2epNotEp2eAppendsEntityToEpisode() {
        Episode episode = episode("ep1", "c");
        episode.setEntities(new ArrayList<>());
        Entity entity = entity("e1", "E1");
        entity.setEpisodes(new ArrayList<>(List.of("ep1")));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);

        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(entity), episode("cur", "cur"), state);

        assertEquals(List.of("e1"), episode.getEntities());
    }

    @Test
    void testValidateEntitiesEpisodesSyncDedupesEpisodeEntities() {
        Episode episode = episode("ep1", "c");
        episode.setEntities(new ArrayList<>(List.of("e1", "e2", "e1")));
        Entity e1 = entity("e1", "E1");
        e1.setEpisodes(new ArrayList<>(List.of("ep1")));
        Entity e2 = entity("e2", "E2");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(e1);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(e2);

        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(e1, e2), episode("cur", "cur"), state);

        assertEquals(episode.getEntities().size(), new java.util.LinkedHashSet<>(episode.getEntities()).size());
    }

    @Test
    void testValidateEntitiesEpisodesSyncDedupesEachEpisode() {
        Episode episode = episode("ep1", "c");
        episode.setEntities(new ArrayList<>(List.of("a", "b", "a")));
        Entity entity = entity("e1", "E1");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);

        PostprocessGraphObjects.validateEntitiesEpisodes(List.of(entity), episode("cur", "cur"), state);

        assertEquals(2, episode.getEntities().size());
    }

    @Test
    void testCreateEpisodeAppendsToMemUpdate() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.setReferenceTimestamp(42L);

        Episode episode = PostprocessGraphObjects.createEpisode(new FakeGraphStore(), "user-1", "content text", state);

        assertEquals("content text", episode.getContent());
        assertEquals("user-1", episode.getUserId());
        assertEquals(1, state.getMemUpdate().getAddedEpisode().size());
        assertSame(episode, state.getMemUpdate().getAddedEpisode().getFirst());
        assertNotNull(episode.getUuid());
    }

    @Test
    void testProcessRelationsAppendsToAddedRelation() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity e1 = entity("e1", "E1");
        Entity e2 = entity("e2", "E2");
        Relation relation = relation("r1", "e1", "e2", "rel");

        PostprocessGraphObjects.processRelations(new FakeGraphStore(), List.of(e1, e2), List.of(relation), state);

        assertEquals(1, state.getMemUpdate().getAddedRelation().size());
        assertSame(relation, state.getMemUpdate().getAddedRelation().getFirst());
    }

    @Test
    void testProcessRelationsRemovesRemovedRelationFromEntities() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getRemovedRelation().add("r-deleted");
        Entity entity = entity("e1", "E1");
        entity.getRelations().add("r-deleted");
        Relation relation = relation("r1", "e1", "e2", "rel");

        PostprocessGraphObjects.processRelations(new FakeGraphStore(), List.of(entity), List.of(relation), state);

        assertFalse(entity.getRelations().contains("r-deleted"));
    }

    @Test
    void testProcessRelationsRemovesRelationObjectFromEntity() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity left = entity("e1", "E1");
        Entity right = entity("e2", "E2");
        Relation relation = relation("r1", "e1", "e2", "rel");

        PostprocessGraphObjects.processRelations(new FakeGraphStore(), List.of(left, right), List.of(relation), state);

        assertTrue(left.getRelations().contains("r1"));
        assertTrue(right.getRelations().contains("r1"));
    }

    @Test
    void testProcessEntitiesAddsNewEntityToAddedEntity() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Episode currentEpisode = episode("ep1", "episode");
        Entity entity = entity("e1", "E1");

        List<Entity> result = PostprocessGraphObjects.processEntities(
                new FakeGraphStore(), new ArrayList<>(List.of(entity)), currentEpisode, state);

        assertEquals(1, result.size());
        assertTrue(state.getMemUpdate().getAddedEntity().contains(entity));
        assertTrue(entity.getEpisodes().contains("ep1"));
    }

    @Test
    void testProcessEntitiesMergingTasksAndRetrievedEntityUpdated() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Episode currentEpisode = episode("ep1", "episode");
        Entity entity = entity("e1", "E1");
        state.getRetrievedEntities().put("e1", entity);
        CompletableFuture<GraphMemory.LlmResponse> future =
                CompletableFuture.completedFuture(new GraphMemory.LlmResponse("merged content"));
        state.getMergingTasks().add(future);
        state.getMergingTasksEntities().put(future, entity);

        PostprocessGraphObjects.processEntities(new FakeGraphStore(), new ArrayList<>(List.of(entity)), currentEpisode, state);

        assertEquals("merged content", entity.getContent());
        assertTrue(state.getMemUpdate().getUpdatedEntity().contains(entity));
    }

    @Test
    void testProcessEntitiesMergingTaskEntityNotInEntitiesAppended() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Episode currentEpisode = episode("ep1", "episode");
        Entity merged = entity("e2", "Merged");
        CompletableFuture<GraphMemory.LlmResponse> future =
                CompletableFuture.completedFuture(new GraphMemory.LlmResponse("merged content"));
        state.getMergingTasks().add(future);
        state.getMergingTasksEntities().put(future, merged);

        List<Entity> result = PostprocessGraphObjects.processEntities(
                new FakeGraphStore(), new ArrayList<>(), currentEpisode, state);

        assertEquals(1, result.size());
        assertSame(merged, result.getFirst());
    }

    @Test
    void testProcessEntitiesRemovesRelationFromEntity() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getRemovedRelation().add("r-deleted");
        Episode currentEpisode = episode("ep1", "episode");
        Entity entity = entity("e1", "E1");
        entity.getRelations().add("r-deleted");

        PostprocessGraphObjects.processEntities(
                new FakeGraphStore(), new ArrayList<>(List.of(entity)), currentEpisode, state);

        assertFalse(entity.getRelations().contains("r-deleted"));
    }

    @Test
    void testProcessEntitiesResolveEntityUuidAssignsUuids() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Episode currentEpisode = episode("ep1", "episode");
        Entity entity = new Entity();
        entity.setName("Entity1");

        PostprocessGraphObjects.processEntities(
                new FakeGraphStore(), new ArrayList<>(List.of(entity)), currentEpisode, state);

        assertNotNull(entity.getUuid());
    }

    @Test
    void testParseRelationUuidsToRemoveExtendsToRemove() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Relation relation = relation("r1", "e1", "e2", "rel");
        PostprocessGraphObjects.RelationDedupeTask task = new PostprocessGraphObjects.RelationDedupeTask(
                relation,
                List.of(relation),
                CompletableFuture.completedFuture(
                        new GraphMemory.LlmResponse(
                                "{\"need_merging\":true,\"combined_content\":\"rel\",\"duplicate_ids\":[1]}"
                        ))
        );

        PostprocessGraphObjects.parseRelationUuidsToRemove(List.of(task), state);

        assertEquals(List.of("r1"), state.getToRemove());
    }

    @Test
    void testParseRelationUuidsToRemoveExceptionLogged() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Relation relation = relation("r1", "e1", "e2", "rel");
        PostprocessGraphObjects.RelationDedupeTask task = new PostprocessGraphObjects.RelationDedupeTask(
                relation,
                List.of(relation),
                CompletableFuture.completedFuture(new GraphMemory.LlmResponse("not json"))
        );

        assertDoesNotThrow(() -> PostprocessGraphObjects.parseRelationUuidsToRemove(List.of(task), state));
        assertTrue(state.getToRemove().isEmpty());
    }

    @Test
    void testParseRelationUuidsToRemoveMapVariant() {
        assertEquals(List.of("r1", "r2"),
                PostprocessGraphObjects.parseRelationUuidsToRemove(Map.of("duplicate_ids", List.of("r1", "r2"))));
    }

    @Test
    void testParseRelationUuidsToRemoveMapVariantMissingField() {
        assertTrue(PostprocessGraphObjects.parseRelationUuidsToRemove(Map.of()).isEmpty());
    }

    @Test
    void testProcessRelationsMissingEntityLeavesNullEndpoints() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Relation relation = relation("r1", "missing", "e2", "rel");

        PostprocessGraphObjects.processRelations(new FakeGraphStore(), List.of(), List.of(relation), state);

        assertNull(relation.getLhs());
        assertNull(relation.getRhs());
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setContent("content-" + name);
        return entity;
    }

    private static Episode episode(String uuid, String content) {
        Episode episode = new Episode();
        episode.setUuid(uuid);
        episode.setContent(content);
        return episode;
    }

    private static Relation relation(String uuid, String lhs, String rhs, String content) {
        Relation relation = new Relation();
        relation.setUuid(uuid);
        relation.setName("R-" + uuid);
        relation.setLhs(lhs);
        relation.setRhs(rhs);
        relation.setContent(content);
        return relation;
    }

    private static GraphConfig graphConfig() {
        return GraphConfig.builder().uri("target/test-postprocess").build();
    }

    private static final class FakeEmbedding extends Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            return List.of(1f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return List.of();
        }

        @Override
        public int getDimension() {
            return 1;
        }
    }

    private static final class FakeGraphStore implements GraphStore {
        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        @Override
        public GraphConfig getConfig() {
            return graphConfig();
        }

        @Override
        public ExecutorService getEmbedExecutor() {
            return executor;
        }

        @Override
        public Embedding getEmbedder() {
            return new FakeEmbedding();
        }

        @Override
        public void refresh() {
        }

        @Override
        public void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert) {
        }

        @Override
        public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
        }

        @Override
        public boolean isEmpty(String collection) {
            return false;
        }

        @Override
        public List<Map<String, Object>> query(String collection, List<Object> ids, QueryExpr expr, boolean silenceErrors) {
            return List.of();
        }

        @Override
        public Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr) {
            return new LinkedHashMap<>();
        }

        @Override
        public Map<String, List<Map<String, Object>>> search(String queryText, int k, String collection,
                                                             Object rankerConfig, int bfsDepth, int bfsK,
                                                             QueryExpr filterExpr, List<String> outputFields,
                                                             List<Float> queryEmbedding, Map<String, Object> kwargs) {
            return Map.of();
        }

        @Override
        public void attachEmbedder(Embedding embedder) {
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
