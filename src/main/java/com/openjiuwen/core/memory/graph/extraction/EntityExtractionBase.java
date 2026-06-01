/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Formatting helpers for entity extraction prompts.
 * <p>
 * Mirrors Python's {@code prompts.entity_extraction.base}.
 */
public final class EntityExtractionBase {

    public static final Set<String> REGISTERED_LANGUAGE = new LinkedHashSet<>();
    public static final Map<String, String> SOURCE_DESCRIPTION = new LinkedHashMap<>();
    public static final Map<String, String> REF_JSON_OBJECT_DEF = new LinkedHashMap<>();
    public static final Map<String, String> OUTPUT_FORMAT = new LinkedHashMap<>();
    public static final Map<String, String> DISPLAY_ENTITY = new LinkedHashMap<>();
    public static final Map<String, String> MARK_CURRENT_MSG = new LinkedHashMap<>();
    public static final Map<String, String> MARK_HISTORY_MSG = new LinkedHashMap<>();
    public static final Map<String, String> RELATION_FORMAT = new LinkedHashMap<>();
    public static final Map<String, String> NO_RELATION_GIVEN = new LinkedHashMap<>();

    public static final String SCHEMA_INFO_HEADER = "\n\n---\n";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_SECONDS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    static {
        REGISTERED_LANGUAGE.add("cn");
        REGISTERED_LANGUAGE.add("en");
        SOURCE_DESCRIPTION.put("cn", "Source: {source_description}");
        SOURCE_DESCRIPTION.put("en", "Source: {source_description}");
        REF_JSON_OBJECT_DEF.put("cn", "JSON object definitions");
        REF_JSON_OBJECT_DEF.put("en", "JSON object definitions");
        OUTPUT_FORMAT.put("cn", "Output format");
        OUTPUT_FORMAT.put("en", "Output format");
        DISPLAY_ENTITY.put("cn", "{i}. {name}: {content}");
        DISPLAY_ENTITY.put("en", "{i}. {name}: {content}");
        MARK_CURRENT_MSG.put("cn", "Current: {content}");
        MARK_CURRENT_MSG.put("en", "Current: {content}");
        MARK_HISTORY_MSG.put("cn", "History: {history}");
        MARK_HISTORY_MSG.put("en", "History: {history}");
        RELATION_FORMAT.put("cn", "{name}: {description} ({lhs}-{rhs})");
        RELATION_FORMAT.put("en", "{name}: {description} ({lhs}-{rhs})");
        NO_RELATION_GIVEN.put("cn", "No relations");
        NO_RELATION_GIVEN.put("en", "No relations");
    }

    private EntityExtractionBase() {
    }

    public static String formatSchemaInfo(MultilingualBaseModel outputModel, String language) {
        return formatSchemaInfo(outputModel, 2, language);
    }

    public static String formatSchemaInfo(MultilingualBaseModel outputModel, int indent, String language) {
        if (outputModel == null) {
            return "";
        }
        MultilingualBaseModel.ReadableSchema readableSchema = outputModel.readableSchema(language);
        StringBuilder schemaInfo = new StringBuilder(SCHEMA_INFO_HEADER);
        if (!readableSchema.refs().isEmpty()) {
            schemaInfo.append("# ").append(REF_JSON_OBJECT_DEF.get(language)).append('\n');
            for (Map.Entry<String, Object> ref : readableSchema.refs().entrySet()) {
                schemaInfo.append("## ").append(ref.getKey()).append('\n')
                        .append("```json\n")
                        .append(toJson(ref.getValue()))
                        .append("\n```\n");
            }
        }
        schemaInfo.append("---\n# ").append(OUTPUT_FORMAT.get(language)).append('\n')
                .append("```python\n")
                .append(readableSchema.outputSchema())
                .append("\n```");
        return schemaInfo.toString();
    }

    public static String formatSourceDescription(String sourceDescription, String language) {
        if (sourceDescription == null || sourceDescription.isEmpty()) {
            return "";
        }
        return formatTemplate(SOURCE_DESCRIPTION.get(language), Map.of("source_description", sourceDescription));
    }

