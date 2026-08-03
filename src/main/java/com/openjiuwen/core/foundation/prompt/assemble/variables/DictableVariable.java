/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt.assemble.variables;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.events.LogEventType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variable class for processing dict or list type placeholders recursively.
 * <p>
 * Mirrors Python's {@code DictableVariable} in
 * {@code openjiuwen/core/foundation/prompt/assemble/variables/dictable.py}.
 */
public class DictableVariable extends Variable {

    private static final LoggerProtocol PROMPT_LOGGER = Loggers.PROMPT;

    private final Object data;
    private final String prefix;
    private final String suffix;
    private final Pattern pattern;
    private final List<String> placeholders;

    public DictableVariable(Object data) {
        this(data, "default");
    }

    public DictableVariable(Object data, String name) {
        this(data, name, "{{", "}}");
    }

    public DictableVariable(Object data, String name, String prefix, String suffix) {
        super(name, List.of());
        this.data = data;
        this.prefix = prefix;
        this.suffix = suffix;
        this.pattern = Pattern.compile(Pattern.quote(prefix) + "([^{}]*?)" + Pattern.quote(suffix));

        LinkedHashSet<String> uniquePlaceholders = new LinkedHashSet<>();
        scanPlaceholders(data, uniquePlaceholders);
        this.placeholders = new ArrayList<>(uniquePlaceholders);

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String placeholder : placeholders) {
            keys.add(placeholder.split("\\.")[0]);
        }
        this.inputKeys = new ArrayList<>(keys);
    }

    @Override
    public Object update(Map<String, Object> kwargs) {
        this.value = recursiveFormat(deepCopy(data), kwargs != null ? kwargs : Map.of());
        return this.value;
    }

    private void scanPlaceholders(Object obj, LinkedHashSet<String> result) {
        if (obj instanceof String text) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String placeholder = matcher.group(1).strip();
                if (placeholder.isEmpty()) {
                    throw ErrorHelper.buildError(
                            StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                            null,
                            null,
                            null,
                            Map.of("error_msg", "placeholders cannot be empty string")
                    );
                }
                result.add(placeholder);
            }
            return;
        }
        if (obj instanceof List<?> list) {
            for (Object item : list) {
                scanPlaceholders(item, result);
            }
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                scanPlaceholders(value, result);
            }
        }
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
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                result.put(entry.getKey(), recursiveFormat(entry.getValue(), kwargs));
            }
            return result;
        }
        if (!(obj instanceof String text)) {
            return obj;
        }

        String formattedText = text;
        for (String placeholder : placeholders) {
            String placeholderText = prefix + placeholder + suffix;
            if (!formattedText.contains(placeholderText)) {
                continue;
            }
            Object value = kwargs;
            try {
                for (String node : placeholder.split("\\.")) {
                    value = resolveNode(value, node);
                }
            } catch (Exception error) {
                throw ErrorHelper.buildError(
                        StatusCode.PROMPT_ASSEMBLER_VARIABLE_INIT_FAILED,
                        null,
                        null,
                        error,
                        Map.of("error_msg", "error parsing the placeholder `" + placeholder + "`")
                );
            }
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                PROMPT_LOGGER.info(
                        "Converting non-string value using str().Please check if the style is describe. eventType={}, placeholder={}",
                        LogEventType.AGENT_START.getValue(),
                        placeholder
                );
            }
            formattedText = formattedText.replace(placeholderText, pythonString(value));
        }
        return formattedText;
    }

    private Object resolveNode(Object value, String node) throws ReflectiveOperationException {
        if (value instanceof Map<?, ?> map) {
            return map.get(node);
        }
        if (value == null) {
            throw new NoSuchFieldException(node);
        }

        String suffix = node.substring(0, 1).toUpperCase() + node.substring(1);
        for (String methodName : List.of("get" + suffix, "is" + suffix)) {
            Method method = findNoArgMethod(value.getClass(), methodName);
            if (method != null) {
                return method.invoke(value);
            }
        }

        Field field = findField(value.getClass(), node);
        if (field != null) {
            return field.get(value);
        }
        throw new NoSuchFieldException(node);
    }

    private Method findNoArgMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException error) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    private Field findField(Class<?> type, String name) {
        try {
            Field field = type.getField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException error) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object deepCopy(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
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
        return obj;
    }

    private String pythonString(Object rawValue) {
        if (rawValue == null) {
            return "None";
        }
        if (rawValue instanceof Boolean bool) {
            return Boolean.TRUE.equals(bool) ? "True" : "False";
        }
        if (rawValue instanceof Map<?, ?> || rawValue instanceof List<?>) {
            return pythonRepr(rawValue);
        }
        return String.valueOf(rawValue);
    }

    private String pythonRepr(Object rawValue) {
        if (rawValue == null) {
            return "None";
        }
        if (rawValue instanceof String text) {
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (rawValue instanceof Boolean bool) {
            return Boolean.TRUE.equals(bool) ? "True" : "False";
        }
        if (rawValue instanceof List<?> list) {
            StringJoiner joiner = new StringJoiner(", ", "[", "]");
            for (Object item : list) {
                joiner.add(pythonRepr(item));
            }
            return joiner.toString();
        }
        if (rawValue instanceof Map<?, ?> map) {
            StringJoiner joiner = new StringJoiner(", ", "{", "}");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                joiner.add(pythonRepr(entry.getKey()) + ": " + pythonRepr(entry.getValue()));
            }
            return joiner.toString();
        }
        return String.valueOf(rawValue);
    }

    public List<String> getPlaceholders() {
        return List.copyOf(placeholders);
    }
}
