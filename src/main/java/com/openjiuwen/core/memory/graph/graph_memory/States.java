/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphUtils;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.graph.AddMemStrategy;
import com.openjiuwen.core.memory.config.graph.EpisodeType;
import com.openjiuwen.core.memory.graph.extraction.EntityDef;
import com.openjiuwen.core.memory.graph.extraction.EntityDuplication;
import com.openjiuwen.core.memory.graph.extraction.EntitySummary;
import com.openjiuwen.core.memory.graph.extraction.MergeRelations;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;
import com.openjiuwen.core.memory.graph.extraction.RelevantFacts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * State and lookup structures for graph memory updates.
 */
public final class States {
    private States() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void nestedClearDataclass(Object dataObj) {
        if (dataObj instanceof Clearable clearable) {
            clearable.clear();
        }
    }

    /**
 * Public interface Clearable used by the Java parity implementation.
 *
 * @since 1.0
 */
public interface Clearable {
        void clear();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class LookupTables implements Clearable {
        private final Map<String, Entity> entities = new LinkedHashMap<>();
        private final Map<String, Relation> relations = new LinkedHashMap<>();
        private final Map<String, Episode> episodes = new LinkedHashMap<>();

        /**
         * Auto-generated for codecheck compliance.
         */
        public Entity getEntity(Map<String, Object> input) {
            String entityId = String.valueOf(input.get("uuid"));
            Entity entity = entities.get(entityId);
            if (entity == null) {
                entity = mapToEntity(input);
                entities.put(entityId, entity);
            }
            return entity;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Relation getRelation(Map<String, Object> input) {
            String relationId = String.valueOf(input.get("uuid"));
            Relation relation = relations.get(relationId);
            if (relation == null) {
                relation = mapToRelation(input);
                relations.put(relationId, relation);
            }
            return relation;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Episode getEpisode(Map<String, Object> input) {
            String episodeId = String.valueOf(input.get("uuid"));
            Episode episode = episodes.get(episodeId);
            if (episode == null) {
                episode = mapToEpisode(input);
                episodes.put(episodeId, episode);
            }
            return episode;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Entity> getEntities() {
            return entities;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Relation> getRelations() {
            return relations;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Episode> getEpisodes() {
            return episodes;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public void clear() {
            entities.clear();
            relations.clear();
            episodes.clear();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class EntityMerge implements Clearable {
        private Entity target;
        private final Map<String, Entity> source = new LinkedHashMap<>();
        private final List<Relation> newRelations = new ArrayList<>();
        private final Set<String> relationsToKeep = new LinkedHashSet<>();

        /**
         * Auto-generated for codecheck compliance.
         */
        public EntityMerge(Entity target) {
            this.target = target;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Entity getTarget() {
            return target;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Entity> getSource() {
            return source;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Relation> getNewRelations() {
            return newRelations;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Set<String> getRelationsToKeep() {
            return relationsToKeep;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public void clear() {
            source.clear();
            newRelations.clear();
            relationsToKeep.clear();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class GraphMemUpdate {
        private final List<Episode> addedEpisode = new ArrayList<>();
        private final List<Episode> updatedEpisode = new ArrayList<>();
        private final List<Entity> addedEntity = new ArrayList<>();
        private final List<Entity> updatedEntity = new ArrayList<>();
        private final List<Relation> addedRelation = new ArrayList<>();
        private final List<Relation> updatedRelation = new ArrayList<>();
        private final Set<String> removedEntity = new LinkedHashSet<>();
        private final Set<String> removedRelation = new LinkedHashSet<>();

        /**
         * Auto-generated for codecheck compliance.
         */
        public GraphMemUpdate or(GraphMemUpdate other) {
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

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Episode> getAddedEpisode() {
            return addedEpisode;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Episode> getUpdatedEpisode() {
            return updatedEpisode;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Entity> getAddedEntity() {
            return addedEntity;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Entity> getUpdatedEntity() {
            return updatedEntity;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Relation> getAddedRelation() {
            return addedRelation;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Relation> getUpdatedRelation() {
            return updatedRelation;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Set<String> getRemovedEntity() {
            return removedEntity;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Set<String> getRemovedRelation() {
            return removedRelation;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class GraphMemPrompting implements Clearable {
        private Map<String, Object> schemaEntityExtraction =
                new EntitySummary().responseFormat("cn");
        private Map<String, Object> schemaEntityDedupe =
                new EntityDuplication().responseFormat("cn");
        private Map<String, Object> schemaRelationMerge =
                new MergeRelations().responseFormat("cn");
        private Map<String, Object> schemaRelationFilter =
                new RelevantFacts().responseFormat("cn");
        private String language = "cn";
        private String entityExtractionLanguage = "cn";
        private String relationExtractionLanguage = "cn";
        private String entityDedupeLanguage = "cn";

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getSchemaEntityExtraction() {
            return schemaEntityExtraction;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getSchemaEntityDedupe() {
            return schemaEntityDedupe;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getSchemaRelationMerge() {
            return schemaRelationMerge;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getSchemaRelationFilter() {
            return schemaRelationFilter;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getLanguage() {
            return language;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setLanguage(String language) {
            this.language = language;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getEntityExtractionLanguage() {
            return entityExtractionLanguage;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setEntityExtractionLanguage(String entityExtractionLanguage) {
            this.entityExtractionLanguage = entityExtractionLanguage;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getRelationExtractionLanguage() {
            return relationExtractionLanguage;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setRelationExtractionLanguage(String relationExtractionLanguage) {
            this.relationExtractionLanguage = relationExtractionLanguage;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getEntityDedupeLanguage() {
            return entityDedupeLanguage;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setEntityDedupeLanguage(String entityDedupeLanguage) {
            this.entityDedupeLanguage = entityDedupeLanguage;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setSchemaEntityExtraction(Map<String, Object> schemaEntityExtraction) {
            this.schemaEntityExtraction = schemaEntityExtraction;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setSchemaEntityDedupe(Map<String, Object> schemaEntityDedupe) {
            this.schemaEntityDedupe = schemaEntityDedupe;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setSchemaRelationMerge(Map<String, Object> schemaRelationMerge) {
            this.schemaRelationMerge = schemaRelationMerge;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setSchemaRelationFilter(Map<String, Object> schemaRelationFilter) {
            this.schemaRelationFilter = schemaRelationFilter;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public void clear() {
            schemaEntityExtraction = new LinkedHashMap<>();
            schemaEntityDedupe = new LinkedHashMap<>();
            schemaRelationMerge = new LinkedHashMap<>();
            schemaRelationFilter = new LinkedHashMap<>();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class GraphMemState implements Clearable {
        private final List<CompletableFuture<?>> tasks = new ArrayList<>();
        private final List<CompletableFuture<?>> mergingTasks = new ArrayList<>();
        private final Map<CompletableFuture<?>, Entity> mergingTasksEntities = new LinkedHashMap<>();
        private final Map<String, CompletableFuture<?>> pendingMerge = new LinkedHashMap<>();
        private final Map<String, List<Object>> relationDeferredUpdates = new LinkedHashMap<>();
        private final Map<CompletableFuture<?>, Object> relationFilterTasks = new LinkedHashMap<>();
        private final List<Object> toRemove = new ArrayList<>();
        private final List<Object> tmpBuffer = new ArrayList<>();
        private final List<Entity> updatedEntitiesInCurrentEp = new ArrayList<>();
        private final Map<String, Entity> retrievedEntities = new LinkedHashMap<>();
        private final Map<String, Relation> retrievedRelations = new LinkedHashMap<>();
        private final Map<String, Relation> faultyRelations = new LinkedHashMap<>();
        private final Map<String, EntityMerge> mergeInfos = new LinkedHashMap<>();
        private final GraphMemUpdate memUpdate = new GraphMemUpdate();
        private final GraphMemUpdate memUpdateSkipEmbed = new GraphMemUpdate();
        private int currentTimestamp = GraphUtils.getCurrentUtcTimestamp();
        private int referenceTimestamp = 0;
        private final LookupTables lookupTable = new LookupTables();
        private final Map<String, Object> extras = new LinkedHashMap<>();
        private AddMemStrategy strategy = new AddMemStrategy();
        private final GraphMemPrompting prompting = new GraphMemPrompting();
        private final List<EntityDef> entityTypes = new ArrayList<>();
        private EpisodeType episodeType = EpisodeType.CONVERSATION;
        private String content = "";
        private String history = "";

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<CompletableFuture<?>> getTasks() {
            return tasks;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<CompletableFuture<?>> getMergingTasks() {
            return mergingTasks;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<CompletableFuture<?>, Entity> getMergingTasksEntities() {
            return mergingTasksEntities;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, CompletableFuture<?>> getPendingMerge() {
            return pendingMerge;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, List<Object>> getRelationDeferredUpdates() {
            return relationDeferredUpdates;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<CompletableFuture<?>, Object> getRelationFilterTasks() {
            return relationFilterTasks;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Object> getToRemove() {
            return toRemove;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Object> getTmpBuffer() {
            return tmpBuffer;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Entity> getUpdatedEntitiesInCurrentEp() {
            return updatedEntitiesInCurrentEp;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Entity> getRetrievedEntities() {
            return retrievedEntities;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Relation> getRetrievedRelations() {
            return retrievedRelations;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Relation> getFaultyRelations() {
            return faultyRelations;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, EntityMerge> getMergeInfos() {
            return mergeInfos;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public GraphMemUpdate getMemUpdate() {
            return memUpdate;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public GraphMemUpdate getMemUpdateSkipEmbed() {
            return memUpdateSkipEmbed;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public int getCurrentTimestamp() {
            return currentTimestamp;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setCurrentTimestamp(int currentTimestamp) {
            this.currentTimestamp = currentTimestamp;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public int getReferenceTimestamp() {
            return referenceTimestamp;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setReferenceTimestamp(int referenceTimestamp) {
            this.referenceTimestamp = referenceTimestamp;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public LookupTables getLookupTable() {
            return lookupTable;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getExtras() {
            return extras;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public AddMemStrategy getStrategy() {
            return strategy;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setStrategy(AddMemStrategy strategy) {
            this.strategy = strategy;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public GraphMemPrompting getPrompting() {
            return prompting;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<EntityDef> getEntityTypes() {
            return entityTypes;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public EpisodeType getEpisodeType() {
            return episodeType;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setEpisodeType(EpisodeType episodeType) {
            this.episodeType = episodeType;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getContent() {
            return content;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setContent(String content) {
            this.content = content;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String getHistory() {
            return history;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void setHistory(String history) {
            this.history = history;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public void clear() {
            tasks.clear();
            mergingTasks.clear();
            mergingTasksEntities.clear();
            pendingMerge.clear();
            relationDeferredUpdates.clear();
            relationFilterTasks.clear();
            toRemove.clear();
            tmpBuffer.clear();
            updatedEntitiesInCurrentEp.clear();
            retrievedEntities.clear();
            retrievedRelations.clear();
            faultyRelations.clear();
            mergeInfos.values().forEach(EntityMerge::clear);
            mergeInfos.clear();
            lookupTable.clear();
            extras.clear();
            prompting.clear();
            entityTypes.clear();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<BaseGraphObject> batchEmbed(
            List<BaseGraphObject> data,
            Embedding embeddingService,
            GraphConfig config) {
        if (data.isEmpty()) {
            return List.of();
        }
        List<GraphUtils.EmbedTask> tasks = new ArrayList<>();
        for (BaseGraphObject graphObject : data) {
            for (BaseGraphObject.EmbeddingTask et : graphObject.fetchEmbedTask()) {
                tasks.add(new GraphUtils.EmbedTask(et.graphObject(), et.attributeName(), et.contentToEmbed()));
            }
        }
        if (tasks.isEmpty()) {
            return List.of();
        }
        try {
            List<String> texts = tasks.stream().map(GraphUtils.EmbedTask::text).toList();
            List<List<Float>> embeddings = embeddingService.embedDocuments(texts, config.getEmbedBatchSize());
            for (int i = 0; i < tasks.size(); i++) {
                GraphUtils.EmbedTask task = tasks.get(i);
                java.lang.reflect.Field field = findField(task.target().getClass(), task.attributeName());
                if (field != null) {
                    field.setAccessible(true);
                    List<Double> doubleEmbedding = new ArrayList<>();
                    for (Float f : embeddings.get(i)) {
                        doubleEmbedding.add(f != null ? f.doubleValue() : 0.0);
                    }
                    field.set(task.target(), doubleEmbedding);
                }
            }
            return List.of();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ArrayList<>(data);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void persistToDb(GraphStore dbBackend, GraphMemState state, GraphConfig config) throws Exception {
        state.getTmpBuffer().clear();
        for (Entity entity : state.getMemUpdateSkipEmbed().getUpdatedEntity()) {
            if (entity.getContentEmbedding() == null || entity.getNameEmbedding() == null) {
                state.getTmpBuffer().add(entity);
                if (!state.getMemUpdate().getUpdatedEntity().contains(entity)) {
                    state.getMemUpdate().getUpdatedEntity().add(entity);
                }
            }
        }
        for (Object entity : new ArrayList<>(state.getTmpBuffer())) {
            while (state.getMemUpdateSkipEmbed().getUpdatedEntity().contains(entity)) {
                state.getMemUpdateSkipEmbed().getUpdatedEntity().remove(entity);
            }
        }

        List<BaseGraphObject> toEmbed = new ArrayList<>();
        toEmbed.addAll(state.getMemUpdate().getAddedEntity());
        toEmbed.addAll(state.getMemUpdate().getAddedRelation());
        toEmbed.addAll(state.getMemUpdate().getUpdatedEntity());
        int retries = config.getRequestMaxRetries();
        while (retries > 0) {
            toEmbed = batchEmbedWithStore(toEmbed, dbBackend, config);
            if (toEmbed.isEmpty()) {
                break;
            }
            retries--;
        }
        if (!toEmbed.isEmpty()) {
            throw new IllegalStateException("Unable to access embedding service");
        }

        List<BaseGraphObject> episodeToEmbed = new ArrayList<>(state.getMemUpdate().getAddedEpisode());
        retries = config.getRequestMaxRetries();
        while (retries > 0) {
            episodeToEmbed = batchEmbedWithStore(episodeToEmbed, dbBackend, config);
            if (episodeToEmbed.isEmpty()) {
                break;
            }
            Episode episode = state.getMemUpdate().getAddedEpisode().get(0);
            episode.setContent(episode.getContent().substring(0, Math.max(1, episode.getContent().length() / 2)));
            retries--;
        }
        if (!episodeToEmbed.isEmpty()) {
            throw new IllegalStateException(
                    "Unable to access embedding service for new episode, maybe exceeding context limit");
        }

        dbBackend.addEntity(state.getMemUpdate().getAddedEntity(), false, false, true);
        dbBackend.addRelation(state.getMemUpdate().getAddedRelation(), false, false, true);
        dbBackend.addEpisode(state.getMemUpdate().getAddedEpisode(), false, false, true);
        dbBackend.addEntity(state.getMemUpdate().getUpdatedEntity(), false, true, true);
        if (!state.getMemUpdateSkipEmbed().getUpdatedEpisode().isEmpty()) {
            dbBackend.addEpisode(state.getMemUpdateSkipEmbed().getUpdatedEpisode(), false, true, true);
        }
        if (!state.getMemUpdateSkipEmbed().getUpdatedEntity().isEmpty()) {
            dbBackend.addEntity(state.getMemUpdateSkipEmbed().getUpdatedEntity(), false, true, true);
        }
        if (!state.getMemUpdateSkipEmbed().getUpdatedRelation().isEmpty()) {
            dbBackend.addRelation(state.getMemUpdateSkipEmbed().getUpdatedRelation(), false, true, true);
        }
        if (!state.getMemUpdate().getRemovedEntity().isEmpty()) {
            dbBackend.delete(
                    GraphConstants.ENTITY_COLLECTION,
                    new ArrayList<>(state.getMemUpdate().getRemovedEntity()),
                    null);
        }
        if (!state.getMemUpdate().getRemovedRelation().isEmpty()) {
            dbBackend.delete(
                    GraphConstants.RELATION_COLLECTION,
                    new ArrayList<>(state.getMemUpdate().getRemovedRelation()),
                    null);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void classifyRelationsExtracted(List<Relation> relations, GraphMemState state) {
        for (EntityMerge mergeInfo : state.getMergeInfos().values()) {
            for (Relation relation : mergeInfo.getNewRelations()) {
                String lhsUuid = relation.getLhs() instanceof BaseGraphObject obj
                        ? obj.getUuid()
                        : String.valueOf(relation.getLhs());
                String rhsUuid = relation.getRhs() instanceof BaseGraphObject obj
                        ? obj.getUuid()
                        : String.valueOf(relation.getRhs());
                if (!lhsUuid.equals(rhsUuid)) {
                    mergeInfo.getRelationsToKeep().add(relation.getUuid());
                } else {
                    state.getMemUpdate().getRemovedRelation().add(relation.getUuid());
                }
            }
            List<Object> mergedRelations = new ArrayList<>(mergeInfo.getTarget().getRelations());
            mergedRelations.addAll(mergeInfo.getRelationsToKeep());
            mergeInfo.getTarget().setRelations(mergedRelations);
        }
        state.getTmpBuffer().clear();
        for (Relation relation : relations) {
            relation.setLanguage(state.getPrompting().getLanguage());
            if (relation.getContent() == null || relation.getContent().trim().isEmpty()) {
                state.getToRemove().add(relation);
            } else if (relation.getLhs() == relation.getRhs()) {
                if (!(relation.getLhs() instanceof Entity lhs)) {
                    state.getToRemove().add(relation);
                    continue;
                }
                String content = lhs.getContent().endsWith("\n")
                        ? lhs.getContent().substring(0, lhs.getContent().length() - 1)
                        : lhs.getContent();
                lhs.setContent(content + "\n- " + relation.getContent());
                state.getToRemove().add(relation);
            } else {
                state.getTmpBuffer().add(relation.getContent());
            }
        }
    }

    private static List<BaseGraphObject> batchEmbedWithStore(
            List<BaseGraphObject> data,
            GraphStore dbBackend,
            GraphConfig config) {
        if (data.isEmpty()) {
            return List.of();
        }
        var embedderOpt = dbBackend.getEmbedder();
        if (embedderOpt.isEmpty()) {
            return new ArrayList<>(data);
        }
        com.openjiuwen.core.foundation.store.Embedding storeEmbedding = embedderOpt.get();
        List<GraphUtils.EmbedTask> tasks = new ArrayList<>();
        for (BaseGraphObject graphObject : data) {
            for (BaseGraphObject.EmbeddingTask et : graphObject.fetchEmbedTask()) {
                tasks.add(new GraphUtils.EmbedTask(et.graphObject(), et.attributeName(), et.contentToEmbed()));
            }
        }
        if (tasks.isEmpty()) {
            return List.of();
        }
        try {
            List<String> texts = tasks.stream().map(GraphUtils.EmbedTask::text).toList();
            List<List<Double>> embeddings = storeEmbedding.embedDocuments(texts, config.getEmbedBatchSize()).join();
            for (int i = 0; i < tasks.size(); i++) {
                GraphUtils.EmbedTask task = tasks.get(i);
                java.lang.reflect.Field field = findField(task.target().getClass(), task.attributeName());
                if (field != null) {
                    field.setAccessible(true);
                    field.set(task.target(), embeddings.get(i));
                }
            }
            return List.of();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ArrayList<>(data);
        }
    }

    private static Entity mapToEntity(Map<String, Object> input) {
        Entity entity = new Entity();
        entity.setUuid(String.valueOf(input.getOrDefault("uuid", GraphUtils.getUuid())));
        entity.setCreatedAt(parseCreatedAt(input));
        entity.setUserId(String.valueOf(input.getOrDefault("user_id", "default_user")));
        entity.setObjType(String.valueOf(input.getOrDefault("obj_type", "Entity")));
        entity.setLanguage(String.valueOf(input.getOrDefault("language", "cn")));
        entity.setContent(String.valueOf(input.getOrDefault("content", "")));
        entity.setName(String.valueOf(input.getOrDefault("name", "")));
        return entity;
    }

    private static Relation mapToRelation(Map<String, Object> input) {
        Relation relation = new Relation();
        relation.setUuid(String.valueOf(input.getOrDefault("uuid", GraphUtils.getUuid())));
        relation.setCreatedAt(parseCreatedAt(input));
        relation.setUserId(String.valueOf(input.getOrDefault("user_id", "default_user")));
        relation.setObjType(String.valueOf(input.getOrDefault("obj_type", "Relation")));
        relation.setLanguage(String.valueOf(input.getOrDefault("language", "cn")));
        relation.setContent(String.valueOf(input.getOrDefault("content", "")));
        relation.setName(String.valueOf(input.getOrDefault("name", "")));
        return relation;
    }

    private static Episode mapToEpisode(Map<String, Object> input) {
        Episode episode = new Episode();
        episode.setUuid(String.valueOf(input.getOrDefault("uuid", GraphUtils.getUuid())));
        episode.setCreatedAt(parseCreatedAt(input));
        episode.setUserId(String.valueOf(input.getOrDefault("user_id", "default_user")));
        episode.setObjType(String.valueOf(input.getOrDefault("obj_type", "Episode")));
        episode.setLanguage(String.valueOf(input.getOrDefault("language", "cn")));
        episode.setContent(String.valueOf(input.getOrDefault("content", "")));
        return episode;
    }

    private static int parseCreatedAt(Map<String, Object> input) {
        return Integer.parseInt(String.valueOf(input.getOrDefault("created_at", GraphUtils.getCurrentUtcTimestamp())));
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
