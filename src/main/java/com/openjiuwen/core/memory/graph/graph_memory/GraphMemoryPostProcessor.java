/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStoreUtils;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.graph.extraction.ParseResponse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Validation and post-processing for graph-memory entities, episodes, and relations.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.postprocess_graph_objects} in
 * {@code openjiuwen/core/memory/graph/graph_memory/postprocess_graph_objects.py}.</p>
 */
public final class GraphMemoryPostProcessor {

    public static final List<String> ALL = List.of(
            "validate_entities_episodes",
            "create_episode",
            "process_relations",
            "process_entities",
            "parse_relation_uuids_to_remove"
    );

    private GraphMemoryPostProcessor() {
    }

    public static void validateEntitiesEpisodes(List<Entity> entities,
                                                Episode currentEpisode,
                                                GraphMemoryStates.GraphMemState state) {
        LinkedHashSet<Object> episodeEntities = new LinkedHashSet<>();
        for (Object entityRef : currentEpisode.getEntities()) {
            episodeEntities.add(entityRef instanceof Entity entity ? entity.getUuid() : entityRef);
        }
        for (Entity entity : entities) {
            episodeEntities.add(entity.getUuid());
        }
        currentEpisode.setEntities(new ArrayList<>(episodeEntities));

        state.getMemUpdateSkipEmbed().getUpdatedEntity().removeIf(
                entity -> state.getMemUpdate().getUpdatedEntity().contains(entity));

        for (Map.Entry<String, GraphMemoryStates.EntityMerge> mergeEntry : state.getMergeInfos().entrySet()) {
            String targetUuid = mergeEntry.getKey();
            GraphMemoryStates.EntityMerge mergeInfo = mergeEntry.getValue();
            for (Map.Entry<String, Entity> sourceEntry : mergeInfo.getSource().entrySet()) {
                String sourceUuid = sourceEntry.getKey();
                Entity source = sourceEntry.getValue();
                for (String episodeUuid : source.getEpisodes()) {
                    Episode episode = state.getLookupTable().getEpisodes().get(episodeUuid);
                    if (episode == null) {
                        continue;
                    }
                    boolean updated = false;
                    if (episode.getEntities().contains(sourceUuid)) {
                        episode.getEntities().remove(sourceUuid);
                        updated = true;
                    } else if (episode.getEntities().contains(source)) {
                        episode.getEntities().remove(source);
                        updated = true;
                    }
                    if (updated) {
                        episode.getEntities().add(targetUuid);
                        if (!state.getMemUpdateSkipEmbed().getUpdatedEpisode().contains(episode)) {
                            state.getMemUpdateSkipEmbed().getUpdatedEpisode().add(episode);
                        }
                    }
                }
            }
        }

        List<Episode> episodes = new ArrayList<>(state.getMemUpdateSkipEmbed().getUpdatedEpisode());
        episodes.add(currentEpisode);
        List<Entity> updatedEntities = new ArrayList<>(state.getMemUpdate().getUpdatedEntity());
        updatedEntities.addAll(state.getMemUpdateSkipEmbed().getUpdatedEntity());
        for (Episode episode : episodes) {
            for (Entity entity : updatedEntities) {
                boolean episodeToEntity = episode.getEntities().contains(entity.getUuid());
                boolean entityToEpisode = entity.getEpisodes().contains(episode.getUuid());
                if (episodeToEntity && !entityToEpisode) {
                    episode.getEntities().remove(entity.getUuid());
                } else if (entityToEpisode && !episodeToEntity) {
                    episode.getEntities().add(entity.getUuid());
                }
            }
            episode.setEntities(new ArrayList<>(new LinkedHashSet<>(episode.getEntities())));
        }
    }

