/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.context.schema.OffloadMessage;
import com.openjiuwen.core.context.schema.OffloadMessages;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Trims and offloads large context messages when configured count or token thresholds are exceeded.
 *
 * <p>Mirrors Python's {@code MessageOffloader} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_offloader.py}.</p>
 */
public class MessageOffloader extends ContextProcessor {
    public static final String OMIT_STRING = "...";

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageOffloader.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    static {
        ContextEngine.registerProcessor("MessageOffloader", MessageOffloader.class);
    }

    private final MessageOffloaderConfig config;

    public MessageOffloader(Object config) {
        this(asConfig(config));
    }

    public MessageOffloader(MessageOffloaderConfig config) {
        this(config == null ? new MessageOffloaderConfig() : config, true);
    }

    private MessageOffloader(MessageOffloaderConfig config, boolean ignored) {
        super(config);
        this.config = config;
        validateConfig();
    }

    public MessageOffloaderConfig getConfig() {
        return config;
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messagesToAdd,
                                                                            boolean force,
                                                                            Map<String, Object> kwargs) {
        List<BaseMessage> contextMessages = new ArrayList<>(context.getMessages());
        int contextSize = contextMessages.size();
        List<BaseMessage> incoming = messagesToAdd == null ? List.of() : new ArrayList<>(messagesToAdd);
        contextMessages.addAll(incoming);

        OffloadResult offloadResult = offloadLargeMessages(contextMessages, context, kwargs);
        List<BaseMessage> processedMessages = offloadResult.messages();
        int splitIndex = Math.min(contextSize, processedMessages.size());
        List<BaseMessage> processedContextMessages = new ArrayList<>(processedMessages.subList(0, splitIndex));
        List<BaseMessage> processedMessagesToAdd = new ArrayList<>(
                processedMessages.subList(splitIndex, processedMessages.size()));
        context.setMessages(processedContextMessages, true);
        return CompletableFuture.completedFuture(
                new SessionModelContext.ProcessResult(offloadResult.event(), processedMessagesToAdd, null));
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messagesToAdd,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> allMessages = new ArrayList<>(context == null ? List.of() : context.getMessages());
        allMessages.addAll(messagesToAdd == null ? List.of() : messagesToAdd);
        int messageSize = allMessages.size();
        if (config.getMessagesToKeep() != null && messageSize <= config.getMessagesToKeep()) {
            return CompletableFuture.completedFuture(false);
        }

        Integer messagesThreshold = config.getMessagesThreshold();
        if (messagesThreshold != null && messageSize > messagesThreshold) {
            if (!hasOffloadCandidate(allMessages, context)) {
                return CompletableFuture.completedFuture(false);
            }
            LOGGER.info("[{} triggered] context messages num {} exceeds threshold of {}",
                    processorType(), messageSize, messagesThreshold);
            return CompletableFuture.completedFuture(true);
        }

        int tokens = 0;
        if (context != null && context.tokenCounter() != null) {
            tokens += context.tokenCounter().countTokens(context.getMessages());
            tokens += context.tokenCounter().countTokens(messagesToAdd == null ? List.of() : messagesToAdd);
        }
        if (tokens > config.getTokensThreshold()) {
            if (!hasOffloadCandidate(allMessages, context)) {
                return CompletableFuture.completedFuture(false);
            }
            LOGGER.info("[{} triggered] context tokens {} exceeds threshold of {}",
                    processorType(), tokens, config.getTokensThreshold());
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public void loadState(Map<String, Object> state) {
        // Python implementation is stateless.
    }

    @Override
    public Map<String, Object> saveState() {
        return new LinkedHashMap<>();
    }

    OffloadResult offloadLargeMessages(List<BaseMessage> messages, SessionModelContext context,
                                       Map<String, Object> kwargs) {
        List<BaseMessage> processedMessages = new ArrayList<>(messages == null ? List.of() : messages);
        int offloadRange = getOffloadRange(processedMessages);
        List<Integer> modifiedIndices = new ArrayList<>();

        for (int index = offloadRange - 1; index >= 0; index--) {
            BaseMessage message = processedMessages.get(index);
            if (!shouldOffloadMessage(message, processedMessages, context)) {
                continue;
            }
            BaseMessage offloadMessage = offloadMessage(message, context, kwargs).toCompletableFuture().join();
            processedMessages = ContextUtils.replaceMessages(processedMessages, List.of(offloadMessage), index, index);
            modifiedIndices.add(index);
        }

        return new OffloadResult(
                new ContextEvent(processorType(), modifiedIndices, "", null),
                processedMessages);
    }

    CompletionStage<BaseMessage> offloadMessage(BaseMessage message, SessionModelContext context,
                                                Map<String, Object> kwargs) {
        String content = (String) message.getContent();
        String trimmedContent = content.substring(0, Math.min(config.getTrimSize(), content.length())) + OMIT_STRING;
        Map<String, Object> extraFields = new LinkedHashMap<>(message.modelDump());
        extraFields.remove("role");
        extraFields.remove("content");
        extraFields.remove("offload_type");
        extraFields.remove("offload_handle");
        if (kwargs != null) {
            extraFields.putAll(kwargs);
        }
        OffloadTarget target = newOffloadHandleAndPath(context);
        return offloadMessages(
                message.getRole(),
                trimmedContent,
                List.of(message),
                context,
                target.offloadHandle(),
                "filesystem",
                target.offloadPath(),
                extraFields
        ).thenApply(rawMessage -> wrapOffloadMessage(message, rawMessage, target.offloadHandle(), extraFields));
    }

    OffloadTarget newOffloadHandleAndPath(SessionModelContext context) {
        String offloadHandle = UUID.randomUUID().toString().replace("-", "");
        String sessionId = context == null ? "" : context.sessionId();
        String workspaceDir = context == null ? "" : context.workspaceDir();
        String fileName = processorType() + "_" + offloadHandle + ".json";
        if (workspaceDir != null && !workspaceDir.isBlank()) {
            return new OffloadTarget(offloadHandle,
                    Path.of(workspaceDir, "context", sessionId + "_context", "offload", fileName).toString());
        }
        return new OffloadTarget(offloadHandle, null);
    }

    int getOffloadRange(List<BaseMessage> messages) {
        List<BaseMessage> safeMessages = messages == null ? List.of() : messages;
        Optional<Integer> lastAiMessageIndex = Optional.empty();
        if (config.isKeepLastRound()) {
            lastAiMessageIndex = ContextUtils.findLastAiMessageWithoutToolCall(safeMessages);
        }
        int keepIndex = config.getMessagesToKeep() == null
                ? safeMessages.size()
                : safeMessages.size() - config.getMessagesToKeep();
        return lastAiMessageIndex.map(index -> Math.min(index, keepIndex)).orElse(keepIndex);
    }

    boolean hasOffloadCandidate(List<BaseMessage> messages, SessionModelContext context) {
        int offloadRange = getOffloadRange(messages);
        for (int index = offloadRange - 1; index >= 0; index--) {
            if (shouldOffloadMessage(messages.get(index), messages, context)) {
                return true;
            }
        }
        return false;
    }

    boolean shouldOffloadMessage(BaseMessage message, List<BaseMessage> contextMessages, SessionModelContext context) {
        if (message == null || !config.getOffloadMessageType().contains(message.getRole())) {
            return false;
        }
        if (!(message.getContent() instanceof String content)) {
            return false;
        }
        if (content.length() <= config.getLargeMessageThreshold()) {
            return false;
        }
        if (message instanceof OffloadMessage) {
            return false;
        }
        return !isProtectedToolMessage(message, contextMessages);
    }

    boolean isProtectedToolMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        if (!(message instanceof ToolMessage)) {
            return false;
        }
        Optional<Object> toolCall = resolveToolCallFromMessage(message, contextMessages);
        if (toolCall.isEmpty()) {
            return false;
        }
        String toolName = ContextUtils.extractToolName(toolCall.get()).orElse(null);
        Map<String, Object> toolArgs = extractToolArgs(toolCall.get());
        for (String protectedName : config.getProtectedToolNames()) {
            if (protectedName == null) {
                continue;
            }
            int separator = protectedName.indexOf(':');
            if (separator >= 0) {
                String protectedTool = protectedName.substring(0, separator);
                String protectedPattern = protectedName.substring(separator + 1);
                if (protectedTool.equals(toolName) && matchPattern(toolArgs, protectedPattern)) {
                    return true;
                }
            } else if (protectedName.equals(toolName)) {
                return true;
            }
        }
        return false;
    }

    Optional<Object> resolveToolCallFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        return ContextUtils.resolveToolCallFromMessage(message, contextMessages == null ? List.of() : contextMessages);
    }

