/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.graph.GraphStoreUtils;
import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;

import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared formatting and schema helpers for entity extraction prompt generation.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.extraction.prompts.entity_extraction.base} in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/base.py}.</p>
 */
public final class EntityExtractionPromptBase {

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TEMPLATE_FIELD = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final DateTimeFormatter PYTHON_SECONDS_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private EntityExtractionPromptBase() {
    }

    public static String formatSchemaInfo(MultilingualBaseModel outputModel) {
        return formatSchemaInfo(outputModel, 2, "cn");
    }

    public static String formatSchemaInfo(MultilingualBaseModel outputModel, int indent, String language) {
        if (outputModel == null) {
            return "";
        }
        MultilingualBaseModel.ReadableSchema readableSchema = outputModel.readableSchema(language);
        StringBuilder schemaInfo = new StringBuilder(SCHEMA_INFO_HEADER);
        if (readableSchema.refs() != null && !readableSchema.refs().isEmpty()) {
            schemaInfo.append("# ").append(REF_JSON_OBJECT_DEF.get(language)).append('\n');
            for (Map.Entry<String, Object> entry : readableSchema.refs().entrySet()) {
                schemaInfo.append("## ").append(entry.getKey()).append("\n```json\n")
                        .append(toJson(entry.getValue(), indent)).append("\n```\n");
            }
        }
        schemaInfo.append("---\n# ").append(OUTPUT_FORMAT.get(language)).append("\n```python\n")
                .append(readableSchema.outputSchema()).append("\n```");
        return schemaInfo.toString();
    }

    public static String formatSourceDescription(String sourceDescription) {
        return formatSourceDescription(sourceDescription, "cn");
    }

    public static String formatSourceDescription(String sourceDescription, String language) {
        if (sourceDescription == null || sourceDescription.isEmpty()) {
            return "";
        }
        return renderTemplate(SOURCE_DESCRIPTION.get(language), Map.of("source_description", sourceDescription));
    }

    public static Map<String, String> getFormattingKwargs(String sourceDescription,
                                                          MultilingualBaseModel outputModel,
                                                          int outputIndent,
                                                          String history,
                                                          String content,
                                                          String language) {
        StringBuilder context = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            context.append(renderTemplate(MARK_HISTORY_MSG.get(language), Map.of("history", history)));
        }
        if (content != null && !content.isEmpty()) {
            context.append(renderTemplate(MARK_CURRENT_MSG.get(language), Map.of("content", content)));
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("source_description", formatSourceDescription(sourceDescription, language));
        result.put("extra_message", formatSchemaInfo(outputModel, outputIndent, language));
        result.put("context", context.toString());
        return result;
    }

    public static Map<String, String> getFormattingKwargs(String sourceDescription,
                                                          MultilingualBaseModel outputModel,
                                                          int outputIndent,
                                                          String history,
                                                          String content) {
        return getFormattingKwargs(sourceDescription, outputModel, outputIndent, history, content, "cn");
    }

    public static String formatRelationDefinitions(List<EntityTypeDefinition.RelationDef> relationTypes,
                                                   String language) {
        if (relationTypes == null || relationTypes.isEmpty()) {
            return NO_RELATION_GIVEN.get(language);
        }
        String template = RELATION_FORMAT.get(language);
        List<String> rendered = new ArrayList<>();
        for (EntityTypeDefinition.RelationDef relationType : relationTypes) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("name", relationType.getName());
            values.put("description", relationType.getDescription().get(language));
            values.put("lhs", relationEndpointName(relationType.getLhs()));
            values.put("rhs", relationEndpointName(relationType.getRhs()));
            rendered.add(renderTemplate(template, values));
        }
        return String.join("\n", rendered);
    }

    public static String formatRelationDefinitions(List<EntityTypeDefinition.RelationDef> relationTypes) {
        return formatRelationDefinitions(relationTypes, "cn");
    }

    public static String formatExistingRelations(List<Map<String, Object>> relations) {
        return formatExistingRelations(relations, 1, true);
    }

    public static String formatExistingRelations(List<Map<String, Object>> relations,
                                                 int startIdx,
                                                 boolean includeTime) {
        List<String> stringBuilder = new ArrayList<>();
        int index = startIdx;
        for (Map<String, Object> relation : relations) {
            String content = stringValue(relation.getOrDefault("content", ""));
            double validSince = numberValue(relation.getOrDefault("valid_since", 0));
            double validUntil = numberValue(relation.getOrDefault("valid_until", 0));
            if (includeTime && validSince != -1) {
                int offsetSince = intValue(relation.getOrDefault("offset_since", 0));
                content += "\nvalid_since=" + formatStoredTime(validSince, offsetSince);
            }
            if (includeTime && validUntil != -1) {
                int offsetUntil = intValue(relation.getOrDefault("offset_until", 0));
                content += "\nvalid_until=" + formatStoredTime(validUntil, offsetUntil);
            }
            stringBuilder.add(index + ". " + content);
            index++;
        }
        return String.join("\n\n", stringBuilder);
    }

    public static String formatExistingEntities(List<Map<String, Object>> entities) {
        return formatExistingEntities(entities, 1, "cn");
    }

    public static String formatExistingEntities(List<Map<String, Object>> entities, int startIdx, String language) {
        String template = DISPLAY_ENTITY.get(language);
        List<String> rendered = new ArrayList<>();
        int index = startIdx;
        for (Map<String, Object> entity : entities) {
            Map<String, Object> values = new LinkedHashMap<>(entity);
            values.put("i", index);
            rendered.add(renderTemplate(template, values));
            index++;
        }
        return String.join("\n\n", rendered);
    }

    public static String ensureValidLanguage(Object language, int maxLen) {
        String languageText = language instanceof String value ? value : String.valueOf(language);
        if (!REGISTERED_LANGUAGE.contains(languageText)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GRAPH_LANGUAGE_INVALID,
                    "error_msg",
                    "graph memory does not support language " + languageText + ", registered: " + REGISTERED_LANGUAGE);
        }
        if (languageText.length() > maxLen) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_GRAPH_LANGUAGE_INVALID,
                    "error_msg",
                    "language \"" + languageText + "\" exceeds max length set in db_storage_config.language (" + maxLen + ")");
        }
        return languageText;
    }

    private static String relationEndpointName(Class<? extends EntityTypeDefinition.EntityDef> endpointClass) {
        try {
            return endpointClass.getDeclaredConstructor().newInstance().getName();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            throw new IllegalArgumentException("relation endpoint cannot be instantiated: " + endpointClass, ex);
        }
    }

    private static String formatStoredTime(double timestamp, int offset) {
        Optional<OffsetDateTime> dateTime = GraphStoreUtils.loadStoredTimeFromDb(timestamp, offset);
        return dateTime.map(value -> value.toLocalDateTime().format(PYTHON_SECONDS_ISO) + formatOffset(value))
                .orElse("");
    }

    private static String formatOffset(OffsetDateTime dateTime) {
        String offset = dateTime.getOffset().getId();
        return "Z".equals(offset) ? "+00:00" : offset;
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
            throw new IllegalArgumentException("failed to serialize schema reference", ex);
        }
    }

    private static String renderTemplate(String template, Map<String, ?> values) {
        Matcher matcher = TEMPLATE_FIELD.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("missing template field: " + key);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(stringValue(values.get(key))));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String stringValue(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