    public static Map<String, String> getFormattingKwargs(
            String sourceDescription,
            MultilingualBaseModel outputModel,
            int outputIndent,
            String history,
            String content,
            String language) {
        StringBuilder context = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            context.append(formatTemplate(MARK_HISTORY_MSG.get(language), Map.of("history", history)));
        }
        if (content != null && !content.isEmpty()) {
            context.append(formatTemplate(MARK_CURRENT_MSG.get(language), Map.of("content", content)));
        }

        Map<String, String> kwargs = new LinkedHashMap<>();
        kwargs.put("source_description", formatSourceDescription(sourceDescription, language));
        kwargs.put("extra_message", formatSchemaInfo(outputModel, outputIndent, language));
        kwargs.put("context", context.toString());
        return kwargs;
    }

    public static Map<String, String> getFormattingKwargs(String history, String content, String language) {
        return getFormattingKwargs(null, null, 2, history, content, language);
    }

    public static String formatRelationDefinitions(
            List<EntityTypeDefinition.RelationDef> relationTypes,
            String language) {
        if (relationTypes == null || relationTypes.isEmpty()) {
            return NO_RELATION_GIVEN.get(language);
        }
        String template = RELATION_FORMAT.get(language);
        return String.join("\n", relationTypes.stream()
                .map(relation -> formatTemplate(template, Map.of(
                        "name", relation.getName(),
                        "description", relation.getDescription().getOrDefault(language, ""),
                        "lhs", entityName(relation.getLhs()),
                        "rhs", entityName(relation.getRhs()))))
                .toList());
    }

    public static String formatExistingRelations(List<Map<String, Object>> relations) {
        return formatExistingRelations(relations, 1, true);
    }

    public static String formatExistingRelations(
            List<Map<String, Object>> relations,
            int startIdx,
            boolean includeTime) {
        StringBuilder result = new StringBuilder();
        int index = startIdx;
        for (Map<String, Object> relation : relations) {
            if (!result.isEmpty()) {
                result.append("\n\n");
            }
            StringBuilder content = new StringBuilder(String.valueOf(relation.getOrDefault("content", "")));
            long validSince = longValue(relation.get("valid_since"), 0L);
            long validUntil = longValue(relation.get("valid_until"), 0L);
            if (includeTime && validSince != -1) {
                content.append("\nvalid_since=").append(formatStoredTime(validSince));
            }
            if (includeTime && validUntil != -1) {
                content.append("\nvalid_until=").append(formatStoredTime(validUntil));
            }
            result.append(index).append(". ").append(content);
            index++;
        }
        return result.toString();
    }

    public static String formatExistingEntities(
            List<Map<String, Object>> entities,
            int startIdx,
            String language) {
        String template = DISPLAY_ENTITY.get(language);
        StringBuilder result = new StringBuilder();
        int index = startIdx;
        for (Map<String, Object> entity : entities) {
            if (!result.isEmpty()) {
                result.append("\n\n");
            }
            Map<String, Object> values = new LinkedHashMap<>(entity);
            values.put("i", index);
            result.append(formatTemplate(template, values));
            index++;
        }
        return result.toString();
    }

    public static String ensureValidLanguage(Object language, int maxLen) {
        if (language == null) {
            throw languageError("graph memory language option cannot be casted to string");
        }
        String languageString = language instanceof String s ? s : language.toString();
        if (!REGISTERED_LANGUAGE.contains(languageString)) {
            throw languageError("graph memory does not support language " + languageString
                    + ", registered: " + REGISTERED_LANGUAGE);
        }
        if (languageString.length() > maxLen) {
            throw languageError("language \"" + languageString
                    + "\" exceeds max length set in db_storage_config.language (" + maxLen + ")");
        }
        return languageString;
    }

    private static String formatTemplate(String template, Map<String, ?> values) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static String entityName(Class<? extends EntityTypeDefinition.EntityDef> entityClass) {
        if (entityClass == null) {
            return "";
        }
        try {
            return entityClass.getDeclaredConstructor().newInstance().getName();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                 | NoSuchMethodException e) {
            return entityClass.getSimpleName();
        }
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String formatStoredTime(long epochSecond) {
        return ISO_SECONDS.format(Instant.ofEpochSecond(epochSecond).atOffset(ZoneOffset.UTC));
    }

    private static String toJson(Object value) {
        try {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static BaseError languageError(String message) {
        return new BaseError(StatusCode.MEMORY_INIT_ERROR, message, null, null);
    }
}
