/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Validation and processing of graph entities, episodes, and relations.
 *
 * <p>Mirrors Python's {@code postprocess_graph_objects.py} module from
 * {@code openjiuwen.core.memory.graph.graph_memory.postprocess_graph_objects}.</p>
 */
public final class PostprocessGraphObjects {

    private static final Logger LOGGER = Logger.getLogger(PostprocessGraphObjects.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PostprocessGraphObjects() {
    }

    public static void validateEntitiesEpisodes(List<Entity> entities,
                                                Episode currentEpisode,
                                                GraphMemoryStates.GraphMemState state) {
        Set<String> episodeEntities = new LinkedHashSet<>(currentEpisode.getEntities());
        for (Entity entity : entities) {
            episodeEntities.add(entity.getUuid());
        }
        currentEpisode.setEntities(new ArrayList<>(episodeEntities));

        state.getMemUpdateSkipEmbed().getUpdatedEntity()
                .removeIf(entity -> state.getMemUpdate().getUpdatedEntity().contains(entity));

        for (Map.Entry<String, GraphMemoryStates.EntityMerge> entry : state.getMergeInfos().entrySet()) {
            String targetUuid = entry.getKey();
            GraphMemoryStates.EntityMerge mergeInfo = entry.getValue();
            for (Map.Entry<String, Entity> sourceEntry : mergeInfo.getSource().entrySet()) {
                String sourceUuid = sourceEntry.getKey();
                Entity source = sourceEntry.getValue();
                for (String episodeUuid : source.getEpisodes()) {
                    Episode episode = state.getLookupTables().getEpisodes().get(episodeUuid);
                    if (episode == null) {
                        continue;
                    }
                    boolean updated = episode.getEntities().remove(sourceUuid);
                    if (updated) {
                        episode.getEntities().add(targetUuid);
                        if (!state.getMemUpdateSkipEmbed().getUpdatedEpisode().contains(episode)) {
                            state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
                        }
                    }
                }
            }
        }

        List<Episode> episodesToSync = new ArrayList<>(state.getMemUpdateSkipEmbed().getUpdatedEpisode());
        episodesToSync.add(currentEpisode);
        List<Entity> entitiesToSync = new ArrayList<>(state.getMemUpdate().getUpdatedEntity());
        entitiesToSync.addAll(state.getMemUpdateSkipEmbed().getUpdatedEntity());

        for (Episode episode : episodesToSync) {
            for (Entity entity : entitiesToSync) {
                boolean ep2e = episode.getEntities().contains(entity.getUuid());
                boolean e2ep = entity.getEpisodes().contains(episode.getUuid());
                if (ep2e && !e2ep) {
                    episode.getEntities().remove(entity.getUuid());
                } else if (e2ep && !ep2e) {
                    episode.getEntities().add(entity.getUuid());
                }
            }
            episode.setEntities(new ArrayList<>(new LinkedHashSet<>(episode.getEntities())));
        }
    }

    public static Episode createEpisode(GraphStore database,
                                        String userId,
                                        String content,
                                        GraphMemoryStates.GraphMemState state) {
        Episode currentEpisode = new Episode();
        currentEpisode.setCreatedAt(state.getCurrentTimestamp());
        currentEpisode.setValidSince(state.getReferenceTimestamp());
        currentEpisode.setUserId(userId);
        currentEpisode.setEpisodeType(state.getEpisodeType().name());
        currentEpisode.setLanguage(state.getPrompting().getLanguage());
        currentEpisode.setContent(content);

        state.getMemUpdate().getAddedEpisode().add(currentEpisode);
        return currentEpisode;
    }

    public static void processRelations(GraphStore database,
                                        List<Entity> entities,
                                        List<Relation> relations,
                                        GraphMemoryStates.GraphMemState state) {
        state.getTmpBuffer().clear();
        for (String relationUuid : state.getMemUpdate().getRemovedRelation()) {
            for (Entity entity : concatEntities(entities, state.getMemUpdateSkipEmbed().getUpdatedEntity())) {
                entity.getRelations().remove(relationUuid);
            }
        }
        for (Relation relation : relations) {
            Entity lhs = findEntity(entities, relation.getLhs());
            Entity rhs = findEntity(entities, relation.getRhs());
            relation.updateConnectedEntities(lhs, rhs);
            state.getMemUpdate().getAddedRelation().add(relation);
            state.getTmpBuffer().add(relation.getUuid());
        }
    }

    public static List<Entity> processEntities(GraphStore database,
                                               List<Entity> entities,
                                               Episode currentEpisode,
                                               GraphMemoryStates.GraphMemState state) {
        state.getTmpBuffer().clear();

        for (CompletableFuture<GraphMemory.LlmResponse> future : new ArrayList<>(state.getMergingTasks())) {
            GraphMemory.LlmResponse response = future.join();
            Entity entity = state.getMergingTasksEntities().get(future);
            if (entity != null) {
                entity.setContent(response.content());
                if (!entities.contains(entity)) {
                    entities.add(entity);
                }
            }
        }

        for (Entity entity : entities) {
            if (entity.getContent() != null && entity.getContent().startsWith("\n")) {
                entity.setContent(entity.getContent().substring(1));
            }
            state.getToRemove().clear();
            for (String relationUuid : entity.getRelations()) {
                if (state.getMemUpdate().getRemovedRelation().contains(relationUuid)) {
                    state.getToRemove().add(relationUuid);
                }
            }
            for (Object relationUuid : state.getToRemove()) {
                entity.getRelations().remove(String.valueOf(relationUuid));
            }
            if (!entity.getEpisodes().contains(currentEpisode.getUuid())) {
                entity.getEpisodes().add(currentEpisode.getUuid());
            }
            entity.setLanguage(state.getPrompting().getLanguage());
            if (state.getRetrievedEntities().containsKey(entity.getUuid())) {
                state.getMemUpdate().getUpdatedEntity().add(entity);
            } else {
                state.getMemUpdate().getAddedEntity().add(entity);
                state.getTmpBuffer().add(entity.getUuid());
            }
        }
        return entities;
    }

    public static void parseRelationUuidsToRemove(List<RelationDedupeTask> dedupeRelationTasks,
                                                  GraphMemoryStates.GraphMemState state) {
        for (RelationDedupeTask task : dedupeRelationTasks) {
            try {
                GraphMemory.LlmResponse result = task.future().join();
                Map<String, Object> response = MAPPER.readValue(result.content(), new TypeReference<>() {});
                state.getToRemove().addAll(ParseLlmResponse.parseRelationMerging(
                        response, task.relation(), task.currentRelations().stream().map(PostprocessGraphObjects::relationMap).toList()));
            } catch (Exception e) {
                LOGGER.info("Graph Memory: Failed to parse relation uuids to remove: " + e.getMessage());
            }
        }
    }

    public static List<String> parseRelationUuidsToRemove(Map<String, Object> response) {
        List<String> result = new ArrayList<>();
        Object ids = response == null ? null : response.get("duplicate_ids");
        if (ids instanceof Iterable<?> iterable) {
            for (Object id : iterable) {
                result.add(String.valueOf(id));
            }
        }
        return result;
    }

    private static Map<String, Object> relationMap(Relation relation) {
        return Map.of("uuid", relation.getUuid());
    }

    private static List<Entity> concatEntities(List<Entity> first, List<Entity> second) {
        List<Entity> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private static Entity findEntity(List<Entity> entities, String uuid) {
        for (Entity entity : entities) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    public record RelationDedupeTask(Relation relation,
                                     List<Relation> currentRelations,
                                     CompletableFuture<GraphMemory.LlmResponse> future) {
    }
}
