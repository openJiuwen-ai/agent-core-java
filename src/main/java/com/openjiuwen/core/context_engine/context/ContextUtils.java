/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ContextWindow;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Utility helpers for manipulating and parsing conversation contexts.
 *
 * <p>Mirrors Python's {@code ContextUtils} in
 * {@code openjiuwen/core/context_engine/context/context_utils.py}.</p>
 */
public final class ContextUtils {
    public static final String CONTEXT_MESSAGE_ID_KEY = "context_message_id";
    public static final int DEFAULT_CONTEXT_MAX_TOKENS = 200000;
    public static final String OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";
    public static final long OPENROUTER_MODEL_CACHE_TTL_SECONDS = 3600L;
    public static final Map<String, Integer> MODEL_DEFAULT_CONTEXT_WINDOW_TOKENS = buildDefaultContextWindowTokens();

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Object OPENROUTER_CACHE_LOCK = new Object();
    private static Map<String, Integer> openRouterModelContextWindowTokens = new LinkedHashMap<>();
    private static long openRouterModelContextWindowTokensFetchedAtNanos;

    private ContextUtils() {
    }

    public static Optional<ModelContextWindow> parseOpenrouterModel(Object model) {
        Map<String, Object> modelMap = asStringObjectMap(model);
        if (modelMap.isEmpty()) {
            return Optional.empty();
        }
        Object modelId = modelMap.get("id");
        Object contextLength = modelMap.get("context_length");
        if (!(modelId instanceof String id) || id.isBlank()) {
            return Optional.empty();
        }
        Integer length = positiveInteger(contextLength);
        if (length == null) {
            return Optional.empty();
        }
        return Optional.of(new ModelContextWindow(id, length));
    }

    public static Map<String, Integer> parseOpenrouterModelContextWindowTokens(List<?> models) {
        Map<String, Integer> fetchedTokens = new LinkedHashMap<>();
        Map<String, Integer> aliasTokens = new LinkedHashMap<>();
        Set<String> ambiguousAliases = new HashSet<>();
        for (Object model : models == null ? List.of() : models) {
            Optional<ModelContextWindow> parsedModel = parseOpenrouterModel(model);
            if (parsedModel.isEmpty()) {
                continue;
            }
            String modelId = parsedModel.get().modelId();
            int contextLength = parsedModel.get().contextLength();
            fetchedTokens.put(modelId, contextLength);
            if (!modelId.contains("/")) {
                continue;
            }
            String alias = modelId.substring(modelId.indexOf('/') + 1);
            if (aliasTokens.containsKey(alias)) {
                ambiguousAliases.add(alias);
            } else {
                aliasTokens.put(alias, contextLength);
            }
        }
        aliasTokens.forEach((alias, contextLength) -> {
            if (!ambiguousAliases.contains(alias)) {
                fetchedTokens.put(alias, contextLength);
            }
        });
        return fetchedTokens;
    }

