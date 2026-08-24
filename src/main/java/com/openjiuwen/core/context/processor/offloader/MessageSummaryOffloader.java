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
import com.openjiuwen.core.context.schema.OffloadMessage;
import com.openjiuwen.core.context.schema.OffloadMessages;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Offloads large messages using task-aware adaptive summary compression.
 *
 * <p>Mirrors Python's {@code MessageSummaryOffloader} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_summary_offloader.py}.</p>
 */
public class MessageSummaryOffloader extends MessageOffloader {
    public static final String TRUNCATED_MARKER = "...[TRUNCATED]...";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final List<String> CONTEXT_OVERFLOW_KEYWORDS = List.of(
            "context length",
            "token limit",
            "too long",
            "exceeds",
            "maximum context",
            "context window"
    );

    private static final String ADAPTIVE_OFFLOAD_PROMPT_TEMPLATE = """
            # Adaptive Information Compression Expert
            
            ## Core Role
            You are an adaptive information compression expert in a React Agent. Your task is to intelligently analyze the information density and structural characteristics of tool return content, automatically select the most suitable compression strategy, generate an optimal condensed text, and offload detailed content to the file system for on-demand loading.
            
            ## Constraints
            - Strictly prohibited from executing the step: You are only responsible for compression; you must not execute any steps, calculations, or operations from the step.
            - Based solely on provided information: Only use the information in tool_content for compression.
            - No speculative operations: Do not perform additional queries, calculations, or analysis based on step content.
            
            # Compression Logic Flow
            
            ## Step 1: Analyze User Intent
            - Tool Purpose: Understand the core purpose of this tool call.
            - Key Parameters: What parameters were passed in the function_call? This directly indicates the focus of required information.
            - Role in the step: What subtasks in the current step is this tool call meant to accomplish?
            
            ## Step 2: Select Compression Strategy
            Based on the analyzed user intent, quickly scan the important information in tool_content:
            
            ### Characteristics favoring EXTRACTIVE compression:
            - Clear and direct results: Key information related to user intent is explicitly present in the tool return results.
            - No deep processing needed: The answer already exists directly in the return content.
            - Clear structure: For example, batches of key information, attribute lists, keyword collections, address details, etc.
            
            ### Characteristics favoring ABSTRACTIVE compression:
            - Requires integration and understanding: To obtain an answer that matches user intent, it is necessary to summarize and synthesize multiple paragraphs, viewpoints, or data.
            - Highly narrative: For example, long analytical reports, article content, Q&A responses, log analysis, etc.
            
            ## Step 3: Execute Compression Strategy
            Based on the above evaluation, select a compression strategy according to the following process:
            
            ### If EXTRACTIVE compression was selected in the previous step:
            Analyze `tool_content` and perform the following operations:
            - Identify core information: Find sentences and key data that directly answer the calling intent.
            - Execute extractive compression:
              - RETAIN: All original sentences or phrases that directly contain core answers, key facts, final results, main status, and necessary definitions.
              - DELETE: Background introductions, duplicate meaning, overly detailed examples, pure formatting metadata, internal log information, and redundant transitional statements.
            - Ensure coherence: Connect the retained original sentences or fragments in a logically clear way.
            
            ### If ABSTRACTIVE compression was selected in the previous step:
            Compress the tool message content to generate a high-density, high-integrity summary that can adequately support the current `step`'s task needs without loading the original text.
            
            Summary requirements:
            - Integrity priority: Retain all key facts, data, conclusions, conditions, and limitations related to the current `step`.
            - Strict accuracy: All data, names, relationships, and judgments must be strictly accurate.
            - Focus and conciseness: Center around the `step` requirements; remove redundancy, but do not oversimplify core information.
            - Clear structure: Maintain logical coherence with clear information hierarchy.
            - Objective neutrality: Make only factual statements; do not add unsupported explanations, evaluations, or speculations.
            
            [Current step requirements]
            @@STEP@@
            
            [Current tool call function call]
            @@FUNCTION_CALL@@
            
            [Tool message content begins]
            @@TOOL_CONTENT@@
            [Tool message content ends]
            
            Return JSON with this schema:
            @@OUTPUT_JSON_SCHEMA@@
            """;

    private static final String STEP_SUMMARY_PROMPT = """
            Summarize the current user task in one concise sentence.
            Return the task only.
            
            Conversation context:
            @@CONTEXT@@
            """;

    private final MessageSummaryOffloaderConfig summaryConfig;
    private Model model;

    static {
        ContextEngine.registerProcessor("MessageSummaryOffloader", MessageSummaryOffloader.class);
    }

