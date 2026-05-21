/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.Relation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lookup tables and state structures for graph memory updates.
 * <p>
 * Mirrors Python's state dataclasses from
 * <code>memory/graph/graph_memory/states.py</code>.
 */
public final class GraphMemoryStates {

    private GraphMemoryStates() {}

    /**
     * Lookup Tables for UUID-to-Entity/Relation.
     */
    public static class LookupTables {
        private final Map<String, Entity> entities = new HashMap<>();
        private final Map<String, Relation> relations = new HashMap<>();
        private final Map<String, Episode> episodes = new HashMap<>();

        public Entity getEntity(Map<String, Object> input) {
            String entityId = (String) input.get("uuid");
            return entities.computeIfAbsent(entityId, k -> {
                Entity e = new Entity();
                if (input.containsKey("name")) e.setName((String) input.get("name"));
                if (input.containsKey("content")) e.setContent((String) input.get("content"));
                return e;
            });
        }

        public Relation getRelation(Map<String, Object> input) {
            String relationId = (String) input.get("uuid");
            return relations.computeIfAbsent(relationId, k -> {
                Relation r = new Relation();
                if (input.containsKey("name")) r.setName((String) input.get("name"));
                return r;
            });
        }

        public Episode getEpisode(Map<String, Object> input) {
            String episodeId = (String) input.get("uuid");
            return episodes.computeIfAbsent(episodeId, k -> {
                Episode ep = new Episode();
                if (input.containsKey("content")) ep.setContent((String) input.get("content"));
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
        private final Map<String, Entity> source = new HashMap<>();
        private final List<Relation> newRelations = new ArrayList<>();
        private final Set<String> relationsToKeep = new HashSet<>();

        public EntityMerge(Entity target) {
            this.target = target;
        }

        public Entity getTarget() { return target; }
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
        private final Set<String> removedEntity = new HashSet<>();
        private final Set<String> removedRelation = new HashSet<>();

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

        // Getters
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

        // Getters and setters
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
    }

    /**
     * GraphMemState — the full state for graph memory operations.
     */
    public static class GraphMemState {
        private final LookupTables lookupTables = new LookupTables();
        private final GraphMemUpdate update = new GraphMemUpdate();
        private final GraphMemPrompting prompting = new GraphMemPrompting();

        public LookupTables getLookupTables() { return lookupTables; }
        public GraphMemUpdate getUpdate() { return update; }
        public GraphMemPrompting getPrompting() { return prompting; }
    }
}
