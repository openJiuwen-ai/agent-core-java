/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Qwen3Guard model output parser.
 * 
 * Standard output format:
 * Safety: Unsafe
 * Categories: Violent
 * 
 * Safety values: Safe, Unsafe, Controversial
 * Categories: Violent, Sexual, Hate, Harassment, Self-Harm, etc.
 * 
 * Risk level mapping:
 * - Safe -> SAFE
 * - Controversial -> MEDIUM
 * - Unsafe -> HIGH
 * 
 * Mirrors Python's openjiuwen.core.security.guardrail.context.QwenGuardParser
 */
public class QwenGuardParser implements ModelOutputParser {
    
    private static final Map<String, RiskLevel> RISK_LEVEL_MAP = new HashMap<>();
    static {
        RISK_LEVEL_MAP.put("safe", RiskLevel.SAFE);
        RISK_LEVEL_MAP.put("controversial", RiskLevel.MEDIUM);
        RISK_LEVEL_MAP.put("unsafe", RiskLevel.HIGH);
    }
    
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
        if (modelOutput instanceof Map) {
            return parseDict((Map<?, ?>) modelOutput);
        }
        
        String text = modelOutput != null ? String.valueOf(modelOutput) : "";
        
        // Try JSON format first
        Map<String, Object> jsonResult = tryParseJson(text);
        if (jsonResult != null) {
            return parseDict(jsonResult);
        }
        
