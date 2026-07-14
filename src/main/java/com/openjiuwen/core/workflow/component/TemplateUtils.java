/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for template operations: rendering and splitting.
 * <p>
 * Mirrors Python's {@code TemplateUtils} in
 * {@code openjiuwen/core/workflow/components/flow/end_comp.py}.
 */
public class TemplateUtils {

    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("(\\{\\{[^}]+\\}\\})");

    private TemplateUtils() {
    }

    /**
     * Render a template string with {@code {{variable}}} substitution.
     * Uses safe substitution, so missing keys remain as {@code $variable}.
     * <p>
     * Mirrors Python's {@code TemplateUtils.render_template(template, inputs)}.
     */
    public static String renderTemplate(String template, Map<String, Object> inputs) {
        if (template == null) {
            throw new IllegalArgumentException("template must be a string");
        }
        if (inputs == null) {
            throw new IllegalArgumentException("inputs must be a dict");
        }

        return safeSubstitute(template.replace("{{", "$").replace("}}", ""), inputs);
    }

    private static String safeSubstitute(String template, Map<String, Object> inputs) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (current != '$') {
                result.append(current);
                index++;
                continue;
            }
            if (index + 1 >= template.length()) {
                result.append(current);
                index++;
                continue;
            }
            char next = template.charAt(index + 1);
            if (next == '$') {
                result.append('$');
                index += 2;
                continue;
            }
            if (next == '{') {
                int end = template.indexOf('}', index + 2);
                if (end > index + 2) {
                    String name = template.substring(index + 2, end);
                    if (isIdentifier(name)) {
                        appendReplacement(result, inputs, "${" + name + "}", name);
                        index = end + 1;
                        continue;
                    }
                }
                result.append(current);
                index++;
                continue;
            }
            if (isIdentifierStart(next)) {
                int end = index + 2;
                while (end < template.length() && isIdentifierPart(template.charAt(end))) {
                    end++;
                }
                String name = template.substring(index + 1, end);
                appendReplacement(result, inputs, "$" + name, name);
                index = end;
                continue;
            }
            result.append(current);
            index++;
        }
        return result.toString();
    }

    private static void appendReplacement(StringBuilder result, Map<String, Object> inputs,
                                          String placeholder, String name) {
        if (inputs.containsKey(name)) {
            Object value = inputs.get(name);
            result.append(stringifyTemplateValue(value));
        } else {
            result.append(placeholder);
        }
    }

    static String stringifyTemplateValue(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return text;
        }
        return pythonRepr(value);
    }

    private static String pythonRepr(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return pythonStringRepr(text);
        }
        if (value instanceof Character character) {
            return pythonStringRepr(String.valueOf(character));
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                builder.append(pythonRepr(entry.getKey())).append(": ")
                        .append(pythonRepr(entry.getValue()));
                if (iterator.hasNext()) {
                    builder.append(", ");
                }
            }
            return builder.append("}").toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                builder.append(pythonRepr(iterator.next()));
                if (iterator.hasNext()) {
                    builder.append(", ");
                }
            }
            return builder.append("]").toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder builder = new StringBuilder("[");
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(pythonRepr(java.lang.reflect.Array.get(value, index)));
            }
            return builder.append("]").toString();
        }
        if (value instanceof Double number) {
            return pythonFloatingPointRepr(number);
        }
        if (value instanceof Float number) {
            return pythonFloatingPointRepr(number.doubleValue());
        }
        return String.valueOf(value);
    }

    private static String pythonStringRepr(String value) {
        char quote = value.indexOf('\'') >= 0 && value.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder builder = new StringBuilder(value.length() + 2).append(quote);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == quote) {
                builder.append('\\').append(character);
                continue;
            }
            switch (character) {
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> builder.append(character);
            }
        }
        return builder.append(quote).toString();
    }

    private static String pythonFloatingPointRepr(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-inf";
        }
        return String.valueOf(value);
    }

    private static boolean isIdentifier(String value) {
        if (value == null || value.isEmpty() || !isIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!isIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifierStart(char value) {
        return value == '_' || (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }

    private static boolean isIdentifierPart(char value) {
        return isIdentifierStart(value) || (value >= '0' && value <= '9');
    }

    /**
     * Split template into a list of segments (static text and {@code {{variable}}} parts).
     * Empty segments are filtered out.
     * <p>
     * Mirrors Python's {@code TemplateUtils.render_template_to_list(template)}.
     */
    public static List<String> renderTemplateToList(String template) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String text = template.substring(lastEnd, matcher.start());
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            result.add(matcher.group(1));
            lastEnd = matcher.end();
        }
        if (lastEnd < template.length()) {
            String text = template.substring(lastEnd);
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }
}
