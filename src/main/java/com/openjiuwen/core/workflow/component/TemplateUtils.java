/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for template operations: rendering and splitting.
 * <p>
 * Mirrors Python's {@code TemplateUtils} from {@code end_comp.py}.
 */
public class TemplateUtils {

    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("(\\{\\{[^}]+\\}\\})");

    private TemplateUtils() {
    }

    /**
     * Render a template string with {@code {{variable}}} substitution.
     * Uses safe substitution – missing keys are replaced with empty string.
     * <p>
     * Mirrors Python's {@code TemplateUtils.render_template(template, inputs)}.
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
            result.append(value != null ? value.toString() : "");
            lastEnd = matcher.end();
        }
        result.append(template.substring(lastEnd));
        return result.toString();
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
