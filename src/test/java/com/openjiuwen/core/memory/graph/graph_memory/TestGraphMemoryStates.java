/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.spi.store.query.QueryExpr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_states.py} in
 * {@code tests/unit_tests/core/memory/graph/graph_memory/test_states.py}.
 */
class TestGraphMemoryStates {

    @Test
    void testNonDataclassNoOp() {
        assertDoesNotThrow(() -> GraphMemoryStates.nestedClearDataclass("not a dataclass"));
        assertDoesNotThrow(() -> GraphMemoryStates.nestedClearDataclass(null));
    }

    @Test
    void testDataclassWithClearableField() {
        GraphMemoryStates.LookupTables tables = new GraphMemoryStates.LookupTables();
        tables.getEntity(Map.of("uuid", "e1", "name", "Ent1", "content", ""));

        GraphMemoryStates.nestedClearDataclass(tables);

        assertTrue(tables.getEntities().isEmpty());
    }

    @Test
    void testGetEntityCreatesAndCaches() {
        GraphMemoryStates.LookupTables tables = new GraphMemoryStates.LookupTables();

        Entity entity = tables.getEntity(Map.of("uuid", "e1", "name", "Ent1", "content", ""));

        assertEquals("e1", entity.getUuid());
        assertEquals("Ent1", entity.getName());
        assertSame(entity, tables.getEntities().get("e1"));
        assertSame(entity, tables.getEntity(Map.of("uuid", "e1", "name", "Ent1", "content", "")));
    }

    @Test
    void testGetRelationCreatesAndCaches() {
        GraphMemoryStates.LookupTables tables = new GraphMemoryStates.LookupTables();

        Relation relation = tables.getRelation(Map.of(
                "uuid", "r1",
                "name", "R",
                "content", "rel",
                "lhs", "e1",
                "rhs", "e2",
                "valid_since", 0,
                "valid_until", -1
        ));

        assertEquals("r1", relation.getUuid());
        assertSame(relation, tables.getRelations().get("r1"));
    }

    @Test
    void testGetEpisodeCreatesAndCaches() {
        GraphMemoryStates.LookupTables tables = new GraphMemoryStates.LookupTables();

        Episode episode = tables.getEpisode(Map.of("uuid", "ep1", "content", "ep content", "user_id", "u1"));

        assertEquals("ep1", episode.getUuid());
        assertSame(episode, tables.getEpisodes().get("ep1"));
    }

    @Test
    void testClearClearsReferences() {
        GraphMemoryStates.LookupTables tables = new GraphMemoryStates.LookupTables();
        tables.getEntity(Map.of("uuid", "e1", "name", "E", "content", ""));

        tables.clear();

        assertEquals(0, tables.getEntities().size());
        assertEquals(0, tables.getRelations().size());
        assertEquals(0, tables.getEpisodes().size());
    }

    @Test
    void testGetEntitiesReturnsMap() {
        assertNotNull(new GraphMemoryStates.LookupTables().getEntities());
    }

    @Test
    void testEntityMergeDefaults() {
        Entity entity = entity("target", "T");

        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(entity);

        assertSame(entity, merge.getTarget());
        assertTrue(merge.getSource().isEmpty());
        assertTrue(merge.getNewRelations().isEmpty());
        assertTrue(merge.getRelationsToKeep().isEmpty());
    }

    @Test
    void testEntityMergeClear() {
        Entity target = entity("target", "T");
        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(target);
        merge.getSource().put("s1", entity("source", "S"));
        merge.getNewRelations().add(relation("r1", "source", "target", "rel"));
        merge.getRelationsToKeep().add("r1");

        merge.clear();

        assertSame(target, merge.getTarget());
        assertTrue(merge.getSource().isEmpty());
        assertTrue(merge.getNewRelations().isEmpty());
        assertTrue(merge.getRelationsToKeep().isEmpty());
    }

    @Test
    void testGraphMemUpdateOrCombinesListsAndSets() {
        GraphMemoryStates.GraphMemUpdate left = new GraphMemoryStates.GraphMemUpdate();
        left.getAddedEntity().add(entity("e1", "E1"));
        left.getRemovedRelation().add("r1");

        GraphMemoryStates.GraphMemUpdate right = new GraphMemoryStates.GraphMemUpdate();
        right.getUpdatedEntity().add(entity("e2", "E2"));
        right.getRemovedRelation().add("r2");

        GraphMemoryStates.GraphMemUpdate merged = left.merge(right);

        assertEquals(1, merged.getAddedEntity().size());
        assertEquals(1, merged.getUpdatedEntity().size());
        assertEquals(Set.of("r1", "r2"), merged.getRemovedRelation());
    }

