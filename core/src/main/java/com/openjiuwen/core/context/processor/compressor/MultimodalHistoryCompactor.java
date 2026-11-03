/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.ContextEvent;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Retires base64 media payloads from older multimodal tool-result messages.
 *
 * <p>ReActAgent appends tool images and videos as user messages. Without this
 * processor, every base64 payload remains in history and is resent on each model
 * call, causing quadratic prompt growth for long multimodal tasks. The processor
 * keeps the configured number of recent media-bearing messages unchanged and
 * replaces older image and video payloads with text markers.</p>
 *
 * @since 0.1.17
 */
public class MultimodalHistoryCompactor extends ContextProcessor {
    private static final String COMPACTED_METADATA_KEY = "multimodal_history_compacted";

    static {
        ContextEngine.registerProcessor("MultimodalHistoryCompactor", MultimodalHistoryCompactor.class);
    }

    private final MultimodalHistoryCompactorConfig config;

    /**
     * Creates a compactor with externally supplied configuration.
     *
     * @param config compactor configuration, may be {@code null}
     */
    public MultimodalHistoryCompactor(Object config) {
        this(asConfig(config));
    }

    /**
     * Creates a compactor with typed configuration.
     *
     * @param config compactor configuration, may be {@code null}
     */
    public MultimodalHistoryCompactor(MultimodalHistoryCompactorConfig config) {
        super(config == null ? new MultimodalHistoryCompactorConfig() : config);
        this.config = config == null ? new MultimodalHistoryCompactorConfig() : config;
    }

    /**
     * Gets the effective compactor configuration.
     *
     * @return compactor configuration
     */
    public MultimodalHistoryCompactorConfig getConfig() {
        return config;
    }

    @Override
    public CompletionStage<Boolean> triggerAddMessages(SessionModelContext context, List<BaseMessage> messages,
                                                       Map<String, Object> kwargs) {
        List<BaseMessage> allMessages = concatenate(
                context == null ? List.of() : context.getMessages(),
                messages == null ? List.of() : messages);
        int mediaTurnCount = base64MultimodalMessageIndices(allMessages).size();
        int excessTurnCount = mediaTurnCount - config.getKeepRecentTurns();
        return CompletableFuture.completedFuture(excessTurnCount >= config.getTriggerThreshold());
    }

    @Override
    public CompletionStage<SessionModelContext.ProcessResult> onAddMessages(SessionModelContext context,
                                                                            List<BaseMessage> messages,
                                                                            boolean isForced,
                                                                            Map<String, Object> kwargs) {
        List<BaseMessage> incoming = messages == null ? List.of() : messages;
        List<BaseMessage> allMessages = concatenate(context == null ? List.of() : context.getMessages(), incoming);
        List<Integer> indicesToCompact = indicesToCompact(allMessages, isForced || kwargsForce(kwargs));
        if (indicesToCompact.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new SessionModelContext.ProcessResult(null, incoming, null));
        }

