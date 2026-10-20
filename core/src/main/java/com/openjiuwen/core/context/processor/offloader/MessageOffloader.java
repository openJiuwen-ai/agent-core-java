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
import com.openjiuwen.core.context.offload.api.OffloadException;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadRef;
import com.openjiuwen.core.context.offload.model.OffloadRequest;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Trims and offloads large context messages when configured token thresholds are exceeded.
 *
 * <p>Mirrors Python's {@code MessageOffloader} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_offloader.py}.</p>
 *
 * <p>v2 (架构文档 §7.2.1): the message-count threshold trigger was removed;
 * triggering is token-total based only, and per-message decisions additionally
 * honor the {@code openjiuwen.offload_done} metadata flag and the recent-window
 * protection.</p>
 */
public class MessageOffloader extends ContextProcessor {
    public static final String OMIT_STRING = "...";

    /** Metadata flag marking messages whose content already lives offloaded (防重循环, 架构文档 §1.1). */
    public static final String OFFLOAD_DONE_METADATA_KEY = "openjiuwen.offload_done";

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageOffloader.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    static {
        ContextEngine.registerProcessor("MessageOffloader", MessageOffloader.class);
    }

    private final MessageOffloaderConfig config;

    /** Optional v2 pipeline; {@code null} keeps the legacy buffer/filesystem path. */
    private volatile OffloadService offloadService;

    /** Namespace supplier for the v2 pipeline; resolved per-save when null. */
    private volatile Supplier<OffloadNamespace> namespaceSupplier;

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

    /**
     * Wires the v2 {@link OffloadService} pipeline (架构文档 §2.2, T-P0-04).
     * When unset the processor keeps its legacy buffer/filesystem behavior.
     */
    public void setOffloadService(OffloadService offloadService, Supplier<OffloadNamespace> namespaceSupplier) {
        this.offloadService = offloadService;
        this.namespaceSupplier = namespaceSupplier;
    }

    /**
     * @return the wired v2 pipeline, or {@code null} when running in legacy mode.
     */
    public OffloadService getOffloadService() {
        return offloadService;
    }

    /**
     * Subclass-visible accessor for the v2 pipeline.
     */
    protected OffloadService offloadService() {
        return offloadService;
    }

