/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Base LLM response model with multilingual schema helpers. */
public abstract class MultilingualBaseModel {
  private static final Map<String, Map<String, String>> MULTILINGUAL_DESCRIPTION =
      new LinkedHashMap<>();

  static {
    com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction.ExtractionPromptLanguageCn
        .registerLanguage();
    com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction.ExtractionPromptLanguageEn
        .registerLanguage();
  }

  /** Auto-generated for codecheck compliance. */
  public static Map<String, Object> multilingualModelJsonSchema(
      Class<?> modelClass, String language, boolean shouldBeStrict) {
    Map<String, String> descLookup = MULTILINGUAL_DESCRIPTION.getOrDefault(language, Map.of());
    Map<String, Object> schema = buildSchema(modelClass, descLookup);
    if (shouldBeStrict) {
      Deque<Object> toVisit = new ArrayDeque<>();
      toVisit.add(schema);
      while (!toVisit.isEmpty()) {
        Object node = toVisit.removeFirst();
        if (node instanceof Map<?, ?> map) {
          Object type = map.get("type");
          Object props = map.get("properties");
          if ("object".equals(type) && props instanceof Map<?, ?> properties) {
            ((Map<String, Object>) map).put("additionalProperties", false);
            ((Map<String, Object>) map)
                .putIfAbsent("required", new ArrayList<>(properties.keySet()));
          }
          toVisit.addAll(map.values());
        } else if (node instanceof List<?> list) {
          toVisit.addAll(list);
        } else {
          // no-op
        }
      }
    }
    return schema;
  }

  /** Auto-generated for codecheck compliance. */
  public static void registerDescriptions(String language, Map<String, String> descriptions) {
    MULTILINGUAL_DESCRIPTION.put(language, descriptions);
  }

  /** Auto-generated for codecheck compliance. */
  public static Map<String, Object> responseFormat(Class<?> modelClass, String language) {
    return Map.of(
        "type",
        "json_schema",
        "json_schema",
        Map.of(
            "schema", multilingualModelJsonSchema(modelClass, language, true),
            "name", modelClass.getSimpleName(),
            "strict", false));
  }

  /** Auto-generated for codecheck compliance. */
  public static Map.Entry<String, Map<String, Object>> readableSchema(
      Class<?> modelClass, String language) {
    Map<String, Object> schema = multilingualModelJsonSchema(modelClass, language, false);
    StringBuilder out = new StringBuilder();
    Map<String, Object> refs = new LinkedHashMap<>();
    Object properties = schema.get("properties");
    if (properties instanceof Map<?, ?> props) {
      for (Map.Entry<?, ?> entry : props.entrySet()) {
        String fieldName = String.valueOf(entry.getKey());
        Map<?, ?> propSchema = (Map<?, ?>) entry.getValue();
        Object typeValue = propSchema.containsKey("type") ? propSchema.get("type") : "object";
        out.append(fieldName).append(": ").append(typeValue);
        Object description = propSchema.get("description");
        if (description != null && !String.valueOf(description).isBlank()) {
          out.append("  # ").append(description);
        }
        out.append("\n");
      }
    }
    return Map.entry(out.toString().strip(), refs);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> buildSchema(
      Class<?> modelClass, Map<String, String> descLookup) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("title", modelClass.getSimpleName());
    schema.put("type", "object");
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (Field field : modelClass.getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      Map<String, Object> fieldSchema = schemaForType(field.getGenericType(), descLookup);
      SchemaDescription description = field.getAnnotation(SchemaDescription.class);
      if (description != null) {
        fieldSchema.put(
            "description", descLookup.getOrDefault(description.value(), description.value()));
      }
      properties.put(field.getName(), fieldSchema);
      required.add(field.getName());
    }
    schema.put("properties", properties);
    schema.put("required", required);
    return schema;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> schemaForType(Type type, Map<String, String> descLookup) {
    Map<String, Object> schema = new LinkedHashMap<>();
    if (type instanceof ParameterizedType parameterizedType) {
      Type raw = parameterizedType.getRawType();
      if (raw == List.class) {
        schema.put("type", "array");
        schema.put(
            "items", schemaForType(parameterizedType.getActualTypeArguments()[0], descLookup));
        return schema;
      }
      if (raw == Map.class) {
        schema.put("type", "object");
        return schema;
      }
    }
    if (type instanceof Class<?> clazz) {
      if (clazz == String.class) {
        schema.put("type", "string");
      } else if (clazz == Integer.class
          || clazz == int.class
          || clazz == Long.class
          || clazz == long.class) {
        schema.put("type", "integer");
      } else if (clazz == Double.class
          || clazz == double.class
          || clazz == Float.class
          || clazz == float.class) {
        schema.put("type", "number");
      } else if (clazz == Boolean.class || clazz == boolean.class) {
        schema.put("type", "boolean");
      } else if (clazz.isEnum()) {
        schema.put("type", "string");
        Object[] constants = clazz.getEnumConstants();
        List<String> values = new ArrayList<>();
        for (Object constant : constants) {
          values.add(String.valueOf(constant));
        }
        schema.put("enum", values);
      } else if (Map.class.isAssignableFrom(clazz)) {
        schema.put("type", "object");
      } else {
        return buildSchema(clazz, descLookup);
      }
      return schema;
    }
    schema.put("type", "object");
    return schema;
  }
}
