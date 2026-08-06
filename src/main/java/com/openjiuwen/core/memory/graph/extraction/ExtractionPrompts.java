/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.graph.EpisodeType;
import com.openjiuwen.core.memory.graph.extraction.prompts.TemplateManager;
import com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction.ExtractionPromptLanguageBase;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prompt building and entity extraction orchestration by episode type.
 * 
 * @since 0.1.7
 */
public final class ExtractionPrompts {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * ExtractionPrompts.
     * 
     * @since 0.1.7
     */
    private ExtractionPrompts() {
    }

    /**
     * Public record PromptCall used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record PromptCall(Map<String, Object> kwargs, PromptTemplate template, Map<String, Object> outputModel) {
    }

    /**
     * extractEntityDeclaration.
     * 
     * @param srcType srcType
     * @param content content
     * @param history history
     * @param description description
     * @param entityTypes entityTypes
     * @param language language
     * @param extras extras
     * @param indent indent
     * @return the result
     * @since 0.1.7
     */
    public static PromptCall extractEntityDeclaration(EpisodeType srcType, String content, String history,
            String description, List<EntityDef> entityTypes, String language, Map<String, Object> extras, int indent) {
        String operation = srcType.name().toLowerCase(Locale.ROOT);
        Map<String, Object> kwargs = new LinkedHashMap<>(ExtractionPromptLanguageBase.getFormattingKwargs(description,
                EntityExtraction.class, indent, history, content, language));
        if (extras != null) {
            kwargs.putAll(extras);
        }
        List<EntityDef> resolvedEntityTypes =
            entityTypes != null && !entityTypes.isEmpty() ? entityTypes : List.of(new EntityDef());
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < resolvedEntityTypes.size(); i++) {
            EntityDef entityType = resolvedEntityTypes.get(i);
            String descriptionText = entityType.getDescription().getOrDefault(language, "");
            lines.add(i + ". " + entityType.getName() + descriptionText);
        }
        kwargs.put("entity_types", String.join("\n", lines));
        String templateName = "entity_extraction_" + operation + "_" + language;
        return new PromptCall(kwargs, TemplateManager.getInstance().get(templateName),
                MultilingualBaseModel.responseFormat(EntityExtraction.class, language));
    }

    /**
     * extractEntityAttributes.
     * 
     * @param entity entity
     * @param content content
     * @param history history
     * @param language language
     * @param extras extras
     * @param indent indent
     * @return the result
     * @since 0.1.7
     */
    public static PromptCall extractEntityAttributes(Entity entity, String content, String history, String language,
            Map<String, Object> extras, int indent) {
        String templateName = "entity_extraction_summary_create_" + language;
        Map<String, Object> kwargs = new LinkedHashMap<>(ExtractionPromptLanguageBase.getFormattingKwargs(null,
                EntitySummary.class, indent, history, content, language));
        kwargs.put("entity_name", entity.getName());
        kwargs.put("entity_summary", entity.getContent() != null ? entity.getContent() : "");
        if (entity.getAttributes() != null && !entity.getAttributes().isEmpty()) {
            kwargs.put("entity_attribute", toJson(entity.getAttributes()));
        }
        if (extras != null) {
            kwargs.putAll(extras);
        }
        applyHumanSummaryTarget(entity, kwargs);
        return new PromptCall(kwargs, TemplateManager.getInstance().get(templateName),
                MultilingualBaseModel.responseFormat(EntitySummary.class, language));
    }

    private static void applyHumanSummaryTarget(Entity entity, Map<String, Object> kwargs) {
        if (!"Human".equalsIgnoreCase(entity.getObjType())) {
            return;
        }
        Number doubledTarget = doubleSummaryTarget(kwargs.get("summary_target"));
        if (doubledTarget != null) {
            kwargs.put("summary_target", doubledTarget);
        }
    }

    private static Number doubleSummaryTarget(Object summaryTarget) {
        BigInteger target;
        if (summaryTarget instanceof BigInteger bigInteger) {
            target = bigInteger;
        } else if (summaryTarget instanceof Number number) {
            target = BigInteger.valueOf(number.longValue());
        } else if (summaryTarget instanceof String value
                && !value.isEmpty() && value.codePoints().allMatch(Character::isDigit)) {
            target = new BigInteger(value);
        } else {
            return null;
        }
        BigInteger doubledTarget = target.shiftLeft(1);
        if (doubledTarget.bitLength() < Integer.SIZE) {
            return Integer.valueOf(doubledTarget.intValue());
        }
        if (doubledTarget.bitLength() < Long.SIZE) {
            return Long.valueOf(doubledTarget.longValue());
        }
        return doubledTarget;
    }

    /**
     * extractRelationDeclaration.
     * 
     * @param relationTypes relationTypes
     * @param entities entities
     * @param referenceTime referenceTime
     * @param tzInfo tzInfo
     * @param content content
     * @param history history
     * @param entityTypes entityTypes
     * @param description description
     * @param language language
     * @param indent indent
     * @return the result
     * @since 0.1.7
     */
    public static PromptCall extractRelationDeclaration(List<RelationDef> relationTypes,
            List<EntityDeclaration> entities, int referenceTime, Object tzInfo, String content, String history,
            List<EntityDef> entityTypes, String description, String language, int indent) {
        String templateName = "entity_extraction_relation_" + language;
        Map<String, Object> kwargs = new LinkedHashMap<>(ExtractionPromptLanguageBase.getFormattingKwargs(description,
                RelationExtraction.class, indent, history, content, language));
        kwargs.put("tz_info",
                (tzInfo instanceof Map || tzInfo instanceof List) ? toJson(tzInfo) : String.valueOf(tzInfo));
        kwargs.put("entities", formatNewEntities(entities, entityTypes, 1, language));
        kwargs.put("relation_types", ExtractionPromptLanguageBase.formatRelationDefinitions(relationTypes, language));
        kwargs.put("reference_time", Instant.ofEpochSecond(referenceTime).toString());
        kwargs.put("id_range", "1-" + entities.size());
        return new PromptCall(kwargs, TemplateManager.getInstance().get(templateName),
                MultilingualBaseModel.responseFormat(RelationExtraction.class, language));
    }

    /**
     * extractTimezone.
     * 
     * @param content content
     * @param history history
     * @param description description
     * @param language language
     * @param indent indent
     * @return the result
     * @since 0.1.7
     */
    public static PromptCall extractTimezone(String content, String history, String description, String language,
            int indent) {
        Map<String, Object> kwargs = new LinkedHashMap<>(ExtractionPromptLanguageBase.getFormattingKwargs(description,
                TimezonePredictions.class, indent, history, content, language));
        String templateName = "entity_extraction_timezone_" + language;
        return new PromptCall(kwargs, TemplateManager.getInstance().get(templateName),
                MultilingualBaseModel.responseFormat(TimezonePredictions.class, language));
    }

    /**
     * mergeExistingEntities.
     * 
     * @param target target
     * @param sources sources
     * @param language language
     * @param extras extras
     * @param indent indent
     * @return the result
     * @since 0.1.7
     */
    public static PromptCall mergeExistingEntities(Entity target, List<Entity> sources, String language,
            Map<String, Object> extras, int indent) {
        String templateName = "entity_extraction_entity_merge_" + language;
        Map<String, Object> kwargs = new LinkedHashMap<>(
                ExtractionPromptLanguageBase.getFormattingKwargs(null, EntitySummary.class, indent, "", "", language));
        kwargs.put("entity_name", target.getName());
        kwargs.put("entity_summary", target.getContent() != null ? target.getContent() : "");
        if (target.getAttributes() != null && !target.getAttributes().isEmpty()) {
            kwargs.put("entity_attribute", toJson(target.getAttributes()));
        }
        List<Map<String, Object>> existing = new ArrayList<>();
        for (Entity source : sources) {
            existing.add(source.toMap());
        }
        kwargs.put("entities_to_merge", ExtractionPromptLanguageBase.formatExistingEntities(existing, 1, language));
        if (extras != null) {
            kwargs.putAll(extras);
        }
        return new PromptCall(kwargs, TemplateManager.getInstance().get(templateName),
                MultilingualBaseModel.responseFormat(EntitySummary.class, language));
    }

    /**
     * dedupeEntityList.
     * 
     * @param content content
     * @param candidateEntities candidateEntities
     * @param existingEntities existingEntities
     * @param entityTypes entityTypes
     * @param history history
     * @param description description
     * @param language language
     * @param indent indent
     * @return the result
     * @since 0.1.7
     */
    public static PromptCall dedupeEntityList(String content, List<EntityDeclaration> candidateEntities,
            List<Map<String, Object>> existingEntities, List<EntityDef> entityTypes, String history, String description,
            String language, int indent) {
        Map<String, Object> kwargs = new LinkedHashMap<>(ExtractionPromptLanguageBase.getFormattingKwargs(description,
                EntityDuplication.class, indent, history, content, language));
        kwargs.put("entities", ExtractionPromptLanguageBase.formatExistingEntities(existingEntities, 1, language));
        kwargs.put("candidate_entities",
                formatNewEntities(candidateEntities, entityTypes, existingEntities.size() + 1, language));
        String templateName = "entity_extraction_dedupe_entity_" + language;
        return new PromptCall(kwargs, TemplateManager.getInstance().get(templateName),
                MultilingualBaseModel.responseFormat(EntityDuplication.class, language));
    }

    /**
     * filterRelationsForMerge.
     * 
     * @param target target
     * @param relations relations
     * @param language language
     * @param extras extras
     * @param indent indent
     * @return the result
     * @since 0.1.7
     */
    public static PromptCall filterRelationsForMerge(Entity target, List<Relation> relations, String language,
            Map<String, Object> extras, int indent) {
        Map<String, Object> kwargs = new LinkedHashMap<>(
                ExtractionPromptLanguageBase.getFormattingKwargs(null, RelevantFacts.class, indent, "", "", language));
        kwargs.put("entity_name", target.getName());
        kwargs.put("entity_summary", target.getContent() != null ? target.getContent() : "");
        if (target.getAttributes() != null && !target.getAttributes().isEmpty()) {
            kwargs.put("entity_attribute", toJson(target.getAttributes()));
        }
        kwargs.put("existing_relations", formatExistingRelations(relations, true));
        if (extras != null) {
            kwargs.putAll(extras);
        }
        String templateName = "entity_extraction_relation_filter_" + language;
        return new PromptCall(kwargs, TemplateManager.getInstance().get(templateName),
                MultilingualBaseModel.responseFormat(RelevantFacts.class, language));
    }

    /**
     * dedupeRelationList.
     * 
     * @param content content
     * @param relation relation
     * @param existingRelations existingRelations
     * @param existingEntities existingEntities
     * @param history history
     * @param description description
     * @param language language
     * @param indent indent
     * @return the result
     * @since 0.1.7
     */
    public static PromptCall dedupeRelationList(String content, Relation relation, List<?> existingRelations,
            List<Entity> existingEntities, String history, String description, String language, int indent) {
        String templateName = "entity_extraction_dedupe_relation_" + language;
        Map<String, Object> kwargs = new LinkedHashMap<>(ExtractionPromptLanguageBase.getFormattingKwargs(description,
                MergeRelations.class, indent, history, content, language));
        List<Map<String, Object>> existingRelationMaps = new ArrayList<>();
        for (Object existingRelation : existingRelations) {
            if (existingRelation instanceof Relation rel) {
                existingRelationMaps.add(rel.toMap());
            } else if (existingRelation instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = new LinkedHashMap<>((Map<String, Object>) map);
                existingRelationMaps.add(typed);
            } else {
                // no-op
            }
        }
        List<Map<String, Object>> entityMaps = existingEntities.stream().map(Entity::toMap).toList();
        kwargs.put("entities", ExtractionPromptLanguageBase.formatExistingEntities(entityMaps, 1, language));
        kwargs.put("existing_relations", formatExistingRelationMaps(existingRelationMaps, true));
        kwargs.put("new_relation",
                formatExistingRelationMaps(List.of(relation.toMap()), true).replaceFirst("^1\\.\\s*", ""));
        return new PromptCall(kwargs, TemplateManager.getInstance().get(templateName),
                MultilingualBaseModel.responseFormat(MergeRelations.class, language));
    }

    /**
     * formatNewEntities.
     * 
     * @param entities entities
     * @param entityTypes entityTypes
     * @param startIdx startIdx
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static String formatNewEntities(List<EntityDeclaration> entities, List<EntityDef> entityTypes, int startIdx,
            String language) {
        List<String> lines = new ArrayList<>();
        int typeIdMax = entityTypes.size() - 1;
        int current = startIdx;
        for (EntityDeclaration entity : entities) {
            EntityDef type = entityTypes.get(Math.min(entity.getEntityTypeId(), typeIdMax));
            lines.add(current++ + ". " + entity.getName() + " (" + type.getName() + ")");
        }
        return String.join("\n", lines);
    }

    /**
     * formatExistingRelations.
     * 
     * @param relations relations
     * @param isIncludeTime isIncludeTime
     * @return the result
     * @since 0.1.7
     */
    private static String formatExistingRelations(List<Relation> relations, boolean isIncludeTime) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Relation relation : relations) {
            mapped.add(relation.toMap());
        }
        return formatExistingRelationMaps(mapped, isIncludeTime);
    }

    /**
     * formatExistingRelationMaps.
     * 
     * @param relations relations
     * @param isIncludeTime isIncludeTime
     * @return the result
     * @since 0.1.7
     */
    private static String formatExistingRelationMaps(List<Map<String, Object>> relations, boolean isIncludeTime) {
        List<String> lines = new ArrayList<>();
        int i = 1;
        for (Map<String, Object> relation : relations) {
            String content = String.valueOf(relation.getOrDefault("content", ""));
            int validSince = Integer.parseInt(String.valueOf(relation.getOrDefault("valid_since", -1)));
            int validUntil = Integer.parseInt(String.valueOf(relation.getOrDefault("valid_until", -1)));
            if (isIncludeTime && validSince != -1) {
                content += "\nvalidSince=" + validSince;
            }
            if (isIncludeTime && validUntil != -1) {
                content += "\nvalidUntil=" + validUntil;
            }
            lines.add(i++ + ". " + content);
        }
        return String.join("\n\n", lines);
    }

    /**
     * toJson.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String toJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
