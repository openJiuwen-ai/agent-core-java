/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.memory.graph.extraction.prompts.ThreadSafePromptManager;
import com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction.EntityExtractionPromptBase;
import com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction.EntityExtractionPromptsPackage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Prompt builders and entity extraction orchestration by episode type.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.extraction.extraction_prompts} in
 * {@code openjiuwen/core/memory/graph/extraction/extraction_prompts.py}.</p>
 */
public final class ExtractionPrompts {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter PYTHON_SECONDS_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private ExtractionPrompts() {
    }

    /**
     * Tuple-style return matching Python's {@code (kwargs, prompt_template, response_format)}.
     *
     * @param kwargs formatting keyword arguments
     * @param promptTemplate prompt template selected by name
     * @param responseFormat structured output response format
     */
    public record PromptRequest(Map<String, Object> kwargs,
                                PromptTemplate promptTemplate,
                                Map<String, Object> responseFormat) {

        public PromptRequest {
            kwargs = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
            responseFormat = responseFormat == null ? new LinkedHashMap<>() : new LinkedHashMap<>(responseFormat);
        }
    }

    public static PromptRequest extractEntityDeclaration(EpisodeType srcType,
                                                         String content,
                                                         String history,
                                                         String description,
                                                         List<EntityTypeDefinition.EntityDef> entityTypes,
                                                         String language,
                                                         Map<String, Object> extras,
                                                         int indent) {
        registerLanguages();
        String operation = srcType.name().toLowerCase(Locale.ROOT);
        String templateName = "entity_extraction_" + operation + "_" + language;
        ExtractionModels.EntityExtraction outputModel = new ExtractionModels.EntityExtraction();
        Map<String, Object> kwargs = formattingKwargs(
                description,
                outputModel,
                indent,
                history,
                content,
                language
        );
        mergeExtras(kwargs, extras);
        List<EntityTypeDefinition.EntityDef> effectiveTypes =
                entityTypes == null ? List.of(new EntityTypeDefinition.EntityDef()) : entityTypes;
        List<String> entityTypeLines = new ArrayList<>();
        for (int i = 0; i < effectiveTypes.size(); i++) {
            EntityTypeDefinition.EntityDef entityType = effectiveTypes.get(i);
            entityTypeLines.add(i + ". " + entityType.getName() + entityType.getDescription().get(language));
        }
        kwargs.put("entity_types", String.join("\n", entityTypeLines));
        return request(kwargs, templateName, outputModel, language);
    }

    public static PromptRequest extractEntityAttributes(Entity entity,
                                                        String content,
                                                        String history,
                                                        String language,
                                                        Map<String, Object> extras,
                                                        int indent) {
        registerLanguages();
        String templateName = "entity_extraction_summary_create_" + language;
        ExtractionModels.EntitySummary outputModel = new ExtractionModels.EntitySummary();
        Map<String, Object> kwargs = formattingKwargs(null, outputModel, indent, history, content, language);
        kwargs.put("entity_name", entity.getName());
        kwargs.put("entity_summary", entity.getContent() == null ? "" : entity.getContent());
        if (!entity.getAttributes().isEmpty()) {
            kwargs.put("entity_attribute", toJson(entity.getAttributes(), indent));
        }
        mergeExtras(kwargs, extras);
        if ("human".equalsIgnoreCase(entity.getObjType()) && kwargs.containsKey("summary_target")) {
            Object summaryTarget = kwargs.get("summary_target");
            if (summaryTarget instanceof Number number) {
                kwargs.put("summary_target", number.intValue() * 2);
            } else if (summaryTarget instanceof String text && text.chars().allMatch(Character::isDigit)) {
                kwargs.put("summary_target", Integer.parseInt(text) * 2);
            }
        }
        return request(kwargs, templateName, outputModel, language);
    }

