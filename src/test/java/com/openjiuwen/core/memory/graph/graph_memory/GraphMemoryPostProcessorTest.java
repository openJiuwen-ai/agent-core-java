/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Mirrors Python's graph object post-processing helpers in
 * {@code openjiuwen/core/memory/graph/graph_memory/postprocess_graph_objects.py}.
 */
class GraphMemoryPostProcessorTest {

    @Test
    void validateEntitiesEpisodesSyncsCurrentEpisodeAndMergedSourceEpisodes() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Entity entity = entity("entity-1", "Alice");
        entity.getEpisodes().add("episode-current");
        Episode current = episode("episode-current");
        current.getEntities().add("stale-entity");

        Entity source = entity("source", "Source");
        source.getEpisodes().add("episode-old");
        Episode oldEpisode = episode("episode-old");
        oldEpisode.getEntities().add(source);
        state.getLookupTable().getEpisodes().put("episode-old", oldEpisode);
        GraphMemoryStates.EntityMerge merge = new GraphMemoryStates.EntityMerge(entity("target", "Target"));
        merge.getSource().put("source", source);
        state.getMergeInfos().put("target", merge);

        GraphMemoryPostProcessor.validateEntitiesEpisodes(List.of(entity), current, state);

        assertThat(current.getEntities()).containsExactly("stale-entity", "entity-1");
        assertThat(oldEpisode.getEntities()).containsExactly("target");
        assertThat(state.getMemUpdateSkipEmbed().getUpdatedEpisode()).containsExactly(oldEpisode);
    }

    @Test
    void createEpisodeCopiesStateFieldsAndRegistersAddedEpisode() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.setReferenceTimestamp(1234L);
        state.setEpisodeType(EpisodeType.JSON);
        state.getPrompting().setLanguage("en");
        state.getStrategy().setSkipUuidDedupe(true);
        GraphStore database = mock(GraphStore.class);

        Episode episode = GraphMemoryPostProcessor.createEpisode(database, "user", "payload", state).join();

        assertThat(episode.getValidSince()).isEqualTo(1234L);
        assertThat(episode.getUserId()).isEqualTo("user");
        assertThat(episode.getObjType()).isEqualTo("JSON");
        assertThat(episode.getLanguage()).isEqualTo("en");
        assertThat(episode.getContent()).isEqualTo("payload");
        assertThat(state.getMemUpdate().getAddedEpisode()).containsExactly(episode);
    }

    @Test
    void processRelationsRemovesDeprecatedRelationRefsAndAddsNewRelations() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getStrategy().setSkipUuidDedupe(true);
        state.getMemUpdate().getRemovedRelation().add("removed-rel");
        Entity entity = entity("entity-1", "Alice");
        Relation removed = relation("removed-rel", entity, entity, "old");
        entity.getRelations().add("removed-rel");
        entity.getRelations().add(removed);
        state.getLookupTable().getRelations().put("removed-rel", removed);
        Relation added = relation("new-rel", entity, entity("entity-2", "Bob"), "new");

        GraphMemoryPostProcessor.processRelations(mock(GraphStore.class), List.of(entity), List.of(added), state).join();

        assertThat(entity.getRelations()).doesNotContain("removed-rel", removed);
        assertThat(state.getMemUpdate().getAddedRelation()).containsExactly(added);
        assertThat(state.getTmpBuffer()).containsExactly("new-rel");
    }

    @Test
    void processEntitiesAppliesMergingTaskAndClassifiesAddedUpdatedEntities() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        state.getStrategy().setSkipUuidDedupe(true);
        state.getPrompting().setLanguage("en");
        Episode episode = episode("episode-1");
        Entity added = entity("added", "Added");
        added.setContent("\nsummary");
        Relation removed = relation("removed-rel", added, added, "old");
        added.getRelations().add(removed);
        state.getMemUpdate().getRemovedRelation().add("removed-rel");
        Entity merged = entity("merged", "Merged");
        CompletableFuture<ContentResponse> future =
                CompletableFuture.completedFuture(new ContentResponse("{\"summary\":\"new summary\"}"));
        state.getMergingTasks().add(future);
        state.getMergingTasksEntities().put(future, merged);
        Entity updated = entity("updated", "Updated");
        state.getRetrievedEntities().put("updated", updated);

        GraphMemoryPostProcessor.processEntities(
                mock(GraphStore.class),
                new java.util.ArrayList<>(List.of(added, updated)),
                episode,
                state
        ).join();

        assertThat(added.getContent()).isEqualTo("summary");
        assertThat(added.getRelations()).doesNotContain(removed);
        assertThat(added.getEpisodes()).containsExactly("episode-1");
        assertThat(added.getLanguage()).isEqualTo("en");
        assertThat(state.getMemUpdate().getAddedEntity()).contains(added, merged);
        assertThat(state.getMemUpdate().getUpdatedEntity()).contains(updated);
    }

    @Test
    void parseRelationUuidsToRemoveKeepsStateWhenSchemaParsingReturnsEmptyMap() {
        GraphMemoryStates.GraphMemState state = new GraphMemoryStates.GraphMemState();
        Relation newRelation = relation("new", entity("a", "A"), entity("b", "B"), "new content");
        Relation existing = relation("existing", entity("a", "A"), entity("b", "B"), "old content");
        CompletableFuture<ContentResponse> future = CompletableFuture.completedFuture(new ContentResponse("""
                {"need_merging": true, "combined_content": "merged", "duplicate_ids": [1],
                 "valid_since": "", "valid_until": ""}
                """));

        GraphMemoryPostProcessor.parseRelationUuidsToRemove(
                List.of(new GraphMemoryPostProcessor.DedupeRelationTask(newRelation, List.of(existing), future)),
                state
        ).join();

        assertThat(newRelation.getContent()).isEqualTo("new content");
        assertThat(state.getToRemove()).isEmpty();
    }

    private static Entity entity(String uuid, String name) {
        Entity entity = new Entity();
        entity.setUuid(uuid);
        entity.setName(name);
        return entity;
    }

    private static Episode episode(String uuid) {
        Episode episode = new Episode();
        episode.setUuid(uuid);
        return episode;
    }

    private static Relation relation(String uuid, Object lhs, Object rhs, String content) {
        Relation relation = new Relation(lhs, rhs);
        relation.setUuid(uuid);
        relation.setContent(content);
        relation.setValidUntil(-1);
        return relation;
    }

    private record ContentResponse(String content) {
        public String getContent() {
            return content;
        }
    }
}
