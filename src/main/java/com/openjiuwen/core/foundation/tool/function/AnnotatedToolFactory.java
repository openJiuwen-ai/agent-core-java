/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.annotation.ToolDefinition;
import com.openjiuwen.core.foundation.tool.utils.CallableSchemaExtractor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Factory that turns {@link ToolDefinition}-annotated methods into {@link LocalFunction}s.
 */
public final class AnnotatedToolFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AnnotatedToolFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<LocalFunction> scan(Object target) {
        Class<?> targetClass = target instanceof Class<?> clazz ? clazz : target.getClass();
        List<LocalFunction> tools = new ArrayList<>();
        for (Method method : targetClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(ToolDefinition.class)) {
                tools.add(fromMethod(target, method));
            }
        }
        return tools;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static LocalFunction fromMethod(Object target, Method method) {
        ToolDefinition definition = method.getAnnotation(ToolDefinition.class);
        if (definition == null) {
            throw new IllegalArgumentException("Method is not annotated with @ToolDefinition: " + method);
        }
        if (!Modifier.isStatic(method.getModifiers()) && target == null) {
            throw new IllegalArgumentException("Non-static tool methods require a target instance: " + method);
        }
        method.setAccessible(true);

        String name = definition.name().isBlank() ? method.getName() : definition.name();
        String description = definition.description().isBlank()
                ? CallableSchemaExtractor.extractFunctionDescription(method)
                : definition.description();
        Map<String, Object> inputParams = definition.autoExtract()
                ? CallableSchemaExtractor.generateSchema(method)
                : Map.of("type", "object", "properties", Map.of());

        ToolCard card = ToolCard.builder()
                .id(name)
                .name(name)
                .description(description)
                .inputParams(inputParams)
                .properties(new LinkedHashMap<>())
                .build();

        return new LocalFunction(card, inputs -> invokeMethod(target, method, inputs));
    }

    private static Object invokeMethod(Object target, Method method, Map<String, Object> inputs) {
        Object[] args = new Object[method.getParameterCount()];
        Type[] genericTypes = method.getGenericParameterTypes();
        Class<?>[] parameterTypes = method.getParameterTypes();
        String[] parameterNames = new String[method.getParameterCount()];

        for (int i = 0; i < method.getParameterCount(); i++) {
            parameterNames[i] = method.getParameters()[i].getName();
            Object rawValue = inputs != null ? inputs.get(parameterNames[i]) : null;
            args[i] = convertValue(rawValue, parameterTypes[i], genericTypes[i]);
        }

        try {
            return method.invoke(target instanceof Class<?> ? null : target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke annotated tool method " + method.getName(), e);
        }
    }

    private static Object convertValue(Object rawValue, Class<?> parameterType, Type genericType) {
        if (rawValue == null) {
            if (parameterType == Optional.class) {
                return Optional.empty();
            }
            return null;
        }

        if (parameterType == Optional.class && genericType instanceof ParameterizedType pt) {
            Type nestedType = pt.getActualTypeArguments()[0];
            return Optional.ofNullable(MAPPER.convertValue(rawValue, MAPPER.getTypeFactory().constructType(nestedType)));
        }

        return MAPPER.convertValue(rawValue, MAPPER.getTypeFactory().constructType(genericType));
    }
}