    public static PromptRequest extractRelationDeclaration(List<EntityTypeDefinition.RelationDef> relationTypes,
                                                           List<ExtractionModels.EntityDeclaration> entities,
                                                           long referenceTime,
                                                           Object tzInfo,
                                                           String content,
                                                           String history,
                                                           List<EntityTypeDefinition.EntityDef> entityTypes,
                                                           String description,
                                                           String language,
                                                           int indent) {
        registerLanguages();
        String templateName = "entity_extraction_relation_" + language;
        ExtractionModels.RelationExtraction outputModel = new ExtractionModels.RelationExtraction();
        Map<String, Object> kwargs = formattingKwargs(
                description,
                outputModel,
                indent,
                history,
                content,
                language
        );
        kwargs.put("tz_info", tzInfo instanceof Map<?, ?> || tzInfo instanceof List<?>
                ? toJson(tzInfo, indent)
                : String.valueOf(tzInfo));
        kwargs.put("entities", formatNewEntities(entities, entityTypes, 1, language));
        kwargs.put("relation_types", EntityExtractionPromptBase.formatRelationDefinitions(relationTypes, language));
        kwargs.put("reference_time", Instant.ofEpochSecond(referenceTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(PYTHON_SECONDS_ISO));
        kwargs.put("id_range", "1-" + entities.size());
        return request(kwargs, templateName, outputModel, language);
    }

    public static PromptRequest extractTimezone(String content,
                                                String history,
                                                String description,
                                                String language,
                                                int indent) {
        registerLanguages();
        String templateName = "entity_extraction_timezone_" + language;
        ExtractionModels.TimezonePredictions outputModel = new ExtractionModels.TimezonePredictions();
        Map<String, Object> kwargs = formattingKwargs(
                description,
                outputModel,
                indent,
                history,
                content,
                language
        );
        return request(kwargs, templateName, outputModel, language);
    }

    public static PromptRequest mergeExistingEntities(Entity target,
                                                      List<Entity> sources,
                                                      String language,
                                                      Map<String, Object> extras,
                                                      int indent) {
        registerLanguages();
        String templateName = "entity_extraction_entity_merge_" + language;
        ExtractionModels.EntitySummary outputModel = new ExtractionModels.EntitySummary();
        Map<String, Object> kwargs = formattingKwargs(null, outputModel, indent, "", "", language);
        kwargs.put("entity_name", target.getName());
        kwargs.put("entity_summary", target.getContent() == null ? "" : target.getContent());
        if (!target.getAttributes().isEmpty()) {
            kwargs.put("entity_attribute", toJson(target.getAttributes(), indent));
        }
        kwargs.put("entities_to_merge", EntityExtractionPromptBase.formatExistingEntities(entityMaps(sources), 1, language));
        mergeExtras(kwargs, extras);
        return request(kwargs, templateName, outputModel, language);
    }

    public static PromptRequest filterRelationsForMerge(Entity target,
                                                        List<Relation> relations,
                                                        String language,
                                                        Map<String, Object> extras,
                                                        int indent) {
        registerLanguages();
        String templateName = "entity_extraction_relation_filter_" + language;
        ExtractionModels.RelevantFacts outputModel = new ExtractionModels.RelevantFacts();
        Map<String, Object> kwargs = formattingKwargs(null, outputModel, indent, "", "", language);
        kwargs.put("entity_name", target.getName());
        kwargs.put("entity_summary", target.getContent() == null ? "" : target.getContent());
        if (!target.getAttributes().isEmpty()) {
            kwargs.put("entity_attribute", toJson(target.getAttributes(), indent));
        }
        kwargs.put("existing_relations", EntityExtractionPromptBase.formatExistingRelations(
                relationMaps(relations),
                1,
                false
        ));
        mergeExtras(kwargs, extras);
        return request(kwargs, templateName, outputModel, language);
    }

    public static PromptRequest dedupeEntityList(String content,
                                                 List<ExtractionModels.EntityDeclaration> candidateEntities,
                                                 List<Map<String, Object>> existingEntities,
                                                 List<EntityTypeDefinition.EntityDef> entityTypes,
                                                 String history,
                                                 String description,
                                                 String language,
                                                 int indent) {
        registerLanguages();
        String templateName = "entity_extraction_dedupe_entity_" + language;
        ExtractionModels.EntityDuplication outputModel = new ExtractionModels.EntityDuplication();
        Map<String, Object> kwargs = formattingKwargs(
                description,
                outputModel,
                indent,
                history,
                content,
                language
        );
        kwargs.put("entities", EntityExtractionPromptBase.formatExistingEntities(existingEntities, 1, language));
        kwargs.put("candidate_entities", formatNewEntities(
                candidateEntities,
                entityTypes,
                existingEntities.size() + 1,
                language
        ));
        return request(kwargs, templateName, outputModel, language);
    }

    public static PromptRequest dedupeRelationList(String content,
                                                   Relation relation,
                                                   List<?> existingRelations,
                                                   List<Entity> existingEntities,
                                                   String history,
                                                   String description,
                                                   String language,
                                                   int indent) {
        registerLanguages();
        String templateName = "entity_extraction_dedupe_relation_" + language;
        ExtractionModels.MergeRelations outputModel = new ExtractionModels.MergeRelations();
        Map<String, Object> kwargs = formattingKwargs(
                description,
                outputModel,
                indent,
                history,
                content,
                language
        );
        kwargs.put("entities", EntityExtractionPromptBase.formatExistingEntities(entityMaps(existingEntities), 1, language));
        kwargs.put("existing_relations", EntityExtractionPromptBase.formatExistingRelations(
                relationLikeMaps(existingRelations),
                1,
                true
        ));
        String newRelation = EntityExtractionPromptBase.formatExistingRelations(
                List.of(relationMap(relation)),
                0,
                true
        );
        kwargs.put("new_relation", newRelation.startsWith("0. ") ? newRelation.substring("0. ".length()) : newRelation);
        return request(kwargs, templateName, outputModel, language);
    }

    public static String formatNewEntities(List<ExtractionModels.EntityDeclaration> entities,
                                           List<EntityTypeDefinition.EntityDef> entityTypes,
                                           int startIdx,
                                           String language) {
        registerLanguages();
        List<String> entityList = new ArrayList<>();
        if (entityTypes != null && !entityTypes.isEmpty()) {
            String sep = MultilingualBaseModel.getMultilingualDescription().get(language).get(":");
            TreeSet<Integer> typeIds = new TreeSet<>();
            for (ExtractionModels.EntityDeclaration entity : entities) {
                typeIds.add(entity.getEntityTypeId());
            }
            for (Integer typeId : typeIds) {
                EntityTypeDefinition.EntityDef entityType = entityTypes.get(typeId);
                entityList.add(entityType.getName() + sep + entityType.getDescription().get(language));
            }
            entityList.add("---");
            int index = startIdx;
            for (ExtractionModels.EntityDeclaration entity : entities) {
                entityList.add(index + ". " + entity.getName() + " ("
                        + entityTypes.get(entity.getEntityTypeId()).getName() + ")");
                index++;
            }
        } else {
            int index = startIdx;
            for (ExtractionModels.EntityDeclaration entity : entities) {
                entityList.add(index + ". " + entity.getName());
                index++;
            }
        }
        return String.join("\n", entityList);
    }

    private static PromptRequest request(Map<String, Object> kwargs,
                                         String templateName,
                                         MultilingualBaseModel outputModel,
                                         String language) {
        PromptTemplate template = ThreadSafePromptManager.getInstance().get(templateName);
        return new PromptRequest(kwargs, template, outputModel.responseFormat(language));
    }

    private static Map<String, Object> formattingKwargs(String sourceDescription,
                                                        MultilingualBaseModel outputModel,
                                                        int outputIndent,
                                                        String history,
                                                        String content,
                                                        String language) {
        return new LinkedHashMap<>(EntityExtractionPromptBase.getFormattingKwargs(
                sourceDescription,
                outputModel,
                outputIndent,
                history == null ? "" : history,
                content == null ? "" : content,
                language
        ));
    }

    private static void mergeExtras(Map<String, Object> kwargs, Map<String, Object> extras) {
        if (extras != null && !extras.isEmpty()) {
            kwargs.putAll(extras);
        }
    }

    private static void registerLanguages() {
        EntityExtractionPromptsPackage.registerLanguages();
    }

    private static List<Map<String, Object>> entityMaps(List<Entity> entities) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (entities != null) {
            for (Entity entity : entities) {
                maps.add(entityMap(entity));
            }
        }
        return maps;
    }

