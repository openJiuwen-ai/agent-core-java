/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * ReMe utility functions.
 * <p>
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.reme.utils}.
 * 
 * @since 0.1.7
 */
public class ReMeUtils {
    private static final Logger logger = LoggerFactory.getLogger(ReMeUtils.class);

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern JSON_CODE_BLOCK =
        Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*\\]", Pattern.DOTALL);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern OBSERVATION_KEY_PATTERN = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:");

    /**
     * parseJsonExperienceResponse.
     * 
     * @param response response
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseJsonExperienceResponse(String response) {
        List<Map<String, Object>> experiences = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return experiences;
        }

        try {
            Matcher codeBlockMatcher = JSON_CODE_BLOCK.matcher(response);
            if (codeBlockMatcher.find()) {
                Object parsed = objectMapper.readValue(codeBlockMatcher.group(1), Object.class);
                collectExperiences(parsed, experiences);
                if (!experiences.isEmpty()) {
                    return experiences;
                }
            }

            Object direct = objectMapper.readValue(response, Object.class);
            collectExperiences(direct, experiences);
            if (!experiences.isEmpty()) {
                return experiences;
            }
        } catch (JsonProcessingException primaryError) {
            try {
                Matcher arrayMatcher = JSON_ARRAY.matcher(response);
                if (arrayMatcher.find()) {
                    Object parsed = objectMapper.readValue(arrayMatcher.group(0), Object.class);
                    collectExperiences(parsed, experiences);
                    if (!experiences.isEmpty()) {
                        return experiences;
                    }
                }

                Matcher objectMatcher = JSON_OBJECT.matcher(response);
                if (objectMatcher.find()) {
                    Object parsed = objectMapper.readValue(objectMatcher.group(0), Object.class);
                    collectExperiences(parsed, experiences);
                    if (!experiences.isEmpty()) {
                        return experiences;
                    }
                }
            } catch (JsonProcessingException fallbackError) {
                logger.warn("Failed to parse JSON experience response: {}", fallbackError.getMessage());
            }
            logger.debug("Primary ReMe JSON parse failed: {}", primaryError.getMessage());
        }

        return experiences;
    }

    /**
     * compactWhitespace.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static String compactWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * extractPrefixedLines.
     * 
     * @param text text
     * @param prefix prefix
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * extractObservationKeys.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    public static List<String> extractObservationKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        Matcher matcher = OBSERVATION_KEY_PATTERN.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1));
            if (keys.size() >= 6) {
                break;
            }
        }
        return new ArrayList<>(keys);
    }

    /**
     * extractToolNames.
     * 
     * @param trajectory trajectory
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * lastPrefixedLine.
     * 
     * @param text text
     * @param prefix prefix
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * extractFeedbackSignal.
     * 
     * @param trajectory trajectory
     * @return the result
     * @since 0.1.7
     */
    public static String extractFeedbackSignal(String trajectory) {
        String feedback = compactWhitespace(lastPrefixedLine(trajectory, "FEEDBACK:")).toLowerCase(Locale.ROOT);
        if (feedback.isBlank()) {
            String normalized = normalizeSignalSource(trajectory);
            if (normalized.contains(" feedback helpful ") || normalized.contains(" status success ")
                    || normalized.contains(" successful ")) {
                return "helpful";
            }
            if (normalized.contains(" feedback harmful ") || normalized.contains(" status failure ")
                    || normalized.contains(" failed ")) {
                return "harmful";
            }
            return "";
        }
        if (feedback.contains("helpful") || feedback.contains("success") || feedback.contains("positive")) {
            return "helpful";
        }
        if (feedback.contains("harmful") || feedback.contains("failure") || feedback.contains("negative")) {
            return "harmful";
        }
        return "";
    }

    /**
     * tokenize.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static List<String> tokenize(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalized = value != null ? value.toLowerCase(Locale.ROOT) : "";
        for (String token : normalized.split("[^a-z0-9]+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return new ArrayList<>(tokens);
    }

    /**
     * Calculate cosine similarity between two vectors.
     * 
     * @param vec1 First vector
     * @param vec2 Second vector
     * @return Cosine similarity (0-1)
     * @since 0.1.7
     */
    public static double calculateCosineSimilarity(List<Double> vec1, List<Double> vec2) {
        if (vec1 == null || vec2 == null || vec1.size() != vec2.size() || vec1.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Calculate cosine similarity between two float arrays.
     * 
     * @param vec1 First vector
     * @param vec2 Second vector
     * @return Cosine similarity (0-1)
     * @since 0.1.7
     */
    public static double calculateCosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length || vec1.length == 0) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    @SuppressWarnings("unchecked")
    /**
     * collectExperiences.
     * 
     * @param parsed parsed
     * @param target target
     * @since 0.1.7
     */
    private static void collectExperiences(Object parsed, List<Map<String, Object>> target) {
        if (parsed instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = normalizeExperience((Map<String, Object>) map);
                    if (isValidExperience(normalized)) {
                        target.add(normalized);
                    }
                }
            }
            return;
        }
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> normalized = normalizeExperience((Map<String, Object>) map);
            if (isValidExperience(normalized)) {
                target.add(normalized);
            }
        }
    }

    /**
     * normalizeExperience.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> normalizeExperience(Map<String, Object> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (raw == null) {
            return normalized;
        }
        normalized.putAll(raw);
        if (!normalized.containsKey("when_to_use") && normalized.containsKey("condition")) {
            normalized.put("when_to_use", normalized.get("condition"));
        }
        return normalized;
    }

    /**
     * isValidExperience.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private static boolean isValidExperience(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        String experience = compactWhitespace(String.valueOf(data.getOrDefault("experience", "")));
        String whenToUse = compactWhitespace(String.valueOf(data.getOrDefault("when_to_use", "")));
        return !experience.isBlank() && !whenToUse.isBlank();
    }

    /**
     * normalizeSignalSource.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeSignalSource(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return " " + value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }
}
