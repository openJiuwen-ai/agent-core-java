/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Missing Python parity tests for graph-memory post-processing helpers.
 *
 * <p>Mirrors Python's {@code TestValidateEntitiesEpisodes}, {@code TestCreateEpisode},
 * {@code TestProcessRelations}, {@code TestProcessEntities}, and {@code TestParseRelationUuidsToRemove} in
 * {@code tests/unit_tests/core/memory/graph/graph_memory/test_postprocess_graph_objects.py}.</p>
 */
class GraphMemoryPostProcessorMissingTest {

    @Test
    void currentEpisodeEntitiesUnion() {
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");
        Entity e2 = entity("e2", "E2");
        episode.getEntities().add(e1);
        GraphMemoryStates.GraphMemState state = state();

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(e1, e2), episode, state);

        assertThat(episode.getEntities()).containsExactly("e1", "e2");
    }

    @Test
    void validateEntitiesEpisodesMergeInfosUpdatesEpisodes() {
        Episode episode = episode("ep1", "c");
        Entity target = entity("tgt-uuid", "Tgt");
        Entity source = entity("src-uuid", "Src");
        source.getEpisodes().add("ep1");
        episode.getEntities().add(source);
        GraphMemoryStates.GraphMemState state = state();
        state.getLookupTable().getEpisodes().put("ep1", episode);
        state.getMergeInfos().put("tgt-uuid", merge(target, source));

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(target), episode, state);

        assertThat(episode.getEntities()).doesNotContain("src-uuid", source);
        assertThat(episode.getEntities()).contains("tgt-uuid");
    }

    @Test
    void validateEntitiesEpisodesMergeEpisodeNotInLookupSkipped() {
        Entity target = entity("tgt-uuid", "Tgt");
        Entity source = entity("src-uuid", "Src");
        source.getEpisodes().add("ep-missing");
        GraphMemoryStates.GraphMemState state = state();
        state.getMergeInfos().put("tgt-uuid", merge(target, source));
        Episode episode = episode("ep1", "c");
        episode.getEntities().add(target);

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(target), episode, state);

        assertThat(episode.getEntities()).contains("tgt-uuid");
    }

    @Test
    void validateEntitiesEpisodesMergeSourceEntityObjectInEpisodeEntities() {
        Episode episode = episode("ep1", "c");
        Entity target = entity("tgt-uuid", "Tgt");
        Entity source = entity("src-uuid", "Src");
        source.getEpisodes().add("ep1");
        episode.getEntities().add(source);
        GraphMemoryStates.GraphMemState state = state();
        state.getLookupTable().getEpisodes().put("ep1", episode);
        state.getMergeInfos().put("tgt-uuid", merge(target, source));

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(target), episode, state);

        assertThat(episode.getEntities()).doesNotContain("src-uuid", source);
        assertThat(episode.getEntities()).contains("tgt-uuid");
    }

    @Test
    void validateEntitiesEpisodesMergeElifSourceInEpisodeEntities() {
        Episode currentEpisode = episode("cur-ep", "cur");
        Episode lookupEpisode = episode("ep1", "old");
        Entity target = entity("tgt-uuid", "Tgt");
        Entity source = entity("src-uuid", "Src");
        source.getEpisodes().add("ep1");
        lookupEpisode.getEntities().add(source);
        GraphMemoryStates.GraphMemState state = state();
        state.getLookupTable().getEpisodes().put("ep1", lookupEpisode);
        state.getMergeInfos().put("tgt-uuid", merge(target, source));

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(target), currentEpisode, state);

        assertThat(lookupEpisode.getEntities()).doesNotContain(source, "src-uuid");
        assertThat(lookupEpisode.getEntities()).contains("tgt-uuid");
    }

    @Test
    void validateEntitiesEpisodesSyncEp2eNotE2epRemovesFromEpisode() {
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");
        episode.getEntities().add("e1");
        GraphMemoryStates.GraphMemState state = state();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(e1);

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(e1), episode, state);

        assertThat(episode.getEntities()).doesNotContain("e1");
    }

    @Test
    void validateEntitiesEpisodesSyncEpisodeEntityLists() {
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");
        e1.getEpisodes().add("ep1");
        episode.getEntities().add(e1);
        GraphMemoryStates.GraphMemState state = state();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(e1), episode, state);

        assertThat(episode.getEntities()).contains("e1");
        assertThat(e1.getEpisodes()).contains("ep1");
    }

    @Test
    void validateEntitiesEpisodesSyncE2epNotEp2eAppendsEntityToEpisode() {
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");
        e1.getEpisodes().add("ep1");
        GraphMemoryStates.GraphMemState state = state();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(e1);
        Episode currentEpisode = episode("cur-ep", "cur");

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(e1), currentEpisode, state);

        assertThat(episode.getEntities()).containsExactly("e1");
    }

    @Test
    void validateEntitiesEpisodesSyncDedupesEpisodeEntities() {
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");
        e1.getEpisodes().add("ep1");
        Entity e2 = entity("e2", "E2");
        episode.getEntities().addAll(List.of("e1", "e2", "e1"));
        GraphMemoryStates.GraphMemState state = state();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().addAll(List.of(e1, e2));

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(e1, e2), episode("cur-ep", "cur"), state);

        assertThat(episode.getEntities()).doesNotHaveDuplicates();
    }

    @Test
    void validateEntitiesEpisodesSyncDedupesEachEpisode() {
        Episode episode = episode("ep1", "p1");
        episode.getEntities().addAll(List.of("a", "b", "a"));
        Episode currentEpisode = episode("cur", "cur");
        currentEpisode.getEntities().addAll(List.of("x", "x"));
        Entity entity = entity("e", "E");
        GraphMemoryStates.GraphMemState state = state();
        state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
        state.getMemUpdateSkipEmbed().getUpdatedEntity().add(entity);

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(entity), currentEpisode, state);

        assertThat(episode.getEntities()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void createEpisodeAppendsToMemUpdate() {
        GraphMemoryStates.GraphMemState state = state();

        Episode episode = GraphMemoryPostProcessor.createEpisode(mock(GraphStore.class), "user-1", "content text", state)
                .join();

        assertThat(episode.getContent()).isEqualTo("content text");
        assertThat(episode.getUserId()).isEqualTo("user-1");
        assertThat(state.getMemUpdate().getAddedEpisode()).containsExactly(episode);
        assertThat(episode.getUuid()).isNotBlank();
    }

    @Test
    void processRelationsAppendsToAddedRelation() {
        GraphMemoryStates.GraphMemState state = state();
        Entity e1 = entity("e1", "E1");
        Entity e2 = entity("e2", "E2");
        Relation relation = relation("r-uuid-1", e1, e2, "rel");

        GraphMemoryPostProcessor.processRelations(mock(GraphStore.class), List.of(e1, e2), List.of(relation), state)
                .join();

        assertThat(state.getMemUpdate().getAddedRelation()).containsExactly(relation);
        assertThat(relation.getUuid()).isEqualTo("r-uuid-1");
    }

    @Test
    void processRelationsRemovesRemovedRelationFromEntities() {
        GraphMemoryStates.GraphMemState state = state();
        Entity e1 = entity("e1", "E1");
        e1.getRelations().addAll(List.of("r-old", "r-keep"));
        Entity e2 = entity("e2", "E2");
        Relation oldRelation = relation("r-old", "e1", "e2", "x");
        state.getMemUpdate().getRemovedRelation().add("r-old");
        state.getLookupTable().getRelations().put("r-old", oldRelation);
        Relation newRelation = relation("r-new-uuid", e1, e2, "y");

        GraphMemoryPostProcessor.processRelations(mock(GraphStore.class), List.of(e1, e2), List.of(newRelation), state)
                .join();

        assertThat(e1.getRelations()).doesNotContain("r-old");
        assertThat(e1.getRelations()).contains("r-keep");
    }

    @Test
    void processRelationsRemovesRelationObjectFromEntity() {
        GraphMemoryStates.GraphMemState state = state();
        Entity e1 = entity("e1", "E1");
        Relation oldRelation = relation("r-old", "e1", "e2", "x");
        e1.getRelations().add(oldRelation);
        Entity e2 = entity("e2", "E2");
        state.getMemUpdate().getRemovedRelation().add("r-old");
        state.getLookupTable().getRelations().put("r-old", oldRelation);
        Relation newRelation = relation("r-new-uuid", e1, e2, "y");

        GraphMemoryPostProcessor.processRelations(mock(GraphStore.class), List.of(e1, e2), List.of(newRelation), state)
                .join();

        assertThat(e1.getRelations()).doesNotContain(oldRelation);
    }

    @Test
    void processEntitiesAddsNewEntityToAddedEntity() {
        GraphMemoryStates.GraphMemState state = state();
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");

        GraphMemoryPostProcessor.processEntities(mock(GraphStore.class), List.of(e1), episode, state).join();

        assertThat(state.getMemUpdate().getAddedEntity()).containsExactly(e1);
        assertThat(e1.getEpisodes()).contains("ep1");
    }

    @Test
    void processEntitiesMergingTasksAndRetrievedEntityUpdated() {
        GraphMemoryStates.GraphMemState state = state();
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");
        state.getRetrievedEntities().put("e1", e1);
        CompletableFuture<ContentResponse> future =
                CompletableFuture.completedFuture(new ContentResponse("{\"summary\":\"merged\"}"));
        state.getMergingTasks().add(future);
        state.getMergingTasksEntities().put(future, e1);

        GraphMemoryPostProcessor.processEntities(mock(GraphStore.class), new ArrayList<>(List.of(e1)), episode, state)
                .join();

        assertThat(state.getMemUpdate().getUpdatedEntity()).contains(e1);
        assertThat(state.getMemUpdate().getAddedEntity()).doesNotContain(e1);
        assertThat(e1.getContent()).isEqualTo("merged");
    }

    @Test
    void processEntitiesMergingTaskEntityNotInEntitiesAppended() {
        GraphMemoryStates.GraphMemState state = state();
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");
        Entity e2 = entity("e2", "E2");
        state.getRetrievedEntities().put("e2", e2);
        CompletableFuture<ContentResponse> future =
                CompletableFuture.completedFuture(new ContentResponse("{\"summary\":\"merged e2\"}"));
        state.getMergingTasks().add(future);
        state.getMergingTasksEntities().put(future, e2);
        List<Entity> entities = new ArrayList<>(List.of(e1));

        GraphMemoryPostProcessor.processEntities(mock(GraphStore.class), entities, episode, state).join();

        assertThat(entities).contains(e2);
        assertThat(state.getMemUpdate().getUpdatedEntity()).contains(e2);
    }

    @Test
    void processEntitiesRemovesRelationFromEntity() {
        GraphMemoryStates.GraphMemState state = state();
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("e1", "E1");
        e1.getRelations().add("r-gone");
        state.getMemUpdate().getRemovedRelation().add("r-gone");

        GraphMemoryPostProcessor.processEntities(mock(GraphStore.class), List.of(e1), episode, state).join();

        assertThat(e1.getRelations()).doesNotContain("r-gone");
    }

    @Test
    void processEntitiesResolveEntityUuidAssignsUuids() {
        GraphMemoryStates.GraphMemState state = state();
        Episode episode = episode("ep1", "c");
        Entity e1 = entity("", "E1");

        GraphMemoryPostProcessor.processEntities(mock(GraphStore.class), List.of(e1), episode, state).join();

        assertThat(e1.getUuid()).isNotBlank();
        assertThat(state.getMemUpdate().getAddedEntity()).contains(e1);
    }

    @Test
    void parseRelationUuidsToRemoveExtendsToRemove() {
        GraphMemoryStates.GraphMemState state = state();
        Relation relation = relation("new-r", "e1", "e2", "c");
        Relation currentRelation = relation("old-r1", "e1", "e2", "old");
        CompletableFuture<ContentResponse> future = CompletableFuture.completedFuture(new ContentResponse("""
                {"need_merging": true, "combined_content": "m", "duplicate_ids": [1]}
                """));

        GraphMemoryPostProcessor.parseRelationUuidsToRemove(
                List.of(new GraphMemoryPostProcessor.DedupeRelationTask(relation, List.of(currentRelation), future)),
                state
        ).join();

        assertThat(state.getToRemove()).contains("old-r1");
        assertThat(relation.getContent()).isEqualTo("m");
    }

    @Test
    void parseRelationUuidsToRemoveExceptionLogged() {
        GraphMemoryStates.GraphMemState state = state();
        Relation relation = relation("new-r", "e1", "e2", "c");
        CompletableFuture<ContentResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new ValueFailure("mock fail"));

        GraphMemoryPostProcessor.parseRelationUuidsToRemove(
                List.of(new GraphMemoryPostProcessor.DedupeRelationTask(
                        relation,
                        List.of(relation("x", "e1", "e2", "x")),
                        future)),
                state
        ).join();

        assertThat(state.getToRemove()).doesNotContain("x");
    }

    private static GraphMemoryStates.GraphMemState state() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getStrategy().setSkipUuidDedupe(true);
        return state;
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        entity.setContent("");
        return entity;
    }

    private static Episode episode(String uuid, String content) {
        Episode episode = new Episode();
        episode.setUuid(uuid);
        episode.setContent(content);
        episode.setUserId("u1");
        episode.setObjType("conversation");
        episode.setCreatedAt(0L);
        episode.setValidSince(0L);
        return episode;
    }

    private static Relation relation(String uuid, Object lhs, Object rhs, String content) {
        Relation relation = new Relation(lhs, rhs);
        relation.setUuid(uuid);
        relation.setName("R");
        relation.setContent(content);
        relation.setObjType("Relation");
        relation.setValidSince(0L);
        relation.setValidUntil(-1L);
        return relation;
    }

    private static GraphMemoryStates.EntityMerge merge(Entity target, Entity source) {
        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(target);
        merge.getSource().put(source.getUuid(), source);
        return merge;
    }

    private record ContentResponse(String content) {
        public String getContent() {
            return content;
        }
    }

    private static final class ValueFailure extends RuntimeException {
        private ValueFailure(String message) {
            super(message);
        }
    }
}