    public MessageSummaryOffloader(Object config) {
        this(asConfig(config));
    }

    public MessageSummaryOffloader(MessageSummaryOffloaderConfig config) {
        this(config, null);
    }

    MessageSummaryOffloader(MessageSummaryOffloaderConfig config, Model model) {
        super(toOffloaderConfig(config == null ? new MessageSummaryOffloaderConfig() : config));
        this.summaryConfig = config == null ? new MessageSummaryOffloaderConfig() : config;
        this.model = model;
    }

    public MessageSummaryOffloaderConfig getSummaryConfig() {
        return summaryConfig;
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messagesToAdd,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> incoming = messagesToAdd == null ? List.of() : messagesToAdd;
        List<BaseMessage> contextMessages = new ArrayList<>(context == null ? List.of() : context.getMessages());
        contextMessages.addAll(incoming);
        for (BaseMessage message : incoming) {
            if (shouldOffloadMessage(message, contextMessages, context)) {
                return CompletableFuture.completedFuture(true);
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messagesToAdd,
                                                                            boolean force,
                                                                            Map<String, Object> kwargs) {
        List<BaseMessage> incoming = messagesToAdd == null ? List.of() : messagesToAdd;
        List<BaseMessage> processedMessages = new ArrayList<>(incoming);
        List<BaseMessage> contextMessages = new ArrayList<>(context == null ? List.of() : context.getMessages());
        contextMessages.addAll(incoming);
        List<Integer> modifiedIndices = new ArrayList<>();
        int baseIndex = context == null ? 0 : context.length();

        for (int index = 0; index < incoming.size(); index++) {
            BaseMessage message = incoming.get(index);
            if (!shouldOffloadMessage(message, contextMessages, context)) {
                continue;
            }
            BaseMessage processed = offloadMessage(message, context, kwargs).toCompletableFuture().join();
            processedMessages.set(index, processed);
            modifiedIndices.add(baseIndex + index);
        }
        if (modifiedIndices.isEmpty()) {
            return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(null, incoming, null));
        }
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(
                new ContextEvent(processorType(), modifiedIndices, "", currentCompressionUsage()),
                processedMessages,
                null));
    }

    @Override
    CompletionStage<BaseMessage> offloadMessage(BaseMessage message, SessionModelContext context,
                                                Map<String, Object> kwargs) {
        return offloadMessageAdaptive(message, context, kwargs);
    }

    @Override
    boolean shouldOffloadMessage(BaseMessage message, List<BaseMessage> contextMessages, SessionModelContext context) {
        if (message == null || !summaryConfig.getOffloadMessageType().contains(message.getRole())) {
            return false;
        }
        if (message instanceof OffloadMessage) {
            return false;
        }
        if (message instanceof AssistantMessage assistant
                && assistant.getToolCalls() != null
                && !assistant.getToolCalls().isEmpty()) {
            return false;
        }
        List<BaseMessage> safeContextMessages = contextMessages == null && context != null
                ? context.getMessages()
                : (contextMessages == null ? List.of() : contextMessages);
        if ("tool".equals(message.getRole()) && isProtectedToolMessage(message, safeContextMessages)) {
            return false;
        }
        if (!(message.getContent() instanceof String content)) {
            return false;
        }
        return content.length() > summaryConfig.getLargeMessageThreshold();
    }

