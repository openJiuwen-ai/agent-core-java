/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code _is_valid_experience}, {@code parse_json_experience_response}, and
 * {@code calculate_cosine_similarity} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/utils.py}.
 */
public final class ReMeUtils {

    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private ReMeUtils() {
    }

    public static List<Map<String, Object>> parseJsonExperienceResponse(String response) {
        try {
            Matcher matcher = JSON_CODE_BLOCK.matcher(response);
            if (matcher.find()) {
                Object parsed = OBJECT_MAPPER.readValue(matcher.group(1), Object.class);
                if (parsed instanceof List<?> listValue) {
                    List<Map<String, Object>> experiences = new ArrayList<>();
                    for (Object item : listValue) {
                        if (isValidExperience(item)) {
                            experiences.add(asStringMap((Map<?, ?>) item));
                        }
                    }
                    return experiences;
                }
                if (isValidExperience(parsed)) {
                    return List.of(asStringMap((Map<?, ?>) parsed));
                }
            }

            Object parsed = OBJECT_MAPPER.readValue(response, Object.class);
            if (parsed instanceof List<?> listValue) {
                return toMapList(listValue);
            }
            if (parsed instanceof Map<?, ?> mapValue) {
                return List.of(asStringMap(mapValue));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            LOGGER.warning("Failed to parse JSON experience response: {}", exception.getMessage());
        }
        return List.of();
    }

    public static double calculateCosineSimilarity(List<Double> embedding1, List<Double> embedding2) {
        try {
            if (embedding1 == null || embedding2 == null || embedding1.size() != embedding2.size()) {
                return 0.0;
            }

            double dotProduct = 0.0;
            double norm1 = 0.0;
            double norm2 = 0.0;
            for (int index = 0; index < embedding1.size(); index++) {
                double value1 = embedding1.get(index);
                double value2 = embedding2.get(index);
                dotProduct += value1 * value2;
                norm1 += value1 * value1;
                norm2 += value2 * value2;
            }

            if (norm1 == 0.0 || norm2 == 0.0) {
                return 0.0;
            }
            return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
        } catch (Exception exception) {
            LOGGER.error("Error calculating cosine similarity: {}", exception.getMessage());
            return 0.0;
        }
    }

    public static String compactWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    public static List<String> extractPrefixedLines(String text, String prefix) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || prefix == null || prefix.isBlank()) {
            return lines;
        }
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith(prefix)) {
                lines.add(line.substring(prefix.length()).trim());
            }
        }
        return lines;
    }

    public static List<String> extractObservationKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        Pattern quotedKey = Pattern.compile("[\"']([A-Za-z0-9_]+)[\"']\\s*:");
        for (String observation : extractPrefixedLines(text, "OBSERVATION:")) {
            Matcher matcher = quotedKey.matcher(observation);
            while (matcher.find() && keys.size() < 6) {
                keys.add(matcher.group(1));
            }
            if (keys.size() >= 6) {
                break;
            }
        }
        if (keys.isEmpty()) {
            Matcher matcher = quotedKey.matcher(text);
            while (matcher.find() && keys.size() < 6) {
                keys.add(matcher.group(1));
            }
        }
        return new ArrayList<>(keys);
    }

    public static List<String> extractToolNames(String trajectory) {
        Set<String> tools = new LinkedHashSet<>();
        for (String action : extractPrefixedLines(trajectory, "ACTION:")) {
            int bracketIndex = action.indexOf('(');
            String candidate = bracketIndex >= 0 ? action.substring(0, bracketIndex) : action;
            candidate = compactWhitespace(candidate);
            if (!candidate.isBlank()) {
                tools.add(candidate);
            }
        }
        return new ArrayList<>(tools);
    }

    public static String lastPrefixedLine(String text, String prefix) {
        if (text == null || text.isBlank() || prefix == null || prefix.isBlank()) {
            return "";
        }
        String last = "";
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith(prefix)) {
                last = line.substring(prefix.length()).trim();
            }
        }
        return last;
    }

    public static String extractFeedbackSignal(String trajectory) {
        String feedback = compactWhitespace(lastPrefixedLine(trajectory, "FEEDBACK:")).toLowerCase(Locale.ROOT);
        if (feedback.isBlank()) {
            String normalized = normalizeSignalSource(trajectory);
            if (normalized.contains(" feedback helpful ")
                    || normalized.contains(" status success ")
                    || normalized.contains(" successful ")
                    || normalized.contains(" correct ")) {
                return "helpful";
            }
            if (normalized.contains(" feedback harmful ")
                    || normalized.contains(" status failure ")
                    || normalized.contains(" failed ")
                    || normalized.contains(" incorrect ")
                    || normalized.contains(" error ")) {
                return "harmful";
            }
            return "";
        }
        if (feedback.contains("helpful")
                || feedback.contains("success")
                || feedback.contains("positive")
                || feedback.contains("correct")) {
            return "helpful";
        }
        if (feedback.contains("harmful")
                || feedback.contains("failure")
                || feedback.contains("failed")
                || feedback.contains("negative")
                || feedback.contains("incorrect")
                || feedback.contains("error")) {
            return "harmful";
        }
        return "";
    }

    private static boolean isValidExperience(Object data) {
        if (!(data instanceof Map<?, ?> mapValue)) {
            return false;
        }
        return mapValue.containsKey("experience")
                && (mapValue.containsKey("when_to_use") || mapValue.containsKey("condition"));
    }

    private static List<Map<String, Object>> toMapList(List<?> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> mapValue) {
                result.add(asStringMap(mapValue));
            }
        }
        return result;
    }

    private static Map<String, Object> asStringMap(Map<?, ?> rawMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private static String normalizeSignalSource(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return " " + value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }
}
