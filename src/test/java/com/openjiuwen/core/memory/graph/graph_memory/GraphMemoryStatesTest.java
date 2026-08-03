/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreConstants;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Python parity tests for graph-memory state helpers.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/memory/graph/graph_memory/test_states.py}.</p>
 */
class GraphMemoryStatesTest {

    @Test
    void nestedClearDataclassNonDataclassNoOp() {
        assertThatNoException().isThrownBy(() -> {
            GraphMemoryStates.nestedClearDataclass("not a dataclass");
            GraphMemoryStates.nestedClearDataclass(null);
        });
    }

    @Test
    void nestedClearDataclassWithClearableField() {
        ClearableHolder holder = new ClearableHolder(List.of(1, 2, 3));

        GraphMemoryStates.nestedClearDataclass(holder);

        assertThat(holder.items).isEmpty();
    }

    @Test
    void lookupTablesGetEntityCreatesAndCaches() {
        GraphMemoryStates.LookupTables table = new GraphMemoryStates.LookupTables();
        Map<String, Object> input = Map.of("uuid", "e1", "name", "Ent1", "obj_type", "Entity", "content", "");

        Entity entity = table.getEntity(input);

        assertThat(entity).isInstanceOf(Entity.class);
        assertThat(entity.getUuid()).isEqualTo("e1");
        assertThat(entity.getName()).isEqualTo("Ent1");
        assertThat(table.getEntities()).containsEntry("e1", entity);
        assertThat(table.getEntity(input)).isSameAs(entity);
    }

    @Test
    void lookupTablesGetRelationCreatesAndCaches() {
        GraphMemoryStates.LookupTables table = new GraphMemoryStates.LookupTables();
        Map<String, Object> input = Map.of(
                "uuid", "r1",
                "name", "R",
                "obj_type", "Relation",
                "content", "rel",
                "lhs", "e1",
                "rhs", "e2",
                "valid_since", 0,
                "valid_until", -1
        );

        Relation relation = table.getRelation(input);

        assertThat(relation.getUuid()).isEqualTo("r1");
        assertThat(table.getRelations()).containsEntry("r1", relation);
        assertThat(table.getRelation(input)).isSameAs(relation);
    }

    @Test
    void lookupTablesGetEpisodeCreatesAndCaches() {
        GraphMemoryStates.LookupTables table = new GraphMemoryStates.LookupTables();
        Map<String, Object> input = Map.of(
                "uuid", "ep1",
                "content", "ep content",
                "obj_type", "conversation",
                "user_id", "u1"
        );

        Episode episode = table.getEpisode(input);

        assertThat(episode.getUuid()).isEqualTo("ep1");
        assertThat(table.getEpisodes()).containsEntry("ep1", episode);
        assertThat(table.getEpisode(input)).isSameAs(episode);
    }

    @Test
    void lookupTablesClearClearsReferences() {
        GraphMemoryStates.LookupTables table = new GraphMemoryStates.LookupTables();
        table.getEntity(Map.of("uuid", "e1", "name", "E", "obj_type", "Entity", "content", ""));

        table.clear();

        assertThat(table.getEntities()).isEmpty();
        assertThat(table.getRelations()).isEmpty();
        assertThat(table.getEpisodes()).isEmpty();
    }

    @Test
    void entityMergeDefaults() {
        Entity target = entity("target", "T");

        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(target);

        assertThat(merge.getTarget()).isSameAs(target);
        assertThat(merge.getSource()).isEmpty();
        assertThat(merge.getNewRelations()).isEmpty();
        assertThat(merge.getRelationsToKeep()).isEmpty();
    }

    @Test
    void entityMergeClear() {
        Entity target = entity("target", "T");
        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(target);
        merge.getSource().put("s1", entity("source", "S"));

        merge.clear();

        assertThat(merge.getTarget()).isSameAs(target);
        assertThat(merge.getSource()).isEmpty();
    }

