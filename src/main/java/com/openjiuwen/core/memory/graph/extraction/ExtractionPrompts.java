/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt argument assembly helpers for graph extraction.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.graph.extraction.extraction_prompts}.
 */
public final class ExtractionPrompts {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ExtractionPrompts() {
    }

    public record PromptArtifacts(Map<String, String> kwargs,
                                  PromptTemplate template,
                                  Map<String, Object> responseFormat) {
    }

    public static PromptArtifacts extractEntityDeclaration(EpisodeType srcType, String content, String language) {
        return extractEntityDeclaration(srcType, content, "", null, null, language, null, 2);
    }

    public static PromptArtifacts extractEntityDeclaration(
            EpisodeType srcType,
            String content,
            String history,
            String description,
            List<EntityTypeDefinition.EntityDef> entityTypes,
            String language,
            Map<String, String> extras,
            int indent) {
        String operation = srcType.name().toLowerCase();
        ExtractionModels.EntityExtraction outputModel = new ExtractionModels.EntityExtraction();
        Map<String, String> kwargs = EntityExtractionBase.getFormattingKwargs(
                description, outputModel, indent, history, content, language);
        if (extras != null) {
            kwargs.putAll(extras);
        }
        List<EntityTypeDefinition.EntityDef> effectiveTypes =
                entityTypes == null ? List.of(new EntityTypeDefinition.EntityDef()) : entityTypes;
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < effectiveTypes.size(); i++) {
            EntityTypeDefinition.EntityDef type = effectiveTypes.get(i);
            lines.add(i + ". " + type.getName() + type.getDescription().getOrDefault(language, ""));
        }
        kwargs.put("entity_types", String.join("\n", lines));
        return artifacts(kwargs, "entity_extraction_" + operation + "_" + language,
                outputModel.responseFormat(language));
    }

    public static PromptArtifacts extractEntityAttributes(Entity entity, String content, String language) {
        ExtractionModels.EntitySummary outputModel = new ExtractionModels.EntitySummary();
        Map<String, String> kwargs = EntityExtractionBase.getFormattingKwargs(
                null, outputModel, 2, "", content, language);
        kwargs.put("entity_name", entity.getName());
        kwargs.put("entity_summary", entity.getContent() == null ? "" : entity.getContent());
        if (entity.getAttributes() != null && !entity.getAttributes().isEmpty()) {
            kwargs.put("entity_attribute", toJson(entity.getAttributes()));
        }
        if ("human".equalsIgnoreCase(entity.getEntityType()) && kwargs.containsKey("summary_target")) {
            try {
                kwargs.put("summary_target", String.valueOf(Integer.parseInt(kwargs.get("summary_target")) * 2));
            } catch (NumberFormatException ignored) {
                // Preserve the original Python behavior only when the value is numeric.
            }
        }
        return artifacts(kwargs, "entity_extraction_summary_create_" + language,
                outputModel.responseFormat(language));
    }

    public static PromptArtifacts extractRelationDeclaration(
            List<EntityTypeDefinition.RelationDef> relationTypes,
            List<ExtractionModels.EntityDeclaration> entities,
            long referenceTime,
            Object tzInfo,
            String content,
            String language) {
        ExtractionModels.RelationExtraction outputModel = new ExtractionModels.RelationExtraction();
        Map<String, String> kwargs = EntityExtractionBase.getFormattingKwargs(
                null, outputModel, 2, "", content, language);
        kwargs.put("tz_info", tzInfo instanceof Map<?, ?> || tzInfo instanceof List<?> ? toJson(tzInfo) : String.valueOf(tzInfo));
        kwargs.put("entities", formatNewEntities(entities, null, 1, language));
        kwargs.put("relation_types", EntityExtractionBase.formatRelationDefinitions(relationTypes, language));
        kwargs.put("reference_time", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                Instant.ofEpochSecond(referenceTime).atZone(ZoneId.systemDefault()).toLocalDateTime()));
        kwargs.put("id_range", "1-" + entities.size());
        return artifacts(kwargs, "entity_extraction_relation_" + language,
                outputModel.responseFormat(language));
    }

    public static PromptArtifacts extractTimezone(String content, String language) {
        ExtractionModels.TimezonePredictions outputModel = new ExtractionModels.TimezonePredictions();
        Map<String, String> kwargs = EntityExtractionBase.getFormattingKwargs(
                null, outputModel, 2, "", content, language);
        return artifacts(kwargs, "entity_extraction_timezone_" + language, outputModel.responseFormat(language));
    }

    public static PromptArtifacts mergeExistingEntities(Entity target, List<Entity> sources, String language) {
        ExtractionModels.EntitySummary outputModel = new ExtractionModels.EntitySummary();
        Map<String, String> kwargs = EntityExtractionBase.getFormattingKwargs(null, outputModel, 2, "", "", language);
        kwargs.put("entity_name", target.getName());
        kwargs.put("entity_summary", target.getContent() == null ? "" : target.getContent());
        if (target.getAttributes() != null && !target.getAttributes().isEmpty()) {
            kwargs.put("entity_attribute", toJson(target.getAttributes()));
        }
        kwargs.put("entities_to_merge", EntityExtractionBase.formatExistingEntities(
                sources.stream().map(ExtractionPrompts::entityMap).toList(), 1, language));
        return artifacts(kwargs, "entity_extraction_entity_merge_" + language, outputModel.responseFormat(language));
    }

    public static PromptArtifacts filterRelationsForMerge(Entity target, List<Relation> relations, String language) {
        ExtractionModels.RelevantFacts outputModel = new ExtractionModels.RelevantFacts();
        Map<String, String> kwargs = EntityExtractionBase.getFormattingKwargs(null, outputModel, 2, "", "", language);
        kwargs.put("entity_name", target.getName());
        kwargs.put("entity_summary", target.getContent() == null ? "" : target.getContent());
        kwargs.put("existing_relations", EntityExtractionBase.formatExistingRelations(
                relations.stream().map(ExtractionPrompts::relationMap).toList(), 1, false));
        return artifacts(kwargs, "entity_extraction_relation_filter_" + language, outputModel.responseFormat(language));
    }

    public static PromptArtifacts dedupeEntityList(
            String content,
            List<ExtractionModels.EntityDeclaration> candidateEntities,
            List<Map<String, Object>> existingEntities,
            String language) {
        ExtractionModels.EntityDuplication outputModel = new ExtractionModels.EntityDuplication();
        Map<String, String> kwargs = EntityExtractionBase.getFormattingKwargs(
                null, outputModel, 2, "", content, language);
        kwargs.put("entities", EntityExtractionBase.formatExistingEntities(existingEntities, 1, language));
        kwargs.put("candidate_entities", formatNewEntities(candidateEntities, null, existingEntities.size() + 1, language));
        return artifacts(kwargs, "entity_extraction_dedupe_entity_" + language, outputModel.responseFormat(language));
    }

    public static PromptArtifacts dedupeRelationList(
            String content,
            Relation relation,
            List<Relation> existingRelations,
            List<Entity> existingEntities,
            String language) {
        ExtractionModels.MergeRelations outputModel = new ExtractionModels.MergeRelations();
        Map<String, String> kwargs = EntityExtractionBase.getFormattingKwargs(
                null, outputModel, 2, "", content, language);
        kwargs.put("entities", EntityExtractionBase.formatExistingEntities(
                existingEntities.stream().map(ExtractionPrompts::entityMap).toList(), 1, language));
        kwargs.put("existing_relations", EntityExtractionBase.formatExistingRelations(
                existingRelations.stream().map(ExtractionPrompts::relationMap).toList(), 1, true));
        String newRelation = EntityExtractionBase.formatExistingRelations(List.of(relationMap(relation)), 0, true);
        kwargs.put("new_relation", newRelation.startsWith("0. ") ? newRelation.substring(3) : newRelation);
        return artifacts(kwargs, "entity_extraction_dedupe_relation_" + language, outputModel.responseFormat(language));
    }

    public static String formatNewEntities(List<ExtractionModels.EntityDeclaration> entities, String language) {
        return formatNewEntities(entities, null, 1, language);
    }

    public static String formatNewEntities(
            List<ExtractionModels.EntityDeclaration> entities,
            List<EntityTypeDefinition.EntityDef> entityTypes,
            int startIdx,
            String language) {
        if (entities == null || entities.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        if (entityTypes != null && !entityTypes.isEmpty()) {
            String sep = MultilingualBaseModel.MULTILINGUAL_DESCRIPTION
                    .getOrDefault(language, Map.of())
                    .getOrDefault(":", ":");
            List<Integer> usedTypeIds = entities.stream().map(ExtractionModels.EntityDeclaration::getEntityTypeId)
                    .distinct().sorted().toList();
            for (Integer typeId : usedTypeIds) {
                EntityTypeDefinition.EntityDef type = entityTypes.get(typeId);
                lines.add(type.getName() + sep + type.getDescription().getOrDefault(language, ""));
            }
            lines.add("---");
            int i = startIdx;
            for (ExtractionModels.EntityDeclaration entity : entities) {
                lines.add(i + ". " + entity.getName() + " ("
                        + entityTypes.get(entity.getEntityTypeId()).getName() + ")");
                i++;
            }
        } else {
            int i = startIdx;
            for (ExtractionModels.EntityDeclaration entity : entities) {
                lines.add(i + ". " + entity.getName());
                i++;
            }
        }
        return String.join("\n", lines);
    }

    private static PromptArtifacts artifacts(Map<String, String> kwargs, String templateName,
                                             Map<String, Object> responseFormat) {
        return new PromptArtifacts(kwargs, new PromptTemplate(templateName, "", "{{", "}}"), responseFormat);
    }

    private static Map<String, Object> entityMap(Entity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", entity.getName());
        map.put("content", entity.getContent() == null ? "" : entity.getContent());
        map.put("obj_type", entity.getObjType());
        return map;
    }

    private static Map<String, Object> relationMap(Relation relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", relation.getContent() == null ? "" : relation.getContent());
        map.put("lhs", relation.getLhs());
        map.put("rhs", relation.getRhs());
        map.put("valid_since", relation.getValidSince());
        map.put("valid_until", relation.getValidUntil());
        return map;
    }

    private static String toJson(Object value) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
