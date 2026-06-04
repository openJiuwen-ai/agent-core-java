/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Lookup tables and state structures for graph memory updates.
 * <p>
 * Mirrors Python's state dataclasses from
 * <code>memory/graph/graph_memory/states.py</code>.
 */
public final class GraphMemoryStates {

    private GraphMemoryStates() {}

    public static void nestedClearDataclass(Object dataObj) {
        if (dataObj instanceof LookupTables lookupTables) {
            lookupTables.clear();
        } else if (dataObj instanceof EntityMerge entityMerge) {
            entityMerge.clear();
        } else if (dataObj instanceof GraphMemUpdate update) {
            update.clear();
        } else if (dataObj instanceof GraphMemState state) {
            state.clearReferences();
        }
    }

    /**
     * Lookup Tables for UUID-to-Entity/Relation/Episode.
     */
    public static class LookupTables {
        private final Map<String, Entity> entities = new HashMap<>();
        private final Map<String, Relation> relations = new HashMap<>();
        private final Map<String, Episode> episodes = new HashMap<>();

        public Entity getEntity(Map<String, Object> input) {
            String entityId = stringValue(input.get("uuid"));
            return entities.computeIfAbsent(entityId, k -> {
                Entity e = new Entity();
                e.setUuid(entityId);
                if (input.containsKey("name")) {
                    e.setName(stringValue(input.get("name")));
                }
                if (input.containsKey("content")) {
                    e.setContent(stringValue(input.get("content")));
                }
                if (input.containsKey("user_id")) {
                    e.setUserId(stringValue(input.get("user_id")));
                }
                if (input.containsKey("relations")) {
                    e.setRelations(stringList(input.get("relations")));
                }
                if (input.containsKey("episodes")) {
                    e.setEpisodes(stringList(input.get("episodes")));
                }
                return e;
            });
        }

        public Relation getRelation(Map<String, Object> input) {
            String relationId = stringValue(input.get("uuid"));
            return relations.computeIfAbsent(relationId, k -> {
                Relation r = new Relation();
                r.setUuid(relationId);
                if (input.containsKey("name")) {
                    r.setName(stringValue(input.get("name")));
                }
                if (input.containsKey("content")) {
                    r.setContent(stringValue(input.get("content")));
                }
                if (input.containsKey("lhs")) {
                    r.setLhs(stringValue(input.get("lhs")));
                }
                if (input.containsKey("rhs")) {
                    r.setRhs(stringValue(input.get("rhs")));
                }
                if (input.containsKey("valid_since")) {
                    r.setValidSince(longValue(input.get("valid_since"), r.getValidSince()));
                }
                if (input.containsKey("valid_until")) {
                    r.setValidUntil(longValue(input.get("valid_until"), r.getValidUntil()));
                }
                return r;
            });
        }

        public Episode getEpisode(Map<String, Object> input) {
            String episodeId = stringValue(input.get("uuid"));
            return episodes.computeIfAbsent(episodeId, k -> {
                Episode ep = new Episode();
                ep.setUuid(episodeId);
                if (input.containsKey("content")) {
                    ep.setContent(stringValue(input.get("content")));
                }
                if (input.containsKey("user_id")) {
                    ep.setUserId(stringValue(input.get("user_id")));
                }
                if (input.containsKey("created_at")) {
                    ep.setCreatedAt(longValue(input.get("created_at"), ep.getCreatedAt()));
                }
                if (input.containsKey("valid_since")) {
                    ep.setValidSince(longValue(input.get("valid_since"), ep.getValidSince()));
                }
                if (input.containsKey("entities")) {
                    ep.setEntities(stringList(input.get("entities")));
                }
                return ep;
            });
        }

        public void clear() {
            entities.clear();
            relations.clear();
            episodes.clear();
        }

        public Map<String, Entity> getEntities() { return entities; }
        public Map<String, Relation> getRelations() { return relations; }
        public Map<String, Episode> getEpisodes() { return episodes; }
    }

    /**
     * Entity merge tracking structure.
     */
    public static class EntityMerge {
        private Entity target;
        private final Map<String, Entity> source = new LinkedHashMap<>();
        private final List<Relation> newRelations = new ArrayList<>();
        private final Set<String> relationsToKeep = new LinkedHashSet<>();

