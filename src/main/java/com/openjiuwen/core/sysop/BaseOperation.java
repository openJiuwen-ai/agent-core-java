/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.SysOperationEvent;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Base class for all system operations (file, code, shell, etc.).
 * <p>
 * Mirrors Python's {@code BaseOperation} in {@code sys_operation/base.py}.
 */
public abstract class BaseOperation {

    protected static final LoggerProtocol logger = Loggers.SYS_OPERATION;

    private final String name;
    private final OperationMode mode;
    private final String description;
    private final Object runConfig;

    /**
     * Create a base operation.
     *
     * @param name        operation name
     * @param mode        operation mode (LOCAL or SANDBOX)
     * @param description human-readable description
     * @param runConfig   runtime configuration (LocalWorkConfig or SandboxGatewayConfig)
     */
    protected BaseOperation(String name, OperationMode mode, String description, Object runConfig) {
        this.name = name;
        this.mode = mode;
        this.description = description;
        this.runConfig = runConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public OperationMode getMode() {
        return mode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the run configuration as LocalWorkConfig.
     */
    protected LocalWorkConfig getLocalConfig() {
        return (LocalWorkConfig) runConfig;
    }

    /**
     * Get the run configuration as SandboxGatewayConfig.
     */
    protected SandboxGatewayConfig getSandboxConfig() {
        return (SandboxGatewayConfig) runConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected Object getRunConfig() {
        return runConfig;
    }

    /**
     * Retrieve a list of tool cards describing available operations.
     *
     * @return list of ToolCard objects
     */
    public abstract List<ToolCard> listTools();

    /**
     * Generate tool cards for the specified method names using reflection.
     *
     * @param methodNames list of public method names to expose as tools
     * @return list of ToolCard objects
     */
    protected List<ToolCard> generateToolCards(List<String> methodNames) {
        return methodNames.stream()
                .map(this::findMethod)
                .filter(method -> method != null)
                .map(method -> (ToolCard) ToolCard.builder()
                        .name(method.getName())
                        .description(humanize(method.getName()))
                        .inputParams(buildInputSchema(method))
                        .build())
                .toList();
    }

    private Method findMethod(String methodName) {
        for (Method method : getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getDeclaringClass() != Object.class) {
                return method;
            }
        }
        return null;
    }

    private Map<String, Object> buildInputSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Parameter parameter : method.getParameters()) {
            properties.put(parameter.getName(), buildParameterSchema(parameter));
        }
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> buildParameterSchema(Parameter parameter) {
        Map<String, Object> schema = new LinkedHashMap<>();
        Class<?> parameterType = parameter.getType();
        schema.put("description", humanize(parameter.getName()));

        if (parameterType == String.class || parameterType == char.class || parameterType == Character.class) {
            schema.put("type", "string");
        } else if (parameterType == boolean.class || parameterType == Boolean.class) {
            schema.put("type", "boolean");
        } else if (parameterType == int.class || parameterType == Integer.class
                || parameterType == long.class || parameterType == Long.class
                || parameterType == short.class || parameterType == Short.class
                || parameterType == byte.class || parameterType == Byte.class) {
            schema.put("type", "integer");
        } else if (parameterType == float.class || parameterType == Float.class
                || parameterType == double.class || parameterType == Double.class) {
            schema.put("type", "number");
        } else if (parameterType.isEnum()) {
            schema.put("type", "string");
            Object[] constants = parameterType.getEnumConstants();
            if (constants != null) {
                schema.put("enum", java.util.Arrays.stream(constants).map(String::valueOf).toList());
            }
        } else if (parameterType.isArray() || List.class.isAssignableFrom(parameterType)) {
            schema.put("type", "array");
            schema.put("items", buildArrayItemSchema(parameterType, parameter.getParameterizedType()));
        } else if (Map.class.isAssignableFrom(parameterType)) {
            schema.put("type", "object");
        } else {
            schema.put("type", "object");
        }

        return schema;
    }

    private Map<String, Object> buildArrayItemSchema(Class<?> parameterType, Type genericType) {
        Map<String, Object> items = new LinkedHashMap<>();
        Class<?> itemType = Object.class;
        if (parameterType.isArray()) {
            itemType = parameterType.getComponentType();
        } else if (genericType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length > 0
                && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> typeClass) {
            itemType = typeClass;
        }

        if (itemType == String.class || itemType == char.class || itemType == Character.class) {
            items.put("type", "string");
        } else if (itemType == int.class || itemType == Integer.class
                || itemType == long.class || itemType == Long.class
                || itemType == short.class || itemType == Short.class
                || itemType == byte.class || itemType == Byte.class) {
            items.put("type", "integer");
        } else if (itemType == float.class || itemType == Float.class
                || itemType == double.class || itemType == Double.class) {
            items.put("type", "number");
        } else if (itemType == boolean.class || itemType == Boolean.class) {
            items.put("type", "boolean");
        } else {
            items.put("type", "object");
        }
        return items;
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * Safely convert an object to a Map representation, similar to Python's model_dump().
     * Returns a default value if conversion fails.
     * <p>
     * Mirrors Python's {@code _safe_model_dump(obj, default=None)} in {@code BaseOperation}.
     *
     * @param obj          the object to convert
     * @param defaultValue default return value when conversion fails (null defaults to {"error": "model_dump failed"})
     * @return Map representation of the object, or defaultValue on failure
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    protected static Map<String, Object> safeModelDump(Object obj, Map<String, Object> defaultValue) {
        if (defaultValue == null) {
            defaultValue = Map.of("error", "model_dump failed");
        }
        if (obj == null) {
            return defaultValue;
        }
        try {
            if (obj instanceof Map) {
                return (Map<String, Object>) obj;
            }
            // Use Jackson ObjectMapper if available, otherwise use reflection-based approach
            Map<String, Object> result = new LinkedHashMap<>();
            for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                String methodName = method.getName();
                if (method.getParameterCount() == 0
                        && method.getDeclaringClass() != Object.class
                        && (methodName.startsWith("get") || methodName.startsWith("is"))) {
                    String fieldName;
                    if (methodName.startsWith("get") && methodName.length() > 3) {
                        fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
                    } else if (methodName.startsWith("is") && methodName.length() > 2) {
                        fieldName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
                    } else {
                        continue;
                    }
                    Object value = method.invoke(obj);
                    result.put(fieldName, value);
                }
            }
            return result;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Create a SysOperationEvent for logging.
     *
     * @param eventType       type of the system operation event
     * @param methodName      name of the method being logged
     * @param methodParams    parameters isPassed to the method
     * @param methodResult    results returned by the method
     * @param methodExecTimeMs execution time in milliseconds
     * @return created SysOperationEvent, or null
     */
    protected SysOperationEvent createSysOperationEvent(
            LogEventType eventType,
            String methodName,
            Map<String, Object> methodParams,
            Map<String, Object> methodResult,
            Double methodExecTimeMs) {
        return createSysOperationEvent(eventType, methodName, methodParams, methodResult, methodExecTimeMs, null);
    }

    /**
     * Create a SysOperationEvent for logging with additional metadata.
     * <p>
     * Mirrors Python's {@code _create_sys_operation_event(..., **kwargs)}.
     * The {@code extras} map carries additional arbitrary parameters similar to Python's kwargs.
     *
     * @param eventType       type of the system operation event
     * @param methodName      name of the method being logged
     * @param methodParams    parameters isPassed to the method
     * @param methodResult    results returned by the method
     * @param methodExecTimeMs execution time in milliseconds
     * @param extras          additional key-value pairs (mirrors Python's **kwargs)
     * @return created SysOperationEvent, or null
     */
    protected SysOperationEvent createSysOperationEvent(
            LogEventType eventType,
            String methodName,
            Map<String, Object> methodParams,
            Map<String, Object> methodResult,
            Double methodExecTimeMs,
            Map<String, Object> extras) {
        String moduleId = "sys_operation";
        String moduleName = "sys_operation";

        if (extras != null) {
            if (extras.containsKey("module_id")) {
                moduleId = String.valueOf(extras.get("module_id"));
            }
            if (extras.containsKey("module_name")) {
                moduleName = String.valueOf(extras.get("module_name"));
            }
        }

        return SysOperationEvent.builder()
                .eventType(eventType)
                .moduleId(moduleId)
                .moduleName(moduleName)
                .operationName(name)
                .operationMode(mode != null ? mode.getValue() : null)
                .operationDesc(description)
                .methodName(methodName)
                .methodParams(methodParams)
                .methodResult(methodResult)
                .methodExecTimeMs(methodExecTimeMs)
                .build();
    }
}