    @Test
    void testDefaultLanguageCn() {
        GraphMemoryStates.GraphMemPrompting prompting = new GraphMemoryStates.GraphMemPrompting();

        assertEquals("cn", prompting.getLanguage());
        assertEquals("cn", prompting.getEntityExtractionLanguage());
    }

    @Test
    void testStateHasStrategyAndLookupTable() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

        state.setStrategy(new GraphMemory.AddMemStrategy());
        state.setEntityTypes(List.of());

        assertNotNull(state.getStrategy());
        assertTrue(state.getLookupTable() instanceof GraphMemoryStates.LookupTables);
        assertEquals(EpisodeType.CONVERSATION, state.getEpisodeType());
    }

    @Test
    void testClearReferencesRunsWithoutError() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMergeInfos().put("u1", new GraphMemoryStates.EntityMerge(entity("e1", "E")));

        state.clearReferences();

        assertTrue(state.getMergeInfos().isEmpty());
    }

    @Test
    void testSelfPointingRelationAddedToRemoved() {
        Entity entity = entity("e1", "E");
        Relation relation = relation("r-self", "e1", "e1", "fact");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMergeInfos().put("e1", new GraphMemoryStates.EntityMerge(entity));
        state.getMergeInfos().get("e1").getNewRelations().add(relation);
        state.getLookupTables().getEntities().put("e1", entity);

        GraphMemoryStates.classifyRelationsExtracted(List.of(relation), state);

        assertTrue(state.getMemUpdate().getRemovedRelation().contains("r-self"));
        assertEquals(state.getPrompting().getLanguage(), relation.getLanguage());
    }

    @Test
    void testRelationWithDifferentLhsRhsKept() {
        Entity left = entity("e1", "E1");
        Entity right = entity("e2", "E2");
        Relation relation = relation("r1", "e1", "e2", "rel");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(left);
        merge.getNewRelations().add(relation);
        state.getMergeInfos().put("e1", merge);
        state.getLookupTables().getEntities().put("e1", left);

        GraphMemoryStates.classifyRelationsExtracted(List.of(relation), state);

        assertTrue(merge.getRelationsToKeep().contains("r1"));
        assertTrue(left.getRelations().contains("r1"));
    }

    @Test
    void testClassifyRelationsEmptyContentAppendsToRemove() {
        Entity entity = entity("e1", "E");
        Relation relation = relation("r1", "e1", "e2", "");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getLookupTables().getEntities().put("e1", entity);

        GraphMemoryStates.classifyRelationsExtracted(List.of(relation), state);

        assertTrue(state.getToRemove().contains(relation));
    }

    @Test
    void testBatchEmbedEmptyDataReturnsEmpty() {
        List<BaseGraphObject> result = GraphMemoryStates.batchEmbed(
                List.of(),
                new FakeEmbedding(List.of()),
                graphConfig()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testBatchEmbedNoEmbedTasksReturnsEmpty() {
        BaseGraphObject object = new NoTaskGraphObject();

        List<BaseGraphObject> result = GraphMemoryStates.batchEmbed(
                List.of(object),
                new FakeEmbedding(List.of()),
                graphConfig()
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testBatchEmbedSuccessReturnsEmpty() {
        Entity entity = entity("e1", "Entity");
        entity.setContent("hello");

        List<BaseGraphObject> result = GraphMemoryStates.batchEmbed(
                List.of(entity),
                new FakeEmbedding(List.of(List.of(1f), List.of(2f))),
                graphConfig()
        );

        assertTrue(result.isEmpty());
        assertArrayEquals(new float[]{1f}, entity.getContentEmbedding());
        assertArrayEquals(new float[]{2f}, entity.getNameEmbedding());
    }

    @Test
    void testBatchEmbedExceptionReturnsObjects() {
        Entity entity = entity("e1", "Entity");
        entity.setContent("hello");

        List<BaseGraphObject> result = GraphMemoryStates.batchEmbed(
                List.of(entity),
                new ThrowingEmbedding(),
                graphConfig()
        );

        assertEquals(1, result.size());
        assertSame(entity, result.getFirst());
    }

    @Test
    void testPersistToDbEmbedsAndFlushes() throws Exception {
        FakeGraphStore store = new FakeGraphStore(new FakeEmbedding(List.of(
                List.of(1f), List.of(2f), List.of(3f), List.of(4f), List.of(5f)
        )));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getAddedEntity().add(entity("e1", "Entity"));
        state.getMemUpdate().getAddedRelation().add(relation("r1", "e1", "e2", "rel"));
        state.getMemUpdate().getAddedEpisode().add(episode("ep1", "episode content"));
        Entity updated = entity("e2", "Updated");
        state.getMemUpdate().getUpdatedEntity().add(updated);

        GraphMemoryStates.persistToDb(store, state, graphConfig());

        assertEquals(1, store.addedEntities.size());
        assertEquals(1, store.addedRelations.size());
        assertEquals(1, store.addedEpisodes.size());
        assertEquals(1, store.updatedEntities.size() + store.skipUpdatedEntities.size());
    }

    @Test
    void testPersistToDbSkipEmbedEntityMissingEmbeddingMovedToEmbed() throws Exception {
        FakeGraphStore store = new FakeGraphStore(new FakeEmbedding(List.of(List.of(1f), List.of(2f))));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity entity = entity("e1", "Entity");
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);

        GraphMemoryStates.persistToDb(store, state, graphConfig());

        assertTrue(state.getMemUpdateSkipEmbed().getUpdatedEntity().isEmpty());
        assertTrue(state.getMemUpdate().getUpdatedEntity().contains(entity));
    }

    @Test
    void testPersistToDbSkipEmbedEntityAlreadyInUpdatedEntityNotAppendedAgain() throws Exception {
        FakeGraphStore store = new FakeGraphStore(new FakeEmbedding(List.of(List.of(1f), List.of(2f))));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity entity = entity("e1", "Entity");
        state.getMemUpdate().getUpdatedEntity().add(entity);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);

        GraphMemoryStates.persistToDb(store, state, graphConfig());

        assertEquals(1, state.getMemUpdate().getUpdatedEntity().size());
    }

    @Test
    void testPersistToDbEmbedFailureRaises() {
        FakeGraphStore store = new FakeGraphStore(new ThrowingEmbedding());
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getAddedEntity().add(entity("e1", "Entity"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GraphMemoryStates.persistToDb(store, state, graphConfig()));

        assertTrue(error.getMessage().contains("embedding service"));
    }

    @Test
    void testPersistToDbSkipEmbedAndRemovedBranches() throws Exception {
        FakeGraphStore store = new FakeGraphStore(new FakeEmbedding(List.of()));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Episode episode = episode("ep1", "content");
        episode.setContentEmbedding(new float[]{1f});
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        Entity entity = entity("e1", "Entity");
        entity.setContentEmbedding(new float[]{1f});
        entity.setNameEmbedding(new float[]{2f});
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);
        Relation relation = relation("r1", "e1", "e2", "rel");
        relation.setContentEmbedding(new float[]{3f});
        state.getMemUpdateSkipEmbed().getUpdatedRelation().add(relation);
        state.getMemUpdate().getRemovedEntity().add("e-removed");
        state.getMemUpdate().getRemovedRelation().add("r-removed");

        GraphMemoryStates.persistToDb(store, state, graphConfig());

        assertEquals(1, store.skipUpdatedEpisodes.size());
        assertEquals(1, store.skipUpdatedEntities.size());
        assertEquals(1, store.skipUpdatedRelations.size());
        assertEquals(List.of("e-removed"), store.deletedEntities);
        assertEquals(List.of("r-removed"), store.deletedRelations);
    }

    @Test
    void testPersistToDbEpisodeEmbedRetryTruncatesContent() {
        FakeGraphStore store = new FakeGraphStore(new ThrowingEmbedding());
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Episode episode = episode("ep1", "1234567890");
        state.getMemUpdate().getAddedEpisode().add(episode);

        assertThrows(IllegalStateException.class, () -> GraphMemoryStates.persistToDb(store, state, graphConfig()));
        assertTrue(episode.getContent().length() < 10);
    }

    @Test
    void testPersistToDbSkipEmbedUpdatedEntityWithEmbeddingsCallsAddEntity() throws Exception {
        FakeGraphStore store = new FakeGraphStore(new FakeEmbedding(List.of()));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity entity = entity("e1", "Entity");
        entity.setContentEmbedding(new float[]{1f});
        entity.setNameEmbedding(new float[]{2f});
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);

        GraphMemoryStates.persistToDb(store, state, graphConfig());

        assertEquals(1, store.skipUpdatedEntities.size());
        assertSame(entity, store.skipUpdatedEntities.getFirst());
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setContent("content-" + name);
        return entity;
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

    private static Episode episode(String uuid, String content) {
        Episode episode = new Episode();
        episode.setUuid(uuid);
        episode.setContent(content);
        return episode;
    }

    private static GraphConfig graphConfig() {
        return GraphConfig.builder()
                .uri("target/test-graph-store")
                .requestMaxRetries(2)
                .embedBatchSize(10)
                .build();
    }

    private static final class NoTaskGraphObject extends BaseGraphObject {
        @Override
        public List<EmbedTask> fetchEmbedTask() {
            return List.of();
        }
    }

    private static final class FakeEmbedding extends Embedding {
        private final List<List<Float>> documents;

        private FakeEmbedding(List<List<Float>> documents) {
            this.documents = documents;
        }

        @Override
        public List<Float> embedQuery(String text) {
            return List.of(1f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            return documents;
        }

        @Override
        public int getDimension() {
            return 1;
        }
    }

    private static final class ThrowingEmbedding extends Embedding {
        @Override
        public List<Float> embedQuery(String text) {
            throw new RuntimeException("boom");
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            throw new RuntimeException("boom");
        }

        @Override
        public int getDimension() {
            return 1;
        }
    }

    private static final class FakeGraphStore implements GraphStore {
        private final Embedding embedder;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final GraphConfig config = graphConfig();
        private final List<Entity> addedEntities = new ArrayList<>();
        private final List<Entity> updatedEntities = new ArrayList<>();
        private final List<Entity> skipUpdatedEntities = new ArrayList<>();
        private final List<Relation> addedRelations = new ArrayList<>();
        private final List<Relation> skipUpdatedRelations = new ArrayList<>();
        private final List<Episode> addedEpisodes = new ArrayList<>();
        private final List<Episode> skipUpdatedEpisodes = new ArrayList<>();
        private final List<String> deletedEntities = new ArrayList<>();
        private final List<String> deletedRelations = new ArrayList<>();

        private FakeGraphStore(Embedding embedder) {
            this.embedder = embedder;
        }

        @Override
        public GraphConfig getConfig() {
            return config;
        }

        @Override
        public ExecutorService getEmbedExecutor() {
            return executor;
        }

        @Override
        public Embedding getEmbedder() {
            return embedder;
        }

        @Override
        public void refresh() {
        }

        @Override
        public void addData(String collection, Iterable<Map<String, Object>> data, boolean flush, boolean upsert) {
        }

        @Override
        public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
            for (Object entity : entities) {
                if (entity instanceof Entity value) {
                    if (!upsert) {
                        addedEntities.add(value);
                    } else if (value.getContentEmbedding() != null && value.getNameEmbedding() != null
                            && !addedEntities.contains(value)) {
                        skipUpdatedEntities.add(value);
                    } else {
                        updatedEntities.add(value);
                    }
                }
            }
        }

        @Override
        public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
            for (Object relation : relations) {
                if (relation instanceof Relation value) {
                    if (upsert) {
                        skipUpdatedRelations.add(value);
                    } else {
                        addedRelations.add(value);
                    }
                }
            }
        }

        @Override
        public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
            for (Object episode : episodes) {
                if (episode instanceof Episode value) {
                    if (upsert) {
                        skipUpdatedEpisodes.add(value);
                    } else {
                        addedEpisodes.add(value);
                    }
                }
            }
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
            if (GraphMemory.ENTITY_COLLECTION.equals(collection)) {
                for (Object id : ids) {
                    deletedEntities.add(String.valueOf(id));
                }
            } else if (GraphMemory.RELATION_COLLECTION.equals(collection)) {
                for (Object id : ids) {
                    deletedRelations.add(String.valueOf(id));
                }
            }
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