    Optional<String> resolveToolNameFromMessage(BaseMessage message, List<BaseMessage> contextMessages) {
        return ContextUtils.resolveToolNameFromMessage(message, contextMessages == null ? List.of() : contextMessages);
    }

    static Map<String, Object> extractToolArgs(Object toolCall) {
        if (toolCall instanceof ToolCall call) {
            return parseArgs(call.getArguments());
        }
        if (toolCall instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = toStringObjectMap(rawMap);
            Object function = map.get("function");
            if (function instanceof Map<?, ?> functionMap) {
                Map<String, Object> normalizedFunction = toStringObjectMap(functionMap);
                Object args = normalizedFunction.get("arguments");
                Map<String, Object> parsedArgs = parseArgs(args);
                if (!parsedArgs.isEmpty()) {
                    return parsedArgs;
                }
            }
            Map<String, Object> parsedArgs = parseArgs(map.get("arguments"));
            if (!parsedArgs.isEmpty()) {
                return parsedArgs;
            }
        }

        Optional<Object> function = readProperty(toolCall, "function");
        Optional<Object> args = function.flatMap(value -> readProperty(value, "arguments"));
        if (args.isEmpty()) {
            args = readProperty(toolCall, "arguments");
        }
        return args.map(MessageOffloader::parseArgs).orElseGet(LinkedHashMap::new);
    }

