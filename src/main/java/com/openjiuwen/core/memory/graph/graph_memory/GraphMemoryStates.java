/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStoreUtils;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.AddMemStrategy;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.ExtractionModels;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * State and helper functions for graph memory updates.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.states} module in
 * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
 */
public final class GraphMemoryStates {

    private GraphMemoryStates() {
    }

    public static void nestedClearDataclass(Object dataObject) {
        if (dataObject == null) {
            return;
        }
        Class<?> current = dataObject.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!field.trySetAccessible()) {
                    continue;
                }
                try {
                    Object value = field.get(dataObject);
                    clearValue(value);
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Unable to clear graph memory state field " + field.getName(),
                            exception);
                }
            }
            current = current.getSuperclass();
        }
    }

    public static void blockKeyboardInterrupt(Runnable runnable) {
        runnable.run();
    }

    public static CompletableFuture<List<BaseGraphObject>> batchEmbed(List<? extends BaseGraphObject> data,
                                                                      Embedding embeddingService,
                                                                      GraphConfig config) {
        if (data == null || data.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<BaseGraphObject.EmbeddingTask> embedTasks = new ArrayList<>();
        for (BaseGraphObject graphObject : data) {
            embedTasks.addAll(graphObject.fetchEmbedTask());
        }
        if (embedTasks.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (embeddingService == null) {
            return CompletableFuture.completedFuture(new ArrayList<>(data));
        }
        List<String> texts = embedTasks.stream()
                .map(BaseGraphObject.EmbeddingTask::contentToEmbed)
                .map(value -> value == null ? "" : String.valueOf(value))
                .toList();
        return embeddingService.embedDocuments(texts, config.getEmbedBatchSize())
                .handle((embeddings, error) -> {
                    if (error != null || embeddings == null) {
                        return new ArrayList<BaseGraphObject>(data);
                    }
                    for (int index = 0; index < Math.min(embedTasks.size(), embeddings.size()); index++) {
                        applyEmbedding(embedTasks.get(index), embeddings.get(index));
                    }
                    return List.<BaseGraphObject>of();
                });
    }

    public static CompletableFuture<Void> persistToDb(GraphStore dbBackend,
                                                      GraphMemState state,
                                                      GraphConfig config) {
        state.getTmpBuffer().clear();
        for (Entity entity : state.getMemUpdateSkipEmbed().getUpdatedEntity()) {
            if (entity.getContentEmbedding() == null || entity.getNameEmbedding() == null) {
                state.getTmpBuffer().add(entity);
                if (!state.getMemUpdate().getUpdatedEntity().contains(entity)) {
                    state.getMemUpdate().getUpdatedEntity().add(entity);
                }
            }
        }
        for (Object entity : state.getTmpBuffer()) {
            while (state.getMemUpdateSkipEmbed().getUpdatedEntity().remove(entity)) {
                // Mirrors Python's repeated remove loop.
            }
        }

        List<BaseGraphObject> graphObjectsToEmbed = new ArrayList<>();
        graphObjectsToEmbed.addAll(state.getMemUpdate().getAddedEntity());
        graphObjectsToEmbed.addAll(state.getMemUpdate().getAddedRelation());
        graphObjectsToEmbed.addAll(state.getMemUpdate().getUpdatedEntity());

        return embedWithRetries(graphObjectsToEmbed, dbBackend.getEmbedder().orElse(null), config,
                config.getRequestMaxRetries(), "Unable to access embedding service")
                .thenCompose(ignored -> embedEpisodesWithRetries(state.getMemUpdate().getAddedEpisode(),
                        dbBackend.getEmbedder().orElse(null), config, config.getRequestMaxRetries()))
                .thenCompose(ignored -> persistMutations(dbBackend, state));
    }

    public static void classifyRelationsExtracted(List<Relation> relations, GraphMemState state) {
        for (EntityMerge mergeInfo : state.getMergeInfos().values()) {
            for (Relation relation : mergeInfo.getNewRelations()) {
                String lhsUuid = graphObjectUuidOrValue(relation.getLhs());
                String rhsUuid = graphObjectUuidOrValue(relation.getRhs());
                if (!lhsUuid.equals(rhsUuid)) {
                    mergeInfo.getRelationsToKeep().add(relation.getUuid());
                } else {
                    state.getMemUpdate().getRemovedRelation().add(relation.getUuid());
                }
            }
            LinkedHashSet<Object> mergedRelations = new LinkedHashSet<>(mergeInfo.getRelationsToKeep());
            mergedRelations.addAll(mergeInfo.getTarget().getRelations());
            mergeInfo.getTarget().setRelations(new ArrayList<>(mergedRelations));
        }

        state.getTmpBuffer().clear();
        for (Relation relation : relations) {
            relation.setLanguage(state.getPrompting().getLanguage());
            if (relation.getContent() == null || relation.getContent().strip().isEmpty()) {
                state.getToRemove().add(relation);
            } else if (Objects.equals(relation.getLhs(), relation.getRhs())) {
                if (relation.getLhs() instanceof Entity entity) {
                    entity.setContent(removeSuffix(entity.getContent(), "\n") + "\n- " + relation.getContent());
                }
                state.getToRemove().add(relation);
            } else {
                state.getTmpBuffer().add(relation.getContent());
            }
        }
    }

    private static CompletableFuture<List<BaseGraphObject>> embedWithRetries(List<BaseGraphObject> data,
                                                                            Embedding embeddingService,
                                                                            GraphConfig config,
                                                                            int retries,
                                                                            String errorMessage) {
        if (retries <= 0) {
            return CompletableFuture.failedFuture(ErrorHelper.buildError(
                    StatusCode.MEMORY_GRAPH_EMBEDDING_CALL_FAILED, "error_msg", errorMessage));
        }
        return batchEmbed(data, embeddingService, config).thenCompose(failed -> {
            if (failed.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }
            return embedWithRetries(failed, embeddingService, config, retries - 1, errorMessage);
        });
    }

    private static CompletableFuture<List<BaseGraphObject>> embedEpisodesWithRetries(List<Episode> episodes,
                                                                                    Embedding embeddingService,
                                                                                    GraphConfig config,
                                                                                    int retries) {
        List<BaseGraphObject> graphObjects = new ArrayList<>(episodes);
        return embedEpisodesWithRetries0(graphObjects, episodes, embeddingService, config, retries);
    }

    private static CompletableFuture<List<BaseGraphObject>> embedEpisodesWithRetries0(List<BaseGraphObject> data,
                                                                                     List<Episode> episodes,
                                                                                     Embedding embeddingService,
                                                                                     GraphConfig config,
                                                                                     int retries) {
        if (retries <= 0) {
            return CompletableFuture.failedFuture(ErrorHelper.buildError(
                    StatusCode.MEMORY_GRAPH_EMBEDDING_CALL_FAILED,
                    "error_msg",
                    "Unable to access embedding service for new episode, maybe exceeding context limit"));
        }
        return batchEmbed(data, embeddingService, config).thenCompose(failed -> {
            if (failed.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }
            if (!episodes.isEmpty()) {
                Episode episode = episodes.get(0);
                String content = episode.getContent();
                episode.setContent(content.substring(0, content.length() / 2));
            }
            return embedEpisodesWithRetries0(failed, episodes, embeddingService, config, retries - 1);
        });
    }

    private static CompletableFuture<Void> persistMutations(GraphStore dbBackend, GraphMemState state) {
        return dbBackend.addEntity(state.getMemUpdate().getAddedEntity(), false, false, true)
                .thenCompose(ignored -> dbBackend.addRelation(state.getMemUpdate().getAddedRelation(),
                        false, false, true))
                .thenCompose(ignored -> dbBackend.addEpisode(state.getMemUpdate().getAddedEpisode(),
                        false, false, true))
                .thenCompose(ignored -> dbBackend.addEntity(state.getMemUpdate().getUpdatedEntity(),
                        false, true, true))
                .thenCompose(ignored -> state.getMemUpdateSkipEmbed().getUpdatedEpisode().isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : dbBackend.addEpisode(state.getMemUpdateSkipEmbed().getUpdatedEpisode(),
                                false, true, true))
                .thenCompose(ignored -> state.getMemUpdateSkipEmbed().getUpdatedEntity().isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : dbBackend.addEntity(state.getMemUpdateSkipEmbed().getUpdatedEntity(),
                                false, true, true))
                .thenCompose(ignored -> state.getMemUpdateSkipEmbed().getUpdatedRelation().isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : dbBackend.addRelation(state.getMemUpdateSkipEmbed().getUpdatedRelation(),
                                false, true, true))
                .thenCompose(ignored -> state.getMemUpdate().getRemovedEntity().isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : deleteAndIgnore(dbBackend, GraphStoreConstants.ENTITY_COLLECTION,
                                List.copyOf(state.getMemUpdate().getRemovedEntity())))
                .thenCompose(ignored -> state.getMemUpdate().getRemovedRelation().isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : deleteAndIgnore(dbBackend, GraphStoreConstants.RELATION_COLLECTION,
                                List.copyOf(state.getMemUpdate().getRemovedRelation())));
    }

    private static CompletableFuture<Void> deleteAndIgnore(GraphStore dbBackend, String collection, List<?> ids) {
        return dbBackend.delete(collection, ids, null, Map.of()).thenApply(ignored -> null);
    }

    private static void clearValue(Object value) {
        if (value instanceof ClearableState clearable) {
            clearable.clear();
        } else if (value instanceof Map<?, ?> map) {
            map.clear();
        } else if (value instanceof Collection<?> collection) {
            collection.clear();
        } else {
            Method clearMethod = findClearMethod(value);
            if (clearMethod != null) {
                try {
                    clearMethod.invoke(value);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to clear graph memory state value", exception);
                }
            }
        }
    }

    private static Method findClearMethod(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Method method = value.getClass().getMethod("clear");
            if (method.getParameterCount() == 0 && method.trySetAccessible()) {
                return method;
            }
        } catch (NoSuchMethodException ignored) {
            return null;
        }
        return null;
    }

    private static void applyEmbedding(BaseGraphObject.EmbeddingTask task, List<Double> embedding) {
        switch (task.attributeName()) {
            case "content_embedding" -> task.graphObject().setContentEmbedding(embedding);
            case "name_embedding" -> {
                if (task.graphObject() instanceof Entity entity) {
                    entity.setNameEmbedding(embedding);
                }
            }
            default -> {
            }
        }
    }

    private static String graphObjectUuidOrValue(Object value) {
        return value instanceof BaseGraphObject graphObject ? graphObject.getUuid() : String.valueOf(value);
    }

    private static String removeSuffix(String value, String suffix) {
        if (value != null && value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length());
        }
        return value == null ? "" : value;
    }

    private static Entity entityFromMap(Map<String, Object> input) {
        Entity entity = new Entity();
        populateBaseGraphObject(entity, input);
        if (input.containsKey("name")) {
            entity.setName(String.valueOf(input.get("name")));
        }
        return entity;
    }

    private static Relation relationFromMap(Map<String, Object> input) {
        Relation relation = new Relation();
        populateBaseGraphObject(relation, input);
        if (input.containsKey("name")) {
            relation.setName(String.valueOf(input.get("name")));
        }
        if (input.containsKey("valid_since")) {
            relation.setValidSince(longValue(input.get("valid_since")));
        }
        if (input.containsKey("valid_until")) {
            relation.setValidUntil(longValue(input.get("valid_until")));
        }
        relation.setLhs(input.get("lhs"));
        relation.setRhs(input.get("rhs"));
        return relation;
    }

    private static Episode episodeFromMap(Map<String, Object> input) {
        Episode episode = new Episode();
        populateBaseGraphObject(episode, input);
        if (input.containsKey("valid_since")) {
            episode.setValidSince(longValue(input.get("valid_since")));
        }
        return episode;
    }

    private static void populateBaseGraphObject(BaseGraphObject object, Map<String, Object> input) {
        if (input.containsKey("uuid")) {
            object.setUuid(String.valueOf(input.get("uuid")));
        }
        if (input.containsKey("created_at")) {
            object.setCreatedAt(longValue(input.get("created_at")));
        }
        if (input.containsKey("user_id")) {
            object.setUserId(String.valueOf(input.get("user_id")));
        }
        if (input.containsKey("obj_type")) {
            object.setObjType(String.valueOf(input.get("obj_type")));
        }
        if (input.containsKey("language")) {
            object.setLanguage(String.valueOf(input.get("language")));
        }
        if (input.containsKey("content")) {
            object.setContent(String.valueOf(input.get("content")));
        }
        if (input.get("metadata") instanceof Map<?, ?> metadata) {
            object.setMetadata(stringObjectMap(metadata));
        }
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return copy;
    }

    private interface ClearableState {
        void clear();
    }

    /**
     * Lookup tables for UUID-to-entity/relation/episode identity preservation.
     *
     * <p>Mirrors Python's {@code LookupTables} in
     * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
     */
    public static final class LookupTables implements ClearableState {
        private final Map<String, Entity> entities = new LinkedHashMap<>();
        private final Map<String, Relation> relations = new LinkedHashMap<>();
        private final Map<String, Episode> episodes = new LinkedHashMap<>();

        public Entity getEntity(Map<String, Object> inputObject) {
            String entityId = String.valueOf(inputObject.get("uuid"));
            return entities.computeIfAbsent(entityId, ignored -> entityFromMap(inputObject));
        }

        public Relation getRelation(Map<String, Object> inputObject) {
            String relationId = String.valueOf(inputObject.get("uuid"));
            return relations.computeIfAbsent(relationId, ignored -> relationFromMap(inputObject));
        }

        public Episode getEpisode(Map<String, Object> inputObject) {
            String episodeId = String.valueOf(inputObject.get("uuid"));
            return episodes.computeIfAbsent(episodeId, ignored -> episodeFromMap(inputObject));
        }

        public Map<String, Entity> getEntities() {
            return entities;
        }

        public Map<String, Relation> getRelations() {
            return relations;
        }

        public Map<String, Episode> getEpisodes() {
            return episodes;
        }

        @Override
        public void clear() {
            nestedClearDataclass(this);
        }
    }

    /**
     * Entity merge state.
     *
     * <p>Mirrors Python's {@code EntityMerge} in
     * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
     */
    public static final class EntityMerge implements ClearableState {
        private final Entity target;
        private final Map<String, Entity> source = new LinkedHashMap<>();
        private final List<Relation> newRelations = new ArrayList<>();
        private final Set<String> relationsToKeep = new LinkedHashSet<>();

        public EntityMerge(Entity target) {
            this.target = target;
        }

        public Entity getTarget() {
            return target;
        }

        public Map<String, Entity> getSource() {
            return source;
        }

        public List<Relation> getNewRelations() {
            return newRelations;
        }

        public Set<String> getRelationsToKeep() {
            return relationsToKeep;
        }

        @Override
        public void clear() {
            nestedClearDataclass(this);
        }
    }

    /**
     * Accumulated graph memory mutations.
     *
     * <p>Mirrors Python's {@code GraphMemUpdate} in
     * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
     */
    public static final class GraphMemUpdate implements ClearableState {
        private final List<Episode> addedEpisode = new ArrayList<>();
        private final List<Episode> updatedEpisode = new ArrayList<>();
        private final List<Entity> addedEntity = new ArrayList<>();
        private final List<Entity> updatedEntity = new ArrayList<>();
        private final List<Relation> addedRelation = new ArrayList<>();
        private final List<Relation> updatedRelation = new ArrayList<>();
        private final Set<String> removedEntity = new LinkedHashSet<>();
        private final Set<String> removedRelation = new LinkedHashSet<>();

        public GraphMemUpdate merge(GraphMemUpdate other) {
            GraphMemUpdate merged = new GraphMemUpdate();
            merged.addedEpisode.addAll(addedEpisode);
            merged.addedEpisode.addAll(other.addedEpisode);
            merged.updatedEpisode.addAll(updatedEpisode);
            merged.updatedEpisode.addAll(other.updatedEpisode);
            merged.addedEntity.addAll(addedEntity);
            merged.addedEntity.addAll(other.addedEntity);
            merged.updatedEntity.addAll(updatedEntity);
            merged.updatedEntity.addAll(other.updatedEntity);
            merged.addedRelation.addAll(addedRelation);
            merged.addedRelation.addAll(other.addedRelation);
            merged.updatedRelation.addAll(updatedRelation);
            merged.updatedRelation.addAll(other.updatedRelation);
            merged.removedEntity.addAll(removedEntity);
            merged.removedEntity.addAll(other.removedEntity);
            merged.removedRelation.addAll(removedRelation);
            merged.removedRelation.addAll(other.removedRelation);
            return merged;
        }

        public List<Episode> getAddedEpisode() {
            return addedEpisode;
        }

        public List<Episode> getUpdatedEpisode() {
            return updatedEpisode;
        }

        public List<Entity> getAddedEntity() {
            return addedEntity;
        }

        public List<Entity> getUpdatedEntity() {
            return updatedEntity;
        }

        public List<Relation> getAddedRelation() {
            return addedRelation;
        }

        public List<Relation> getUpdatedRelation() {
            return updatedRelation;
        }

        public Set<String> getRemovedEntity() {
            return removedEntity;
        }

        public Set<String> getRemovedRelation() {
            return removedRelation;
        }

        @Override
        public void clear() {
            nestedClearDataclass(this);
        }
    }

    /**
     * Prompt schema and language state for graph memory.
     *
     * <p>Mirrors Python's {@code GraphMemPrompting} in
     * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
     */
    public static final class GraphMemPrompting implements ClearableState {
        private Map<String, Object> schemaEntityExtraction = new ExtractionModels.EntitySummary().responseFormat("cn");
        private Map<String, Object> schemaEntityDedupe = new ExtractionModels.EntityDuplication().responseFormat("cn");
        private Map<String, Object> schemaRelationMerge = new ExtractionModels.MergeRelations().responseFormat("cn");
        private Map<String, Object> schemaRelationFilter = new ExtractionModels.RelevantFacts().responseFormat("cn");
        private String language = "cn";
        private String entityExtractionLanguage = "cn";
        private String relationExtractionLanguage = "cn";
        private String entityDedupeLanguage = "cn";

        public Map<String, Object> getSchemaEntityExtraction() {
            return schemaEntityExtraction;
        }

        public Map<String, Object> getSchemaEntityDedupe() {
            return schemaEntityDedupe;
        }

        public Map<String, Object> getSchemaRelationMerge() {
            return schemaRelationMerge;
        }

        public Map<String, Object> getSchemaRelationFilter() {
            return schemaRelationFilter;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getEntityExtractionLanguage() {
            return entityExtractionLanguage;
        }

        public void setEntityExtractionLanguage(String entityExtractionLanguage) {
            this.entityExtractionLanguage = entityExtractionLanguage;
        }

        public String getRelationExtractionLanguage() {
            return relationExtractionLanguage;
        }

        public void setRelationExtractionLanguage(String relationExtractionLanguage) {
            this.relationExtractionLanguage = relationExtractionLanguage;
        }

        public String getEntityDedupeLanguage() {
            return entityDedupeLanguage;
        }

        public void setEntityDedupeLanguage(String entityDedupeLanguage) {
            this.entityDedupeLanguage = entityDedupeLanguage;
        }

        @Override
        public void clear() {
            nestedClearDataclass(this);
        }
    }

    /**
     * Current graph memory addition state.
     *
     * <p>Mirrors Python's {@code GraphMemState} in
     * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
     */
    public static final class GraphMemState implements ClearableState {
        private final List<CompletableFuture<?>> tasks = new ArrayList<>();
        private final List<CompletableFuture<?>> mergingTasks = new ArrayList<>();
        private final Map<CompletableFuture<?>, Entity> mergingTasksEntities = new LinkedHashMap<>();
        private final Map<String, CompletableFuture<?>> pendingMerge = new LinkedHashMap<>();
        private final Map<String, List<RelationDeferredUpdate>> relationDeferredUpdates = new LinkedHashMap<>();
        private final Map<CompletableFuture<?>, RelationFilterTask> relationFilterTasks = new LinkedHashMap<>();
        private final List<Object> toRemove = new ArrayList<>();
        private final List<Object> tmpBuffer = new ArrayList<>();
        private final List<Entity> updatedEntitiesInCurrentEp = new ArrayList<>();
        private final Map<String, Entity> retrievedEntities = new LinkedHashMap<>();
        private final Map<String, Relation> retrievedRelations = new LinkedHashMap<>();
        private final Map<String, Relation> faultyRelations = new LinkedHashMap<>();
        private final Map<String, EntityMerge> mergeInfos = new LinkedHashMap<>();
        private final GraphMemUpdate memUpdate = new GraphMemUpdate();
        private final GraphMemUpdate memUpdateSkipEmbed = new GraphMemUpdate();
        private long currentTimestamp = GraphStoreUtils.getCurrentUtcTimestamp();
        private long referenceTimestamp;
        private final LookupTables lookupTable = new LookupTables();
        private final Map<String, Object> extras = new LinkedHashMap<>();
        private AddMemStrategy strategy = new AddMemStrategy();
        private GraphMemPrompting prompting = new GraphMemPrompting();
        private final List<EntityTypeDefinition.EntityDef> entityTypes = new ArrayList<>();
        private EpisodeType episodeType = EpisodeType.CONVERSATION;
        private String content = "";
        private String history = "";

        public void clearReferences() {
            for (EntityMerge mergeInfo : mergeInfos.values()) {
                nestedClearDataclass(mergeInfo);
            }
            nestedClearDataclass(this);
        }

        public List<CompletableFuture<?>> getTasks() {
            return tasks;
        }

        public List<CompletableFuture<?>> getMergingTasks() {
            return mergingTasks;
        }

        public Map<CompletableFuture<?>, Entity> getMergingTasksEntities() {
            return mergingTasksEntities;
        }

        public Map<String, CompletableFuture<?>> getPendingMerge() {
            return pendingMerge;
        }

        public Map<String, List<RelationDeferredUpdate>> getRelationDeferredUpdates() {
            return relationDeferredUpdates;
        }

        public Map<CompletableFuture<?>, RelationFilterTask> getRelationFilterTasks() {
            return relationFilterTasks;
        }

        public List<Object> getToRemove() {
            return toRemove;
        }

        public List<Object> getTmpBuffer() {
            return tmpBuffer;
        }

        public List<Entity> getUpdatedEntitiesInCurrentEp() {
            return updatedEntitiesInCurrentEp;
        }

        public Map<String, Entity> getRetrievedEntities() {
            return retrievedEntities;
        }

        public Map<String, Relation> getRetrievedRelations() {
            return retrievedRelations;
        }

        public Map<String, Relation> getFaultyRelations() {
            return faultyRelations;
        }

        public Map<String, EntityMerge> getMergeInfos() {
            return mergeInfos;
        }

        public GraphMemUpdate getMemUpdate() {
            return memUpdate;
        }

        public GraphMemUpdate getMemUpdateSkipEmbed() {
            return memUpdateSkipEmbed;
        }

        public long getCurrentTimestamp() {
            return currentTimestamp;
        }

        public long getReferenceTimestamp() {
            return referenceTimestamp;
        }

        public void setReferenceTimestamp(long referenceTimestamp) {
            this.referenceTimestamp = referenceTimestamp;
        }

        public LookupTables getLookupTable() {
            return lookupTable;
        }

        public Map<String, Object> getExtras() {
            return extras;
        }

        public AddMemStrategy getStrategy() {
            return strategy;
        }

        public void setStrategy(AddMemStrategy strategy) {
            this.strategy = strategy == null ? new AddMemStrategy() : strategy;
        }

        public GraphMemPrompting getPrompting() {
            return prompting;
        }

        public void setPrompting(GraphMemPrompting prompting) {
            this.prompting = prompting == null ? new GraphMemPrompting() : prompting;
        }

        public List<EntityTypeDefinition.EntityDef> getEntityTypes() {
            return entityTypes;
        }

        public EpisodeType getEpisodeType() {
            return episodeType;
        }

        public void setEpisodeType(EpisodeType episodeType) {
            this.episodeType = episodeType;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content == null ? "" : content;
        }

        public String getHistory() {
            return history;
        }

        public void setHistory(String history) {
            this.history = history == null ? "" : history;
        }

        @Override
        public void clear() {
            clearReferences();
        }
    }

    /**
     * Tuple-style relation deferred update.
     *
     * <p>Mirrors tuple entries in Python's {@code GraphMemState.relation_deferred_updates} in
     * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
     */
    public record RelationDeferredUpdate(Relation relation, String lhsUuid, String rhsUuid) {
    }

    /**
     * Tuple-style relation filter task payload.
     *
     * <p>Mirrors tuple entries in Python's {@code GraphMemState.relation_filter_tasks} in
     * {@code openjiuwen/core/memory/graph/graph_memory/states.py}.</p>
     */
    public record RelationFilterTask(Entity entity, List<Relation> relations) {
    }
}
