
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.memory.graph.extraction.ParseResponse;

import java.util.*;

/**
 * Graph Memory Utils - Message conversion and entity update helpers for graph memory.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.utils}.
 */
public final class GraphMemoryUtils {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** Null-like words to ignore when setting entity content */
    private static final Set<String> NULL_WORDS = Set.of("null", "none", "empty");

    private GraphMemoryUtils() {
    }

    /**
     * Convert a list of BaseMessage or Map into a list of role/content dict maps.
     * <p>
     * If preserveMeta is true, uses model_dump style serialization for BaseMessage.
     * If preserveMeta is false, only extracts role and content fields.
     *
     * @param messages      list of messages (each must be a Map or BaseMessage)
     * @param preserveMeta  whether to preserve extra fields from BaseMessage
     * @return list of message dictionaries with role and content
     * @throws BaseError if input is not a valid list of messages
     */
    public static List<Map<String, Object>> msg2dict(List<?> messages, boolean preserveMeta) {
        if (messages == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "store_type", "graph memory",
                    "error_msg", "Input is not a list of dict or BaseMessage");
        }

        // Validate all elements are either Map or BaseMessage
        for (Object msg : messages) {
            if (!(msg instanceof Map) && !(msg instanceof BaseMessage)) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "store_type", "graph memory",
                        "error_msg", "Input is not a list of dict or BaseMessage");
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object msg : messages) {
            if (msg instanceof BaseMessage baseMsg) {
                if (preserveMeta) {
                    // Use Jackson to serialize all fields
                    Map<String, Object> serialized = JSON_MAPPER.convertValue(baseMsg, Map.class);
                    result.add(serialized);
                } else {
                    Map<String, Object> dict = new HashMap<>();
                    dict.put("role", baseMsg.getRole());
                    dict.put("content", baseMsg.getContentAsString());
                    result.add(dict);
                }
            } else if (msg instanceof Map) {
                result.add((Map<String, Object>) msg);
            }
        }
        return result;
    }

    /**
     * Convert messages without preserving meta fields.
     */
    public static List<Map<String, Object>> msg2dict(List<?> messages) {
        return msg2dict(messages, false);
    }

    /**
     * Update entity content based on LLM response.
     * <p>
     * Parses the response JSON, extracts summary and attributes,
     * and updates the entity accordingly.
     *
     * @param entity           the entity to update
     * @param response         the LLM response string
     * @param extractionSchema schema for parsing the response
     */
    public static void updateEntity(Entity entity, String response, Map<String, Object> extractionSchema) {
        if (entity == null || response == null) {
            return;
        }

        Object extractedEntityInfo = ParseResponse.parseJson(response, extractionSchema);
        if (extractedEntityInfo == null) {
            extractedEntityInfo = Collections.emptyMap();
        }

        // Handle list result - take first element
        if (extractedEntityInfo instanceof List) {
            List<?> list = (List<?>) extractedEntityInfo;
            if (!list.isEmpty()) {
                extractedEntityInfo = list.get(0);
            } else {
                extractedEntityInfo = Collections.emptyMap();
            }
        }

        // Handle string result - wrap as summary
        if (extractedEntityInfo instanceof String) {
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("summary", extractedEntityInfo);
            extractedEntityInfo = wrapper;
        }

        // Ensure we have a map
        Map<String, Object> infoMap;
        if (extractedEntityInfo instanceof Map) {
            infoMap = (Map<String, Object>) extractedEntityInfo;
        } else {
            infoMap = Collections.emptyMap();
        }

        parseSummary(entity, infoMap);
        parseAttributes(entity, infoMap);
    }

    /**
     * Process extracted summary and update entity content.
     * <p>
     * Handles summary as string, list, set, or other types.
     * Ignores null-like values ("null", "none", "empty").
     */
    private static void parseSummary(Entity entity, Map<String, Object> extractedEntityInfo) {
        Object summaryObj = extractedEntityInfo.get("summary");
        if (summaryObj == null) {
            summaryObj = "";
        }

        String summary;
        if (summaryObj instanceof List) {
            // Join list elements with newlines
            StringBuilder sb = new StringBuilder();
            for (Object item : (List<?>) summaryObj) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                String line = item != null ? item.toString().trim() : "";
                sb.append(line);
            }
            summary = sb.toString();
        } else if (summaryObj instanceof Set) {
            // Join set elements with newlines
            StringBuilder sb = new StringBuilder();
            for (Object item : (Set<?>) summaryObj) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                String line = item != null ? item.toString().trim() : "";
                sb.append(line);
            }
            summary = sb.toString();
        } else if (summaryObj instanceof String) {
            summary = (String) summaryObj;
        } else {
            // Convert to string or empty
            summary = summaryObj != null ? summaryObj.toString() : "";
        }

        summary = summary.trim();
        String summaryCleaned = summary.toLowerCase();

        // Only update if summary is valid (not null-like)
        if (!summary.isEmpty() && !containsAnyNullWord(summaryCleaned)) {
            entity.setContent(summary);
        }
    }

    /**
     * Check if the cleaned summary contains any null-like word.
     */
    private static boolean containsAnyNullWord(String cleaned) {
        for (String nullWord : NULL_WORDS) {
            if (cleaned.contains(nullWord)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Process extracted attributes and update entity.
     * <p>
     * Handles attributes as dict, JSON string, list, or set.
     * Invalid attributes are logged and set to empty dict.
     */
    private static void parseAttributes(Entity entity, Map<String, Object> extractedEntityInfo) {
        Object attributesObj = extractedEntityInfo.get("attributes");
        if (attributesObj == null) {
            return;
        }

        Map<String, Object> attributes = null;

        if (attributesObj instanceof String) {
            // Try to parse JSON string
            try {
                attributes = JSON_MAPPER.readValue((String) attributesObj, Map.class);
            } catch (Exception e) {
                Loggers.MEMORY.info("Graph Memory: Failed to parse extracted entity attribute from string: {}", e.getMessage());
                attributes = Collections.emptyMap();
            }
        } else if (attributesObj instanceof Map) {
            attributes = (Map<String, Object>) attributesObj;
        } else if (attributesObj instanceof List) {
            // Try to convert list to dict (key-value pairs)
            try {
                attributes = listToMap((List<?>) attributesObj);
            } catch (Exception e) {
                Loggers.MEMORY.info("Graph Memory: Failed to parse extracted entity attribute from list: {}", e.getMessage());
                attributes = Collections.emptyMap();
            }
        } else if (attributesObj instanceof Set) {
            // Set cannot be directly converted to dict
            Loggers.MEMORY.info("Graph Memory: Attributes as set cannot convert to dict");
            attributes = Collections.emptyMap();
        }

        // Only set attributes if we have a valid dict
        if (attributes != null && !attributes.isEmpty()) {
            entity.setAttributes(attributes);
        }
    }

    /**
     * Convert a list of key-value pairs to a map.
     * List should contain pairs like [["key1", "value1"], ["key2", "value2"]].
     */
    private static Map<String, Object> listToMap(List<?> list) {
        Map<String, Object> result = new HashMap<>();
        for (Object item : list) {
            if (item instanceof List) {
                List<?> pair = (List<?>) item;
                if (pair.size() >= 2) {
                    Object key = pair.get(0);
                    Object value = pair.get(1);
                    if (key != null) {
                        result.put(key.toString(), value);
                    }
                }
            } else if (item instanceof Map) {
                // Single-entry map
                Map<?, ?> entry = (Map<?, ?>) item;
                for (Map.Entry<?, ?> e : entry.entrySet()) {
                    if (e.getKey() != null) {
                        result.put(e.getKey().toString(), e.getValue());
                    }
                }
            }
        }
        return result;
    }

    /**
     * Assemble LLM client invoke parameters.
     * <p>
     * Combines template messages with optional output format.
     *
     * @param kwargs       additional parameters
     * @param template     the prompt template to format
     * @param outputModel  optional output format schema (for structured output)
     * @return parameter map ready for LLM invocation
     */
    public static Map<String, Object> assembleInvokeParams(
            Map<String, Object> kwargs,
            PromptTemplate template,
            Map<String, Object> outputModel) {

        Map<String, Object> params = new HashMap<>();

        // Get messages from template
        if (template != null) {
            PromptTemplate formattedTemplate = template.format(kwargs == null ? Collections.emptyMap() : kwargs);
            List<BaseMessage> templateMessages = formattedTemplate.toMessages();
            params.put("messages", msg2dict(templateMessages, false));
        } else {
            params.put("messages", Collections.emptyList());
        }

        // Add response format if specified
        if (outputModel != null) {
            params.put("response_format", outputModel);
        }

        return params;
    }
}
