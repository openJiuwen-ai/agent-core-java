/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for template operations: rendering and splitting.
 * <p>
 * Mirrors Python's {@code TemplateUtils} from {@code end_comp.py}.
 *
 * @since 0.1.7
 */
public class TemplateUtils {
    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("(\\{\\{[^}]+\\}\\})");

    /**
     * TemplateUtils.
     * 
     * @since 0.1.7
     */
    private TemplateUtils() {
    }

    /**
     * Render a template string with {@code {{variable}}} substitution.
     * Uses safe substitution – missing keys are replaced with empty string.
     * <p>
     * Mirrors Python's {@code TemplateUtils.render_template(template, inputs)}.
     * 
     * @param template template
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    public static String renderTemplate(String template, java.util.Map<String, Object> inputs) {
        if (template == null) {
            throw new IllegalArgumentException("template must be a string");
        }
        if (inputs == null) {
            throw new IllegalArgumentException("inputs must be a dict");
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(template, lastEnd, matcher.start());
            String varName = matcher.group(1);
            varName = varName.substring(2, varName.length() - 2);
            Object value = inputs.get(varName);
            result.append(value != null ? pythonStr(value) : "");
            lastEnd = matcher.end();
        }
        result.append(template.substring(lastEnd));
        return result.toString();
    }

    /**
     * Convert a value to its Python {@code str()} representation for template
     * interpolation, matching Python {@code string.Template.safe_substitute}
     * which calls {@code str()} on the substituted value.
     * <p>
     * For collections (List/Collection) the result uses single-quoted element
     * repr (e.g. {@code ['a', 'b']}), matching Python {@code str(list)}; this
     * differs from Java {@code List#toString()} which produces
     * {@code [a, b]} without quotes.
     *
     * @param value value
     * @return the result
     * @since 0.1.14
     */
    public static String pythonStr(Object value) {
        if (value == null) {
            return "";
        }
        if (isCollectionOrArray(value)) {
            return pythonCollectionStr(asCollection(value));
        }
        return value.toString();
    }

    private static String pythonCollectionStr(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        boolean isFirst = true;
        for (Object element : collection) {
            if (!isFirst) {
                sb.append(", ");
            }
            isFirst = false;
            sb.append(pythonRepr(element));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String pythonRepr(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String || value instanceof Character) {
            return "'" + value + "'";
        }
        if (isCollectionOrArray(value)) {
            return pythonCollectionStr(asCollection(value));
        }
        return value.toString();
    }

    /**
     * isCollectionOrArray.
     *
     * @param value value
     * @return {@code true} if value is a Collection or array
     */
    private static boolean isCollectionOrArray(Object value) {
        return value instanceof Collection<?> || value.getClass().isArray();
    }

    /**
     * asCollection.
     *
     * @param value value
     * @return a collection view of {@code value}; never {@code null}
     */
    private static Collection<?> asCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return java.util.Arrays.asList((Object[]) value);
    }

    /**
     * Split template into a list of segments (static text and {@code {{variable}}} parts).
     * Empty segments are filtered out.
     * <p>
     * Mirrors Python's {@code TemplateUtils.render_template_to_list(template)}.
     * 
     * @param template template
     * @return the result
     * @since 0.1.7
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
