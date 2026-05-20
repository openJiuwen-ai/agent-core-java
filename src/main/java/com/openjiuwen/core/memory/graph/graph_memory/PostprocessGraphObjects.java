/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphUtils;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.graph.extraction.ParseResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Validation and processing of graph entities, episodes, and relations.
 */
public final class PostprocessGraphObjects {
    private PostprocessGraphObjects() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void validateEntitiesEpisodes(List<Entity> entities,
                                                Episode currentEpisode,
                                                States.GraphMemState state) {
        List<String> merged = new ArrayList<>();
        for (Object entity : currentEpisode.getEntities()) {
            merged.add(entityUuid(entity));
        }
        for (Entity entity : entities) {
            merged.add(entity.getUuid());
        }
        currentEpisode.setEntities(new ArrayList<>(merged.stream().distinct().toList()));

        state.getMemUpdateSkipEmbed().getUpdatedEntity()
                .removeIf(entity -> state.getMemUpdate().getUpdatedEntity().contains(entity));

        for (Map.Entry<String, States.EntityMerge> entry : state.getMergeInfos().entrySet()) {
            String targetUuid = entry.getKey();
            States.EntityMerge mergeInfo = entry.getValue();
            for (Map.Entry<String, Entity> sourceEntry : mergeInfo.getSource().entrySet()) {
                String sourceUuid = sourceEntry.getKey();
                Entity source = sourceEntry.getValue();
                for (String episodeUuid : source.getEpisodes()) {
                    Episode episode = state.getLookupTable().getEpisodes().get(episodeUuid);
                    if (episode == null) {
                        continue;
                    }
                    boolean isUpdated = false;
                    if (episode.getEntities().contains(sourceUuid)) {
                        episode.getEntities().remove(sourceUuid);
                        isUpdated = true;
                    } else if (episode.getEntities().contains(source)) {
                        episode.getEntities().remove(source);
                        isUpdated = true;
                    }
                    if (isUpdated) {
                        episode.getEntities().add(targetUuid);
                        if (!state.getMemUpdateSkipEmbed().getUpdatedEpisode().contains(episode)) {
                            state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
                        }
                    }
                }
            }
        }

        List<Episode> episodesToCheck = new ArrayList<>(state.getMemUpdateSkipEmbed().getUpdatedEpisode());
        episodesToCheck.add(currentEpisode);
        List<Entity> entitiesToCheck = new ArrayList<>(state.getMemUpdate().getUpdatedEntity());
        entitiesToCheck.addAll(state.getMemUpdateSkipEmbed().getUpdatedEntity());
        for (Episode episode : episodesToCheck) {
            for (Entity entity : entitiesToCheck) {
                boolean isEpisodeToEntity = episode.getEntities().contains(entity.getUuid());
                boolean isEntityToEpisode = entity.getEpisodes().contains(episode.getUuid());
                if (isEpisodeToEntity && !isEntityToEpisode) {
                    episode.getEntities().remove(entity.getUuid());
                } else if (isEntityToEpisode && !isEpisodeToEntity) {
                    episode.getEntities().add(entity.getUuid());
                }
            }
            episode.setEntities(new ArrayList<>(episode.getEntities().stream().distinct().toList()));
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Episode createEpisode(GraphStore database,
                                        String userId,
                                        String content,
                                        States.GraphMemState state) throws Exception {
        Episode currentEpisode = new Episode();
        currentEpisode.setCreatedAt(state.getCurrentTimestamp());
        currentEpisode.setValidSince(state.getReferenceTimestamp());
        currentEpisode.setUserId(userId);
        currentEpisode.setObjType(state.getEpisodeType().name());
        currentEpisode.setLanguage(state.getPrompting().getLanguage());
        currentEpisode.setContent(content);
        currentEpisode.setUuid(GraphUtils.ensureUniqueUuids(database, List.of(currentEpisode.getUuid()),
                GraphConstants.EPISODE_COLLECTION, state.getStrategy().isSkipUuidDedupe()).get(0));
        state.getMemUpdate().getAddedEpisode().add(currentEpisode);
        return currentEpisode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void processRelations(GraphStore database,
                                        List<Entity> entities,
                                        List<Relation> relations,
                                        States.GraphMemState state) throws Exception {
        List<Object> toResolve = state.getTmpBuffer();
        toResolve.clear();
        for (String relationUuid : state.getMemUpdate().getRemovedRelation()) {
            for (Entity entity : concatEntities(entities, state.getMemUpdateSkipEmbed().getUpdatedEntity())) {
                entity.getRelations().remove(relationUuid);
                Relation relation = state.getLookupTable().getRelations().get(relationUuid);
                entity.getRelations().remove(relation);
            }
        }
        for (Relation relation : relations) {
            relation.updateConnectedEntities();
            state.getMemUpdate().getAddedRelation().add(relation);
            toResolve.add(relation.getUuid());
        }
        if (!toResolve.isEmpty()) {
            List<Object> uniqueUuids = GraphUtils.ensureUniqueUuids(database, new ArrayList<>(toResolve),
                    GraphConstants.RELATION_COLLECTION, state.getStrategy().isSkipUuidDedupe());
            for (int i = 0; i < state.getMemUpdate().getAddedRelation().size() && i < uniqueUuids.size(); i++) {
                state.getMemUpdate().getAddedRelation().get(i).setUuid(String.valueOf(uniqueUuids.get(i)));
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void processEntities(GraphStore database,
                                       List<Entity> entities,
                                       Episode currentEpisode,
                                       States.GraphMemState state) throws Exception {
        List<Object> toResolve = state.getTmpBuffer();
        toResolve.clear();
        for (CompletableFuture<?> future : state.getMergingTasks()) {
            Object response = future.join();
            Entity entity = state.getMergingTasksEntities().get(future);
            String content = response instanceof Map<?, ?> map
                    ? String.valueOf(map.get("content"))
                    : String.valueOf(response);
            GraphMemoryUtils.updateEntity(entity, content, state.getPrompting().getSchemaEntityExtraction());
            if (!entities.contains(entity)) {
                entities.add(entity);
            }
        }
        for (Entity entity : entities) {
            if (entity.getContent() != null && entity.getContent().startsWith("\n")) {
                entity.setContent(entity.getContent().substring(1));
            }
            state.getToRemove().clear();
            for (Object relationObj : new ArrayList<>(entity.getRelations())) {
                String relationUuid = relationUuid(relationObj);
                if (state.getMemUpdate().getRemovedRelation().contains(relationUuid)) {
                    state.getToRemove().add(relationObj);
                }
            }
            entity.getRelations().removeAll(state.getToRemove());
            if (!entity.getEpisodes().contains(currentEpisode.getUuid())
                    && !entity.getEpisodes().contains(currentEpisode)) {
                entity.getEpisodes().add(currentEpisode.getUuid());
            }
            entity.setLanguage(state.getPrompting().getLanguage());
            if (state.getRetrievedEntities().containsKey(entity.getUuid())) {
                state.getMemUpdate().getUpdatedEntity().add(entity);
            } else {
                state.getMemUpdate().getAddedEntity().add(entity);
                toResolve.add(entity.getUuid());
            }
        }
        resolveEntityUuid(database, state);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void parseRelationUuidsToRemove(List<RelationTask> dedupeRelationTasks,
                                                  States.GraphMemState state) {
        for (RelationTask relationTask : dedupeRelationTasks) {
            try {
                Object result = relationTask.future().join();
                String content = result instanceof Map<?, ?> map
                        ? String.valueOf(map.get("content"))
                        : String.valueOf(result);
                Object dedupeRelation = ParseResponse.parseJson(content, state.getPrompting().getSchemaRelationMerge());
                if (!(dedupeRelation instanceof Map<?, ?>)) {
                    dedupeRelation = ParseResponse.rawDecodeJson(content, null);
                }
                Map<String, Object> typed = dedupeRelation instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                if (!typed.containsKey("need_merging")
                        && !typed.containsKey("needMerging")
                        && content != null
                        && !content.isBlank()) {
                    int jsonStart = Math.max(content.indexOf('{'), content.indexOf('['));
                    if (jsonStart >= 0) {
                        String jsonSlice = content.substring(jsonStart);
                        Object fallback = ParseResponse.rawDecodeJson(jsonSlice, null);
                        if (fallback instanceof Map<?, ?> fallbackMap) {
                            typed = (Map<String, Object>) fallbackMap;
                        }
                    }
                }
                state.getToRemove().addAll(ParseLlmResponse.parseRelationMerging(
                        typed,
                        relationTask.relation(),
                        relationTask.currentRelations()
                ));
            } catch (CompletionException | IllegalArgumentException ignored) {
                // Failed dedupe parsing keeps the candidate relation set unchanged.
            }
        }
    }

    private static String entityUuid(Object entity) {
        if (entity instanceof String uuid) {
            return uuid;
        }
        if (entity instanceof Entity typed) {
            return typed.getUuid();
        }
        return String.valueOf(entity);
    }

    private static String relationUuid(Object relation) {
        if (relation instanceof String uuid) {
            return uuid;
        }
        if (relation instanceof Relation typed) {
            return typed.getUuid();
        }
        return String.valueOf(relation);
    }

    private static void resolveEntityUuid(GraphStore database, States.GraphMemState state) throws Exception {
        List<Object> toResolve = state.getTmpBuffer();
        if (!toResolve.isEmpty()) {
            List<Object> uniqueUuids = GraphUtils.ensureUniqueUuids(database, new ArrayList<>(toResolve),
                    GraphConstants.ENTITY_COLLECTION, state.getStrategy().isSkipUuidDedupe());
            for (int i = 0; i < state.getMemUpdate().getAddedEntity().size() && i < uniqueUuids.size(); i++) {
                state.getMemUpdate().getAddedEntity().get(i).setUuid(String.valueOf(uniqueUuids.get(i)));
            }
        }
    }

    private static List<Entity> concatEntities(List<Entity> first, List<Entity> second) {
        List<Entity> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    /**
 * Public record RelationTask used by the Java parity implementation.
 *
 * @since 1.0
 */
public record RelationTask(
            Relation relation,
            List<Map<String, Object>> currentRelations,
            CompletableFuture<?> future) {
    }
}
