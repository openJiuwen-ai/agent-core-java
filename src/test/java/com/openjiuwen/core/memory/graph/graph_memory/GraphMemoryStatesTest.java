/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.Relation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for graph-memory state helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.states} module in
 * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
 */
class GraphMemoryStatesTest {

    @Test
    void lookupTablesReuseObjectsByUuidAndClearNestedMaps() {
        GraphMemoryStates.LookupTables lookupTables = new GraphMemoryStates.LookupTables();

        Entity first = lookupTables.getEntity(Map.of("uuid", "entity-1", "name", "Alice"));
        Entity second = lookupTables.getEntity(Map.of("uuid", "entity-1", "name", "Ignored"));

        assertThat(second).isSameAs(first);
        assertThat(first.getName()).isEqualTo("Alice");
        assertThat(lookupTables.getEntities()).containsKey("entity-1");

        lookupTables.clear();

        assertThat(lookupTables.getEntities()).isEmpty();
        assertThat(lookupTables.getRelations()).isEmpty();
        assertThat(lookupTables.getEpisodes()).isEmpty();
    }

    @Test
    void graphMemUpdateMergeConcatenatesListsAndUnionsSets() {
        GraphMemoryStates.GraphMemUpdate left = new GraphMemoryStates.GraphMemUpdate();
        GraphMemoryStates.GraphMemUpdate right = new GraphMemoryStates.GraphMemUpdate();
        Entity leftEntity = entity("left", "Left");
        Entity rightEntity = entity("right", "Right");
        left.getAddedEntity().add(leftEntity);
        left.getRemovedRelation().add("rel-a");
        right.getAddedEntity().add(rightEntity);
        right.getRemovedRelation().add("rel-b");

        GraphMemoryStates.GraphMemUpdate merged = left.merge(right);

        assertThat(merged.getAddedEntity()).containsExactly(leftEntity, rightEntity);
        assertThat(merged.getRemovedRelation()).containsExactly("rel-a", "rel-b");
        assertThat(left.getAddedEntity()).containsExactly(leftEntity);
        assertThat(right.getAddedEntity()).containsExactly(rightEntity);
    }

    @Test
    void batchEmbedSetsEmbeddingAttributesAndReturnsFailuresUnchanged() {
        GraphConfig config = GraphConfig.builder().uri(".").embedBatchSize(2).build();
        Entity entity = entity("entity-1", "Alice");
        entity.setContent("summary");

        List<BaseGraphObject> failed = GraphMemoryStates.batchEmbed(
                List.of(entity),
                new FakeEmbedding(false),
                config
        ).join();

        assertThat(failed).isEmpty();
        assertThat(entity.getContentEmbedding()).containsExactly(1.0d, 2.0d);
        assertThat(entity.getNameEmbedding()).containsExactly(3.0d, 4.0d);

        Entity failingEntity = entity("entity-2", "Bob");
        List<BaseGraphObject> retry = GraphMemoryStates.batchEmbed(
                List.of(failingEntity),
                new FakeEmbedding(true),
                config
        ).join();

        assertThat(retry).containsExactly(failingEntity);
    }

    @Test
    void batchEmbedReturnsEmptyWhenObjectsHaveNoEmbeddingTasks() {
        GraphConfig config = GraphConfig.builder().uri(".").embedBatchSize(2).build();

        List<BaseGraphObject> failed = GraphMemoryStates.batchEmbed(
                List.of(new NoEmbeddingObject()),
                new FakeEmbedding(false),
                config
        ).join();

        assertThat(failed).isEmpty();
    }

    @Test
    void classifyRelationsExtractedSeparatesSelfFactsEmptyRelationsAndMergeRelations() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getPrompting().setLanguage("en");
        Entity target = entity("target", "Target");
        target.getRelations().add("old-relation");
        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(target);
        Relation kept = relation("keep", entity("lhs", "Lhs"), entity("rhs", "Rhs"), "keep content");
        Relation removed = relation("remove", target, target, "self merge");
        merge.getNewRelations().add(kept);
        merge.getNewRelations().add(removed);
        state.getMergeInfos().put("target", merge);

        Entity factEntity = entity("fact-entity", "Fact");
        factEntity.setContent("base\n");
        Relation empty = relation("empty", entity("a", "A"), entity("b", "B"), " ");
        Relation selfFact = relation("self", factEntity, factEntity, "new fact");
        Relation normal = relation("normal", entity("c", "C"), entity("d", "D"), "normal content");

        GraphMemoryStates.classifyRelationsExtracted(List.of(empty, selfFact, normal), state);

        assertThat(merge.getRelationsToKeep()).containsExactly("keep");
        assertThat(target.getRelations()).containsExactly("keep", "old-relation");
        assertThat(state.getMemUpdate().getRemovedRelation()).containsExactly("remove");
        assertThat(state.getToRemove()).containsExactly(empty, selfFact);
        assertThat(factEntity.getContent()).isEqualTo("base\n- new fact");
        assertThat(normal.getLanguage()).isEqualTo("en");
        assertThat(state.getTmpBuffer()).containsExactly("normal content");
    }

    @Test
    void classifyRelationsExtractedTreatsEqualStringEndpointsAsSelfFact() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Relation selfString = relation("self-string", new String("entity-1"), new String("entity-1"), "string fact");

        GraphMemoryStates.classifyRelationsExtracted(List.of(selfString), state);

        assertThat(state.getToRemove()).containsExactly(selfString);
        assertThat(state.getTmpBuffer()).isEmpty();
    }

    @Test
    void graphMemStateClearReferencesClearsBuffersAndNestedMergeState() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity target = entity("target", "Target");
        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(target);
        merge.getSource().put("source", entity("source", "Source"));
        state.getMergeInfos().put("target", merge);
        state.getTmpBuffer().add("value");
        state.getMemUpdate().getRemovedEntity().add("entity-id");

        state.clearReferences();

        assertThat(state.getMergeInfos()).isEmpty();
        assertThat(merge.getSource()).isEmpty();
        assertThat(state.getTmpBuffer()).isEmpty();
        assertThat(state.getMemUpdate().getRemovedEntity()).isEmpty();
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        return entity;
    }

    private static Relation relation(String uuid, Object lhs, Object rhs, String content) {
        Relation relation = new Relation(lhs, rhs);
        relation.setUuid(uuid);
        relation.setContent(content);
        return relation;
    }

    private static final class NoEmbeddingObject extends BaseGraphObject {
        @Override
        public List<EmbeddingTask> fetchEmbedTask() {
            return List.of();
        }
    }

    private static final class FakeEmbedding extends Embedding {
        private final boolean fail;

        private FakeEmbedding(boolean fail) {
            this.fail = fail;
        }

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(1.0d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs) {
            if (fail) {
                CompletableFuture<List<List<Double>>> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("boom"));
                return failed;
            }
            List<List<Double>> embeddings = new ArrayList<>();
            for (int index = 0; index < texts.size(); index++) {
                embeddings.add(index == 0 ? List.of(1.0d, 2.0d) : List.of(3.0d, 4.0d));
            }
            return CompletableFuture.completedFuture(embeddings);
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }
}