    CompletionStage<BaseMessage> offloadMessageAdaptive(BaseMessage message, SessionModelContext context,
                                                        Map<String, Object> kwargs) {
        List<BaseMessage> contextMessages = context == null ? List.of() : context.getMessages();
        Object functionCall = getFunctionCallFromChain(message, contextMessages);
        String step;
        if (summaryConfig.isEnablePreciseStep()) {
            List<BaseMessage> withMessage = new ArrayList<>(contextMessages);
            withMessage.add(message);
            step = getStepFromChainPrecise(withMessage);
            if (step.isEmpty()) {
                step = getStepFromChainDefault(contextMessages);
            }
        } else {
            step = getStepFromChainDefault(contextMessages);
        }

        String toolContent = contentToText(message == null ? null : message.getContent());
        Map<String, Object> compressionResult = compressWithFallback(step, functionCall, toolContent);
        if (compressionResult == null) {
            return CompletableFuture.completedFuture(message);
        }

        String summary = stringValue(compressionResult.get("summary"), "");
        String finalContent = summary;
        Map<String, Object> offloadDataExplanation = asStringObjectMap(
                compressionResult.get("offload_data_explanation"));
        if (offloadDataExplanation != null && !offloadDataExplanation.isEmpty()) {
            finalContent = summary + "\n\n"
                    + "[offloaded_info]\n"
                    + "category: " + stringValue(offloadDataExplanation.get("category"), "") + "\n"
                    + "description: " + stringValue(offloadDataExplanation.get("description"), "") + "\n"
                    + "inferability: " + stringValue(offloadDataExplanation.get("inferability"), "");
        }

        Map<String, Object> extraFields = new LinkedHashMap<>(message.modelDump());
        extraFields.remove("role");
        extraFields.remove("content");
        extraFields.remove("offload_type");
        extraFields.remove("offload_handle");
        if (kwargs != null) {
            extraFields.putAll(kwargs);
        }
        MessageOffloader.OffloadTarget target = newOffloadHandleAndPath(context);
        return offloadMessages(
                message.getRole(),
                finalContent,
                List.of(message),
                context,
                target.offloadHandle(),
                "filesystem",
                target.offloadPath(),
                extraFields
        ).thenApply(rawMessage -> wrapAdaptiveOffloadMessage(message, rawMessage, target.offloadHandle(), extraFields));
    }

    int messageSize(BaseMessage message, SessionModelContext context) {
        if (context != null && context.tokenCounter() != null) {
            return context.tokenCounter().countTokens(List.of(message));
        }
        Object content = message == null ? null : message.getContent();
        if (content instanceof String text) {
            return text.length() / 3;
        }
        try {
            return JSON_MAPPER.writeValueAsString(content).length() / 3;
        } catch (JsonProcessingException ex) {
            return String.valueOf(content).length() / 3;
        }
    }

    Object getFunctionCallFromChain(BaseMessage toolMessage, List<BaseMessage> contextMessages) {
        String toolCallId = readProperty(toolMessage, "toolCallId")
                .or(() -> readProperty(toolMessage, "tool_call_id"))
                .map(String::valueOf)
                .orElse(null);
        if (toolCallId == null || toolCallId.isBlank()) {
            return null;
        }
        List<BaseMessage> safeMessages = contextMessages == null ? List.of() : contextMessages;
        for (int index = safeMessages.size() - 1; index >= 0; index--) {
            BaseMessage message = safeMessages.get(index);
            if (!(message instanceof AssistantMessage assistant) || assistant.getToolCalls() == null) {
                continue;
            }
            for (ToolCall toolCall : assistant.getToolCalls()) {
                if (ContextUtils.toolCallMatchesId(toolCall, toolCallId)) {
                    return toolCall;
                }
            }
        }
        return null;
    }

    String getStepFromChainDefault(List<BaseMessage> contextMessages) {
        List<BaseMessage> safeMessages = contextMessages == null ? List.of() : contextMessages;
        for (int index = safeMessages.size() - 1; index >= 0; index--) {
            BaseMessage message = safeMessages.get(index);
            if (!"user".equals(message.getRole())) {
                continue;
            }
            return contentToText(message.getContent());
        }
        return "";
    }

