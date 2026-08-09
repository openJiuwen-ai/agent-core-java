/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.context.SessionMemorySupport;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Abstract base class for all context-processing plug-ins.
 *
 * <p>Mirrors Python's {@code ContextProcessor} in
 * {@code openjiuwen/core/context_engine/processor/base.py}.</p>
 */
public abstract class ContextProcessor implements SessionModelContext.ContextProcessorPort {
    public static final String OFFLOAD_MESSAGE_HANDLE = "[[OFFLOAD: handle=%s, type=%s]]";
    public static final String OFFLOAD_MESSAGE_HANDLE_WITH_PATH = "[[OFFLOAD: type=%s, path=%s]]";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final Object config;
    private Map<String, Object> compressionUsage;

    protected ContextProcessor(Object config) {
        this.config = config;
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messages,
                                                                            boolean force,
                                                                            Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(
                new SessionModelContext.ProcessResult(null, messages == null ? List.of() : messages, null));
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onGetContextWindow(SessionModelContext context,
                                                                                 ContextWindow window,
                                                                                 Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, null, window));
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messages,
                                                       Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletionStage<Boolean> triggerGetContextWindow(SessionModelContext context, ContextWindow window,
                                                            Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(false);
    }

    public abstract void loadState(Map<String, Object> state);

    public abstract Map<String, Object> saveState();