    public static CompletableFuture<Episode> createEpisode(GraphStore database,
                                                           String userId,
                                                           String content,
                                                           GraphMemoryStates.GraphMemState state) {
        Episode currentEpisode = new Episode();
        currentEpisode.setCreatedAt(state.getCurrentTimestamp());
        currentEpisode.setValidSince(state.getReferenceTimestamp());
        currentEpisode.setUserId(userId);
        currentEpisode.setObjType(state.getEpisodeType().name());
        currentEpisode.setLanguage(state.getPrompting().getLanguage());
        currentEpisode.setContent(content);

        return GraphStoreUtils.ensureUniqueUuids(
                database,
                List.of(currentEpisode.getUuid()),
                GraphStoreConstants.EPISODE_COLLECTION,
                state.getStrategy().isSkipUuidDedupe()
        ).thenApply(uniqueUuids -> {
            currentEpisode.setUuid(String.valueOf(uniqueUuids.get(0)));
            state.getMemUpdate().getAddedEpisode().add(currentEpisode);
            return currentEpisode;
        });
    }

    public static CompletableFuture<Void> processRelations(GraphStore database,
                                                           List<Entity> entities,
                                                           List<Relation> relations,
                                                           GraphMemoryStates.GraphMemState state) {
        List<Object> toResolve = state.getTmpBuffer();
        toResolve.clear();
        for (String relationUuid : state.getMemUpdate().getRemovedRelation()) {
            for (Entity entity : concatEntities(entities, state.getMemUpdateSkipEmbed().getUpdatedEntity())) {
                entity.getRelations().remove(relationUuid);
                Relation relation = state.getLookupTable().getRelations().get(relationUuid);
                if (relation != null) {
                    entity.getRelations().remove(relation);
                }
            }
        }

        for (Relation relation : relations) {
            relation.updateConnectedEntities();
            state.getMemUpdate().getAddedRelation().add(relation);
            toResolve.add(relation.getUuid());
        }

        if (toResolve.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return GraphStoreUtils.ensureUniqueUuids(
                database,
                List.copyOf(toResolve),
                GraphStoreConstants.RELATION_COLLECTION,
                state.getStrategy().isSkipUuidDedupe()
        ).thenAccept(uniqueUuids -> {
            for (int index = 0; index < Math.min(state.getMemUpdate().getAddedRelation().size(), uniqueUuids.size());
                    index++) {
                state.getMemUpdate().getAddedRelation().get(index).setUuid(String.valueOf(uniqueUuids.get(index)));
            }
        });
    }

    public static CompletableFuture<Void> processEntities(GraphStore database,
                                                          List<Entity> entities,
                                                          Episode currentEpisode,
                                                          GraphMemoryStates.GraphMemState state) {
        List<Object> toResolve = state.getTmpBuffer();
        toResolve.clear();
        CompletableFuture<?>[] futures = state.getMergingTasks().toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenCompose(ignored -> {
            for (CompletableFuture<?> future : state.getMergingTasks()) {
                Object response = future.join();
                Entity entity = state.getMergingTasksEntities().get(future);
                GraphMemoryUtils.updateEntity(
                        entity,
                        contentFrom(response),
                        state.getPrompting().getSchemaEntityExtraction()
                );
                if (!entities.contains(entity)) {
                    entities.add(entity);
                }
            }

            for (Entity entity : entities) {
                entity.setContent(removePrefix(entity.getContent(), "\n"));
                state.getToRemove().clear();
                for (Object relationRef : new ArrayList<>(entity.getRelations())) {
                    String relationUuid = relationRef instanceof Relation relation ? relation.getUuid() : String.valueOf(relationRef);
                    if (state.getMemUpdate().getRemovedRelation().contains(relationUuid)) {
                        state.getToRemove().add(relationRef);
                    }
                }
                for (Object relationRef : state.getToRemove()) {
                    entity.getRelations().remove(relationRef);
                }
                if (!entity.getEpisodes().contains(currentEpisode.getUuid())) {
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
            return resolveEntityUuid(database, state);
        });
    }

    public static CompletableFuture<Void> parseRelationUuidsToRemove(List<DedupeRelationTask> dedupeRelationTasks,
                                                                     GraphMemoryStates.GraphMemState state) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (DedupeRelationTask task : dedupeRelationTasks) {
            chain = chain.thenCompose(ignored -> task.future().handle((result, error) -> {
                if (error != null) {
                    Loggers.MEMORY.info("Graph Memory: Failed to parse relation uuids to remove: {}", error);
                    return null;
                }
                try {
                    Object parsed = ParseResponse.parseJson(
                            contentFrom(result),
                            state.getPrompting().getSchemaRelationMerge()
                    );
                    Map<String, Object> dedupeRelation = parsed instanceof Map<?, ?> map
                            ? stringObjectMap(map)
                            : new LinkedHashMap<>();
                    Set<String> toRemove = GraphMemoryLlmResponseParser.parseRelationMerging(
                            dedupeRelation,
                            task.relation(),
                            relationMaps(task.currentRelations())
                    );
                    state.getToRemove().addAll(toRemove);
                } catch (RuntimeException exception) {
                    Loggers.MEMORY.info(
                            "Graph Memory: Failed to parse relation uuids to remove: {}",
                            exception.getMessage()
                    );
                }
                return null;
            }));
        }
        return chain;
    }

    private static CompletableFuture<Void> resolveEntityUuid(GraphStore database,
                                                             GraphMemoryStates.GraphMemState state) {
        List<Object> toResolve = state.getTmpBuffer();
        if (toResolve.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return GraphStoreUtils.ensureUniqueUuids(
                database,
                List.copyOf(toResolve),
                GraphStoreConstants.ENTITY_COLLECTION,
                state.getStrategy().isSkipUuidDedupe()
        ).thenAccept(uniqueUuids -> {
            for (int index = 0; index < Math.min(state.getMemUpdate().getAddedEntity().size(), uniqueUuids.size());
                    index++) {
                state.getMemUpdate().getAddedEntity().get(index).setUuid(String.valueOf(uniqueUuids.get(index)));
            }
        });
    }

    private static List<Entity> concatEntities(List<Entity> left, List<Entity> right) {
        List<Entity> result = new ArrayList<>();
        if (left != null) {
            result.addAll(left);
        }
        if (right != null) {
            result.addAll(right);
        }
        return result;
    }

    private static String contentFrom(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof CharSequence text) {
            return text.toString();
        }
        try {
            Method getter = result.getClass().getDeclaredMethod("getContent");
            getter.setAccessible(true);
            Object value = getter.invoke(result);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field field = result.getClass().getDeclaredField("content");
                field.setAccessible(true);
                Object value = field.get(result);
                return value == null ? null : String.valueOf(value);
            } catch (ReflectiveOperationException exception) {
                return String.valueOf(result);
            }
        }
    }

    private static String removePrefix(String value, String prefix) {
        if (value != null && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value == null ? "" : value;
    }

    private static List<Map<String, Object>> relationMaps(List<Relation> relations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Relation relation : relations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uuid", relation.getUuid());
            map.put("content", relation.getContent());
            map.put("valid_since", relation.getValidSince());
            map.put("valid_until", relation.getValidUntil());
            map.put("offset_since", relation.getOffsetSince());
            map.put("offset_until", relation.getOffsetUntil());
            result.add(map);
        }
        return result;
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Tuple-style relation dedupe task.
     *
     * <p>Mirrors tuple entries passed to Python's {@code parse_relation_uuids_to_remove} in
     * {@code openjiuwen/core/memory/graph/graph_memory/postprocess_graph_objects.py}.</p>
     */
    public record DedupeRelationTask(Relation relation,
                                     List<Relation> currentRelations,
                                     CompletableFuture<?> future) {
        public DedupeRelationTask {
            currentRelations = currentRelations == null ? List.of() : List.copyOf(currentRelations);
        }
    }
}