    String getStepFromChainPrecise(List<BaseMessage> contextMessages) {
        List<BaseMessage> messagesToUse = selectMessagesForStepSummary(contextMessages);
        if (messagesToUse.isEmpty()) {
            return "";
        }
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                String contextText = buildStepContextText(messagesToUse);
                AssistantMessage response = invokeModel(List.of(new UserMessage(
                        STEP_SUMMARY_PROMPT.replace("@@CONTEXT@@", contextText))));
                return contentToText(response == null ? null : response.getContent()).strip();
            } catch (RuntimeException exc) {
                if (!isContextOverflowError(exc)) {
                    throw exc;
                }
                if (attempt >= maxRetries - 1 || messagesToUse.size() <= 2) {
                    throw contextExecutionError(
                            "Failed to generate precise step summary after " + maxRetries + " attempts: "
                                    + exc.getMessage(),
                            exc);
                }
                messagesToUse = new ArrayList<>(messagesToUse.subList(2, messagesToUse.size()));
            }
        }
        return "";
    }

    Map<String, Object> compressWithFallback(String step, Object functionCall, String toolContent) {
        List<String> attempts = buildCompressionAttempts(toolContent == null ? "" : toolContent);
        for (int index = 0; index < attempts.size(); index++) {
            try {
                String prompt = buildCompressionPrompt(step, functionCall, attempts.get(index));
                AssistantMessage response = invokeModel(List.of(new UserMessage(prompt)));
                String responseContent = contentToText(response == null ? null : response.getContent());
                try {
                    return parseCompressionResult(responseContent);
                } catch (RuntimeException parseFailure) {
                    if (responseContent.length() >= (toolContent == null ? 0 : toolContent.length())) {
                        return null;
                    }
                    Map<String, Object> fallback = new LinkedHashMap<>();
                    fallback.put("summary", responseContent);
                    fallback.put("offload_data_explanation", Map.of());
                    return fallback;
                }
            } catch (RuntimeException exc) {
                if (!isContextOverflowError(exc)) {
                    throw exc;
                }
                if (index >= attempts.size() - 1) {
                    throw contextExecutionError(
                            "Failed to compress message after " + attempts.size() + " attempts: "
                                    + exc.getMessage(),
                            exc);
                }
            }
        }
        return Map.of();
    }

    List<String> buildCompressionAttempts(String toolContent) {
        List<String> attempts = new ArrayList<>();
        attempts.add(toolContent);
        int maxChars = summaryConfig.getContentMaxCharsForCompression();
        if (toolContent.length() <= maxChars) {
            return attempts;
        }
        attempts.add(smartTruncateContent(toolContent, maxChars));
        int reducedLimit = Math.max(maxChars / 2, 1);
        if (reducedLimit < maxChars) {
            attempts.add(smartTruncateContent(toolContent, reducedLimit));
        }
        return attempts;
    }

    String smartTruncateContent(String content, int maxChars) {
        if (content.length() <= maxChars) {
            return content;
        }
        int joinerOverhead = 4;
        if (maxChars <= TRUNCATED_MARKER.length() * 2 + joinerOverhead + 3) {
            return content.substring(0, maxChars);
        }
        int availableChars = maxChars - TRUNCATED_MARKER.length() * 2 - joinerOverhead;
        int headChars = Math.max(availableChars / 3, 1);
        int tailChars = Math.max(availableChars / 3, 1);
        int middleChars = Math.max(availableChars - headChars - tailChars, 1);

        int center = content.length() / 2;
        int middleStart = Math.max(center - middleChars / 2, headChars);
        int middleEnd = Math.min(middleStart + middleChars, content.length() - tailChars);
        middleStart = Math.max(headChars, middleEnd - middleChars);

        String head = content.substring(0, headChars);
        String middle = content.substring(middleStart, middleEnd);
        String tail = content.substring(content.length() - tailChars);
        return head + "\n" + TRUNCATED_MARKER + "\n" + middle + "\n" + TRUNCATED_MARKER + "\n" + tail;
    }

    String buildCompressionPrompt(String step, Object functionCall, String toolContent) {
        return ADAPTIVE_OFFLOAD_PROMPT_TEMPLATE
                .replace("@@STEP@@", step == null || step.isBlank() ? "N/A" : step)
                .replace("@@FUNCTION_CALL@@", functionCallToText(functionCall))
                .replace("@@TOOL_CONTENT@@", toolContent == null ? "" : toolContent)
                .replace("@@OUTPUT_JSON_SCHEMA@@", outputJsonSchema(summaryConfig.getSummaryMaxTokens()));
    }

    Map<String, Object> parseCompressionResult(String responseContent) {
        String trimmed = responseContent == null ? "" : responseContent.strip();
        try {
            Map<String, Object> result = JSON_MAPPER.readValue(trimmed, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            return requireSummary(result);
        } catch (JsonProcessingException originalException) {
            int jsonStart = trimmed.indexOf('{');
            int jsonEnd = trimmed.lastIndexOf('}');
            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                throw contextExecutionError("No JSON found in compression result: " + preview(trimmed),
                        originalException);
            }
            try {
                Map<String, Object> result = JSON_MAPPER.readValue(
                        trimmed.substring(jsonStart, jsonEnd + 1),
                        new TypeReference<LinkedHashMap<String, Object>>() {
                        });
                return requireSummary(result);
            } catch (JsonProcessingException exc) {
                throw contextExecutionError("Failed to parse compression result as JSON: " + preview(trimmed), exc);
            }
        }
    }

    boolean isContextOverflowError(Throwable exc) {
        String errorMessage = String.valueOf(exc).toLowerCase(Locale.ROOT);
        for (String keyword : CONTEXT_OVERFLOW_KEYWORDS) {
            if (errorMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private AssistantMessage invokeModel(List<BaseMessage> messages) {
        try {
            AssistantMessage response = getModel().invoke(messages).toCompletableFuture().join();
            recordCompressionUsage(response);
            return response;
        } catch (CompletionException exc) {
            Throwable cause = exc.getCause() == null ? exc : exc.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
    }

    private Model getModel() {
        if (model == null) {
            model = new Model(summaryConfig.getModelClient(), summaryConfig.getModel());
        }
        return model;
    }

    private List<BaseMessage> selectMessagesForStepSummary(List<BaseMessage> contextMessages) {
        List<BaseMessage> filtered = new ArrayList<>();
        for (BaseMessage message : contextMessages == null ? List.<BaseMessage>of() : contextMessages) {
            if (isValidForStepSummary(message)) {
                filtered.add(message);
            }
        }
        if (filtered.size() <= 1) {
            return List.of();
        }
        int maxMessages = summaryConfig.getStepSummaryMaxContextMessages();
        if (filtered.size() <= maxMessages) {
            return filtered;
        }
        return new ArrayList<>(filtered.subList(filtered.size() - maxMessages, filtered.size()));
    }

    private static boolean isValidForStepSummary(BaseMessage message) {
        if (message == null) {
            return false;
        }
        if ("user".equals(message.getRole())) {
            return true;
        }
        return message instanceof AssistantMessage assistant
                && (assistant.getToolCalls() == null || assistant.getToolCalls().isEmpty());
    }

    private static String buildStepContextText(List<BaseMessage> messagesToUse) {
        List<String> lines = new ArrayList<>();
        for (BaseMessage message : messagesToUse) {
            String content = contentToText(message.getContent());
            lines.add("[" + message.getRole() + "] " + content.substring(0, Math.min(2000, content.length())));
        }
        return String.join("\n\n", lines);
    }

    private BaseMessage wrapAdaptiveOffloadMessage(BaseMessage source, BaseMessage rawMessage, String fallbackHandle,
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

    private static Map<String, Object> requireSummary(Map<String, Object> result) {
        if (result == null || !result.containsKey("summary")) {
            throw contextExecutionError("Missing 'summary' field in compression result", null);
        }
        return result;
    }

    private static String outputJsonSchema(int summaryMaxTokens) {
        return """
                {
                  "compression_strategy": "extractive" | "abstractive",
                  "summary": "A compact result generated based on the selected strategy (within @@SUMMARY_MAX_TOKENS@@ tokens). If using extractive strategy, directly concatenate key original text; if using abstractive strategy, provide a condensed summary. Ensure it contains all key information needed for the step, with clear structure and appropriate length.",
                  "offload_data_explanation": {
                    "category": "The category of information offloaded.",
                    "description": "Briefly describe what detailed information is missing from the compressed text and its potential use cases.",
                    "inferability": "high" | "medium" | "low"
                  }
                }
                """.replace("@@SUMMARY_MAX_TOKENS@@", String.valueOf(summaryMaxTokens));
    }

    private static String functionCallToText(Object functionCall) {
        if (functionCall == null) {
            return "N/A";
        }
        if (functionCall instanceof String text) {
            return text;
        }
        return contentToText(functionCall);
    }

    private static String contentToText(Object content) {
        if (content instanceof String text) {
            return text;
        }
        try {
            return JSON_MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException exc) {
            return String.valueOf(content);
        }
    }

    private static Map<String, Object> asStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private static Optional<Object> readProperty(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (target instanceof Map<?, ?> map) {
            return Optional.ofNullable(map.get(name));
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

    private static MessageOffloaderConfig toOffloaderConfig(MessageSummaryOffloaderConfig config) {
        MessageOffloaderConfig offloaderConfig = new MessageOffloaderConfig();
        offloaderConfig.setLargeMessageThreshold(Math.max(2, config.getLargeMessageThreshold()));
        offloaderConfig.setTrimSize(1);
        offloaderConfig.setOffloadMessageType(config.getOffloadMessageType());
        offloaderConfig.setProtectedToolNames(config.getProtectedToolNames());
        offloaderConfig.setKeepLastRound(false);
        return offloaderConfig;
    }

    private static MessageSummaryOffloaderConfig asConfig(Object config) {
        if (config == null) {
            return new MessageSummaryOffloaderConfig();
        }
        if (config instanceof MessageSummaryOffloaderConfig messageSummaryConfig) {
            return messageSummaryConfig;
        }
        throw new IllegalArgumentException("MessageSummaryOffloader requires MessageSummaryOffloaderConfig");
    }

    private static RuntimeException contextExecutionError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.CONTEXT_EXECUTION_ERROR,
                message,
                null,
                cause,
                Map.of("error_msg", message));
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.substring(0, Math.min(200, text.length()));
    }
}
