/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt.assemble.variables;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variable class for processing dict or list type placeholders recursively.
 * <p>
 * Mirrors Python's {@code DictableVariable}.
 */
public class DictableVariable extends Variable {

    private static final Logger LOG = LoggerFactory.getLogger(DictableVariable.class);

    private final Object data; // List or Map
    private final String prefix;
    private final String suffix;
    private final Pattern pattern;
    private final List<String> placeholders;

    /**
     * Construct a DictableVariable.
     *
     * @param data   the template data (List or Map) containing placeholders
     * @param name   variable name
     * @param prefix placeholder prefix
     * @param suffix placeholder suffix
     */
    public DictableVariable(Object data, String name, String prefix, String suffix) {
        super(name, List.of());
        this.data = data;
        this.prefix = prefix;
        this.suffix = suffix;
        this.pattern = Pattern.compile(
                Pattern.quote(prefix) + "([^{}]*?)" + Pattern.quote(suffix));

        LinkedHashSet<String> uniquePlaceholders = new LinkedHashSet<>();
        scanPlaceholders(data, uniquePlaceholders);
        this.placeholders = new ArrayList<>(uniquePlaceholders);

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String placeholder : placeholders) {
            keys.add(placeholder.split("\\.")[0]);
        }
        this.inputKeys = new ArrayList<>(keys);
    }

    private void scanPlaceholders(Object obj, LinkedHashSet<String> result) {
        if (obj instanceof String s) {
            Matcher matcher = pattern.matcher(s);
            while (matcher.find()) {
                String placeholder = matcher.group(1).strip();
                if (placeholder.isEmpty()) {
                    throw ErrorHelper.buildError(StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                            "error_msg", "placeholders cannot be empty string");
                }
                result.add(placeholder);
            }
        } else if (obj instanceof List<?> list) {
            for (Object item : list) {
                scanPlaceholders(item, result);
            }
        } else if (obj instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                scanPlaceholders(v, result);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void update(Map<String, Object> kwargs) {
        this.value = recursiveFormat(deepCopy(data), kwargs);
    }

    @SuppressWarnings("unchecked")
    private Object recursiveFormat(Object obj, Map<String, Object> kwargs) {
        if (obj instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(recursiveFormat(item, kwargs));
            }
            return result;
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                result.put(entry.getKey(), recursiveFormat(entry.getValue(), kwargs));
            }
            return result;
        }
        if (!(obj instanceof String s)) {
            return obj;
        }

        String formattedText = s;
        for (String placeholder : placeholders) {
            String placeholderStr = prefix + placeholder + suffix;
            if (!formattedText.contains(placeholderStr)) {
                continue;
            }
            Object val = kwargs;
            try {
                for (String node : placeholder.split("\\.")) {
                    if (val instanceof Map<?, ?> m) {
                        val = m.get(node);
                    } else {
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
            formattedText = formattedText.replace(placeholderStr, String.valueOf(val));
        }
        return formattedText;
    }

    @SuppressWarnings("unchecked")
    private Object deepCopy(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                copy.put(entry.getKey(), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (obj instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return obj; // immutable primitives / strings
    }
}
