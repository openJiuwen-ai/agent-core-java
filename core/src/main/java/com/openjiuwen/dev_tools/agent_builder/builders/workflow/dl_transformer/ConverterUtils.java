/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code ConverterUtils} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converter_utils.py}.
 */
public final class ConverterUtils {
    public static final Map<String, String> LLM_MODEL_CONFIG = Map.of(
            "id", "52",
            "name", "siliconf-qwen3-8b",
            "type", "Qwen/Qwen3-8B");

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{\\s*(\\w+)\\.(\\w+)\\s*\\}");

    private ConverterUtils() {
    }

    public static synchronized String generateNodeId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
    }

    public static String[] extractVariable(String expr) {
        if (expr == null) {
            return null;
        }

        Matcher matcher = VAR_PATTERN.matcher(expr.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new String[]{matcher.group(1), matcher.group(2)};
    }

    public static Map<String, Object> convertRefVariable(String expr) {
        String[] refNodeVariable = extractVariable(expr);
        if (refNodeVariable == null) {
            throw ErrorHelper.buildError(
                    StatusCode.WORKFLOW_EXECUTE_INPUT_INVALID,
                    null,
                    Map.of("expr", expr),
                    null,
                    Map.of(
                            "inputs", expr,
                            "reason", "Invalid reference variable expression: " + expr,
                            "workflow", ""));
        }

        String refNode = refNodeVariable[0];
        String refVariable = refNodeVariable[1];
        String[] parts = refVariable.split("_of_");
        List<String> content = new ArrayList<>();
        content.add(refNode);
        Collections.reverse(Arrays.asList(parts));
        for (String part : parts) {
            if (!part.isEmpty()) {
                content.add(part);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", SourceType.ref.getValue());
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
        if (obj instanceof List<?> list) {
            List<Object> converted = new ArrayList<>();
            for (Object item : list) {
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
        if (obj instanceof Map<?, ?> || isModelBean(obj)) {
            return convertValue(obj);
        }
        return convertBeanFields(obj);
    }

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
        if (isModelBean(value)) {
            return convertBean(value);
        }
        return value;
    }

    private static Object readEnumValue(Enum<?> enumValue) {
        try {
            Method method = enumValue.getClass().getMethod("getValue");
            return method.invoke(enumValue);
        } catch (ReflectiveOperationException ignored) {
            return enumValue.name();
        }
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

    private static Map<String, Object> convertBeanFields(Object bean) {
        Map<String, Object> converted = new LinkedHashMap<>();
        try {
            for (var field : bean.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(bean);
                if (value == null) {
                    continue;
                }
                converted.put(field.getName(), value);
            }
        } catch (IllegalAccessException ignored) {
            // Match Python fallback behavior when direct conversion is unavailable.
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
