/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.utils.CallableSchemaExtractor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Factory utilities for converting Java callables into {@link LocalFunction} tools.
 *
 * <p>Mirrors Python's {@code tool(...)} decorator in
 * {@code openjiuwen/core/foundation/tool/tool.py}.</p>
 */
public final class ToolDecorator {

    private ToolDecorator() {
    }

    /**
     * Direct decorator-style conversion equivalent to Python's {@code tool(existing_function)}.
     *
     * @param func Java callable accepting a structured input map
     * @return local function tool
     */
    public static LocalFunction tool(Function<Map<String, Object>, Object> func) {
        return tool(func, Options.builder().build());
    }

    /**
     * Direct conversion with an explicit name.
     *
     * @param name tool name override
     * @param func Java callable accepting a structured input map
     * @return local function tool
     */
    public static LocalFunction tool(String name, Function<Map<String, Object>, Object> func) {
        return tool(func, Options.builder().name(name).build());
    }

    /**
     * Returns a configured Java decorator equivalent to Python's {@code @tool(...)}.
     *
     * @return function that converts a callable into a local function tool
     */
    public static Function<Function<Map<String, Object>, Object>, LocalFunction> tool() {
        return tool(Options.builder().build());
    }

    /**
     * Returns a configured Java decorator equivalent to Python's {@code @tool(...)}.
     *
     * @param options tool construction options
     * @return function that converts a callable into a local function tool
     */
    public static Function<Function<Map<String, Object>, Object>, LocalFunction> tool(Options options) {
        Options safeOptions = options != null ? options : Options.builder().build();
        return func -> tool(func, safeOptions);
    }

    /**
     * Returns a configured Java decorator using keyword-like parameters.
     *
     * @param name optional name override
     * @param description optional description override
     * @param inputParams optional JSON schema
     * @param card optional prebuilt tool card
     * @param autoExtract whether to use reflection extraction when a source method is known
     * @return function that converts a callable into a local function tool
     */
    public static Function<Function<Map<String, Object>, Object>, LocalFunction> tool(
            String name,
            String description,
            Map<String, Object> inputParams,
            ToolCard card,
            boolean autoExtract) {
        return tool(Options.builder()
                .name(name)
                .description(description)
                .inputParams(inputParams)
                .card(card)
                .autoExtract(autoExtract)
                .build());
    }

    /**
     * Converts a callable using explicit options.
     *
     * @param func Java callable accepting a structured input map
     * @param options tool construction options
     * @return local function tool
     */
    public static LocalFunction tool(Function<Map<String, Object>, Object> func, Options options) {
        Objects.requireNonNull(func, "func");
        Options safeOptions = options != null ? options : Options.builder().build();
        if (safeOptions.card() != null) {
            return handlePrebuiltCard(func, safeOptions);
        }
        String finalName = safeOptions.name() != null ? safeOptions.name() : functionName(func, safeOptions);
        return createNewToolCard(func, finalName, safeOptions);
    }

    /**
     * Converts a reflected Java method into a local function tool.
     *
     * @param target object instance for non-static methods; may be {@code null} for static methods
     * @param method reflected source method
     * @return local function tool
     */
    public static LocalFunction toolFromMethod(Object target, Method method) {
        return toolFromMethod(target, method, Options.builder().build());
    }

    /**
     * Converts a reflected Java method into a local function tool with options.
     *
     * @param target object instance for non-static methods; may be {@code null} for static methods
     * @param method reflected source method
     * @param options tool construction options
     * @return local function tool
     */
    public static LocalFunction toolFromMethod(Object target, Method method, Options options) {
        Objects.requireNonNull(method, "method");
        if (!Modifier.isStatic(method.getModifiers()) && target == null) {
            throw new IllegalArgumentException("target is required for non-static method");
        }
        method.setAccessible(true);
        Options safeOptions = (options != null ? options : Options.builder().build()).withSourceMethod(method);
        return tool(inputs -> invokeMethod(target, method, inputs), safeOptions);
    }

    @SuppressWarnings("unchecked")
    private static LocalFunction handlePrebuiltCard(Function<Map<String, Object>, Object> func, Options options) {
        ToolCard card = options.card();
        String finalName = options.name();
        Map<String, Object> overrides = new LinkedHashMap<>();
        if (finalName != null && !finalName.equals(card.getName())) {
            overrides.put("name", finalName);
            String functionName = functionName(func, options);
            if (functionName != null && !finalName.equals(functionName)) {
                Loggers.TOOL.warning("Overriding card name '{}' with '{}'", card.getName(), finalName);
            }
        }
        if (options.description() != null && !options.description().equals(card.getDescription())) {
            overrides.put("description", options.description());
        }
        if (options.inputParams() != null && !options.inputParams().equals(card.getInputParams())) {
            overrides.put("inputParams", options.inputParams());
        }
        if (overrides.isEmpty()) {
            return new LocalFunction(card, func);
        }
        ToolCard newCard = ToolCard.builder()
                .id(card.getId())
                .name((String) overrides.getOrDefault("name", card.getName()))
                .description((String) overrides.getOrDefault("description", card.getDescription()))
                .inputParams((Map<String, Object>) overrides.getOrDefault("inputParams", card.getInputParams()))
                .properties(card.getProperties())
                .build();
        return new LocalFunction(newCard, func);
    }

