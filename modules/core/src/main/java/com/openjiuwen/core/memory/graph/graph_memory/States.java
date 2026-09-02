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
 * 
 * @since 0.1.7
 */
public final class States {
    /**
     * States.
     * 
     * @since 0.1.7
     */
    private States() {
    }

    /**
     * nestedClearDataclass.
     * 
     * @param dataObj dataObj
     * @since 0.1.7
     */
    public static void nestedClearDataclass(Object dataObj) {
        if (dataObj instanceof Clearable clearable) {
            clearable.clear();
        }
    }

    /**
     * Public interface Clearable used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public interface Clearable {
        /**
         * clear.
         * 
         * @since 0.1.7
         */
        void clear();
    }

    /**
     * LookupTables.
     * 
     * @since 0.1.7
     */
    public static final class LookupTables implements Clearable {
        private final Map<String, Entity> entities = new LinkedHashMap<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, Relation> relations = new LinkedHashMap<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, Episode> episodes = new LinkedHashMap<>();

        /**
         * getEntity.
         * 
         * @param input input
         * @return the result
         * @since 0.1.7
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
         * getRelation.
         * 
         * @param input input
         * @return the result
         * @since 0.1.7
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
         * getEpisode.
         * 
         * @param input input
         * @return the result
         * @since 0.1.7
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
         * getEntities.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Entity> getEntities() {
            return entities;
        }

        /**
         * getRelations.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Relation> getRelations() {
            return relations;
        }

        /**
         * getEpisodes.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Episode> getEpisodes() {
            return episodes;
        }

        /**
         * clear.
         * 
         * @since 0.1.7
         */
        @Override
        public void clear() {
            entities.clear();
            relations.clear();
            episodes.clear();
        }
    }

    /**
     * EntityMerge.
     * 
     * @since 0.1.7
     */
    public static final class EntityMerge implements Clearable {
        private Entity target;

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, Entity> source = new LinkedHashMap<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Relation> newRelations = new ArrayList<>();

        /**
         * LinkedHashSet<>.
         * 
         * @since 0.1.7
         */
        private final Set<String> relationsToKeep = new LinkedHashSet<>();

        /**
         * EntityMerge.
         * 
         * @param target target
         * @since 0.1.7
         */
        public EntityMerge(Entity target) {
            this.target = target;
        }

        /**
         * getTarget.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Entity getTarget() {
            return target;
        }

        /**
         * getSource.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Entity> getSource() {
            return source;
        }

        /**
         * getNewRelations.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Relation> getNewRelations() {
            return newRelations;
        }

        /**
         * getRelationsToKeep.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Set<String> getRelationsToKeep() {
            return relationsToKeep;
        }

        /**
         * clear.
         * 
         * @since 0.1.7
         */
        @Override
        public void clear() {
            source.clear();
            newRelations.clear();
            relationsToKeep.clear();
        }
    }

    /**
     * GraphMemUpdate.
     * 
     * @since 0.1.7
     */
    public static final class GraphMemUpdate {
        private final List<Episode> addedEpisode = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Episode> updatedEpisode = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Entity> addedEntity = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Entity> updatedEntity = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Relation> addedRelation = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Relation> updatedRelation = new ArrayList<>();

        /**
         * LinkedHashSet<>.
         * 
         * @since 0.1.7
         */
        private final Set<String> removedEntity = new LinkedHashSet<>();

        /**
         * LinkedHashSet<>.
         * 
         * @since 0.1.7
         */
        private final Set<String> removedRelation = new LinkedHashSet<>();

        /**
         * or.
         * 
         * @param other other
         * @return the result
         * @since 0.1.7
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
         * getAddedEpisode.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Episode> getAddedEpisode() {
            return addedEpisode;
        }

        /**
         * getUpdatedEpisode.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Episode> getUpdatedEpisode() {
            return updatedEpisode;
        }

        /**
         * getAddedEntity.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Entity> getAddedEntity() {
            return addedEntity;
        }

        /**
         * getUpdatedEntity.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Entity> getUpdatedEntity() {
            return updatedEntity;
        }

        /**
         * getAddedRelation.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Relation> getAddedRelation() {
            return addedRelation;
        }

        /**
         * getUpdatedRelation.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Relation> getUpdatedRelation() {
            return updatedRelation;
        }

        /**
         * getRemovedEntity.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Set<String> getRemovedEntity() {
            return removedEntity;
        }

        /**
         * getRemovedRelation.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Set<String> getRemovedRelation() {
            return removedRelation;
        }
    }

    /**
     * GraphMemPrompting.
     * 
     * @since 0.1.7
     */
    public static final class GraphMemPrompting implements Clearable {
        private Map<String, Object> schemaEntityExtraction =
            MultilingualBaseModel.responseFormat(EntitySummary.class, "cn");

        /**
         * MultilingualBaseModel.responseFormat.
         * 
         * @since 0.1.7
         */
        private Map<String, Object> schemaEntityDedupe =
            MultilingualBaseModel.responseFormat(EntityDuplication.class, "cn");

        /**
         * MultilingualBaseModel.responseFormat.
         * 
         * @since 0.1.7
         */
        private Map<String, Object> schemaRelationMerge =
            MultilingualBaseModel.responseFormat(MergeRelations.class, "cn");

        /**
         * MultilingualBaseModel.responseFormat.
         * 
         * @since 0.1.7
         */
        private Map<String, Object> schemaRelationFilter =
            MultilingualBaseModel.responseFormat(RelevantFacts.class, "cn");
        private String language = "cn";
        private String entityExtractionLanguage = "cn";
        private String relationExtractionLanguage = "cn";
        private String entityDedupeLanguage = "cn";

        /**
         * getSchemaEntityExtraction.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Object> getSchemaEntityExtraction() {
            return schemaEntityExtraction;
        }

        /**
         * getSchemaEntityDedupe.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Object> getSchemaEntityDedupe() {
            return schemaEntityDedupe;
        }

        /**
         * getSchemaRelationMerge.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Object> getSchemaRelationMerge() {
            return schemaRelationMerge;
        }

        /**
         * getSchemaRelationFilter.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Object> getSchemaRelationFilter() {
            return schemaRelationFilter;
        }

        /**
         * getLanguage.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getLanguage() {
            return language;
        }

        /**
         * setLanguage.
         * 
         * @param language language
         * @since 0.1.7
         */
        public void setLanguage(String language) {
            this.language = language;
        }

        /**
         * getEntityExtractionLanguage.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getEntityExtractionLanguage() {
            return entityExtractionLanguage;
        }

        /**
         * setEntityExtractionLanguage.
         * 
         * @param entityExtractionLanguage entityExtractionLanguage
         * @since 0.1.7
         */
        public void setEntityExtractionLanguage(String entityExtractionLanguage) {
            this.entityExtractionLanguage = entityExtractionLanguage;
        }

        /**
         * getRelationExtractionLanguage.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getRelationExtractionLanguage() {
            return relationExtractionLanguage;
        }

        /**
         * setRelationExtractionLanguage.
         * 
         * @param relationExtractionLanguage relationExtractionLanguage
         * @since 0.1.7
         */
        public void setRelationExtractionLanguage(String relationExtractionLanguage) {
            this.relationExtractionLanguage = relationExtractionLanguage;
        }

        /**
         * getEntityDedupeLanguage.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getEntityDedupeLanguage() {
            return entityDedupeLanguage;
        }

        /**
         * setEntityDedupeLanguage.
         * 
         * @param entityDedupeLanguage entityDedupeLanguage
         * @since 0.1.7
         */
        public void setEntityDedupeLanguage(String entityDedupeLanguage) {
            this.entityDedupeLanguage = entityDedupeLanguage;
        }

        /**
         * setSchemaEntityExtraction.
         * 
         * @param schemaEntityExtraction schemaEntityExtraction
         * @since 0.1.7
         */
        public void setSchemaEntityExtraction(Map<String, Object> schemaEntityExtraction) {
            this.schemaEntityExtraction = schemaEntityExtraction;
        }

        /**
         * setSchemaEntityDedupe.
         * 
         * @param schemaEntityDedupe schemaEntityDedupe
         * @since 0.1.7
         */
        public void setSchemaEntityDedupe(Map<String, Object> schemaEntityDedupe) {
            this.schemaEntityDedupe = schemaEntityDedupe;
        }

        /**
         * setSchemaRelationMerge.
         * 
         * @param schemaRelationMerge schemaRelationMerge
         * @since 0.1.7
         */
        public void setSchemaRelationMerge(Map<String, Object> schemaRelationMerge) {
            this.schemaRelationMerge = schemaRelationMerge;
        }

        /**
         * setSchemaRelationFilter.
         * 
         * @param schemaRelationFilter schemaRelationFilter
         * @since 0.1.7
         */
        public void setSchemaRelationFilter(Map<String, Object> schemaRelationFilter) {
            this.schemaRelationFilter = schemaRelationFilter;
        }

        /**
         * clear.
         * 
         * @since 0.1.7
         */
        @Override
        public void clear() {
            schemaEntityExtraction = new LinkedHashMap<>();
            schemaEntityDedupe = new LinkedHashMap<>();
            schemaRelationMerge = new LinkedHashMap<>();
            schemaRelationFilter = new LinkedHashMap<>();
        }
    }

    /**
     * GraphMemState.
     * 
     * @since 0.1.7
     */
    public static final class GraphMemState implements Clearable {
        private final List<CompletableFuture<?>> tasks = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<CompletableFuture<?>> mergingTasks = new ArrayList<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<CompletableFuture<?>, Entity> mergingTasksEntities = new LinkedHashMap<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, CompletableFuture<?>> pendingMerge = new LinkedHashMap<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, List<Object>> relationDeferredUpdates = new LinkedHashMap<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<CompletableFuture<?>, Object> relationFilterTasks = new LinkedHashMap<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Object> toRemove = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Object> tmpBuffer = new ArrayList<>();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<Entity> updatedEntitiesInCurrentEp = new ArrayList<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, Entity> retrievedEntities = new LinkedHashMap<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, Relation> retrievedRelations = new LinkedHashMap<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, Relation> faultyRelations = new LinkedHashMap<>();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, EntityMerge> mergeInfos = new LinkedHashMap<>();

        /**
         * GraphMemUpdate.
         * 
         * @since 0.1.7
         */
        private final GraphMemUpdate memUpdate = new GraphMemUpdate();

        /**
         * GraphMemUpdate.
         * 
         * @since 0.1.7
         */
        private final GraphMemUpdate memUpdateSkipEmbed = new GraphMemUpdate();

        /**
         * GraphUtils.getCurrentUtcTimestamp.
         * 
         * @since 0.1.7
         */
        private int currentTimestamp = GraphUtils.getCurrentUtcTimestamp();
        private int referenceTimestamp = 0;

        /**
         * LookupTables.
         * 
         * @since 0.1.7
         */
        private final LookupTables lookupTable = new LookupTables();

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, Object> extras = new LinkedHashMap<>();

        /**
         * AddMemStrategy.
         * 
         * @since 0.1.7
         */
        private AddMemStrategy strategy = new AddMemStrategy();

        /**
         * GraphMemPrompting.
         * 
         * @since 0.1.7
         */
        private final GraphMemPrompting prompting = new GraphMemPrompting();

        /**
         * ArrayList<>.
         * 
         * @since 0.1.7
         */
        private final List<EntityDef> entityTypes = new ArrayList<>();
        private EpisodeType episodeType = EpisodeType.CONVERSATION;
        private String content = "";
        private String history = "";

        /**
         * getTasks.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<CompletableFuture<?>> getTasks() {
            return tasks;
        }

        /**
         * getMergingTasks.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<CompletableFuture<?>> getMergingTasks() {
            return mergingTasks;
        }

        /**
         * getMergingTasksEntities.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<CompletableFuture<?>, Entity> getMergingTasksEntities() {
            return mergingTasksEntities;
        }

        /**
         * getPendingMerge.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, CompletableFuture<?>> getPendingMerge() {
            return pendingMerge;
        }

        /**
         * getRelationDeferredUpdates.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, List<Object>> getRelationDeferredUpdates() {
            return relationDeferredUpdates;
        }

        /**
         * getRelationFilterTasks.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<CompletableFuture<?>, Object> getRelationFilterTasks() {
            return relationFilterTasks;
        }

        /**
         * getToRemove.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Object> getToRemove() {
            return toRemove;
        }

        /**
         * getTmpBuffer.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Object> getTmpBuffer() {
            return tmpBuffer;
        }

        /**
         * getUpdatedEntitiesInCurrentEp.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<Entity> getUpdatedEntitiesInCurrentEp() {
            return updatedEntitiesInCurrentEp;
        }

        /**
         * getRetrievedEntities.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Entity> getRetrievedEntities() {
            return retrievedEntities;
        }

        /**
         * getRetrievedRelations.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Relation> getRetrievedRelations() {
            return retrievedRelations;
        }

        /**
         * getFaultyRelations.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Relation> getFaultyRelations() {
            return faultyRelations;
        }

        /**
         * getMergeInfos.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, EntityMerge> getMergeInfos() {
            return mergeInfos;
        }

        /**
         * getMemUpdate.
         * 
         * @return the result
         * @since 0.1.7
         */
        public GraphMemUpdate getMemUpdate() {
            return memUpdate;
        }

        /**
         * getMemUpdateSkipEmbed.
         * 
         * @return the result
         * @since 0.1.7
         */
        public GraphMemUpdate getMemUpdateSkipEmbed() {
            return memUpdateSkipEmbed;
        }

        /**
         * getCurrentTimestamp.
         * 
         * @return the result
         * @since 0.1.7
         */
        public int getCurrentTimestamp() {
            return currentTimestamp;
        }

        /**
         * setCurrentTimestamp.
         * 
         * @param currentTimestamp currentTimestamp
         * @since 0.1.7
         */
        public void setCurrentTimestamp(int currentTimestamp) {
            this.currentTimestamp = currentTimestamp;
        }

        /**
         * getReferenceTimestamp.
         * 
         * @return the result
         * @since 0.1.7
         */
        public int getReferenceTimestamp() {
            return referenceTimestamp;
        }

        /**
         * setReferenceTimestamp.
         * 
         * @param referenceTimestamp referenceTimestamp
         * @since 0.1.7
         */
        public void setReferenceTimestamp(int referenceTimestamp) {
            this.referenceTimestamp = referenceTimestamp;
        }

        /**
         * getLookupTable.
         * 
         * @return the result
         * @since 0.1.7
         */
        public LookupTables getLookupTable() {
            return lookupTable;
        }

        /**
         * getExtras.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Map<String, Object> getExtras() {
            return extras;
        }

        /**
         * getStrategy.
         * 
         * @return the result
         * @since 0.1.7
         */
        public AddMemStrategy getStrategy() {
            return strategy;
        }

        /**
         * setStrategy.
         * 
         * @param strategy strategy
         * @since 0.1.7
         */
        public void setStrategy(AddMemStrategy strategy) {
            this.strategy = strategy;
        }

        /**
         * getPrompting.
         * 
         * @return the result
         * @since 0.1.7
         */
        public GraphMemPrompting getPrompting() {
            return prompting;
        }

        /**
         * getEntityTypes.
         * 
         * @return the result
         * @since 0.1.7
         */
        public List<EntityDef> getEntityTypes() {
            return entityTypes;
        }

        /**
         * getEpisodeType.
         * 
         * @return the result
         * @since 0.1.7
         */
        public EpisodeType getEpisodeType() {
            return episodeType;
        }

        /**
         * setEpisodeType.
         * 
         * @param episodeType episodeType
         * @since 0.1.7
         */
        public void setEpisodeType(EpisodeType episodeType) {
            this.episodeType = episodeType;
        }

        /**
         * getContent.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getContent() {
            return content;
        }

        /**
         * setContent.
         * 
         * @param content content
         * @since 0.1.7
         */
        public void setContent(String content) {
            this.content = content;
        }

        /**
         * getHistory.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String getHistory() {
            return history;
        }

        /**
         * setHistory.
         * 
         * @param history history
         * @since 0.1.7
         */
        public void setHistory(String history) {
            this.history = history;
        }

        /**
         * clear.
         * 
         * @since 0.1.7
         */
        @Override
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
     * batchEmbed.
     * 
     * @param data data
     * @param embeddingService embeddingService
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static List<BaseGraphObject> batchEmbed(List<BaseGraphObject> data, Embedding embeddingService,
            GraphConfig config) {
        if (data.isEmpty()) {
            return List.of();
        }
        List<GraphUtils.EmbedTask> tasks = new ArrayList<>();
        for (BaseGraphObject graphObject : data) {
            tasks.addAll(graphObject.fetchEmbedTask());
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
                    field.set(task.target(), embeddings.get(i));
                }
            }
            return List.of();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new ArrayList<>(data);
        }
    }

    /**
     * persistToDb.
     * 
     * @param dbBackend dbBackend
     * @param state state
     * @param config config
     * @throws Exception Exception
     * @since 0.1.7
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
            toEmbed = batchEmbed(toEmbed, dbBackend.getEmbedder(), config);
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
            episodeToEmbed = batchEmbed(episodeToEmbed, dbBackend.getEmbedder(), config);
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
            dbBackend.delete(GraphConstants.ENTITY_COLLECTION, new ArrayList<>(state.getMemUpdate().getRemovedEntity()),
                    null);
        }
        if (!state.getMemUpdate().getRemovedRelation().isEmpty()) {
            dbBackend.delete(GraphConstants.RELATION_COLLECTION,
                    new ArrayList<>(state.getMemUpdate().getRemovedRelation()), null);
        }
    }

    /**
     * classifyRelationsExtracted.
     * 
     * @param relations relations
     * @param state state
     * @since 0.1.7
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

    /**
     * mapToEntity.
     * 
     * @param input input
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * mapToRelation.
     * 
     * @param input input
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * mapToEpisode.
     * 
     * @param input input
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * parseCreatedAt.
     * 
     * @param input input
     * @return the result
     * @since 0.1.7
     */
    private static int parseCreatedAt(Map<String, Object> input) {
        return Integer.parseInt(String.valueOf(input.getOrDefault("created_at", GraphUtils.getCurrentUtcTimestamp())));
    }

    /**
     * findField.
     * 
     * @param type type
     * @param name name
     * @return Field
     * @since 0.1.7
     */
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
