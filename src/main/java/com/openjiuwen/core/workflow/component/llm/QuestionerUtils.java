/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for the Questioner component.
 * <p>
 * Mirrors Python's {@code QuestionerUtils}.
 */
public final class QuestionerUtils {

    private static final Pattern SUB_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]*)\\}\\}");

    private QuestionerUtils() {
    }

    /**
     * Format a template string replacing {{key}} placeholders with values from userFields.
     */
    public static String formatTemplate(String template, Map<String, Object> userFields) {
        if (template == null || userFields == null) {
            return "";
        }
        try {
            Matcher matcher = SUB_PLACEHOLDER_PATTERN.matcher(template);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String key = matcher.group(1);
                Object value = userFields.get(key);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Build the "continue asking" question text for non-extracted required fields.
     */
    public static String formatContinueAskQuestion(List<FieldInfo> nonExtractedKeyFields, String acceptLanguage) {
        List<String> names = new ArrayList<>();
        for (FieldInfo param : nonExtractedKeyFields) {
            String name = (param.getCnFieldName() != null && !param.getCnFieldName().isEmpty())
                    ? param.getCnFieldName() : param.getDescription();
            names.add(name);
        }
        String joined = String.join(", ", names);
        String template = "en".equals(acceptLanguage)
                ? QuestionerDefaultConfig.CONTINUE_ASK_STATEMENT_EN
                : QuestionerDefaultConfig.CONTINUE_ASK_STATEMENT_ZH;
        return template.replace("{non_extracted_key_fields_names}", joined);
    }

    /**
     * Build the questioner output map from an OutputCache.
     */
    public static Map<String, Object> formatQuestionerOutput(OutputCache outputCache) {
        QuestionerOutput output = QuestionerOutput.fromFields(outputCache.getKeyFields());
        output.setUserResponse(outputCache.getUserResponse());
        output.setQuestion(outputCache.getQuestion());
        return output.toMap();
    }

    /**
     * Validate inputs into a QuestionerInput.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static QuestionerInput validateInputs(Object inputs) {
        if (inputs instanceof Map) {
            return QuestionerInput.fromMap((Map<String, Object>) inputs);
        }
        return new QuestionerInput();
    }

    /**
     * Check if a value is considered "valid" (non-null, non-empty, not "null"/"none").
     */
    public static boolean isValidValue(Object inputValue) {
        if (inputValue == null) {
            return false;
        }
        if ("".equals(inputValue) || (inputValue instanceof Map && ((Map<?, ?>) inputValue).isEmpty())
                || (inputValue instanceof List && ((List<?>) inputValue).isEmpty())) {
            return false;
        }
        if (inputValue instanceof String s) {
            String trimmed = s.strip().toLowerCase(Locale.ROOT);
            return !"null".equals(trimmed) && !"none".equals(trimmed);
        }
        return true;
    }

    /**
     * Validate and convert a value to the expected field type.
     *
     * @return a two-element Object array: [convertedValue, Boolean isValid]
     */
    public static Object[] validateAndConvertType(Object value, String expectedType) {
        if (value == null) {
            return new Object[]{null, false};
        }
        try {
            return switch (expectedType) {
                case "string" -> new Object[]{String.valueOf(value), true};
                case "integer" -> convertToInteger(value);
                case "number" -> convertToNumber(value);
                case "boolean" -> convertToBoolean(value);
                default -> new Object[]{String.valueOf(value), true};
            };
        } catch (Exception e) {
            return new Object[]{null, false};
        }
    }

    private static Object[] convertToInteger(Object value) {
        if (value instanceof Boolean) {
            return new Object[]{null, false};
        }
        if (value instanceof Integer) {
            return new Object[]{value, true};
        }
        if (value instanceof Long l) {
            return new Object[]{l.intValue(), true};
        }
        if (value instanceof Double d) {
            if (d == Math.floor(d)) {
                return new Object[]{(int) d.doubleValue(), true};
            }
            return new Object[]{null, false};
        }
        if (value instanceof String s) {
            return new Object[]{Integer.parseInt(s.strip()), true};
        }
        return new Object[]{null, false};
    }

    private static Object[] convertToNumber(Object value) {
        if (value instanceof Boolean) {
            return new Object[]{null, false};
        }
        if (value instanceof Number n) {
            return new Object[]{n.doubleValue(), true};
        }
        if (value instanceof String s) {
            return new Object[]{Double.parseDouble(s.strip()), true};
        }
        return new Object[]{null, false};
    }

    private static Object[] convertToBoolean(Object value) {
        if (value instanceof Boolean) {
            return new Object[]{value, true};
        }
        if (value instanceof String s) {
            String cleaned = s.strip().toLowerCase(Locale.ROOT);
            if ("true".equals(cleaned)) {
                return new Object[]{true, true};
            }
            if ("false".equals(cleaned)) {
                return new Object[]{false, true};
            }
        }
        return new Object[]{null, false};
    }
}