        public EntityMerge(Entity target) {
            this.target = target;
        }

        public Entity getTarget() { return target; }
        public void setTarget(Entity target) { this.target = target; }
        public Map<String, Entity> getSource() { return source; }
        public List<Relation> getNewRelations() { return newRelations; }
        public Set<String> getRelationsToKeep() { return relationsToKeep; }

        public void clear() {
            source.clear();
            newRelations.clear();
            relationsToKeep.clear();
        }
    }

    /**
     * Graph Memory Update tracking.
     */
    public static class GraphMemUpdate {
        private final List<Episode> addedEpisode = new ArrayList<>();
        private final List<Episode> updatedEpisode = new ArrayList<>();
        private final List<Entity> addedEntity = new ArrayList<>();
        private final List<Entity> updatedEntity = new ArrayList<>();
        private final List<Relation> addedRelation = new ArrayList<>();
        private final List<Relation> updatedRelation = new ArrayList<>();
        private final Set<String> removedEntity = new LinkedHashSet<>();
        private final Set<String> removedRelation = new LinkedHashSet<>();

        public GraphMemUpdate merge(GraphMemUpdate other) {
            GraphMemUpdate result = new GraphMemUpdate();
            result.addedEpisode.addAll(this.addedEpisode);
            result.addedEpisode.addAll(other.addedEpisode);
            result.updatedEpisode.addAll(this.updatedEpisode);
            result.updatedEpisode.addAll(other.updatedEpisode);
            result.addedEntity.addAll(this.addedEntity);
            result.addedEntity.addAll(other.addedEntity);
            result.updatedEntity.addAll(this.updatedEntity);
            result.updatedEntity.addAll(other.updatedEntity);
            result.addedRelation.addAll(this.addedRelation);
            result.addedRelation.addAll(other.addedRelation);
            result.updatedRelation.addAll(this.updatedRelation);
            result.updatedRelation.addAll(other.updatedRelation);
            result.removedEntity.addAll(this.removedEntity);
            result.removedEntity.addAll(other.removedEntity);
            result.removedRelation.addAll(this.removedRelation);
            result.removedRelation.addAll(other.removedRelation);
            return result;
        }

        public void clear() {
            addedEpisode.clear();
            updatedEpisode.clear();
            addedEntity.clear();
            updatedEntity.clear();
            addedRelation.clear();
            updatedRelation.clear();
            removedEntity.clear();
            removedRelation.clear();
        }

        public List<Episode> getAddedEpisode() { return addedEpisode; }
        public List<Episode> getUpdatedEpisode() { return updatedEpisode; }
        public List<Entity> getAddedEntity() { return addedEntity; }
        public List<Entity> getUpdatedEntity() { return updatedEntity; }
        public List<Relation> getAddedRelation() { return addedRelation; }
        public List<Relation> getUpdatedRelation() { return updatedRelation; }
        public Set<String> getRemovedEntity() { return removedEntity; }
        public Set<String> getRemovedRelation() { return removedRelation; }
    }

    /**
     * Graph Memory Prompting schema definitions.
     */
    public static class GraphMemPrompting {
        private Map<String, Object> schemaEntityExtraction = new HashMap<>();
        private Map<String, Object> schemaEntityDedupe = new HashMap<>();
        private Map<String, Object> schemaRelationMerge = new HashMap<>();
        private Map<String, Object> schemaRelationFilter = new HashMap<>();
        private String language = "cn";
        private String entityExtractionLanguage = "cn";
        private String relationExtractionLanguage = "cn";
        private String entityDedupeLanguage = "cn";