    private static LocalFunction createNewToolCard(Function<Map<String, Object>, Object> func,
                                                   String finalName,
                                                   Options options) {
        String finalDescription = finalDescription(finalName, options);
        Map<String, Object> finalInputParams = finalInputParams(options);
        ToolCard card = ToolCard.builder()
                .id(finalName)
                .name(finalName)
                .description(finalDescription)
                .inputParams(finalInputParams)
                .build();
        return new LocalFunction(card, func);
    }

    private static String finalDescription(String finalName, Options options) {
        if (options.description() != null) {
            return options.description();
        }
        if (options.autoExtract() && options.sourceMethod() != null) {
            String extracted = CallableSchemaExtractor.extractFunctionDescription(options.sourceMethod());
            if (extracted != null && !extracted.isBlank()) {
                return extracted;
            }
        }
        return "Function " + finalName;
    }

    private static Map<String, Object> finalInputParams(Options options) {
        if (options.inputParams() != null) {
            return copyMap(options.inputParams());
        }
        if (options.autoExtract() && options.sourceMethod() != null) {
            try {
                return CallableSchemaExtractor.generateSchema(options.sourceMethod());
            } catch (RuntimeException exception) {
                Loggers.TOOL.warning("Failed to auto-extract schema for {}: {}. Using empty schema.",
                        options.sourceMethod().getName(), exception.getMessage());
            }
        }
        return emptySchema();
    }

    private static Object invokeMethod(Object target, Method method, Map<String, Object> inputs) {
        try {
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();
            for (int index = 0; index < parameters.length; index++) {
                args[index] = argumentValue(parameters[index], safeInputs.get(parameters[index].getName()));
            }
            return method.invoke(target, args);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static Object argumentValue(Parameter parameter, Object value) {
        if (parameter.getType() == java.util.Optional.class && !(value instanceof java.util.Optional<?>)) {
            return java.util.Optional.ofNullable(value);
        }
        return value;
    }

    private static String functionName(Function<Map<String, Object>, Object> func, Options options) {
        if (options.sourceMethod() != null) {
            return options.sourceMethod().getName();
        }
        if (func instanceof NamedToolFunction namedFunction && namedFunction.toolName() != null) {
            return namedFunction.toolName();
        }
        return "function";
    }

    private static Map<String, Object> emptySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        return schema;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source != null ? new LinkedHashMap<>(source) : null;
    }

    /**
     * Optional function metadata for Java callables that cannot expose Python's {@code __name__}.
     *
     * <p>Mirrors Python's callable metadata lookup in
     * {@code openjiuwen/core/foundation/tool/tool.py}.</p>
     */
    @FunctionalInterface
    public interface NamedToolFunction extends Function<Map<String, Object>, Object> {
        default String toolName() {
            return null;
        }
    }

    /**
     * Keyword-style options for the Java tool decorator.
     *
     * <p>Mirrors Python's {@code tool(...)} keyword arguments in
     * {@code openjiuwen/core/foundation/tool/tool.py}.</p>
     */
    public record Options(String name,
                          String description,
                          Map<String, Object> inputParams,
                          ToolCard card,
                          boolean autoExtract,
                          Method sourceMethod) {
        public static Builder builder() {
            return new Builder();
        }

        private Options withSourceMethod(Method method) {
            return new Options(name, description, inputParams, card, autoExtract, method);
        }

        /**
         * Builder for {@link Options}.
         */
        public static final class Builder {
            private String name;
            private String description;
            private Map<String, Object> inputParams;
            private ToolCard card;
            private boolean autoExtract = true;
            private Method sourceMethod;

            private Builder() {
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder inputParams(Map<String, Object> inputParams) {
                this.inputParams = inputParams;
                return this;
            }

            public Builder card(ToolCard card) {
                this.card = card;
                return this;
            }

            public Builder autoExtract(boolean autoExtract) {
                this.autoExtract = autoExtract;
                return this;
            }

            public Builder sourceMethod(Method sourceMethod) {
                this.sourceMethod = sourceMethod;
                return this;
            }

            public Options build() {
                return new Options(name, description, copyMap(inputParams), card, autoExtract, sourceMethod);
            }
        }
    }
}
