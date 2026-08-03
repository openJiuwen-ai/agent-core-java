/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.prompt.assemble.variables;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variable class for processing string-type placeholders.
 * <p>
 * Mirrors Python's {@code TextableVariable} in
 * {@code openjiuwen/core/foundation/prompt/assemble/variables/textable.py}.
 */
public class TextableVariable extends Variable {

    private static final LoggerProtocol PROMPT_LOGGER = Loggers.PROMPT;

    private final String text;
    private final String prefix;
    private final String suffix;
    private final List<String> placeholders;

    public TextableVariable(String text) {
        this(text, "default");
    }

    public TextableVariable(String text, String name) {
        this(text, name, "{{", "}}");
    }

    public TextableVariable(String text, String name, String prefix, String suffix) {
        super(name, List.of());
        this.text = text;
        this.prefix = prefix;
        this.suffix = suffix;

        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "([^{}]*?)" + Pattern.quote(suffix));
        LinkedHashSet<String> uniquePlaceholders = new LinkedHashSet<>();
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
            uniquePlaceholders.add(placeholder);
        }
        this.placeholders = new ArrayList<>(uniquePlaceholders);

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String placeholder : this.placeholders) {
            keys.add(placeholder.split("\\.")[0]);
        }
        this.inputKeys = new ArrayList<>(keys);
    }

    @Override
    public Object update(Map<String, Object> kwargs) {
        String formattedText = this.text;
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Map.of();
        for (String placeholder : placeholders) {
            Object value = safeKwargs;
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
            String placeholderText = prefix + placeholder + suffix;
            formattedText = formattedText.replace(placeholderText, pythonString(value, placeholder.contains(".")));
        }
        this.value = formattedText;
        return null;
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

    private String pythonString(Object rawValue, boolean nestedPlaceholder) {
        if (rawValue == null) {
            return "None";
        }
        if (nestedPlaceholder && rawValue instanceof Boolean bool) {
            return Boolean.TRUE.equals(bool) ? "True" : "False";
        }
        if (usesPythonMessageRepr(rawValue)) {
            return pythonRepr(rawValue);
        }
        return String.valueOf(rawValue);
    }

    private String pythonRepr(Object rawValue) {
        if (rawValue == null) {
            return "None";
        }
        if (rawValue instanceof BaseMessage message) {
            return pythonRepr(message.modelDump());
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

    private boolean usesPythonMessageRepr(Object rawValue) {
        if (rawValue instanceof BaseMessage) {
            return true;
        }
        if (rawValue instanceof List<?> list) {
            return !list.isEmpty() && list.stream().allMatch(this::usesPythonMessageRepr);
        }
        if (!(rawValue instanceof Map<?, ?> map)) {
            return false;
        }
        if (map instanceof LinkedHashMap<?, ?> && map.containsKey("role") && map.containsKey("content")) {
            return true;
        }
        return false;
    }

    public String getText() {
        return text;
    }

    public List<String> getPlaceholders() {
        return List.copyOf(placeholders);
    }
}