    public static Map<String, Integer> fetchOpenrouterModelContextWindowTokens(double timeoutSeconds) {
        long now = System.nanoTime();
        synchronized (OPENROUTER_CACHE_LOCK) {
            if (openRouterModelContextWindowTokensFetchedAtNanos != 0
                    && now - openRouterModelContextWindowTokensFetchedAtNanos
                    < Duration.ofSeconds(OPENROUTER_MODEL_CACHE_TTL_SECONDS).toNanos()) {
                return new LinkedHashMap<>(openRouterModelContextWindowTokens);
            }
        }

        synchronized (OPENROUTER_CACHE_LOCK) {
            now = System.nanoTime();
            if (openRouterModelContextWindowTokensFetchedAtNanos != 0
                    && now - openRouterModelContextWindowTokensFetchedAtNanos
                    < Duration.ofSeconds(OPENROUTER_MODEL_CACHE_TTL_SECONDS).toNanos()) {
                return new LinkedHashMap<>(openRouterModelContextWindowTokens);
            }
            try {
                Duration timeout = Duration.ofMillis(Math.max(1L, Math.round(timeoutSeconds * 1000.0d)));
                HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(OPENROUTER_MODELS_URL))
                        .timeout(timeout)
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("response status must be < 400");
                }
                Map<String, Object> payload = JSON_MAPPER.readValue(response.body(),
                        new TypeReference<Map<String, Object>>() {
                        });
                Object data = payload.get("data");
                if (!(data instanceof List<?> models)) {
                    throw new IllegalStateException("response data must be a list");
                }
                Map<String, Integer> fetchedTokens = parseOpenrouterModelContextWindowTokens(models);
                if (fetchedTokens.isEmpty()) {
                    throw new IllegalStateException("response does not contain valid model context windows");
                }
                openRouterModelContextWindowTokens = new LinkedHashMap<>(fetchedTokens);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException | java.io.IOException ignored) {
                // Python logs and returns cached or empty values on request/parse failure.
            } finally {
                openRouterModelContextWindowTokensFetchedAtNanos = now;
            }
            return new LinkedHashMap<>(openRouterModelContextWindowTokens);
        }
    }

    public static Map<String, Integer> buildModelContextWindowTokens(Map<String, Integer> modelContextWindowTokens,
                                                                     boolean enableOpenrouterModelContextWindowTokens,
                                                                     double openrouterRequestTimeout) {
        Map<String, Integer> resolvedTokens = new LinkedHashMap<>();
        if (enableOpenrouterModelContextWindowTokens) {
            resolvedTokens.putAll(fetchOpenrouterModelContextWindowTokens(openrouterRequestTimeout));
        }
        if (modelContextWindowTokens != null) {
            resolvedTokens.putAll(modelContextWindowTokens);
        }
        return resolvedTokens;
    }

    public static void validateMessages(Object messages) {
        if (messages instanceof BaseMessage) {
            return;
        }
        if (messages instanceof List<?> list) {
            for (Object message : list) {
                if (!(message instanceof BaseMessage)) {
                    throw invalidMessageError();
                }
            }
            return;
        }
        throw invalidMessageError();
    }

    public static List<BaseMessage> ensureContextMessageIds(List<BaseMessage> messages) {
        List<BaseMessage> ensured = new ArrayList<>(messages == null ? List.of() : messages);
        for (BaseMessage message : ensured) {
            Map<String, Object> metadata = message.getMetadata();
            if (metadata == null) {
                metadata = new LinkedHashMap<>();
                message.setMetadata(metadata);
            }
            Object existingId = metadata.get(CONTEXT_MESSAGE_ID_KEY);
            if (!(existingId instanceof String text) || text.isBlank()) {
                metadata.put(CONTEXT_MESSAGE_ID_KEY, UUID.randomUUID().toString().replace("-", ""));
            }
        }
        return ensured;
    }

    public static void validateAndFixContextWindow(ContextWindow contextWindow) {
        List<BaseMessage> messages = contextWindow.getContextMessages();
        if (messages.isEmpty()) {
            return;
        }
        int firstNonTool = 0;
        while (firstNonTool < messages.size() && messages.get(firstNonTool) instanceof ToolMessage) {
            firstNonTool++;
        }
        if (firstNonTool == messages.size()) {
            contextWindow.setContextMessages(List.of());
            return;
        }
        if (firstNonTool > 0) {
            contextWindow.setContextMessages(messages.subList(firstNonTool, messages.size()));
        }
    }

    public static int resolveContextMax(String modelName, Integer fallbackContextWindowTokens,
                                        Map<String, Integer> modelContextWindowTokens) {
        if (fallbackContextWindowTokens != null && fallbackContextWindowTokens > 0) {
            return fallbackContextWindowTokens;
        }
        if (modelName != null && !modelName.isBlank()) {
            Integer configuredValue = modelContextWindowTokens == null ? null : modelContextWindowTokens.get(modelName);
            if (configuredValue != null && configuredValue > 0) {
                return configuredValue;
            }
            Integer builtinValue = MODEL_DEFAULT_CONTEXT_WINDOW_TOKENS.get(modelName);
            if (builtinValue != null && builtinValue > 0) {
                return builtinValue;
            }
        }
        return DEFAULT_CONTEXT_MAX_TOKENS;
    }

    public static boolean isCompressionProcessor(Object processor) {
        String processorType = invokeString(processor, "processorType")
                .or(() -> invokeString(processor, "processor_type"))
                .orElse("");
        String moduleName = processor == null ? "" : processor.getClass().getName().toLowerCase();
        String lowerType = processorType.toLowerCase();
        return lowerType.contains("compressor")
                || lowerType.contains("compact")
                || moduleName.contains(".processor.compressor.");
    }

    public static Optional<Integer> findLastAiMessageWithoutToolCall(List<BaseMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            BaseMessage message = messages.get(index);
            if ("assistant".equals(message.getRole()) && !hasSingularToolCall(message)) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    public static List<BaseMessage> replaceMessages(List<BaseMessage> messages, List<BaseMessage> targetMessages,
                                                    int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex >= messages.size() || startIndex > endIndex) {
            throw new IndexOutOfBoundsException("Invalid start/end index");
        }
        List<BaseMessage> result = new ArrayList<>(messages.size() - (endIndex - startIndex + 1)
                + targetMessages.size());
        result.addAll(messages.subList(0, startIndex));
        result.addAll(targetMessages);
        result.addAll(messages.subList(endIndex + 1, messages.size()));
        return result;
    }

    public static String formatReloadedMessages(String offloadHandle, List<BaseMessage> messages) {
        StringBuilder formattedContent = new StringBuilder("reload messages with handle=")
                .append(offloadHandle)
                .append(":\n");
        for (int index = 0; index < messages.size(); index++) {
            formattedContent.append("message ").append(index + 1).append(": ")
                    .append(writeJson(messages.get(index).modelDump()));
            if (index != messages.size() - 1) {
                formattedContent.append('\n');
            }
        }
        return formattedContent.toString();
    }

    public static List<DialogueRound> findAllDialogueRound(List<BaseMessage> messages) {
        List<DialogueRound> rounds = new ArrayList<>();
        int index = messages.size() - 1;
        while (index >= 0) {
            Integer assistantIndex = null;
            int roundEnd = index;
            while (index >= 0 && !"assistant".equals(messages.get(index).getRole())) {
                index--;
            }
            if (index >= 0) {
                BaseMessage message = messages.get(index);
                if (!hasToolCalls(message)) {
                    assistantIndex = index;
                }
                index--;
            } else {
                index = roundEnd;
            }
            while (index >= 0 && !"user".equals(messages.get(index).getRole())) {
                index--;
            }
            if (index < 0) {
                break;
            }
            int foundUserIndex = index;
            int userIndex = findContiguousUserGroupStart(messages, foundUserIndex);
            if (rounds.isEmpty()) {
                for (int lastRoundIndex = messages.size() - 1; lastRoundIndex > foundUserIndex; lastRoundIndex--) {
                    if ("user".equals(messages.get(lastRoundIndex).getRole())) {
                        rounds.add(new DialogueRound(findContiguousUserGroupStart(messages, lastRoundIndex), null));
                        break;
                    }
                }
            }
            rounds.add(new DialogueRound(userIndex, assistantIndex));
            index = userIndex - 1;
        }
        return rounds;
    }

    public static int findLastNDialogueRound(List<BaseMessage> messages, int n) {
        List<DialogueRound> rounds = findAllDialogueRound(messages);
        if (rounds.isEmpty()) {
            return -1;
        }
        return rounds.get(Math.min(n, rounds.size()) - 1).userIndex();
    }

    public static boolean toolCallMatchesId(Object toolCall, String toolCallId) {
        if (toolCall instanceof ToolCall call) {
            return toolCallId.equals(call.getId());
        }
        if (toolCall instanceof Map<?, ?> map) {
            return toolCallId.equals(map.get("id"));
        }
        return readProperty(toolCall, "id").map(toolCallId::equals).orElse(false);
    }

    public static Optional<String> extractToolName(Object toolCall) {
        if (toolCall instanceof ToolCall call) {
            return optionalText(call.getName());
        }
        if (toolCall instanceof Map<?, ?> map) {
            Object function = map.get("function");
            if (function instanceof Map<?, ?> functionMap) {
                Optional<String> functionName = optionalText(functionMap.get("name"));
                if (functionName.isPresent()) {
                    return functionName;
                }
            }
            return optionalText(map.get("name"));
        }
        Optional<Object> function = readProperty(toolCall, "function");
        if (function.isPresent()) {
            Optional<String> functionName = readProperty(function.get(), "name").flatMap(ContextUtils::optionalText);
            if (functionName.isPresent()) {
                return functionName;
            }
        }
        return readProperty(toolCall, "name").flatMap(ContextUtils::optionalText);
    }

    public static Optional<Object> resolveToolCallFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        if (!(message instanceof ToolMessage toolMessage)) {
            return Optional.empty();
        }
        String toolCallId = toolMessage.getToolCallId();
        if (toolCallId == null || toolCallId.isBlank()) {
            return Optional.empty();
        }
        for (int index = contextMessages.size() - 1; index >= 0; index--) {
            BaseMessage contextMessage = contextMessages.get(index);
            if (!(contextMessage instanceof AssistantMessage assistantMessage)
                    || assistantMessage.getToolCalls() == null) {
                continue;
            }
            for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                if (toolCallMatchesId(toolCall, toolCallId)) {
                    return Optional.of(toolCall);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<String> resolveToolNameFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        return resolveToolCallFromMessage(message, contextMessages).flatMap(ContextUtils::extractToolName);
    }

    public static int estimateTokens(Object content) {
        if (content instanceof String text) {
            return Math.max(text.length() / 3, 1);
        }
        try {
            return Math.max(JSON_MAPPER.writeValueAsString(content).length() / 3, 1);
        } catch (JsonProcessingException ex) {
            return Math.max(String.valueOf(content).length() / 3, 1);
        }
    }

    public static int estimateMessageTokens(BaseMessage message) {
        return estimateTokens(message == null ? "" : message.getContent());
    }

    static void clearOpenrouterCacheForTest() {
        synchronized (OPENROUTER_CACHE_LOCK) {
            openRouterModelContextWindowTokens = new LinkedHashMap<>();
            openRouterModelContextWindowTokensFetchedAtNanos = 0L;
        }
    }

    private static RuntimeException invalidMessageError() {
        return ErrorHelper.buildError(StatusCode.CONTEXT_MESSAGE_INVALID,
                "error_msg", "messages should be a BaseMessage or a list of BaseMessage");
    }

    private static Integer positiveInteger(Object value) {
        if (value instanceof Number number) {
            int intValue = number.intValue();
            return intValue > 0 && number.doubleValue() == intValue ? intValue : null;
        }
        return null;
    }

    private static int findContiguousUserGroupStart(List<BaseMessage> messages, int userIndex) {
        int index = userIndex;
        while (index - 1 >= 0 && "user".equals(messages.get(index - 1).getRole())) {
            index--;
        }
        return index;
    }

    private static boolean hasToolCalls(BaseMessage message) {
        return message instanceof AssistantMessage assistantMessage
                && assistantMessage.getToolCalls() != null
                && !assistantMessage.getToolCalls().isEmpty();
    }

    private static boolean hasSingularToolCall(BaseMessage message) {
        Optional<Object> directValue = readProperty(message, "tool_call");
        if (directValue.isPresent()) {
            Object value = directValue.get();
            if (value instanceof List<?> list) {
                return !list.isEmpty();
            }
            return true;
        }
        return message.getMetadata() != null && message.getMetadata().containsKey("tool_call")
                && message.getMetadata().get("tool_call") != null;
    }

    private static Optional<String> invokeString(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return optionalText(value);
        } catch (ReflectiveOperationException ex) {
            return Optional.empty();
        }
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

    private static Optional<String> optionalText(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private static String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return Map.of();
    }

    private static Map<String, Integer> buildDefaultContextWindowTokens() {
        Map<String, Integer> values = new HashMap<>();
        values.put("glm-5.1", 200000);
        values.put("glm-5", 200000);
        values.put("glm-5-turbo", 200000);
        values.put("glm-4.7", 200000);
        values.put("glm-4.7-flash", 200000);
        values.put("glm-4.7-flashx", 200000);
        values.put("glm-4-long", 1000000);
        values.put("glm-4", 128000);
        values.put("glm-4-9b-chat-1m", 1048576);
        values.put("gpt-5.5", 1050000);
        values.put("gpt-5.4", 1050000);
        values.put("gpt-5.4-mini", 400000);
        values.put("gpt-5.4-nano", 400000);
        values.put("gpt-5", 400000);
        values.put("gpt-5-mini", 400000);
        values.put("gpt-5-nano", 400000);
        values.put("gpt-4.1", 1047576);
        values.put("gpt-4.1-mini", 1047576);
        values.put("gpt-4.1-nano", 1047576);
        values.put("gpt-4o", 128000);
        values.put("gpt-4o-mini", 128000);
        values.put("gpt-4-turbo", 128000);
        values.put("gpt-3.5-turbo", 16384);
        values.put("deepseek-v4-pro", 1000000);
        values.put("deepseek-v4-flash", 1000000);
        values.put("deepseek-v3", 128000);
        values.put("deepseek-chat", 65536);
        values.put("claude-opus-4-7", 1000000);
        values.put("claude-opus-4-6", 1000000);
        values.put("claude-sonnet-4-6", 1000000);
        values.put("claude-haiku-4-5", 200000);
        values.put("claude-opus-4.6", 1000000);
        values.put("claude-sonnet-4.6", 1000000);
        values.put("claude-haiku-4.5", 200000);
        values.put("gemini-3-pro-preview", 1048576);
        values.put("gemini-3-flash-preview", 1048576);
        values.put("gemini-2.5-pro", 1048576);
        values.put("gemini-2.5-flash", 1048576);
        values.put("llama-4-maverick", 1000000);
        values.put("llama-4-scout", 10000000);
        values.put("qwen3-max", 262144);
        values.put("qwen3.5-plus", 1000000);
        values.put("qwen3.5-flash", 1000000);
        values.put("qwen3-coder-plus", 1000000);
        values.put("qwen3-coder-next", 262144);
        values.put("qwen-max", 262144);
        values.put("qwen-plus", 1000000);
        values.put("qwen-flash", 1000000);
        values.put("qwen-turbo", 8192);
        values.put("qwen-long", 1000000);
        values.put("kimi-k2.5", 262144);
        values.put("MiniMax-M2.7", 204800);
        values.put("MiniMax-M2.7-highspeed", 204800);
        values.put("MiniMax-M2.5", 204800);
        values.put("MiniMax-M2.5-highspeed", 204800);
        values.put("grok-4.3", 1000000);
        values.put("grok-4.3-latest", 1000000);
        values.put("grok-latest", 1000000);
        return Collections.unmodifiableMap(values);
    }

    /**
     * Parsed OpenRouter context-window row.
     *
     * <p>Mirrors Python's {@code _parse_openrouter_model} tuple in
     * {@code openjiuwen/core/context_engine/context/context_utils.py}.</p>
     */
    public record ModelContextWindow(String modelId, int contextLength) {
    }

    /**
     * Dialogue-round boundary.
     *
     * <p>Mirrors Python's {@code find_all_dialogue_round} list entries in
     * {@code openjiuwen/core/context_engine/context/context_utils.py}.</p>
     */
    public record DialogueRound(int userIndex, Integer assistantIndex) {
    }
}