    /**
     * Subclass-visible v2 save entry (gate → reserve → store chain → commit).
     *
     * @param message the message to offload
     * @param context the session context carrying the namespace
     * @return the offload receipt, or empty when the save failed
     */
    protected Optional<OffloadRef> saveViaOffloadService(BaseMessage message, SessionModelContext context) {
        return doSaveViaOffloadService(message, context);
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

        // v2: message-count threshold trigger removed (架构文档 §7.2.1);
        // only the token-total branch remains ("<= threshold" keeps prod behavior).
        int tokens = 0;
        if (context != null && context.tokenCounter() != null) {
            tokens += context.tokenCounter().countTokens(context.getMessages());
            tokens += context.tokenCounter().countTokens(messagesToAdd == null ? List.of() : messagesToAdd);
        }
        // 设计文档 §11.3 / AC-1-02/03: 达到阈值即触发（含边界，>=）。
        if (tokens >= config.getTokensThreshold()) {
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
        // G.NAM.08: intentional descriptive name for the protection flag
        boolean isRecentProtectionActive = offloadService != null && config.getRecentMessageProtectionCount() > 0;

        for (int index = offloadRange - 1; index >= 0; index--) {
            BaseMessage message = processedMessages.get(index);
            // v2 D7 保护窗口: the most recent N messages stay inline; as the
            // window slides forward with new messages, older candidates become
            // offloadable again (滑动窗口溢出恢复).
            if (isRecentProtectionActive && isWithinRecentProtection(processedMessages, index)) {
                continue;
            }
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
        // v2: route through the OffloadService pipeline when wired; on total
        // store failure the original message is kept inline (共享约定 13).
        if (offloadService != null) {
            Optional<OffloadRef> ref = saveViaOffloadService(message, context);
            if (ref.isEmpty()) {
                return CompletableFuture.completedFuture(message);
            }
            return CompletableFuture.completedFuture(
                    wrapOffloadMessage(message, ref.get(), trimmedContent, extraFields));
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

    /**
     * Saves the message through the v2 pipeline (gate → reserve → store chain
     * → commit). Never throws: any failure returns empty so the caller
     * keeps the original message inline.
     *
     * @param message the message to offload
     * @param context the session context carrying the namespace
     * @return the offload receipt, or empty when the save failed
     */
    private Optional<OffloadRef> doSaveViaOffloadService(BaseMessage message, SessionModelContext context) {
        if (offloadService == null) {
            // Legacy mode: no v2 pipeline wired, caller falls back to the
            // filesystem/in-memory path.
            return Optional.empty();
        }
        try {
            // postTokenCount = the token footprint of what stays context-visible
            // after offloading (preview + marker), AC-6-04.
            String preview = message.getContentAsString().substring(0,
                    Math.min(config.getTrimSize(), message.getContentAsString().length())) + OMIT_STRING;
            long postTokens = ContextUtils.estimateTokens(preview + markerSuffix());
            OffloadRequest request = OffloadRequest.builder()
                    .namespace(resolveNamespace(context))
                    .algorithmId(processorType())
                    .source(String.valueOf(UUID.randomUUID()))
                    .messages(List.of(message))
                    .preTokenCount(estimateTokens(message, context))
                    .postTokenCount(postTokens)
                    .build();
            return offloadService.save(request);
        } catch (OffloadException ex) {
            // Intentional: offloader must not propagate exceptions to the compression pipeline
            LOGGER.warn("[{}] offload save failed, keeping message inline: {}",
                    processorType(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Upper-bound estimate of the rendered marker footprint
     * ({@code [[OFFLOAD: handle=32hex, type=filesystem, path=...]]}) so the
     * postTokenCount metric covers the whole context-visible replacement.
     */
    private static String markerSuffix() {
        return String.format(OFFLOAD_MESSAGE_MARKER,
                "0".repeat(32), "filesystem", MessageOffloader.class.getSimpleName() + "_0".repeat(32) + ".json");
    }

    private OffloadNamespace resolveNamespace(SessionModelContext context) {
        Supplier<OffloadNamespace> supplier = namespaceSupplier;
        if (supplier != null) {
            OffloadNamespace supplied = supplier.get();
            if (supplied != null) {
                return supplied;
            }
        }
        return OffloadNamespace.of("", context == null ? "" : context.sessionId(), "");
    }

    private static long estimateTokens(BaseMessage message, SessionModelContext context) {
        if (context != null && context.tokenCounter() != null) {
            try {
                return context.tokenCounter().countTokens(List.of(message));
            } catch (RuntimeException ex) {
                // Intentional: token-counter failure must not propagate; fall back to char length.
                LOGGER.warn("token count failed, falling back to char length: {}", ex.getMessage());
            }
        }
        return message == null ? 0L : message.getContentAsString().length();
    }

    OffloadTarget newOffloadHandleAndPath(SessionModelContext context) {
        String offloadHandle = UUID.randomUUID().toString().replace("-", "");
        String sessionId = context == null ? "" : context.sessionId();
        String workspaceDir = context == null ? "" : context.workspaceDir();
        String fileName = processorType() + "_" + offloadHandle + ".json";
        if (workspaceDir != null && !workspaceDir.isBlank()) {
            return new OffloadTarget(offloadHandle,
                    java.nio.file.Path.of(workspaceDir, "context", sessionId + "_context", "offload",
                            fileName).toString());
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
        // G.NAM.08: intentional descriptive name for the protection flag
        boolean isRecentProtectionActive = offloadService != null && config.getRecentMessageProtectionCount() > 0;
        for (int index = offloadRange - 1; index >= 0; index--) {
            if (isRecentProtectionActive && isWithinRecentProtection(messages, index)) {
                continue;
            }
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
        // v2 防重循环 (架构文档 §1.1): already-offloaded (or reload-result)
        // messages must never be offloaded again.
        if (isOffloadDone(message)) {
            return false;
        }
        // v2 单条卸载边界 (AC-1-03): content size reaching the threshold
        // (>=) qualifies, in contrast to the trigger scan which stays "> ".
        if (content.length() < config.getLargeMessageThreshold()) {
            return false;
        }
        if (message instanceof OffloadMessage) {
            return false;
        }
        return !isProtectedToolMessage(message, contextMessages);
    }

    /**
     * @return {@code true} when the message metadata carries the
     * {@code openjiuwen.offload_done=true} flag (架构文档 §7.2.1 ①).
     */
    static boolean isOffloadDone(BaseMessage message) {
        Map<String, Object> metadata = message == null ? null : message.getMetadata();
        if (metadata == null) {
            return false;
        }
        Object flag = metadata.get(OFFLOAD_DONE_METADATA_KEY);
        // 共享约定 5: the flag value is the string "true"; Boolean.TRUE is
        // accepted too so programmatic writers keep working.
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }

    /**
     * @return {@code true} when {@code index} sits inside the most recent
     * {@code recentMessageProtectionCount} messages of {@code messages}
     * (架构文档 §7.2.1 ②, D7 protection window).
     */
    boolean isWithinRecentProtection(List<BaseMessage> messages, int index) {
        int protectionCount = config.getRecentMessageProtectionCount();
        if (protectionCount <= 0 || messages == null) {
            return false;
        }
        int lastProtectedStart = messages.size() - protectionCount;
        return index >= lastProtectedStart;
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
        // v2: messages_to_keep / messages_threshold coupling check removed together
        // with the message-count threshold (架构文档 §7.2.1).
    }

    /**
     * v2 wrapper: renders the unified marker from the service receipt and
     * carries {@code tool_call_id} through for tool-role messages.
     */
    private BaseMessage wrapOffloadMessage(BaseMessage source, OffloadRef ref, String trimmedContent,
                                           Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        if ("tool".equals(source.getRole()) && source instanceof ToolMessage toolMessage) {
            safeKwargs.putIfAbsent("tool_call_id", toolMessage.getToolCallId());
        }
        String marker = String.format(OFFLOAD_MESSAGE_MARKER, ref.handle(), ref.type(),
                ref.pathOrKey() == null ? "" : ref.pathOrKey());
        return OffloadMessages.createOffloadMessage(
                source.getRole(),
                trimmedContent + marker,
                ref.handle(),
                ref.type(),
                safeKwargs);
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
