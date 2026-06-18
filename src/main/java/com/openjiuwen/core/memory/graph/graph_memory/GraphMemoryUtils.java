/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.memory.graph.extraction.ParseResponse;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Message conversion and entity update helpers for graph memory.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.utils} module in
 * {@code openjiuwen/core/memory/graph/graph_memory/utils.py}.</p>
 */
public final class GraphMemoryUtils {

    private static final Logger LOGGER = Logger.getLogger(GraphMemoryUtils.class.getName());
    private static final Set<String> NULL_SUMMARY_WORDS = Set.of("null", "none", "empty");

    private GraphMemoryUtils() {
    }

    public static List<Map<String, Object>> msgToDict(List<?> messages) {
        return msgToDict(messages, false, Map.of());
    }

    public static List<Map<String, Object>> msgToDict(List<?> messages, boolean preserveMeta) {
        return msgToDict(messages, preserveMeta, Map.of());
    }

    public static List<Map<String, Object>> msgToDict(List<?> messages,
                                                      boolean preserveMeta,
                                                      Map<String, Object> kwargs) {
        if (!isValidMessages(messages)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "store_type",
                    "graph memory",
                    "error_msg",
                    "Input is not a list of dict or BaseMessage"
            );
        }
        return messages.stream()
                .map(message -> convertMessage(message, preserveMeta, kwargs))
                .toList();
    }

    public static void updateEntity(Entity entity, String response, Map<String, Object> extractionSchema) {
        Object extractedEntityInfo = ParseResponse.parseJson(response, extractionSchema);
        if (!isPythonTruthy(extractedEntityInfo)) {
            extractedEntityInfo = new LinkedHashMap<String, Object>();
        }
        if (extractedEntityInfo instanceof List<?> list) {
            extractedEntityInfo = list.get(0);
        }
        if (extractedEntityInfo instanceof String summary) {
            extractedEntityInfo = new LinkedHashMap<>(Map.of("summary", summary));
        }
        Map<String, Object> extractedMap = castStringObjectMap(extractedEntityInfo);
        parseSummary(entity, extractedMap);
        parseAttributes(entity, extractedMap);
    }

    public static Map<String, Object> assembleInvokeParams(Map<String, Object> kwargs, PromptTemplate template) {
        return assembleInvokeParams(kwargs, template, null);
    }

    public static Map<String, Object> assembleInvokeParams(Map<String, Object> kwargs,
                                                           PromptTemplate template,
                                                           Map<String, Object> outputModel) {
        Object content = template.format(kwargs).getContent();
        if (!(content instanceof List<?> messages)) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "store_type",
                    "graph memory",
                    "error_msg",
                    "Input is not a list of dict or BaseMessage"
            );
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("messages", msgToDict(messages));
        if (outputModel != null && !outputModel.isEmpty()) {
            params.put("response_format", outputModel);
        }
        return params;
    }

    private static boolean isValidMessages(List<?> messages) {
        if (messages == null) {
            return false;
        }
        for (Object message : messages) {
            if (!(message instanceof Map<?, ?> || message instanceof BaseMessage)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> convertMessage(Object message,
                                                      boolean preserveMeta,
                                                      Map<String, Object> kwargs) {
        if (message instanceof Map<?, ?> map) {
            return castStringObjectMap(map);
        }
        BaseMessage baseMessage = (BaseMessage) message;
        if (preserveMeta) {
            return baseMessage.modelDump();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", baseMessage.getRole());
        result.put("content", baseMessage.getContent());
        return result;
    }

    private static void parseSummary(Entity entity, Map<String, Object> extractedEntityInfo) {
        Object summaryValue = extractedEntityInfo.getOrDefault("summary", "");
        String summary;
        if (summaryValue instanceof List<?> list) {
            summary = joinStripped(list);
        } else if (summaryValue instanceof Set<?> set) {
            summary = joinStripped(new LinkedHashSet<>(set));
        } else if (summaryValue instanceof String text) {
            summary = text;
        } else {
            summary = isPythonTruthy(summaryValue) ? String.valueOf(summaryValue) : "";
        }
        summary = summary.strip();
        String summaryCleaned = summary.toLowerCase(Locale.ROOT);
        if (!summary.isEmpty() && NULL_SUMMARY_WORDS.stream().noneMatch(summaryCleaned::contains)) {
            entity.setContent(summary);
        }
    }

    private static void parseAttributes(Entity entity, Map<String, Object> extractedEntityInfo) {
        Object attributes = extractedEntityInfo.get("attributes");
        if (attributes instanceof String text) {
            attributes = ParseResponse.parseJson(text);
        } else if (attributes instanceof List<?> || attributes instanceof Set<?>) {
            try {
                attributes = collectionToMap((Collection<?>) attributes);
            } catch (RuntimeException exception) {
                LOGGER.info("Graph Memory: Failed to parse extracted entity attribute: " + exception.getMessage());
            }
        }
        if (!(attributes instanceof Map<?, ?> map)) {
            attributes = Map.of();
        } else {
            attributes = castStringObjectMap(map);
        }
        Map<String, Object> attributeMap = castStringObjectMap(attributes);
        if (!attributeMap.isEmpty()) {
            entity.setAttributes(attributeMap);
        }
    }

    private static String joinStripped(Collection<?> values) {
        return values.stream()
                .map(value -> ((String) value).strip())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static Map<String, Object> collectionToMap(Collection<?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : values) {
            if (value instanceof Map.Entry<?, ?> entry) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            } else if (value instanceof List<?> list && list.size() >= 2) {
                result.put(String.valueOf(list.get(0)), list.get(1));
            } else if (value != null && value.getClass().isArray() && Array.getLength(value) >= 2) {
                result.put(String.valueOf(Array.get(value, 0)), Array.get(value, 1));
            } else {
                throw new IllegalArgumentException("cannot convert attribute item to key/value pair");
            }
        }
        return result;
    }

    private static boolean isPythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence chars) {
            return !chars.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringObjectMap(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            throw new ClassCastException("Expected Python dict-compatible map, got " + input);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