        return parseStandardFormat(text);
    }
    
    private Map<String, Object> tryParseJson(String text) {
        text = text.trim();
        
        // Direct JSON object
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                return parseJsonString(text);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }
        
        // Find embedded JSON
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            try {
                return parseJsonString(text.substring(start, end + 1));
            } catch (Exception e) {
                // Ignore parse errors
            }
        }
        
        return null;
    }
    
    private Map<String, Object> parseJsonString(String json) {
        // Simple JSON parsing - for complex cases use a proper JSON library
        Map<String, Object> result = new HashMap<>();
        
        // Remove outer braces and quotes
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        // Split by commas (simple approach)
        String[] parts = json.split(",");
        for (String part : parts) {
            String[] keyValue = part.split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    private RiskAssessment parseStandardFormat(String text) {
        String safetyValue = extractSafety(text);
        List<String> categories = extractCategories(text);
        
        RiskLevel riskLevel = mapRiskLevel(safetyValue);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        
        String resultRiskType = !categories.isEmpty() ? categories.get(0) : riskType;
        
        Map<String, Object> details = new HashMap<>();
        details.put("safety", safetyValue);
        details.put("categories", categories);
        details.put("raw_output", text);
        
        return RiskAssessment.builder()
                .hasRisk(hasRisk)
                .riskLevel(riskLevel)
                .riskType(hasRisk ? resultRiskType : null)
                .details(details)
                .build();
    }
    
    private String extractSafety(String text) {
        String[] lines = text.trim().split("\n");
        for (String line : lines) {
            String lineStripped = line.trim();
            if (lineStripped.contains(":")) {
                int colonIndex = lineStripped.indexOf(":");
                String key = lineStripped.substring(0, colonIndex).trim().toLowerCase();
                String value = lineStripped.substring(colonIndex + 1).trim();
                if (key.equals("safety")) {
                    return value;
                }
            }
        }
        
        // Pattern match
        Pattern safetyPattern = Pattern.compile("safety\\s*:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher match = safetyPattern.matcher(text);
        if (match.find()) {
            return match.group(1);
        }
        
        // Keyword search
        String lowerText = text.toLowerCase();
        for (String keyword : Arrays.asList("unsafe", "controversial", "safe")) {
            if (lowerText.contains(keyword)) {
                return keyword;
            }
        }
        
        return "unknown";
    }
    
    private List<String> extractCategories(String text) {
        String[] lines = text.trim().split("\n");
        for (String line : lines) {
            String lineStripped = line.trim();
            if (lineStripped.contains(":")) {
                int colonIndex = lineStripped.indexOf(":");
                String key = lineStripped.substring(0, colonIndex).trim().toLowerCase();
                String value = lineStripped.substring(colonIndex + 1).trim();
                if (key.equals("categories") || key.equals("category")) {
                    if (!value.isEmpty()) {
                        return parseCategoriesList(value);
                    }
                }
            }
        }
        
        // Pattern match
        Pattern categoriesPattern = Pattern.compile("categories?\\s*:\\s*(.+?)(?:\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher match = categoriesPattern.matcher(text);
        if (match.find()) {
            String categoriesStr = match.group(1).trim();
            if (!categoriesStr.isEmpty()) {
                return parseCategoriesList(categoriesStr);
            }
        }
        
        return Collections.emptyList();
    }
    
    private List<String> parseCategoriesList(String value) {
        List<String> result = new ArrayList<>();
        String[] parts = value.replace(",", " ").split(" ");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
    
    private RiskAssessment parseDict(Map<?, ?> data) {
        if (data.containsKey("safety")) {
            return parseDictStandardFormat(data);
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
    
    private RiskAssessment parseDictStandardFormat(Map<?, ?> data) {
        String safety = getString(data, "safety", "unknown");
        Object categoriesObj = data.get("categories");
        List<String> categories;
        
        if (categoriesObj instanceof String) {
            categories = parseCategoriesList((String) categoriesObj);
        } else if (categoriesObj instanceof List) {
            categories = new ArrayList<>();
            for (Object obj : (List<?>) categoriesObj) {
                categories.add(String.valueOf(obj));
            }
        } else {
            categories = Collections.emptyList();
        }
        
        RiskLevel riskLevel = mapRiskLevel(safety);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        
        String resultRiskType = !categories.isEmpty() ? categories.get(0) : riskType;
        
        Map<String, Object> details = new HashMap<>();
        details.put("safety", safety);
        details.put("categories", categories);
        
        return RiskAssessment.builder()
                .hasRisk(hasRisk)
                .riskLevel(riskLevel)
                .riskType(hasRisk ? resultRiskType : null)
                .details(details)
                .build();
    }
    
    private RiskAssessment parseFullFormat(Map<?, ?> data) {
        Map<?, ?> analysis = getMap(data, "analysis");
        String riskLevelStr = getString(analysis, "risk_level", "safe");
        RiskLevel riskLevel = mapRiskLevel(riskLevelStr);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        
        List<?> riskCategories = getList(analysis, "risk_categories");
        String evidence = getString(analysis, "evidence", "");
        String language = getString(analysis, "language", "unknown");
        String decision = getString(data, "decision", "unknown");
        
        String resultRiskType = !riskCategories.isEmpty() ? String.valueOf(riskCategories.get(0)) : riskType;
        
        Map<String, Object> details = new HashMap<>();
        details.put("risk_categories", riskCategories);
        details.put("evidence", evidence);
        details.put("language", language);
        details.put("decision", decision);
        details.put("version", getString(data, "version", "unknown"));
        
        return RiskAssessment.builder()
                .hasRisk(hasRisk)
                .riskLevel(riskLevel)
                .riskType(hasRisk ? resultRiskType : null)
                .details(details)
                .build();
    }
    
    private RiskAssessment parseSimpleFormat(Map<?, ?> data) {
        String judgment = getString(data, "judgment", "Safe");
        RiskLevel riskLevel = mapRiskLevel(judgment);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        
        String reason = getString(data, "reason", "");
        String language = getString(data, "language", "unknown");
        
        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        details.put("language", language);
        details.put("judgment", judgment);
        
        return RiskAssessment.builder()
                .hasRisk(hasRisk)
                .riskLevel(riskLevel)
                .riskType(hasRisk ? riskType : null)
                .details(details)
                .build();
    }
    
    private RiskAssessment parseApiFormat(Map<?, ?> data) {
        String severity = getString(data, "severity_level", "safe");
        RiskLevel riskLevel = mapRiskLevel(severity);
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        
        String reason = getString(data, "reason", "");
        String language = getString(data, "language", "unknown");
        
        Map<String, Object> details = new HashMap<>();
        details.put("reason", reason);
        details.put("language", language);
        details.put("severity_level", severity);
        
        return RiskAssessment.builder()
                .hasRisk(hasRisk)
                .riskLevel(riskLevel)
                .riskType(hasRisk ? riskType : null)
                .details(details)
                .build();
    }
    
    private RiskAssessment parseGenericDict(Map<?, ?> data) {
        String riskLevelStr = getString(data, "risk_level", getString(data, "level", "safe"));
        RiskLevel riskLevel = mapRiskLevel(riskLevelStr);
        
        Object hasRiskObj = data.get("has_risk");
        boolean hasRisk = riskLevel != RiskLevel.SAFE;
        if (hasRiskObj instanceof String) {
            String hasRiskStr = ((String) hasRiskObj).toLowerCase();
            hasRisk = hasRiskStr.equals("true") || hasRiskStr.equals("yes") || hasRiskStr.equals("1");
        } else if (hasRiskObj instanceof Boolean) {
            hasRisk = (Boolean) hasRiskObj;
        }
        
        Map<String, Object> details = new HashMap<>();
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            details.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        
        return RiskAssessment.builder()
                .hasRisk(hasRisk)
                .riskLevel(riskLevel)
                .riskType(hasRisk ? getString(data, "risk_type", riskType) : null)
                .details(details)
                .build();
    }
    
    private RiskLevel mapRiskLevel(String levelStr) {
        String levelLower = levelStr.toLowerCase().trim();
        
        if (RISK_LEVEL_MAP.containsKey(levelLower)) {
            return RISK_LEVEL_MAP.get(levelLower);
        }
        
        // Keyword search (longer keywords first)
        List<String> sortedKeywords = new ArrayList<>(RISK_LEVEL_MAP.keySet());
        sortedKeywords.sort((a, b) -> Integer.compare(b.length(), a.length()));
        
        for (String keyword : sortedKeywords) {
            if (levelLower.contains(keyword)) {
                return RISK_LEVEL_MAP.get(keyword);
            }
        }
        
        return defaultRiskLevel;
    }
    
    // Helper methods
    private String getString(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
    
    private String getString(Map<?, ?> map, String key) {
        return getString(map, key, "");
    }
    
    private Map<?, ?> getMap(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }
    
    private List<?> getList(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }
}