        List<Integer> modifiedIndices = compactMessages(allMessages, indicesToCompact);
        if (modifiedIndices.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new SessionModelContext.ProcessResult(null, incoming, null));
        }
        if (context != null) {
            context.setMessages(allMessages, true);
        }
        ContextEvent event = new ContextEvent(processorType(), modifiedIndices, "", null);
        return CompletableFuture.completedFuture(new SessionModelContext.ProcessResult(event, List.of(), null));
    }

    @Override
    public void loadState(Map<String, Object> state) {
        // Multimodal history compaction is stateless; markers and metadata are stored in messages.
    }

    @Override
    public Map<String, Object> saveState() {
        return new LinkedHashMap<>();
    }

    private List<Integer> compactMessages(List<BaseMessage> allMessages, List<Integer> indicesToCompact) {
        List<Integer> modifiedIndices = new ArrayList<>();
        for (Integer index : indicesToCompact) {
            if (index == null || index < 0 || index >= allMessages.size()) {
                continue;
            }
            BaseMessage message = allMessages.get(index);
            if (!(message instanceof UserMessage userMessage) || !hasBase64MultimodalContent(userMessage)) {
                continue;
            }
            allMessages.set(index, compactedMessage(userMessage));
            modifiedIndices.add(index);
        }
        return modifiedIndices;
    }

    private List<Integer> indicesToCompact(List<BaseMessage> messages, boolean isForced) {
        List<Integer> mediaIndices = base64MultimodalMessageIndices(messages);
        int threshold = isForced ? 0 : config.getKeepRecentTurns() + config.getTriggerThreshold() - 1;
        if (mediaIndices.size() <= threshold) {
            return List.of();
        }
        int retainedTurns = Math.min(config.getKeepRecentTurns(), mediaIndices.size());
        return List.copyOf(mediaIndices.subList(0, mediaIndices.size() - retainedTurns));
    }

    private static List<Integer> base64MultimodalMessageIndices(List<BaseMessage> messages) {
        List<Integer> mediaIndices = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            if (messages.get(index) instanceof UserMessage userMessage && hasBase64MultimodalContent(userMessage)) {
                mediaIndices.add(index);
            }
        }
        return mediaIndices;
    }

    private static boolean hasBase64MultimodalContent(UserMessage message) {
        List<?> parts = message.getContentAsList();
        if (parts == null || parts.isEmpty()) {
            return false;
        }
        return parts.stream().anyMatch(MultimodalHistoryCompactor::isBase64MultimodalPart);
    }

    static boolean isBase64MultimodalPart(Object part) {
        if (!(part instanceof Map<?, ?> rawPart) || !(rawPart.get("type") instanceof String partType)) {
            return false;
        }
        if (!"image_url".equals(partType) && !"video_url".equals(partType)) {
            return false;
        }
        return rawPart.get(partType) instanceof Map<?, ?> media
                && media.get("url") instanceof String url
                && url.startsWith(multimodalDataPrefix(partType));
    }

    private static String multimodalDataPrefix(String partType) {
        return "image_url".equals(partType) ? "data:image/" : "data:video/";
    }

    private UserMessage compactedMessage(UserMessage source) {
        List<?> sourceParts = source.getContentAsList();
        List<Object> compactedParts = new ArrayList<>(sourceParts == null ? List.of() : sourceParts);
        for (int index = 0; index < compactedParts.size(); index++) {
            if (isBase64MultimodalPart(compactedParts.get(index))) {
                compactedParts.set(index, summaryMarkerPart());
            }
        }

        UserMessage compacted = new UserMessage();
        compacted.setContent(compactedParts);
        compacted.setName(source.getName());
        Map<String, Object> metadata = source.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source.getMetadata());
        metadata.put(COMPACTED_METADATA_KEY, Boolean.TRUE);
        compacted.setMetadata(metadata);
        return compacted;
    }

    private Map<String, Object> summaryMarkerPart() {
        Map<String, Object> summaryMarkerPart = new LinkedHashMap<>();
        summaryMarkerPart.put("type", "text");
        summaryMarkerPart.put("text", config.getSummaryMarker());
        return summaryMarkerPart;
    }

    private static List<BaseMessage> concatenate(List<BaseMessage> left, List<BaseMessage> right) {
        List<BaseMessage> result = new ArrayList<>(left == null ? List.of() : left);
        result.addAll(right == null ? List.of() : right);
        return result;
    }

    private static boolean kwargsForce(Map<String, Object> kwargs) {
        Object value = kwargs == null ? null : kwargs.get("force");
        return Boolean.TRUE.equals(value);
    }

    private static MultimodalHistoryCompactorConfig asConfig(Object config) {
        if (config == null) {
            return new MultimodalHistoryCompactorConfig();
        }
        if (config instanceof MultimodalHistoryCompactorConfig multimodalConfig) {
            return multimodalConfig;
        }
        throw new IllegalArgumentException("MultimodalHistoryCompactor requires MultimodalHistoryCompactorConfig");
    }
}