        public Map<String, Object> getSchemaEntityExtraction() { return schemaEntityExtraction; }
        public void setSchemaEntityExtraction(Map<String, Object> schema) { this.schemaEntityExtraction = schema; }
        public Map<String, Object> getSchemaEntityDedupe() { return schemaEntityDedupe; }
        public void setSchemaEntityDedupe(Map<String, Object> schema) { this.schemaEntityDedupe = schema; }
        public Map<String, Object> getSchemaRelationMerge() { return schemaRelationMerge; }
        public void setSchemaRelationMerge(Map<String, Object> schema) { this.schemaRelationMerge = schema; }
        public Map<String, Object> getSchemaRelationFilter() { return schemaRelationFilter; }
        public void setSchemaRelationFilter(Map<String, Object> schema) { this.schemaRelationFilter = schema; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getEntityExtractionLanguage() { return entityExtractionLanguage; }
        public void setEntityExtractionLanguage(String language) { this.entityExtractionLanguage = language; }
        public String getRelationExtractionLanguage() { return relationExtractionLanguage; }
        public void setRelationExtractionLanguage(String language) { this.relationExtractionLanguage = language; }
        public String getEntityDedupeLanguage() { return entityDedupeLanguage; }
        public void setEntityDedupeLanguage(String language) { this.entityDedupeLanguage = language; }

        public void clear() {
            schemaEntityExtraction.clear();
            schemaEntityDedupe.clear();
            schemaRelationMerge.clear();
            schemaRelationFilter.clear();
        }
    }

    /**
     * Deferred relation endpoint update.
     */
    public static class RelationDeferredUpdate {
        private final Relation relation;
        private final String side;
        private final String value;

        public RelationDeferredUpdate(Relation relation, String side, String value) {
            this.relation = relation;
            this.side = side;
            this.value = value;
        }

        public Relation getRelation() { return relation; }
        public String getSide() { return side; }
        public String getValue() { return value; }
    }

    /**
     * Relation filter task metadata.
     */
    public static class RelationFilterContext {
        private final Entity targetEntity;
        private final List<Relation> relations;

        public RelationFilterContext(Entity targetEntity, List<Relation> relations) {
            this.targetEntity = targetEntity;
            this.relations = relations;
        }

        public Entity getTargetEntity() { return targetEntity; }
        public List<Relation> getRelations() { return relations; }
    }

    /**
     * Current State of Graph Memory Addition.
     */
    public static class GraphMemState {
        private final List<CompletableFuture<?>> tasks = new ArrayList<>();
        private final List<CompletableFuture<GraphMemory.LlmResponse>> mergingTasks = new ArrayList<>();
        private final Map<CompletableFuture<GraphMemory.LlmResponse>, Entity> mergingTasksEntities = new LinkedHashMap<>();
        private final Map<String, CompletableFuture<GraphMemory.LlmResponse>> pendingMerge = new LinkedHashMap<>();
        private final Map<String, List<RelationDeferredUpdate>> relationDeferredUpdates = new LinkedHashMap<>();
        private final Map<CompletableFuture<GraphMemory.LlmResponse>, RelationFilterContext> relationFilterTasks =
                new LinkedHashMap<>();

        private final List<Object> toRemove = new ArrayList<>();
        private final List<Object> tmpBuffer = new ArrayList<>();
        private final List<Entity> updatedEntitiesInCurrentEp = new ArrayList<>();
        private final Map<String, Entity> retrievedEntities = new LinkedHashMap<>();
        private final Map<String, Relation> retrievedRelations = new LinkedHashMap<>();
        private final Map<String, Relation> faultyRelations = new LinkedHashMap<>();
        private final Map<String, EntityMerge> mergeInfos = new LinkedHashMap<>();

        private final GraphMemUpdate memUpdate = new GraphMemUpdate();
        private final GraphMemUpdate memUpdateSkipEmbed = new GraphMemUpdate();
        private final LookupTables lookupTables = new LookupTables();
        private final GraphMemPrompting prompting = new GraphMemPrompting();
        private final Map<String, Object> extras = new LinkedHashMap<>();
        private long currentTimestamp = Instant.now().getEpochSecond();
        private long referenceTimestamp;
        private GraphMemory.AddMemStrategy strategy = new GraphMemory.AddMemStrategy();
        private List<GraphMemory.EntityTypeDef> entityTypes = new ArrayList<>();
        private EpisodeType episodeType = EpisodeType.CONVERSATION;
        private String content = "";
        private String history = "";

        public GraphMemState() {}

