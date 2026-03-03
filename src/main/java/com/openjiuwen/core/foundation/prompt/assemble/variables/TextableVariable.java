/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.prompt.assemble.variables;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variable class for processing string-type placeholders.
 * <p>
 * Mirrors Python's {@code TextableVariable}.
 */
public class TextableVariable extends Variable {

    private static final Logger LOG = LoggerFactory.getLogger(TextableVariable.class);

    private final String text;
    private final String prefix;
    private final String suffix;
    private final List<String> placeholders;

    /**
     * Construct a new TextableVariable.
     *
     * @param text   the template text containing placeholders
     * @param name   variable name
     * @param prefix placeholder prefix (default "{{")
     * @param suffix placeholder suffix (default "}}")
     */
    public TextableVariable(String text, String name, String prefix, String suffix) {
        super(name, List.of());
        this.text = text;
        this.prefix = prefix;
        this.suffix = suffix;

        // Parse placeholders from text
        Pattern pattern = Pattern.compile(
                Pattern.quote(prefix) + "([^{}]*?)" + Pattern.quote(suffix));
        LinkedHashSet<String> uniquePlaceholders = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String placeholder = matcher.group(1).strip();
            if (placeholder.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                        "error_msg", "placeholders cannot be empty string");
            }
            uniquePlaceholders.add(placeholder);
        }
        this.placeholders = new ArrayList<>(uniquePlaceholders);

        // Build input keys (top-level segment before '.')
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String placeholder : this.placeholders) {
            String inputKey = placeholder.split("\\.")[0];
            keys.add(inputKey);
        }
        this.inputKeys = new ArrayList<>(keys);
    }

    @Override
    public void update(Map<String, Object> kwargs) {
        String formattedText = this.text;
        for (String placeholder : placeholders) {
            Object val = kwargs;
            try {
                for (String node : placeholder.split("\\.")) {
                    if (val instanceof Map<?, ?> m) {
                        val = m.get(node);
                    } else {
                        // Attempt reflection as a last resort
                        var field = val.getClass().getMethod(
                                "get" + node.substring(0, 1).toUpperCase() + node.substring(1));
                        val = field.invoke(val);
                    }
                }
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                        "error_msg", "error parsing the placeholder `" + placeholder + "`");
            }
            if (!(val instanceof String || val instanceof Number || val instanceof Boolean)) {
                LOG.info("Converting non-string value to String via toString(). " +
                        "Placeholder: {}", placeholder);
            }
            String placeholderStr = prefix + placeholder + suffix;
            formattedText = formattedText.replace(placeholderStr, String.valueOf(val));
        }
        this.value = formattedText;
    }
}
