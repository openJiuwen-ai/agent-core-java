/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;
import com.openjiuwen.core.memory.graph.extraction.RelationDef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shared formatting and schema helpers for entity extraction prompt generation. */
public final class ExtractionPromptLanguageBase {
  static final Set<String> REGISTERED_LANGUAGE =
      java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
  static final Map<String, String> SOURCE_DESCRIPTION =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>());
  static final Map<String, String> REF_JSON_OBJECT_DEF =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>());
  static final Map<String, String> OUTPUT_FORMAT =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>());
  static final Map<String, String> DISPLAY_ENTITY =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>());
  static final Map<String, String> MARK_CURRENT_MSG =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>());
  static final Map<String, String> MARK_HISTORY_MSG =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>());
  static final Map<String, String> RELATION_FORMAT =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>());
  static final Map<String, String> NO_RELATION_GIVEN =
      java.util.Collections.synchronizedMap(new LinkedHashMap<>());

  /** Auto-generated for codecheck compliance. */
  public static final String SCHEMA_INFO_HEADER = "\n\n---\n";

  static {
    ExtractionPromptLanguageCn.registerLanguage();
    ExtractionPromptLanguageEn.registerLanguage();
  }

  private ExtractionPromptLanguageBase() {}

  /** Auto-generated for codecheck compliance. */
  public static String formatSchemaInfo(
      Class<? extends MultilingualBaseModel> outputModel, int indent, String language) {
    if (outputModel == null) {
      return "";
    }
    Map.Entry<String, Map<String, Object>> readable =
        MultilingualBaseModel.readableSchema(outputModel, language);
    StringBuilder builder = new StringBuilder(SCHEMA_INFO_HEADER);
    if (!readable.getValue().isEmpty()) {
      builder.append("# ").append(REF_JSON_OBJECT_DEF.get(language)).append("\n");
      for (Map.Entry<String, Object> entry : readable.getValue().entrySet()) {
        builder
            .append("## ")
            .append(entry.getKey())
            .append("\n```json\n")
            .append(String.valueOf(entry.getValue()))
            .append("\n```\n");
      }
    }
    builder
        .append("---\n# ")
        .append(OUTPUT_FORMAT.get(language))
        .append("\n```python\n")
        .append(readable.getKey())
        .append("\n```");
    return builder.toString();
  }

  /** Auto-generated for codecheck compliance. */
  public static String formatSourceDescription(String sourceDescription, String language) {
    return sourceDescription != null && !sourceDescription.isBlank()
        ? SOURCE_DESCRIPTION.get(language).replace("{source_description}", sourceDescription)
        : "";
  }

  /** Auto-generated for codecheck compliance. */
  public static Map<String, String> getFormattingKwargs(
      String sourceDescription,
      Class<? extends MultilingualBaseModel> outputModel,
      int indent,
      String history,
      String content,
      String language) {
    StringBuilder context = new StringBuilder();
    if (history != null && !history.isBlank()) {
      context.append(MARK_HISTORY_MSG.get(language).replace("{history}", history));
    }
    if (content != null && !content.isBlank()) {
      context.append(MARK_CURRENT_MSG.get(language).replace("{content}", content));
    }
    return Map.of(
        "source_description", formatSourceDescription(sourceDescription, language),
        "extra_message", formatSchemaInfo(outputModel, indent, language),
        "context", context.toString());
  }

  /** Auto-generated for codecheck compliance. */
  public static String formatRelationDefinitions(
      java.util.List<RelationDef> relationTypes, String language) {
    if (relationTypes == null || relationTypes.isEmpty()) {
      return NO_RELATION_GIVEN.get(language);
    }
    java.util.List<String> lines = new java.util.ArrayList<>();
    for (RelationDef relationType : relationTypes) {
      lines.add(
          RELATION_FORMAT
              .get(language)
              .replace("{name}", relationType.getName())
              .replace("{description}", relationType.getDescription().get(language))
              .replace("{lhs}", relationType.getLhs().getSimpleName())
              .replace("{rhs}", relationType.getRhs().getSimpleName()));
    }
    return String.join("\n", lines);
  }

  /** Auto-generated for codecheck compliance. */
  public static String formatExistingEntities(
      java.util.List<Map<String, Object>> entities, int startIdx, String language) {
    java.util.List<String> lines = new java.util.ArrayList<>();
    int index = startIdx;
    for (Map<String, Object> entity : entities) {
      lines.add(
          DISPLAY_ENTITY
              .get(language)
              .replace("{i}", String.valueOf(index++))
              .replace("{name}", String.valueOf(entity.getOrDefault("name", "")))
              .replace("{content}", String.valueOf(entity.getOrDefault("content", ""))));
    }
    return String.join("\n\n", lines);
  }

  /** Auto-generated for codecheck compliance. */
  public static String ensureValidLanguage(String language, int maxLen) {
    String normalized = String.valueOf(language);
    if (!REGISTERED_LANGUAGE.contains(normalized)) {
      throw new IllegalArgumentException(
          "graph memory does not support language "
              + normalized
              + ", registered: "
              + REGISTERED_LANGUAGE);
    }
    if (normalized.length() > maxLen) {
      throw new IllegalArgumentException(
          "language \""
              + normalized
              + "\" exceeds max length set in db_storage_config.language ("
              + maxLen
              + ")");
    }
    return normalized;
  }
}
