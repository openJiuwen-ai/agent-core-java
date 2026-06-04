/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
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

    private static <T> T requireField(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static Map<String, Object> objectSchema(String title, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("title", title);
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        return schema;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> arrayProperty(String description, Map<String, Object> items) {
        Map<String, Object> property = property("array", description);
        property.put("items", items);
        return property;
    }

    private static Map<String, Object> items(String type) {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", type);
        return items;
    }

    private static Map<String, Object> refItems(String refName) {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("$ref", "#/$defs/" + refName);
        return items;
    }

    /**
     * Represents a datetime payload.
     * <p>
     * Mirrors Python's {@code Datetime} extraction model.
     */
    public static class Datetime extends MultilingualBaseModel {
        @JsonProperty("year")
        private Integer year;
        @JsonProperty("month")
        private Integer month;
        @JsonProperty("day")
        private Integer day;
        @JsonProperty("hour")
        private Integer hour;
        @JsonProperty("minute")
        private Integer minute;
        @JsonProperty("second")
        private Integer second;

        public Datetime() {}

        public Datetime(Integer year, Integer month, Integer day, Integer hour, Integer minute, Integer second) {
            this.year = requireField(year, "year");
            this.month = requireField(month, "month");
            this.day = requireField(day, "day");
            this.hour = requireField(hour, "hour");
            this.minute = requireField(minute, "minute");
            this.second = requireField(second, "second");
        }

        public int getYear() { return requireField(year, "year"); }
        public void setYear(Integer year) { this.year = requireField(year, "year"); }
        public int getMonth() { return requireField(month, "month"); }
        public void setMonth(Integer month) { this.month = requireField(month, "month"); }
        public int getDay() { return requireField(day, "day"); }
        public void setDay(Integer day) { this.day = requireField(day, "day"); }
        public int getHour() { return requireField(hour, "hour"); }
        public void setHour(Integer hour) { this.hour = requireField(hour, "hour"); }
        public int getMinute() { return requireField(minute, "minute"); }
        public void setMinute(Integer minute) { this.minute = requireField(minute, "minute"); }
        public int getSecond() { return requireField(second, "second"); }
        public void setSecond(Integer second) { this.second = requireField(second, "second"); }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("year", property("integer", "{{[year]}}"));
            properties.put("month", property("integer", "{{[month]}}"));
            properties.put("day", property("integer", "{{[day]}}"));
            properties.put("hour", property("integer", "{{[hour]}}"));
            properties.put("minute", property("integer", "{{[minute]}}"));
            properties.put("second", property("integer", "{{[second]}}"));
            return objectSchema("Datetime", properties);
        }
    }

    /**
     * Entity declaration output item.
     * <p>
     * Mirrors Python's {@code EntityDeclaration} extraction model.
     */
    public static class EntityDeclaration extends MultilingualBaseModel {
        @JsonProperty("name")
        private String name;
        @JsonProperty("entity_type_id")
        @JsonAlias("entityTypeId")
        private Integer entityTypeId;

        public EntityDeclaration() {}

        public EntityDeclaration(String name, Integer entityTypeId) {
            this.name = requireField(name, "name");
            this.entityTypeId = requireField(entityTypeId, "entity_type_id");
        }

        public String getName() { return requireField(name, "name"); }
        public void setName(String name) { this.name = requireField(name, "name"); }
        public int getEntityTypeId() { return requireField(entityTypeId, "entity_type_id"); }
        public void setEntityTypeId(Integer entityTypeId) {
            this.entityTypeId = requireField(entityTypeId, "entity_type_id");
        }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", property("string", "{{[ent_def_name]}}"));
            properties.put("entity_type_id", property("integer", "{{[ent_def_type]}}"));
            return objectSchema("EntityDeclaration", properties);
        }
    }

    /**
     * Entity duplication output item.
     * <p>
     * Mirrors Python's {@code Duplication} extraction model.
     */
    public static class Duplication extends MultilingualBaseModel {
        @JsonProperty("name")
        private String name;
        @JsonProperty("id")
        private Integer id;
        @JsonProperty("duplicate_ids")
        @JsonAlias("duplicateIds")
        private List<Integer> duplicateIds;

        public Duplication() {}

        public Duplication(String name, Integer id, List<Integer> duplicateIds) {
            this.name = requireField(name, "name");
            this.id = requireField(id, "id");
            this.duplicateIds = requireField(duplicateIds, "duplicate_ids");
        }

        public String getName() { return requireField(name, "name"); }
        public void setName(String name) { this.name = requireField(name, "name"); }
        public int getId() { return requireField(id, "id"); }
        public void setId(Integer id) { this.id = requireField(id, "id"); }
        public List<Integer> getDuplicateIds() { return requireField(duplicateIds, "duplicate_ids"); }
        public void setDuplicateIds(List<Integer> duplicateIds) {
            this.duplicateIds = requireField(duplicateIds, "duplicate_ids");
        }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", property("string", "{{[ent_dupe_name]}}"));
            properties.put("id", property("integer", "{{[ent_dupe_id]}}"));
            properties.put("duplicate_ids", arrayProperty("{{[ent_dupe_id_list]}}", items("integer")));
            return objectSchema("Duplication", properties);
        }
    }

    /**
     * Factual relation output item.
     * <p>
     * Mirrors Python's {@code Fact} extraction model.
     */
    public static class Fact extends MultilingualBaseModel {
        @JsonProperty("name")
        private String name;
        @JsonProperty("fact")
        private String fact;
        @JsonProperty("valid_since")
        @JsonAlias("validSince")
        private String validSince;
        @JsonProperty("valid_until")
        @JsonAlias("validUntil")
        private String validUntil;
        @JsonProperty("source_id")
        @JsonAlias("sourceId")
        private Integer sourceId;
        @JsonProperty("target_id")
        @JsonAlias("targetId")
        private Integer targetId;

        public Fact() {}

        public Fact(String name, String fact, String validSince, String validUntil, Integer sourceId, Integer targetId) {
            this.name = requireField(name, "name");
            this.fact = requireField(fact, "fact");
            this.validSince = requireField(validSince, "valid_since");
            this.validUntil = requireField(validUntil, "valid_until");
            this.sourceId = requireField(sourceId, "source_id");
            this.targetId = requireField(targetId, "target_id");
        }

        public String getName() { return requireField(name, "name"); }
        public void setName(String name) { this.name = requireField(name, "name"); }
        public String getFact() { return requireField(fact, "fact"); }
        public void setFact(String fact) { this.fact = requireField(fact, "fact"); }
        public String getValidSince() { return requireField(validSince, "valid_since"); }
        public void setValidSince(String validSince) { this.validSince = requireField(validSince, "valid_since"); }
        public String getValidUntil() { return requireField(validUntil, "valid_until"); }
        public void setValidUntil(String validUntil) { this.validUntil = requireField(validUntil, "valid_until"); }
        public int getSourceId() { return requireField(sourceId, "source_id"); }
        public void setSourceId(Integer sourceId) { this.sourceId = requireField(sourceId, "source_id"); }
        public int getTargetId() { return requireField(targetId, "target_id"); }
        public void setTargetId(Integer targetId) { this.targetId = requireField(targetId, "target_id"); }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", property("string", "{{[rel_name]}}"));
            properties.put("fact", property("string", "{{[rel_fact]}}"));
            properties.put("valid_since", property("string", "{{[rel_valid_since]}}"));
            properties.put("valid_until", property("string", "{{[rel_valid_until]}}"));
            properties.put("source_id", property("integer", "{{[rel_source_id]}}"));
            properties.put("target_id", property("integer", "{{[rel_target_id]}}"));
            return objectSchema("Fact", properties);
        }
    }

    /**
     * Timezone prediction output item.
     * <p>
     * Mirrors Python's {@code PossibleTimezone} extraction model.
     */
    public static class PossibleTimezone extends MultilingualBaseModel {
        @JsonProperty("name")
        private String name;
        @JsonProperty("offset_from_utc")
        @JsonAlias("offsetFromUtc")
        private String offsetFromUtc;
        @JsonProperty("reasoning")
        private String reasoning;

        public PossibleTimezone() {}

        public PossibleTimezone(String name, String offsetFromUtc, String reasoning) {
            this.name = requireField(name, "name");
            this.offsetFromUtc = requireField(offsetFromUtc, "offset_from_utc");
            this.reasoning = requireField(reasoning, "reasoning");
        }

        public String getName() { return requireField(name, "name"); }
        public void setName(String name) { this.name = requireField(name, "name"); }
        public String getOffsetFromUtc() { return requireField(offsetFromUtc, "offset_from_utc"); }
        public void setOffsetFromUtc(String offsetFromUtc) {
            this.offsetFromUtc = requireField(offsetFromUtc, "offset_from_utc");
        }
        public String getReasoning() { return requireField(reasoning, "reasoning"); }
        public void setReasoning(String reasoning) { this.reasoning = requireField(reasoning, "reasoning"); }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", property("string", "{{[tz_name]}}"));
            properties.put("offset_from_utc", property("string", "{{[tz_offset]}}"));
            properties.put("reasoning", property("string", "{{[tz_reason]}}"));
            return objectSchema("PossibleTimezone", properties);
        }
    }

    /**
     * Output for entity declaration extraction.
     * <p>
     * Mirrors Python's {@code EntityExtraction} extraction model.
     */
    public static class EntityExtraction extends MultilingualBaseModel {
        @JsonProperty("extracted_entities")
        @JsonAlias("extractedEntities")
        private List<EntityDeclaration> extractedEntities;

        public EntityExtraction() {}

        public EntityExtraction(List<EntityDeclaration> extractedEntities) {
            this.extractedEntities = requireField(extractedEntities, "extracted_entities");
        }

        public List<EntityDeclaration> getExtractedEntities() {
            return requireField(extractedEntities, "extracted_entities");
        }
        public void setExtractedEntities(List<EntityDeclaration> extractedEntities) {
            this.extractedEntities = requireField(extractedEntities, "extracted_entities");
        }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("extracted_entities", arrayProperty("{{[ent_ext_list]}}", refItems("EntityDeclaration")));
            Map<String, Object> schema = objectSchema("EntityExtraction", properties);
            Map<String, Object> defs = new LinkedHashMap<>();
            defs.put("EntityDeclaration", new EntityDeclaration().responseFormat());
            schema.put("$defs", defs);
            return schema;
        }
    }

    /**
     * Output for entity summary and attribute extraction.
     * <p>
     * Mirrors Python's {@code EntitySummary} extraction model.
     */
    public static class EntitySummary extends MultilingualBaseModel {
        @JsonProperty("summary")
        private String summary;
        @JsonProperty("attributes")
        private Map<String, Object> attributes;

        public EntitySummary() {}

        public EntitySummary(String summary, Map<String, Object> attributes) {
            this.summary = requireField(summary, "summary");
            this.attributes = requireField(attributes, "attributes");
        }

        public String getSummary() { return requireField(summary, "summary"); }
        public void setSummary(String summary) { this.summary = requireField(summary, "summary"); }
        public Map<String, Object> getAttributes() { return requireField(attributes, "attributes"); }
        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = requireField(attributes, "attributes");
        }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("summary", property("string", "{{[ent_summary]}}"));
            properties.put("attributes", property("object", "{{[ent_attributes]}}"));
            return objectSchema("EntitySummary", properties);
        }
    }

    /**
     * Output for entity de-duplication.
     * <p>
     * Mirrors Python's {@code EntityDuplication} extraction model.
     */
    public static class EntityDuplication extends MultilingualBaseModel {
        @JsonProperty("duplicated_entities")
        @JsonAlias("duplicatedEntities")
        private List<Duplication> duplicatedEntities;

        public EntityDuplication() {}

        public EntityDuplication(List<Duplication> duplicatedEntities) {
            this.duplicatedEntities = requireField(duplicatedEntities, "duplicated_entities");
        }

        public List<Duplication> getDuplicatedEntities() {
            return requireField(duplicatedEntities, "duplicated_entities");
        }
        public void setDuplicatedEntities(List<Duplication> duplicatedEntities) {
            this.duplicatedEntities = requireField(duplicatedEntities, "duplicated_entities");
        }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("duplicated_entities", arrayProperty("{{[ent_dupe_list]}}", refItems("Duplication")));
            return objectSchema("EntityDuplication", properties);
        }
    }

    /**
     * Output for relation extraction.
     * <p>
     * Mirrors Python's {@code RelationExtraction} extraction model.
     */
    public static class RelationExtraction extends MultilingualBaseModel {
        @JsonProperty("extracted_relations")
        @JsonAlias({"extractedRelations", "facts"})
        private List<Fact> extractedRelations;

        public RelationExtraction() {}

        public RelationExtraction(List<Fact> extractedRelations) {
            this.extractedRelations = requireField(extractedRelations, "extracted_relations");
        }

        public List<Fact> getExtractedRelations() { return requireField(extractedRelations, "extracted_relations"); }
        public void setExtractedRelations(List<Fact> extractedRelations) {
            this.extractedRelations = requireField(extractedRelations, "extracted_relations");
        }
        public List<Fact> getFacts() { return getExtractedRelations(); }
        public void setFacts(List<Fact> facts) { setExtractedRelations(facts); }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("extracted_relations", arrayProperty("{{[rel_ext_list]}}", refItems("Fact")));
            return objectSchema("RelationExtraction", properties);
        }
    }

    /**
     * Output for fact/relation filtering.
     * <p>
     * Mirrors Python's {@code RelevantFacts} extraction model.
     */
    public static class RelevantFacts extends MultilingualBaseModel {
        @JsonProperty("brief_reasoning")
        @JsonAlias("briefReasoning")
        private String briefReasoning;
        @JsonProperty("relevant_relations")
        @JsonAlias({"relevantRelations", "relevantFacts"})
        private List<Integer> relevantRelations;

        public RelevantFacts() {}

        public RelevantFacts(String briefReasoning, List<Integer> relevantRelations) {
            this.briefReasoning = requireField(briefReasoning, "brief_reasoning");
            this.relevantRelations = requireField(relevantRelations, "relevant_relations");
        }

        public String getBriefReasoning() { return requireField(briefReasoning, "brief_reasoning"); }
        public void setBriefReasoning(String briefReasoning) {
            this.briefReasoning = requireField(briefReasoning, "brief_reasoning");
        }
        public List<Integer> getRelevantRelations() {
            return requireField(relevantRelations, "relevant_relations");
        }
        public void setRelevantRelations(List<Integer> relevantRelations) {
            this.relevantRelations = requireField(relevantRelations, "relevant_relations");
        }
        public List<Integer> getRelevantFacts() { return getRelevantRelations(); }
        public void setRelevantFacts(List<Integer> relevantFacts) { setRelevantRelations(relevantFacts); }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("brief_reasoning", property("string", "{{[rel_filter_reasoning]}}"));
            properties.put("relevant_relations", arrayProperty("{{[rel_filter_list]}}", items("integer")));
            return objectSchema("RelevantFacts", properties);
        }
    }

    /**
     * Output for timezone prediction.
     * <p>
     * Mirrors Python's {@code TimezonePredictions} extraction model.
     */
    public static class TimezonePredictions extends MultilingualBaseModel {
        @JsonProperty("extracted_relations")
        @JsonAlias({"extractedRelations", "timezones"})
        private List<PossibleTimezone> extractedRelations;

        public TimezonePredictions() {}

        public TimezonePredictions(List<PossibleTimezone> extractedRelations) {
            this.extractedRelations = requireField(extractedRelations, "extracted_relations");
        }

        public List<PossibleTimezone> getExtractedRelations() {
            return requireField(extractedRelations, "extracted_relations");
        }
        public void setExtractedRelations(List<PossibleTimezone> extractedRelations) {
            this.extractedRelations = requireField(extractedRelations, "extracted_relations");
        }
        public List<PossibleTimezone> getTimezones() { return getExtractedRelations(); }
        public void setTimezones(List<PossibleTimezone> timezones) { setExtractedRelations(timezones); }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("extracted_relations", arrayProperty("{{[tz_list]}}", refItems("PossibleTimezone")));
            return objectSchema("TimezonePredictions", properties);
        }
    }

    /**
     * Output for relation merging.
     * <p>
     * Mirrors Python's {@code MergeRelations} extraction model.
     */
    public static class MergeRelations extends MultilingualBaseModel {
        @JsonProperty("need_merging")
        @JsonAlias("needMerging")
        private Boolean needMerging;
        @JsonProperty("short_reasoning")
        @JsonAlias("shortReasoning")
        private String shortReasoning;
        @JsonProperty("combined_content")
        @JsonAlias("combinedContent")
        private String combinedContent;
        @JsonProperty("duplicate_ids")
        @JsonAlias("duplicateIds")
        private List<Integer> duplicateIds;
        @JsonProperty("valid_since")
        @JsonAlias("validSince")
        private String validSince;
        @JsonProperty("valid_until")
        @JsonAlias("validUntil")
        private String validUntil;

        public MergeRelations() {}

        public MergeRelations(Boolean needMerging, String shortReasoning, String combinedContent,
                List<Integer> duplicateIds, String validSince, String validUntil) {
            this.needMerging = requireField(needMerging, "need_merging");
            this.shortReasoning = requireField(shortReasoning, "short_reasoning");
            this.combinedContent = requireField(combinedContent, "combined_content");
            this.duplicateIds = requireField(duplicateIds, "duplicate_ids");
            this.validSince = requireField(validSince, "valid_since");
            this.validUntil = requireField(validUntil, "valid_until");
        }

        public Boolean getNeedMerging() { return requireField(needMerging, "need_merging"); }
        public boolean isNeedMerging() { return getNeedMerging(); }
        public void setNeedMerging(Boolean needMerging) {
            this.needMerging = requireField(needMerging, "need_merging");
        }
        public String getShortReasoning() { return requireField(shortReasoning, "short_reasoning"); }
        public void setShortReasoning(String shortReasoning) {
            this.shortReasoning = requireField(shortReasoning, "short_reasoning");
        }
        public String getCombinedContent() { return requireField(combinedContent, "combined_content"); }
        public void setCombinedContent(String combinedContent) {
            this.combinedContent = requireField(combinedContent, "combined_content");
        }
        public List<Integer> getDuplicateIds() { return requireField(duplicateIds, "duplicate_ids"); }
        public void setDuplicateIds(List<Integer> duplicateIds) {
            this.duplicateIds = requireField(duplicateIds, "duplicate_ids");
        }
        public String getValidSince() { return requireField(validSince, "valid_since"); }
        public void setValidSince(String validSince) { this.validSince = requireField(validSince, "valid_since"); }
        public String getValidUntil() { return requireField(validUntil, "valid_until"); }
        public void setValidUntil(String validUntil) { this.validUntil = requireField(validUntil, "valid_until"); }

        @Override
        public Map<String, Object> responseFormat() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("need_merging", property("boolean", "{{[rel_dupe_need_merge]}}"));
            properties.put("short_reasoning", property("string", "{{[rel_dupe_reasoning]}}"));
            properties.put("combined_content", property("string", "{{[rel_dupe_content]}}"));
            properties.put("duplicate_ids", arrayProperty("{{[rel_dupe_id_list]}}", items("integer")));
            properties.put("valid_since", property("string", "{{[rel_valid_since]}}"));
            properties.put("valid_until", property("string", "{{[rel_valid_until]}}"));
            return objectSchema("MergeRelations", properties);
        }
    }
}