    @Test
    void graphMemUpdateMergeCombinesListsAndSets() {
        GraphMemoryStates.GraphMemUpdate left = new GraphMemoryStates.GraphMemUpdate();
        GraphMemoryStates.GraphMemUpdate right = new GraphMemoryStates.GraphMemUpdate();
        Entity added = entity("e1", "E1");
        Entity updated = entity("e2", "E2");
        left.getAddedEntity().add(added);
        left.getRemovedRelation().add("r1");
        right.getUpdatedEntity().add(updated);
        right.getRemovedRelation().add("r2");

        GraphMemoryStates.GraphMemUpdate merged = left.merge(right);

        assertThat(merged.getAddedEntity()).containsExactly(added);
        assertThat(merged.getUpdatedEntity()).containsExactly(updated);
        assertThat(merged.getRemovedRelation()).containsExactly("r1", "r2");
    }

    @Test
    void graphMemPromptingDefaultLanguageCn() {
        GraphMemoryStates.GraphMemPrompting prompting = new GraphMemoryStates.GraphMemPrompting();

        assertThat(prompting.getLanguage()).isEqualTo("cn");
        assertThat(prompting.getEntityExtractionLanguage()).isEqualTo("cn");
    }

    @Test
    void graphMemStateHasStrategyAndLookupTable() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

