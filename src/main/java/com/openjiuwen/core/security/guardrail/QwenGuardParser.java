/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Qwen3Guard model output parser.
 * <p>
 * Mirrors Python's {@code QwenGuardParser} in
 * {@code openjiuwen/core/security/guardrail/context.py}.
 */
public final class QwenGuardParser implements ModelOutputParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern SAFETY_PATTERN = Pattern.compile("safety\\s*:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CATEGORIES_PATTERN =
            Pattern.compile("categories?\\s*:\\s*(.+?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, RiskLevel> RISK_LEVEL_MAP = Map.of(
            "safe", RiskLevel.SAFE,
            "controversial", RiskLevel.MEDIUM,
            "unsafe", RiskLevel.HIGH
    );

    private final String riskType;
    private final RiskLevel defaultRiskLevel;

    public QwenGuardParser() {
        this("content_risk", RiskLevel.SAFE);
    }

    public QwenGuardParser(String riskType) {
        this(riskType, RiskLevel.SAFE);
    }

    public QwenGuardParser(String riskType, RiskLevel defaultRiskLevel) {
        this.riskType = riskType;
        this.defaultRiskLevel = defaultRiskLevel;
    }

    @Override
    public RiskAssessment parse(Object modelOutput) {
        if (modelOutput instanceof Map<?, ?> map) {
            return parseDict(map);
        }

        String text = modelOutput == null ? "" : String.valueOf(modelOutput);
        Map<String, Object> json = tryParseJson(text);
        if (json != null) {
            return parseDict(json);
        }
        return parseStandardFormat(text);
    }

    private Map<String, Object> tryParseJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            Map<String, Object> parsed = parseJsonObject(trimmed);
            if (parsed != null) {
                return parsed;
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return parseJsonObject(trimmed.substring(start, end + 1));
        }
        return null;
    }

    private Map<String, Object> parseJsonObject(String rawJson) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(rawJson, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return castMap(map);
            }
        } catch (JsonProcessingException ignored) {
        }
        return null;
    }

    private RiskAssessment parseStandardFormat(String text) {
        String safety = extractSafety(text);
        List<String> categories = extractCategories(text);
        RiskLevel riskLevel = mapRiskLevel(safety);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("safety", safety);
        details.put("categories", categories);
        details.put("raw_output", text);
        String resolvedRiskType = categories.isEmpty() ? riskType : categories.getFirst();
        return new RiskAssessment(hasRisk, riskLevel, hasRisk ? resolvedRiskType : null, 0.0d, details);
    }

    private String extractSafety(String text) {
        for (String line : text.strip().split("\\R")) {
            String trimmed = line.trim();
            int split = trimmed.indexOf(':');
            if (split > 0 && "safety".equalsIgnoreCase(trimmed.substring(0, split).trim())) {
                return trimmed.substring(split + 1).trim();
            }
        }
        Matcher matcher = SAFETY_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        for (String keyword : List.of("unsafe", "controversial", "safe")) {
            if (lowered.contains(keyword)) {
                return keyword;
            }
        }
        return "unknown";
    }

    private List<String> extractCategories(String text) {
        for (String line : text.strip().split("\\R")) {
            String trimmed = line.trim();
            int split = trimmed.indexOf(':');
            if (split > 0) {
                String key = trimmed.substring(0, split).trim();
                if ("categories".equalsIgnoreCase(key) || "category".equalsIgnoreCase(key)) {
                    return parseCategories(trimmed.substring(split + 1).trim());
                }
            }
        }
        Matcher matcher = CATEGORIES_PATTERN.matcher(text);
        return matcher.find() ? parseCategories(matcher.group(1).trim()) : List.of();
    }

    private RiskAssessment parseDict(Map<?, ?> data) {
        if (data.containsKey("safety")) {
            return parseStandardDict(data);
        }
        if (data.containsKey("analysis")) {
            return parseFullFormat(data);
        }
        if (data.containsKey("judgment")) {
            return parseSimpleFormat(data);
        }
        if (data.containsKey("severity_level")) {
            return parseApiFormat(data);
        }
        return parseGenericDict(data);
    }

    private RiskAssessment parseStandardDict(Map<?, ?> data) {
        String safety = getString(data, "safety", "unknown");
        List<String> categories = toCategories(data.get("categories"));
        RiskLevel riskLevel = mapRiskLevel(safety);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("safety", safety);
        details.put("categories", categories);
        String resolvedRiskType = categories.isEmpty() ? riskType : categories.getFirst();
        return new RiskAssessment(hasRisk, riskLevel, hasRisk ? resolvedRiskType : null, 0.0d, details);
    }

    private RiskAssessment parseFullFormat(Map<?, ?> data) {
        Map<String, Object> analysis = castMap(data.get("analysis"));
        String riskLevelText = getString(analysis, "risk_level", "safe");
        RiskLevel riskLevel = mapRiskLevel(riskLevelText);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        List<String> categories = toCategories(analysis.get("risk_categories"));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("risk_categories", categories);
        details.put("evidence", getString(analysis, "evidence", ""));
        details.put("language", getString(analysis, "language", "unknown"));
        details.put("decision", getString(data, "decision", "unknown"));
        details.put("version", getString(data, "version", "unknown"));
        String resolvedRiskType = categories.isEmpty() ? riskType : categories.getFirst();
        return new RiskAssessment(hasRisk, riskLevel, hasRisk ? resolvedRiskType : null, 0.0d, details);
    }

    private RiskAssessment parseSimpleFormat(Map<?, ?> data) {
        String judgment = getString(data, "judgment", "Safe");
        RiskLevel riskLevel = mapRiskLevel(judgment);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", getString(data, "reason", ""));
        details.put("language", getString(data, "language", "unknown"));
        details.put("judgment", judgment);
        return new RiskAssessment(hasRisk, riskLevel, hasRisk ? riskType : null, 0.0d, details);
    }

    private RiskAssessment parseApiFormat(Map<?, ?> data) {
        String severity = getString(data, "severity_level", "safe");
        RiskLevel riskLevel = mapRiskLevel(severity);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", getString(data, "reason", ""));
        details.put("language", getString(data, "language", "unknown"));
        details.put("severity_level", severity);
        return new RiskAssessment(hasRisk, riskLevel, hasRisk ? riskType : null, 0.0d, details);
    }

    private RiskAssessment parseGenericDict(Map<?, ?> data) {
        String riskLevelText = getString(data, "risk_level", getString(data, "level", "safe"));
        RiskLevel riskLevel = mapRiskLevel(riskLevelText);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        Object explicitHasRisk = data.get("has_risk");
        if (explicitHasRisk instanceof String text) {
            String lowered = text.trim().toLowerCase(Locale.ROOT);
            hasRisk = "true".equals(lowered) || "yes".equals(lowered) || "1".equals(lowered);
        } else if (explicitHasRisk instanceof Boolean bool) {
            hasRisk = bool;
        }
        return new RiskAssessment(
                hasRisk,
                riskLevel,
                hasRisk ? getString(data, "risk_type", riskType) : null,
                0.0d,
                castMap(data)
        );
    }

    private RiskLevel mapRiskLevel(String levelText) {
        String lowered = levelText == null ? "" : levelText.trim().toLowerCase(Locale.ROOT);
        RiskLevel direct = RISK_LEVEL_MAP.get(lowered);
        if (direct != null) {
            return direct;
        }
        for (String keyword : List.of("controversial", "unsafe", "safe")) {
            if (lowered.contains(keyword)) {
                return RISK_LEVEL_MAP.get(keyword);
            }
        }
        return defaultRiskLevel;
    }

    private static List<String> parseCategories(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> categories = new ArrayList<>();
        for (String part : value.replace(',', ' ').split("\\s+")) {
            if (!part.isBlank()) {
                categories.add(part.trim());
            }
        }
        return categories;
    }

    private static List<String> toCategories(Object value) {
        if (value instanceof List<?> list) {
            List<String> categories = new ArrayList<>();
            for (Object item : list) {
                categories.add(String.valueOf(item));
            }
            return categories;
        }
        if (value instanceof String text) {
            return parseCategories(text);
        }
        return List.of();
    }

    private static String getString(Map<?, ?> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                converted.put(key, entry.getValue());
            }
        }
        return converted;
    }
}