        public List<CompletableFuture<?>> getTasks() { return tasks; }
        public List<CompletableFuture<GraphMemory.LlmResponse>> getMergingTasks() { return mergingTasks; }
        public Map<CompletableFuture<GraphMemory.LlmResponse>, Entity> getMergingTasksEntities() {
            return mergingTasksEntities;
        }
        public Map<String, CompletableFuture<GraphMemory.LlmResponse>> getPendingMerge() { return pendingMerge; }
        public Map<String, List<RelationDeferredUpdate>> getRelationDeferredUpdates() {
            return relationDeferredUpdates;
        }
        public Map<CompletableFuture<GraphMemory.LlmResponse>, RelationFilterContext> getRelationFilterTasks() {
            return relationFilterTasks;
        }
        public List<Object> getToRemove() { return toRemove; }
        public List<Object> getTmpBuffer() { return tmpBuffer; }
        public List<Entity> getUpdatedEntitiesInCurrentEp() { return updatedEntitiesInCurrentEp; }
        public Map<String, Entity> getRetrievedEntities() { return retrievedEntities; }
        public Map<String, Relation> getRetrievedRelations() { return retrievedRelations; }
        public Map<String, Relation> getFaultyRelations() { return faultyRelations; }
        public Map<String, EntityMerge> getMergeInfos() { return mergeInfos; }
        public GraphMemUpdate getMemUpdate() { return memUpdate; }
        public GraphMemUpdate getMemUpdateSkipEmbed() { return memUpdateSkipEmbed; }
        public LookupTables getLookupTables() { return lookupTables; }
        public LookupTables getLookupTable() { return lookupTables; }
        public GraphMemUpdate getUpdate() { return memUpdate; }
        public GraphMemPrompting getPrompting() { return prompting; }
        public Map<String, Object> getExtras() { return extras; }
        public long getCurrentTimestamp() { return currentTimestamp; }
        public void setCurrentTimestamp(long currentTimestamp) { this.currentTimestamp = currentTimestamp; }
        public long getReferenceTimestamp() { return referenceTimestamp; }
        public void setReferenceTimestamp(long referenceTimestamp) { this.referenceTimestamp = referenceTimestamp; }
        public GraphMemory.AddMemStrategy getStrategy() { return strategy; }
        public void setStrategy(GraphMemory.AddMemStrategy strategy) { this.strategy = strategy; }
        public List<GraphMemory.EntityTypeDef> getEntityTypes() { return entityTypes; }
        public void setEntityTypes(List<GraphMemory.EntityTypeDef> entityTypes) {
            this.entityTypes = entityTypes != null ? new ArrayList<>(entityTypes) : new ArrayList<>();
        }
        public EpisodeType getEpisodeType() { return episodeType; }
        public void setEpisodeType(EpisodeType episodeType) { this.episodeType = episodeType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getHistory() { return history; }
        public void setHistory(String history) { this.history = history != null ? history : ""; }

        public void clearReferences() {
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
            lookupTables.clear();
            memUpdate.clear();
            memUpdateSkipEmbed.clear();
            prompting.clear();
            extras.clear();
        }
    }

    public static void classifyRelationsExtracted(List<Relation> relations, GraphMemState state) {
        for (EntityMerge mergeInfo : state.getMergeInfos().values()) {
            for (Relation relation : mergeInfo.getNewRelations()) {
                if (relation.getLhs() != null && !relation.getLhs().equals(relation.getRhs())) {
                    mergeInfo.getRelationsToKeep().add(relation.getUuid());
                } else {
                    state.getMemUpdate().getRemovedRelation().add(relation.getUuid());
                }
            }
            Entity target = mergeInfo.getTarget();
            for (String relationUuid : mergeInfo.getRelationsToKeep()) {
                if (!target.getRelations().contains(relationUuid)) {
                    target.getRelations().add(relationUuid);
                }
            }
        }

        state.getTmpBuffer().clear();
        for (Relation relation : relations) {
            relation.setLanguage(state.getPrompting().getLanguage());
            if (relation.getContent() == null || relation.getContent().trim().isEmpty()) {
                state.getToRemove().add(relation);
            } else if (relation.getLhs() != null && relation.getLhs().equals(relation.getRhs())) {
                Entity lhs = state.getLookupTables().getEntities().get(relation.getLhs());
                if (lhs != null) {
                    String content = lhs.getContent() == null ? "" : lhs.getContent().replaceAll("\\n$", "");
                    lhs.setContent(content + "\n- " + relation.getContent());
                }
                state.getToRemove().add(relation);
            } else {
                state.getTmpBuffer().add(relation.getContent());
            }
        }
    }