    static boolean matchPattern(Map<String, Object> args, String pattern) {
        if (args == null || pattern == null) {
            return false;
        }
        for (Object value : args.values()) {
            if (value instanceof String text && wildcardMatches(text, pattern)) {
                return true;
            }
        }
        return false;
    }

    private void validateConfig() {
        if (config.getTrimSize() >= config.getLargeMessageThreshold()) {
            throw ErrorHelper.buildError(
                    StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg",
                    "trim_size " + config.getTrimSize() + " cannot larger than large_message_threshold "
                            + config.getLargeMessageThreshold());
        }
        if (config.getMessagesToKeep() != null
                && config.getMessagesThreshold() != null
                && config.getMessagesToKeep() >= config.getMessagesThreshold()) {
            throw ErrorHelper.buildError(
                    StatusCode.CONTEXT_EXECUTION_ERROR,
                    "error_msg",
                    "messages_to_keep " + config.getMessagesToKeep()
                            + " cannot larger than messages_threshold " + config.getMessagesThreshold());
        }
    }

    private BaseMessage wrapOffloadMessage(BaseMessage source, BaseMessage rawMessage, String fallbackHandle,
                                           Map<String, Object> kwargs) {
        Map<String, Object> metadata = rawMessage == null || rawMessage.getMetadata() == null
                ? Map.of()
                : rawMessage.getMetadata();
        String offloadHandle = stringValue(metadata.get("offload_handle"), fallbackHandle);
        String offloadType = stringValue(metadata.get("offload_type"), "in_memory");
        Map<String, Object> safeKwargs = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        if ("tool".equals(source.getRole()) && source instanceof ToolMessage toolMessage) {
            safeKwargs.putIfAbsent("tool_call_id", toolMessage.getToolCallId());
        }
        return OffloadMessages.createOffloadMessage(
                source.getRole(),
                rawMessage == null ? "" : rawMessage.getContentAsString(),
                offloadHandle,
                offloadType,
                safeKwargs);
    }

    private static Map<String, Object> parseArgs(Object args) {
        if (args instanceof Map<?, ?> argsMap) {
            return toStringObjectMap(argsMap);
        }
        if (args instanceof String argsText && !argsText.isBlank()) {
            try {
                return JSON_MAPPER.readValue(argsText, new TypeReference<LinkedHashMap<String, Object>>() {
                });
            } catch (JsonProcessingException ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Optional<Object> readProperty(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
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

    private static boolean wildcardMatches(String text, String pattern) {
        return Pattern.matches(toWildcardRegex(pattern), text);
    }

    private static String toWildcardRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < pattern.length(); index++) {
            char ch = pattern.charAt(index);
            if (ch == '*') {
                regex.append(".*");
            } else if (ch == '?') {
                regex.append('.');
            } else if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
                regex.append('\\').append(ch);
            } else {
                regex.append(ch);
            }
        }
        regex.append('$');
        return regex.toString();
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static MessageOffloaderConfig asConfig(Object config) {
        if (config == null) {
            return new MessageOffloaderConfig();
        }
        if (config instanceof MessageOffloaderConfig messageOffloaderConfig) {
            return messageOffloaderConfig;
        }
        throw new IllegalArgumentException("MessageOffloader requires MessageOffloaderConfig");
    }

    record OffloadTarget(String offloadHandle, String offloadPath) {
    }

    record OffloadResult(ContextEvent event, List<BaseMessage> messages) {
        OffloadResult {
            messages = messages == null ? List.of() : new ArrayList<>(messages);
        }
    }
}
