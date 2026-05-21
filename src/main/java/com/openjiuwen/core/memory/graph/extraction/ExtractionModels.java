/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extraction models for entity/relation extraction output.
 * <p>
 * Mirrors Python's extraction model classes from
 * <code>memory/graph/extraction/extraction_models.py</code>.
 */
public final class ExtractionModels {

    private ExtractionModels() {}

    // --- Schema Definition Models ---

    public static class EntityDeclaration {
        private String name;
        private int entityTypeId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getEntityTypeId() { return entityTypeId; }
        public void setEntityTypeId(int entityTypeId) { this.entityTypeId = entityTypeId; }
    }

    public static class Duplication {
        private String name;
        private int id;
        private List<Integer> duplicateIds = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public List<Integer> getDuplicateIds() { return duplicateIds; }
        public void setDuplicateIds(List<Integer> duplicateIds) { this.duplicateIds = duplicateIds; }
    }

    public static class Fact {
        private String name;
        private String fact;
        private String validSince;
        private String validUntil;
        private int sourceId;
        private int targetId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getFact() { return fact; }
        public void setFact(String fact) { this.fact = fact; }
        public String getValidSince() { return validSince; }
        public void setValidSince(String validSince) { this.validSince = validSince; }
        public String getValidUntil() { return validUntil; }
        public void setValidUntil(String validUntil) { this.validUntil = validUntil; }
        public int getSourceId() { return sourceId; }
        public void setSourceId(int sourceId) { this.sourceId = sourceId; }
        public int getTargetId() { return targetId; }
        public void setTargetId(int targetId) { this.targetId = targetId; }
    }

    public static class PossibleTimezone {
        private String name;
        private String offsetFromUtc;
        private String reasoning;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getOffsetFromUtc() { return offsetFromUtc; }
        public void setOffsetFromUtc(String offsetFromUtc) { this.offsetFromUtc = offsetFromUtc; }
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    }

    // --- Output Models ---

    public static class EntityExtraction {
        private List<EntityDeclaration> extractedEntities = new ArrayList<>();

        public List<EntityDeclaration> getExtractedEntities() { return extractedEntities; }
        public void setExtractedEntities(List<EntityDeclaration> extractedEntities) { this.extractedEntities = extractedEntities; }
    }

    public static class EntitySummary {
        private String summary;
        private Map<String, Object> attributes = new HashMap<>();

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public Map<String, Object> getAttributes() { return attributes; }
        public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }

        public Map<String, Object> responseFormat() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            properties.put("summary", Map.of("type", "string", "description", "Entity summary"));
            properties.put("attributes", Map.of("type", "object", "description", "Entity attributes"));
            schema.put("properties", properties);
            return schema;
        }
    }

    public static class EntityDuplication {
        private List<Duplication> duplicatedEntities = new ArrayList<>();

        public List<Duplication> getDuplicatedEntities() { return duplicatedEntities; }
        public void setDuplicatedEntities(List<Duplication> duplicatedEntities) { this.duplicatedEntities = duplicatedEntities; }

        public Map<String, Object> responseFormat() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of("duplicated_entities", Map.of("type", "array")));
            return schema;
        }
    }

    public static class RelationExtraction {
        private List<Fact> facts = new ArrayList<>();

        public List<Fact> getFacts() { return facts; }
        public void setFacts(List<Fact> facts) { this.facts = facts; }
    }

    public static class RelevantFacts {
        private List<Fact> relevantFacts = new ArrayList<>();

        public List<Fact> getRelevantFacts() { return relevantFacts; }
        public void setRelevantFacts(List<Fact> relevantFacts) { this.relevantFacts = relevantFacts; }

        public Map<String, Object> responseFormat() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of("relevant_facts", Map.of("type", "array")));
            return schema;
        }
    }

    public static class TimezonePredictions {
        private List<PossibleTimezone> timezones = new ArrayList<>();

        public List<PossibleTimezone> getTimezones() { return timezones; }
        public void setTimezones(List<PossibleTimezone> timezones) { this.timezones = timezones; }
    }

    public static class MergeRelations {
        private List<Fact> mergedFacts = new ArrayList<>();

        public List<Fact> getMergedFacts() { return mergedFacts; }
        public void setMergedFacts(List<Fact> mergedFacts) { this.mergedFacts = mergedFacts; }

        public Map<String, Object> responseFormat() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            schema.put("properties", Map.of("merged_facts", Map.of("type", "array")));
            return schema;
        }
    }
}