    public static List<BaseGraphObject> batchEmbed(List<? extends BaseGraphObject> data,
                                                   Embedding embeddingService,
                                                   GraphConfig config) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        List<BaseGraphObject.EmbedTask> tasks = new ArrayList<>();
        for (BaseGraphObject graphObject : data) {
            tasks.addAll(graphObject.fetchEmbedTask());
        }
        if (tasks.isEmpty()) {
            return List.of();
        }
        try {
            List<String> texts = tasks.stream().map(BaseGraphObject.EmbedTask::getContentToEmbed).toList();
            List<List<Float>> embeddings = embeddingService.embedDocuments(texts, config.getEmbedBatchSize());
            for (int i = 0; i < tasks.size() && i < embeddings.size(); i++) {
                applyEmbedding(tasks.get(i), embeddings.get(i));
            }
            return List.of();
        } catch (RuntimeException e) {
            return new ArrayList<>(data);
        }
    }

    public static void persistToDb(GraphStore dbBackend, GraphMemState state, GraphConfig config) throws Exception {
        state.getTmpBuffer().clear();
        for (Entity entity : new ArrayList<>(state.getMemUpdateSkipEmbed().getUpdatedEntity())) {
            if (entity.getContentEmbedding() == null || entity.getNameEmbedding() == null) {
                state.getTmpBuffer().add(entity);
                if (!state.getMemUpdate().getUpdatedEntity().contains(entity)) {
                    state.getMemUpdate().getUpdatedEntity().add(entity);
                }
            }
        }
        for (Object entity : state.getTmpBuffer()) {
            state.getMemUpdateSkipEmbed().getUpdatedEntity().remove(entity);
        }

        List<BaseGraphObject> graphObjectsToEmbed = new ArrayList<>();
        graphObjectsToEmbed.addAll(state.getMemUpdate().getAddedEntity());
        graphObjectsToEmbed.addAll(state.getMemUpdate().getAddedRelation());
        graphObjectsToEmbed.addAll(state.getMemUpdate().getUpdatedEntity());
        int retries = config.getRequestMaxRetries();
        while (retries > 0) {
            graphObjectsToEmbed = batchEmbed(graphObjectsToEmbed, dbBackend.getEmbedder(), config);
            if (graphObjectsToEmbed.isEmpty()) {
                break;
            }
            retries--;
        }
        if (!graphObjectsToEmbed.isEmpty()) {
            throw new IllegalStateException("Unable to access embedding service");
        }

        graphObjectsToEmbed = new ArrayList<>(state.getMemUpdate().getAddedEpisode());
        retries = config.getRequestMaxRetries();
        while (retries > 0) {
            graphObjectsToEmbed = batchEmbed(graphObjectsToEmbed, dbBackend.getEmbedder(), config);
            if (graphObjectsToEmbed.isEmpty()) {
                break;
            }
            if (!state.getMemUpdate().getAddedEpisode().isEmpty()) {
                Episode episode = state.getMemUpdate().getAddedEpisode().get(0);
                String content = episode.getContent();
                if (content != null && !content.isEmpty()) {
                    episode.setContent(content.substring(0, content.length() / 2));
                }
            }
            retries--;
        }
        if (!graphObjectsToEmbed.isEmpty()) {
            throw new IllegalStateException("Unable to access embedding service for new episode, maybe exceeding context limit");
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
            dbBackend.delete(GraphMemory.ENTITY_COLLECTION, new ArrayList<>(state.getMemUpdate().getRemovedEntity()), null);
        }
        if (!state.getMemUpdate().getRemovedRelation().isEmpty()) {
            dbBackend.delete(GraphMemory.RELATION_COLLECTION, new ArrayList<>(state.getMemUpdate().getRemovedRelation()), null);
        }
    }

    private static void applyEmbedding(BaseGraphObject.EmbedTask task, List<Float> embedding) {
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i);
        }
        if ("content_embedding".equals(task.getAttributeName())) {
            task.getObject().setContentEmbedding(vector);
        } else if ("name_embedding".equals(task.getAttributeName()) && task.getObject() instanceof Entity entity) {
            entity.setNameEmbedding(vector);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(stringValue(item));
            }
            return result;
        }
        if (value instanceof Set<?> set) {
            List<String> result = new ArrayList<>();
            for (Object item : set) {
                result.add(stringValue(item));
            }
            return result;
        }
        return new ArrayList<>();
    }
}
