/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter utilities for DL transformer.
 * <p>
 * Mirrors Python's {@code ConverterUtils} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converter_utils}.
 */
public final class ConverterUtils {

    public static final Map<String, String> LLM_MODEL_CONFIG = Map.of(
            "id", "52",
            "name", "siliconf-qwen3-8b",
            "type", "Qwen/Qwen3-8B"
    );

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static long counter = 0;

    private ConverterUtils() {
    }

    public static synchronized String generateNodeId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
    }

    public static String[] extractVariable(String input) {
        Matcher matcher = VAR_PATTERN.matcher(input);
        if (!matcher.matches()) return null;
        String inner = matcher.group(1);
        int dotIdx = inner.indexOf('.');
        if (dotIdx < 0) return null;
        return new String[]{inner.substring(0, dotIdx), inner.substring(dotIdx + 1)};
    }

    public static Map<String, Object> convertRefVariable(String input) {
        String[] refNodeVariable = extractVariable(input);
        if (refNodeVariable == null) {
            throw ErrorHelper.buildError(
                    StatusCode.WORKFLOW_EXECUTE_INPUT_INVALID,
                    null,
                    Map.of("expr", input),
                    null,
                    Map.of("inputs", input, "reason", "Invalid reference variable expression: " + input,
                            "workflow", ""));
        }
        String refNode = refNodeVariable[0];
        String refVariable = refNodeVariable[1];
        String[] parts = refVariable.split("_of_");
        List<String> content = new ArrayList<>();
        content.add(refNode);
        Collections.reverse(Arrays.asList(parts));
        for (String part : parts) {
            if (!part.isEmpty()) content.add(part);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "ref");
        result.put("content", content);
        return result;
    }

    public static Map<String, Object> convertLlmParam(String systemPrompt, String userPrompt) {
        Map<String, Object> systemPromptMap = new LinkedHashMap<>();
        systemPromptMap.put("type", "template");
        systemPromptMap.put("content", systemPrompt);

        Map<String, Object> promptMap = new LinkedHashMap<>();
        promptMap.put("type", "template");
        promptMap.put("content", userPrompt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemPrompt", systemPromptMap);
        result.put("prompt", promptMap);
        result.put("mode", LLM_MODEL_CONFIG);
        return result;
    }

    public static Object convertToDict(Object obj) {
        if (obj == null) {
            return new LinkedHashMap<String, Object>();
        }
        return convertValue(obj);
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                Object child = convertValue(entry.getValue());
                if (child != null) {
                    converted.put(String.valueOf(entry.getKey()), child);
                }
            }
            return converted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> converted = new ArrayList<>();
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                Object child = convertValue(item);
                if (child != null) {
                    converted.add(child);
                }
            }
            return converted;
        }
        if (value instanceof Enum<?> enumValue) {
            return readEnumValue(enumValue);
        }
        if (value.getClass().isRecord()) {
            return convertRecord(value);
        }
        if (isModelBean(value)) {
            return convertBean(value);
        }
        return value;
    }

    private static Object readEnumValue(Enum<?> enumValue) {
        try {
            var method = enumValue.getClass().getMethod("getValue");
            return method.invoke(enumValue);
        } catch (ReflectiveOperationException ignored) {
            return enumValue.name();
        }
    }

    private static Map<String, Object> convertRecord(Object record) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                Object value = component.getAccessor().invoke(record);
                if (value == null) {
                    continue;
                }
                Object child = convertValue(value);
                if (child != null) {
                    converted.put(component.getName(), child);
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep conversion best-effort, matching Python's permissive object handling.
            }
        }
        return converted;
    }

    private static boolean isModelBean(Object value) {
        Package pkg = value.getClass().getPackage();
        return pkg != null
                && ConverterUtils.class.getPackageName().equals(pkg.getName())
                && !value.getClass().isEnum();
    }

    private static Map<String, Object> convertBean(Object bean) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Method method : bean.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = beanPropertyName(method.getName());
            if (name == null) {
                continue;
            }
            try {
                Object value = method.invoke(bean);
                if (value == null) {
                    continue;
                }
                Object child = convertValue(value);
                if (child != null) {
                    converted.put(name, child);
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep conversion best-effort, matching Python's permissive object handling.
            }
        }
        return converted;
    }

    private static String beanPropertyName(String methodName) {
        if (!methodName.startsWith("get") || methodName.length() <= 3 || "getClass".equals(methodName)) {
            return null;
        }
        String suffix = methodName.substring(3);
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }
}
