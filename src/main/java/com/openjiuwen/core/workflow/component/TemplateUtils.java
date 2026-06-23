/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            Map<?, ?> ordered = map instanceof LinkedHashMap<?, ?> ? map : new LinkedHashMap<>(map);
            StringBuilder builder = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> iterator = ordered.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                builder.append(pythonRepr(String.valueOf(entry.getKey()))).append(": ")
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
