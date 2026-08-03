/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.JsonUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent builder utility methods.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen/dev_tools/agent_builder/utils/utils.py}.</p>
 */
public final class AgentBuilderUtils {

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");
    private static final Pattern JSON_EXTRACT_REGEX = Pattern.compile(AgentBuilderConstants.JSON_EXTRACT_PATTERN);

    private AgentBuilderUtils() {
    }

    public static String extractJsonFromText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = JSON_EXTRACT_REGEX.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return text;
    }

    public static String formatDialogHistory(List<Map<String, ?>> dialogHistory) {
        return formatDialogHistory(dialogHistory, "\n");
    }

    public static String formatDialogHistory(List<Map<String, ?>> dialogHistory, String separator) {
        if (dialogHistory == null || dialogHistory.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        for (Map<String, ?> message : dialogHistory) {
            Object role = message.containsKey("role") ? message.get("role") : "unknown";
            Object content = message.containsKey("content") ? message.get("content") : "";
            lines.add(String.valueOf(role) + ": " + String.valueOf(content));
        }
        return String.join(separator, lines);
    }

    public static Object safeJsonLoads(String text) {
        return safeJsonLoads(text, null);
    }

    public static Object safeJsonLoads(String text, Object defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        return JsonUtils.safeJsonLoads(text, defaultValue);
    }

    public static boolean validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        return sessionId.matches("^[a-zA-Z0-9_-]+$");
    }

    public static List<Map<String, Object>> mergeDictLists(
            List<Map<String, Object>> existing,
            List<Map<String, Object>> newItems,
            String uniqueKey) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (existing != null) {
            result.addAll(existing);
        }

        if (newItems == null || newItems.isEmpty()) {
            return result;
        }

        Set<Object> existingKeys = new LinkedHashSet<>();
        for (Map<String, Object> item : result) {
            Object keyValue = item.get(uniqueKey);
            if (keyValue != null) {
                existingKeys.add(keyValue);
            }
        }

        for (Map<String, Object> item : newItems) {
            Object keyValue = item.get(uniqueKey);
            if (keyValue != null && !existingKeys.contains(keyValue)) {
                result.add(item);
                existingKeys.add(keyValue);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMergeDict(Map<String, Object> base, Map<String, Object> update) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (base != null) {
            result.putAll(base);
        }

        if (update == null) {
            return result;
        }

        for (Map.Entry<String, Object> entry : update.entrySet()) {
            Object current = result.get(entry.getKey());
            Object value = entry.getValue();
            if (current instanceof Map<?, ?> currentMap && value instanceof Map<?, ?> valueMap) {
                result.put(
                        entry.getKey(),
                        deepMergeDict((Map<String, Object>) currentMap, (Map<String, Object>) valueMap)
                );
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    public static Map<String, Object> loadJsonFile(String filePath) throws FileNotFoundException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            String errorMessage = "File not found: " + filePath;
            LOGGER.error("File not found: {}", filePath);
            throw new FileNotFoundException(errorMessage);
        }

        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            if (raw == null || raw.isBlank()) {
                LOGGER.warning("JSON file is empty: {}", filePath);
                return new LinkedHashMap<>();
            }

            Object data = JsonUtils.safeJsonLoads(raw, new LinkedHashMap<String, Object>());
            if (data == null) {
                return new LinkedHashMap<>();
            }
            if (!(data instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(
                        "JSON top level must be object/dict, got: " + data.getClass()
                );
            }
            return toStringObjectMap(map);
        } catch (FileNotFoundException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            String errorMessage = "JSON parse error: " + exception.getMessage();
            LOGGER.error("JSON parse failed: {}", filePath);
            throw new ValidationError(
                    StatusCode.CONTEXT_MESSAGE_INVALID,
                    errorMessage,
                    Map.of("file_path", filePath, "error", exception.getMessage()),
                    exception,
                    Map.of("error_msg", errorMessage)
            );
        }
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }
}