        assertThat(state.getStrategy()).isNotNull();
        assertThat(state.getLookupTable()).isInstanceOf(GraphMemoryStates.LookupTables.class);
        assertThat(state.getEpisodeType()).isEqualTo(EpisodeType.CONVERSATION);
    }

    @Test
    void graphMemStateClearReferencesRunsWithoutError() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMergeInfos().put("u1", new GraphMemoryStates.EntityMerge(entity("e1", "E")));

        state.clearReferences();

        assertThat(state.getMergeInfos()).isEmpty();
    }

    @Test
    void classifyRelationsExtractedSelfPointingRelationAddedToRemoved() {
        Entity entity = entity("e1", "E");
        Relation relation = relation("r-self", entity, entity, "fact");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMergeInfos().put("e1", new GraphMemoryStates.EntityMerge(entity));
        state.getMergeInfos().get("e1").getNewRelations().add(relation);

        GraphMemoryStates.classifyRelationsExtracted(List.of(relation), state);

        assertThat(state.getMemUpdate().getRemovedRelation()).contains("r-self");
        assertThat(relation.getLanguage()).isEqualTo(state.getPrompting().getLanguage());
    }

    @Test
    void classifyRelationsExtractedDifferentEndpointsKept() {
        Entity left = entity("e1", "E1");
        Entity right = entity("e2", "E2");
        Relation relation = relation("r1", left, right, "rel");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMergeInfos().put("e1", new GraphMemoryStates.EntityMerge(left));
        state.getMergeInfos().get("e1").getNewRelations().add(relation);

        GraphMemoryStates.classifyRelationsExtracted(List.of(relation), state);

        assertThat(state.getMergeInfos().get("e1").getRelationsToKeep()).contains("r1");
    }

    @Test
    void classifyRelationsExtractedEmptyContentAppendsToRemove() {
        Relation relation = relation("r-empty", "e1", "e2", "   ");
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();

        GraphMemoryStates.classifyRelationsExtracted(List.of(relation), state);

        assertThat(state.getToRemove()).containsExactly(relation);
    }

    @Test
    void blockKeyboardInterruptYieldsAndRestoresHandler() {
        AtomicBoolean called = new AtomicBoolean(false);

        GraphMemoryStates.blockKeyboardInterrupt(() -> called.set(true));

        assertThat(called).isTrue();
    }

    @Test
    void batchEmbedEmptyDataReturnsEmpty() {
        List<BaseGraphObject> result = GraphMemoryStates.batchEmbed(
                List.of(),
                new FakeEmbedding(0),
                graphConfig()
        ).join();

        assertThat(result).isEmpty();
    }

    @Test
    void batchEmbedNoEmbedTasksReturnsEmpty() {
        FakeEmbedding embedding = new FakeEmbedding(0);

        List<BaseGraphObject> result = GraphMemoryStates.batchEmbed(
                List.of(new NoEmbeddingObject()),
                embedding,
                graphConfig()
        ).join();

        assertThat(result).isEmpty();
        assertThat(embedding.getEmbedDocumentCallCount()).isZero();
    }

    @Test
    void batchEmbedSuccessReturnsEmpty() {
        Entity entity = entity("e1", "E");
        entity.setContent("text");

        List<BaseGraphObject> result = GraphMemoryStates.batchEmbed(
                List.of(entity),
                new FakeEmbedding(0),
                graphConfig()
        ).join();

        assertThat(result).isEmpty();
        assertThat(entity.getContentEmbedding()).containsExactly(1.0d, 2.0d);
        assertThat(entity.getNameEmbedding()).containsExactly(3.0d, 4.0d);
    }

    @Test
    void batchEmbedExceptionReturnsObjects() {
        Entity entity = entity("e1", "E");
        entity.setContent("text");

        List<BaseGraphObject> result = GraphMemoryStates.batchEmbed(
                List.of(entity),
                new FakeEmbedding(Integer.MAX_VALUE),
                graphConfig()
        ).join();

        assertThat(result).containsExactly(entity);
    }

    @Test
    void persistToDbEmbedsAndFlushes() {
        GraphStore db = graphStore(new FakeEmbedding(0));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getAddedEntity().add(entity("e1", "E"));
        state.getMemUpdate().getAddedEpisode().add(episode("c"));

        GraphMemoryStates.persistToDb(db, state, graphConfig()).join();

        verify(db).addEntity(any(), eq(false), eq(false), eq(true));
        verify(db).addEpisode(any(), eq(false), eq(false), eq(true));
    }

    @Test
    void persistToDbSkipEmbedEntityMissingEmbeddingMovedToEmbed() {
        GraphStore db = graphStore(new FakeEmbedding(0));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getAddedEpisode().add(episode("c"));
        Entity skipEntity = entity("e1", "E");
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(skipEntity);

        GraphMemoryStates.persistToDb(db, state, graphConfig()).join();

        assertThat(state.getMemUpdateSkipEmbed().getUpdatedEntity()).doesNotContain(skipEntity);
        assertThat(state.getMemUpdate().getUpdatedEntity()).contains(skipEntity);
    }

    @Test
    void persistToDbSkipEmbedEntityAlreadyInUpdatedEntityNotAppendedAgain() {
        GraphStore db = graphStore(new FakeEmbedding(0));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getAddedEpisode().add(episode("c"));
        Entity entity = entity("e1", "E");
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);
        state.getMemUpdate().getUpdatedEntity().add(entity);
        int initialSize = state.getMemUpdate().getUpdatedEntity().size();

        GraphMemoryStates.persistToDb(db, state, graphConfig()).join();

        assertThat(state.getTmpBuffer()).contains(entity);
        assertThat(state.getMemUpdate().getUpdatedEntity()).hasSize(initialSize);
    }

    @Test
    void persistToDbEmbedFailureRaises() {
        GraphStore db = graphStore(new FakeEmbedding(Integer.MAX_VALUE));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity entity = entity("e1", "E");
        entity.setContent("x");
        state.getMemUpdate().getAddedEntity().add(entity);

        assertThatThrownBy(() -> GraphMemoryStates.persistToDb(db, state, graphConfig()).join())
                .hasRootCauseInstanceOf(BaseError.class)
                .hasMessageContaining("embedding");
    }

    @Test
    void persistToDbSkipEmbedAndRemovedBranches() {
        GraphStore db = graphStore(new FakeEmbedding(0));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getAddedEpisode().add(episode("c"));
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode("e"));
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity("e2", "E2"));
        state.getMemUpdateSkipEmbed().getUpdatedRelation().add(relation("r2", "e1", "e2", "r"));
        state.getMemUpdate().getRemovedEntity().add("old-e");
        state.getMemUpdate().getRemovedRelation().add("old-r");

        GraphMemoryStates.persistToDb(db, state, graphConfig()).join();

        verify(db, atLeastOnce()).addEpisode(any(), anyBoolean(), anyBoolean(), eq(true));
        verify(db, atLeastOnce()).addEntity(any(), anyBoolean(), anyBoolean(), eq(true));
        verify(db, atLeastOnce()).addRelation(any(), anyBoolean(), anyBoolean(), eq(true));
        verify(db).delete(eq(GraphStoreConstants.ENTITY_COLLECTION), eq(List.of("old-e")), isNull(), eq(Map.of()));
        verify(db).delete(eq(GraphStoreConstants.RELATION_COLLECTION), eq(List.of("old-r")), isNull(), eq(Map.of()));
    }

    @Test
    void persistToDbEpisodeEmbedRetryTruncatesContent() {
        GraphStore db = graphStore(new FakeEmbedding(1));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Episode episode = episode("long content here");
        state.getMemUpdate().getAddedEpisode().add(episode);

        GraphMemoryStates.persistToDb(db, state, graphConfig()).join();

        assertThat(episode.getContent()).hasSizeLessThan("long content here".length());
        verify(db).addEpisode(any(), eq(false), eq(false), eq(true));
    }

    @Test
    void persistToDbSkipEmbedUpdatedEntityWithEmbeddingsCallsAddEntity() {
        GraphStore db = graphStore(new FakeEmbedding(0));
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getMemUpdate().getAddedEpisode().add(episode("c"));
        Entity embedded = entity("e1", "E");
        embedded.setContentEmbedding(List.of(0.1d, 0.1d));
        embedded.setNameEmbedding(List.of(0.1d, 0.1d));
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(embedded);

        GraphMemoryStates.persistToDb(db, state, graphConfig()).join();

        verify(db, atLeastOnce()).addEntity(
                argThat(values -> containsSame(values, embedded)),
                anyBoolean(),
                anyBoolean(),
                eq(true)
        );
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setContent("");
        return entity;
    }

    private static Episode episode(String content) {
        Episode episode = new Episode();
        episode.setContent(content);
        return episode;
    }

    private static Relation relation(String uuid, Object lhs, Object rhs, String content) {
        Relation relation = new Relation(lhs, rhs);
        relation.setUuid(uuid);
        relation.setContent(content);
        return relation;
    }

    private static GraphConfig graphConfig() {
        return GraphConfig.builder()
                .uri(".")
                .embedBatchSize(10)
                .requestMaxRetries(2)
                .build();
    }

    private static GraphStore graphStore(Embedding embedding) {
        GraphStore graphStore = mock(GraphStore.class);
        when(graphStore.getEmbedder()).thenReturn(Optional.ofNullable(embedding));
        when(graphStore.addEntity(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(graphStore.addRelation(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(graphStore.addEpisode(any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(graphStore.delete(anyString(), any(), any(), anyMap()))
                .thenReturn(CompletableFuture.completedFuture(Map.of()));
        return graphStore;
    }

    private static boolean containsSame(Iterable<?> values, Object expected) {
        if (values == null) {
            return false;
        }
        for (Object value : values) {
            if (value == expected) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test holder for clearable-field parity.
     *
     * <p>Mirrors Python's local dataclass in
     * {@code tests/unit_tests/core/memory/graph/graph_memory/test_states.py}.</p>
     */
    private static final class ClearableHolder {
        private final List<Integer> items = new ArrayList<>();

        private ClearableHolder(List<Integer> items) {
            this.items.addAll(items);
        }
    }

    /**
     * Graph object with no embedding tasks.
     *
     * <p>Mirrors Python's mocked {@code BaseGraphObject} in
     * {@code tests/unit_tests/core/memory/graph/graph_memory/test_states.py}.</p>
     */
    private static final class NoEmbeddingObject extends BaseGraphObject {
        @Override
        public List<EmbeddingTask> fetchEmbedTask() {
            return List.of();
        }
    }

    /**
     * Deterministic embedding fake for graph-memory state tests.
     *
     * <p>Mirrors Python's {@code AsyncMock} embedding service in
     * {@code tests/unit_tests/core/memory/graph/graph_memory/test_states.py}.</p>
     */
    private static final class FakeEmbedding extends Embedding {
        private int failuresRemaining;
        private int embedDocumentCallCount;

        private FakeEmbedding(int failuresRemaining) {
            this.failuresRemaining = failuresRemaining;
        }

        private int getEmbedDocumentCallCount() {
            return embedDocumentCallCount;
        }

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(1.0d, 2.0d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs) {
            embedDocumentCallCount++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                CompletableFuture<List<List<Double>>> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("fail"));
                return failed;
            }
            List<List<Double>> embeddings = new ArrayList<>();
            for (int index = 0; index < texts.size(); index++) {
                double base = index * 2.0d + 1.0d;
                embeddings.add(List.of(base, base + 1.0d));
            }
            return CompletableFuture.completedFuture(embeddings);
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }
}