    @Override
    public String processorType() {
        return getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    public <T> T config() {
        return (T) config;
    }

    public void resetCompressionUsage() {
        compressionUsage = null;
    }

    public void recordCompressionUsage(Object response) {
        Map<String, Object> usage = extractUsageMetadata(response);
        if (usage != null) {
            compressionUsage = mergeCompressionUsage(compressionUsage, usage);
        }
    }

    public Map<String, Object> currentCompressionUsage() {
        return compressionUsage == null ? null : new LinkedHashMap<>(compressionUsage);
    }

    public CompletionStage<BaseMessage> offloadMessages(String role, String content, List<BaseMessage> messages,
                                                        SessionModelContext context) {
        return offloadMessages(role, content, messages, context, null, "filesystem", null, Map.of());
    }

    public CompletionStage<BaseMessage> offloadMessages(String role, String content, List<BaseMessage> messages,
                                                        SessionModelContext context, String offloadHandle,
                                                        String offloadType, String offloadPath,
                                                        Map<String, Object> kwargs) {
        if (messages == null || messages.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        String effectiveHandle = isBlank(offloadHandle) ? UUID.randomUUID().toString().replace("-", "")
                : offloadHandle;
        if (context == null) {
            return CompletableFuture.completedFuture(null);
        }
        String effectiveType = isBlank(offloadType) ? "filesystem" : offloadType;
        if ("in_memory".equals(effectiveType)) {
            return CompletableFuture.completedFuture(
                    offloadMessagesToMemory(role, content, messages, context, effectiveHandle, kwargs));
        }
        if ("filesystem".equals(effectiveType)) {
            String path = isBlank(offloadPath)
                    ? generateOffloadPath(context.workspaceDir(), context.sessionId(), effectiveHandle)
                    : offloadPath;
            Object sysOperation = kwargs == null ? null : kwargs.get("sys_operation");
            boolean writeSuccess = writeOffloadToFile(context.sessionId(), effectiveHandle, path, messages,
                    sysOperation);
            if (!writeSuccess) {
                return CompletableFuture.completedFuture(
                        offloadMessagesToMemory(role, content, messages, context, effectiveHandle, kwargs));
            }
            return CompletableFuture.completedFuture(
                    offloadMessagesToFilesystem(role, content, effectiveHandle, path, kwargs));
        }
        return CompletableFuture.completedFuture(null);
    }

    public static String generateOffloadPath(String workspaceDir, String sessionId, String offloadHandle) {
        if (!isBlank(workspaceDir)) {
            return Path.of(workspaceDir, "context", sessionId + "_context", "offload",
                    offloadHandle + ".json").toString();
        }
        return Path.of("memory", "offloads", sessionId, offloadHandle + ".json").toString();
    }

    public static boolean apiRound(List<BaseMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        List<SessionMemorySupport.ApiRound> completedRounds = SessionMemorySupport.groupCompletedApiRounds(messages);
        return !completedRounds.isEmpty() && completedRounds.get(completedRounds.size() - 1).end() == messages.size();
    }

    public static Map<String, Object> extractUsageMetadata(Object response) {
        Optional<Object> metadata = readProperty(response, "usageMetadata")
                .or(() -> readProperty(response, "usage_metadata"));
        if (metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> data = asStringObjectMap(toModelDump(metadata.get()).orElse(metadata.get()));
        if (data == null) {
            return null;
        }
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("calls", 1);
        usage.put("input_tokens", toLong(data.get("input_tokens")));
        usage.put("output_tokens", toLong(data.get("output_tokens")));
        usage.put("total_tokens", toLong(data.get("total_tokens")));
        usage.put("cache_tokens", toLong(data.get("cache_tokens")));
        usage.put("input_cost", toDouble(data.get("input_cost")));
        usage.put("output_cost", toDouble(data.get("output_cost")));
        usage.put("total_cost", toDouble(data.get("total_cost")));
        usage.put("model_name", String.valueOf(data.getOrDefault("model_name", "")));
        usage.put("details", List.of(new LinkedHashMap<>(data)));
        return usage;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mergeCompressionUsage(Map<String, Object> left, Map<String, Object> right) {
        if (left == null) {
            return right == null ? null : new LinkedHashMap<>(right);
        }
        if (right == null) {
            return new LinkedHashMap<>(left);
        }
        Map<String, Object> merged = new LinkedHashMap<>(left);
        for (String key : List.of("calls", "input_tokens", "output_tokens", "total_tokens", "cache_tokens")) {
            merged.put(key, toLong(merged.get(key)) + toLong(right.get(key)));
        }
        for (String key : List.of("input_cost", "output_cost", "total_cost")) {
            merged.put(key, toDouble(merged.get(key)) + toDouble(right.get(key)));
        }
        if (String.valueOf(merged.getOrDefault("model_name", "")).isBlank()) {
            merged.put("model_name", String.valueOf(right.getOrDefault("model_name", "")));
        }
        List<Object> details = new ArrayList<>();
        Object leftDetails = merged.get("details");
        if (leftDetails instanceof List<?> list) {
            details.addAll(list);
        }
        Object rightDetails = right.get("details");
        if (rightDetails instanceof List<?> list) {
            details.addAll(list);
        }
        merged.put("details", details);
        return merged;
    }

    protected static BaseMessage offloadMessagesToMemory(String role, String content, List<BaseMessage> messages,
                                                         SessionModelContext context, String offloadHandle,
                                                         Map<String, Object> kwargs) {
        String markedContent = String.valueOf(content) + String.format(OFFLOAD_MESSAGE_HANDLE, offloadHandle,
                "in_memory");
        context.offloadMessages(offloadHandle, messages);
        return createOffloadMessage(role, markedContent, offloadHandle, "in_memory", kwargs);
    }

    protected static BaseMessage offloadMessagesToFilesystem(String role, String content, String offloadHandle,
                                                             String offloadPath, Map<String, Object> kwargs) {
        String markedContent;
        if (!isBlank(offloadPath)) {
            markedContent = String.valueOf(content) + String.format(OFFLOAD_MESSAGE_HANDLE_WITH_PATH, "filesystem",
                    offloadPath);
        } else {
            markedContent = String.valueOf(content) + String.format(OFFLOAD_MESSAGE_HANDLE, offloadHandle,
                    "filesystem");
        }
        return createOffloadMessage(role, markedContent, offloadHandle, "filesystem", kwargs);
    }

    protected static boolean writeOffloadToFile(String sessionId, String offloadHandle, String offloadPath,
                                                List<BaseMessage> messages, Object sysOperation) {
        String filePath = isBlank(offloadPath) ? Path.of("memory", "offloads", sessionId,
                offloadHandle + ".json").toString() : offloadPath;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("offload_handle", offloadHandle);
        payload.put("messages", messages.stream().map(BaseMessage::modelDump).toList());
        try {
            String content = JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            if (sysOperation == null) {
                Path path = Path.of(filePath);
                if (!path.isAbsolute()) {
                    return false;
                }
                Files.createDirectories(path.getParent());
                Files.writeString(path, content, StandardCharsets.UTF_8);
                return true;
            }
            return invokeSysOperationWrite(sysOperation, filePath, content);
        } catch (JsonProcessingException ex) {
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    private static BaseMessage createOffloadMessage(String role, String content, String offloadHandle,
                                                    String offloadType, Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? Map.of() : kwargs;
        BaseMessage message = switch (role) {
            case "assistant" -> new AssistantMessage(content);
            case "tool" -> new ToolMessage(content, stringValue(safeKwargs.get("tool_call_id"), ""));
            case "system" -> new SystemMessage(content);
            default -> new UserMessage(content);
        };
        if (safeKwargs.get("name") instanceof String name) {
            message.setName(name);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        Object rawMetadata = safeKwargs.get("metadata");
        if (rawMetadata instanceof Map<?, ?> metadataMap) {
            metadataMap.forEach((key, value) -> metadata.put(String.valueOf(key), value));
        }
        metadata.put("offload_handle", offloadHandle);
        metadata.put("offload_type", offloadType);
        message.setMetadata(metadata);
        return message;
    }

    private static boolean invokeSysOperationWrite(Object sysOperation, String filePath, String content) {
        Optional<Object> fs = invokeNoArg(sysOperation, "fs");
        Object target = fs.orElse(sysOperation);
        return invokeWrite(target, "writeFile", filePath, content)
                || invokeWrite(target, "write_file", filePath, content);
    }

    private static boolean invokeWrite(Object target, String methodName, String filePath, String content) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class, String.class);
            Object result = method.invoke(target, filePath, content);
            if (result instanceof CompletionStage<?> stage) {
                stage.toCompletableFuture().join();
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Optional<Object> invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> toModelDump(Object target) {
        return invokeNoArg(target, "modelDump").or(() -> invokeNoArg(target, "model_dump"));
    }

    private static Optional<Object> readProperty(Object target, String name) {
        if (target == null) {
            return Optional.empty();
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method method = target.getClass().getMethod(getter);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = target.getClass().getField(name);
            return Optional.ofNullable(field.get(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> asStringObjectMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return null;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0.0d;
            }
        }
        return 0.0d;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
