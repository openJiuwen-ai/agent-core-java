/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.SysOperationEvent;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.utils.CallableSchemaExtractor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base operation for file, code, shell and custom sys operations.
 *
 * <p>Mirrors Python's {@code BaseOperation} in
 * {@code openjiuwen/core/sys_operation/base.py}.</p>
 */
public abstract class BaseOperation {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String name;
    private final OperationMode mode;
    private final String description;
    private final Object runConfig;

    protected BaseOperation(String name, OperationMode mode, String description, Object runConfig) {
        this.name = name;
        this.mode = mode != null ? mode : OperationMode.LOCAL;
        this.description = description != null ? description : "";
        this.runConfig = runConfig;
    }

    public String getName() {
        return name;
    }

    public OperationMode getMode() {
        return mode;
    }

    public String getDescription() {
        return description;
    }

    public Object getRunConfig() {
        return runConfig;
    }

    public List<ToolCard> listTools() {
        return null;
    }

    public static Map<String, Object> safeModelDump(Object obj) {
        return safeModelDump(obj, Map.of("error", "model_dump failed"));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> safeModelDump(Object obj, Map<String, Object> defaultValue) {
        Map<String, Object> fallback = defaultValue != null
                ? new LinkedHashMap<>(defaultValue)
                : Map.of("error", "model_dump failed");
        if (obj == null) {
            return fallback;
        }
        try {
            if (obj instanceof Map<?, ?> rawMap) {
                Map<String, Object> values = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> values.put(String.valueOf(key), value));
                return values;
            }
            return OBJECT_MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() {
            });
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    protected SysOperationEvent createSysOperationEvent(LogEventType eventType,
                                                        String methodName,
                                                        Map<String, Object> methodParams,
                                                        Map<String, Object> methodResult,
                                                        Double methodExecTimeMs,
                                                        Map<String, Object> kwargs) {
        if (!isSysOperationEventType(eventType)) {
            return null;
        }
        SysOperationEvent event = new SysOperationEvent();
        event.setEventType(eventType);
        setCommonEventFields(event, methodName, methodParams, methodResult, methodExecTimeMs, kwargs);
        return event;
    }

    protected SysOperationEvent createSysOperationEvent(String eventType,
                                                        String methodName,
                                                        Map<String, Object> methodParams,
                                                        Map<String, Object> methodResult,
                                                        Double methodExecTimeMs,
                                                        Map<String, Object> kwargs) {
        return null;
    }

    private void setCommonEventFields(SysOperationEvent event,
                                      String methodName,
                                      Map<String, Object> methodParams,
                                      Map<String, Object> methodResult,
                                      Double methodExecTimeMs,
                                      Map<String, Object> kwargs) {
        event.setOperationName(name);
        event.setOperationMode(mode.value());
        event.setOperationDesc(description);
        event.setMethodName(methodName);
        event.setMethodParams(methodParams);
        event.setMethodResult(methodResult);
        event.setMethodExecTimeMs(methodExecTimeMs);
        Map<String, Object> extra = kwargs != null ? new LinkedHashMap<>(kwargs) : new LinkedHashMap<>();
        Object moduleId = extra.containsKey("module_id") ? extra.remove("module_id") : "sys_operation";
        Object moduleName = extra.containsKey("module_name") ? extra.remove("module_name") : "sys_operation";
        if (moduleId != null) {
            event.setModuleId(String.valueOf(moduleId));
        }
        if (moduleName != null) {
            event.setModuleName(String.valueOf(moduleName));
        }
        applyString(extra, "session_id", event::setSessionId);
        applyString(extra, "conversation_id", event::setConversationId);
        applyString(extra, "trace_id", event::setTraceId);
        applyString(extra, "correlation_id", event::setCorrelationId);
        applyString(extra, "parent_event_id", event::setParentEventId);
        applyString(extra, "error_code", event::setErrorCode);
        applyString(extra, "error_message", event::setErrorMessage);
        applyString(extra, "message", event::setMessage);
        applyString(extra, "stacktrace", event::setStacktrace);
        applyString(extra, "exception", event::setExceptionDetail);
        Object metadata = extra.remove("metadata");
        if (metadata instanceof Map<?, ?> rawMetadata) {
            Map<String, Object> values = new LinkedHashMap<>();
            rawMetadata.forEach((key, value) -> values.put(String.valueOf(key), value));
            event.setMetadata(values);
        }
    }

    private static boolean isSysOperationEventType(LogEventType eventType) {
        return eventType == LogEventType.SYS_OP_START
                || eventType == LogEventType.SYS_OP_END
                || eventType == LogEventType.SYS_OP_ERROR
                || eventType == LogEventType.SYS_OP_STREAM;
    }

    private static void applyString(Map<String, Object> values, String key, Consumer<String> setter) {
        Object value = values.remove(key);
        if (value != null) {
            setter.accept(String.valueOf(value));
        }
    }

    protected List<ToolCard> generateToolCards(List<String> methodNames) {
        if (methodNames == null || methodNames.isEmpty()) {
            return List.of();
        }
        List<ToolCard> toolCards = new ArrayList<>();
        for (String methodName : methodNames) {
            Method method = resolveMethod(methodName);
            if (method == null) {
                continue;
            }
            toolCards.add(ToolCard.builder()
                    .id(methodName)
                    .name(methodName)
                    .description(CallableSchemaExtractor.extractFunctionDescription(method))
                    .inputParams(CallableSchemaExtractor.generateSchema(method))
                    .build());
        }
        return toolCards;
    }

    Method resolveMethod(String methodName) {
        String javaName = snakeToCamel(methodName);
        for (Method method : getClass().getMethods()) {
            if (method.getName().equals(javaName) || method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    static String snakeToCamel(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder(parts[0]);
        for (int index = 1; index < parts.length; index += 1) {
            if (parts[index].isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(parts[index].charAt(0)));
            if (parts[index].length() > 1) {
                builder.append(parts[index].substring(1));
            }
        }
        return builder.toString();
    }
}