    private static Map<String, Object> entityMap(Entity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", entity.getUuid());
        map.put("created_at", entity.getCreatedAt());
        map.put("user_id", entity.getUserId());
        map.put("obj_type", entity.getObjType());
        map.put("language", entity.getLanguage());
        map.put("metadata", entity.getMetadata());
        map.put("content", entity.getContent());
        map.put("name", entity.getName());
        map.put("attributes", entity.getAttributes());
        return map;
    }

    private static List<Map<String, Object>> relationMaps(List<Relation> relations) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (relations != null) {
            for (Relation relation : relations) {
                maps.add(relationMap(relation));
            }
        }
        return maps;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> relationLikeMaps(List<?> relations) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (relations == null) {
            return maps;
        }
        for (Object relation : relations) {
            if (relation instanceof Relation typedRelation) {
                maps.add(relationMap(typedRelation));
            } else if (relation instanceof Map<?, ?> map) {
                maps.add(new LinkedHashMap<>((Map<String, Object>) map));
            } else {
                throw new IllegalArgumentException("relation must be Relation or Map: " + relation);
            }
        }
        return maps;
    }

    private static Map<String, Object> relationMap(Relation relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uuid", relation.getUuid());
        map.put("created_at", relation.getCreatedAt());
        map.put("user_id", relation.getUserId());
        map.put("obj_type", relation.getObjType());
        map.put("language", relation.getLanguage());
        map.put("metadata", relation.getMetadata());
        map.put("content", relation.getContent());
        map.put("name", relation.getName());
        map.put("valid_since", relation.getValidSince());
        map.put("valid_until", relation.getValidUntil());
        map.put("offset_since", relation.getOffsetSince());
        map.put("offset_until", relation.getOffsetUntil());
        return map;
    }

    private static String toJson(Object value, int indent) {
        try {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            DefaultIndenter indenter = new DefaultIndenter(" ".repeat(Math.max(indent, 0)), "\n");
            printer.indentObjectsWith(indenter);
            printer.indentArraysWith(indenter);
            ObjectWriter writer = OBJECT_MAPPER.writer(printer);
            return writer.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to serialize prompt value", ex);
        }
    }
}
