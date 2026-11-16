/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tests retirement of old multimodal tool-result media from context history.
 */
class MultimodalHistoryCompactorTest {
    private static final String DEFAULT_SUMMARY_MARKER = "[Old image or video content cleared]";

    @Test
    void addMessagesKeepsOnlyRecentImageTurns() {
        MultimodalHistoryCompactorConfig config = new MultimodalHistoryCompactorConfig();
        config.setKeepRecentTurns(2);
        config.setTriggerThreshold(1);
        MultimodalHistoryCompactor processor = new MultimodalHistoryCompactor(config);
        SessionModelContext context = contextWith(processor);

        for (int turn = 1; turn <= 5; turn++) {
            context.addMessages(mediaMessage("image_url", "data:image/png;base64,image-" + turn))
                    .toCompletableFuture()
                    .join();
        }

        assertThat(fullResolutionMediaCount(context.getMessages())).isEqualTo(2);
        assertThat(markerCount(context.getMessages())).isEqualTo(3);
    }

    @Test
    void addMessagesKeepsOnlyRecentVideoTurns() {
        MultimodalHistoryCompactorConfig config = new MultimodalHistoryCompactorConfig();
        config.setKeepRecentTurns(2);
        config.setTriggerThreshold(1);
        SessionModelContext context = contextWith(new MultimodalHistoryCompactor(config));

        for (int turn = 1; turn <= 5; turn++) {
            context.addMessages(mediaMessage("video_url", "data:video/mp4;base64,video-" + turn))
                    .toCompletableFuture()
                    .join();
        }

        assertThat(fullResolutionMediaCount(context.getMessages())).isEqualTo(2);
        assertThat(markerCount(context.getMessages())).isEqualTo(3);
    }

    @Test
    void triggerDoesNotRemainActiveAfterCompaction() {
        MultimodalHistoryCompactorConfig config = new MultimodalHistoryCompactorConfig();
        config.setKeepRecentTurns(2);
        config.setTriggerThreshold(1);
        MultimodalHistoryCompactor processor = new MultimodalHistoryCompactor(config);
        SessionModelContext context = contextWith(processor);

        for (int turn = 1; turn <= 5; turn++) {
            context.addMessages(mediaMessage("image_url", "data:image/png;base64,image-" + turn))
                    .toCompletableFuture()
                    .join();
        }

        boolean shouldTrigger = processor.triggerAddMessages(context, List.of(), Map.of())
                .toCompletableFuture()
                .join();

        assertThat(shouldTrigger).isFalse();
    }

    @Test
    void forceCompressionCompactsAllMediaOutsideRecentTail() {
        MultimodalHistoryCompactorConfig config = new MultimodalHistoryCompactorConfig();
        config.setKeepRecentTurns(2);
        config.setTriggerThreshold(999);
        List<BaseMessage> history = new ArrayList<>();
        for (int turn = 1; turn <= 4; turn++) {
            history.add(mediaMessage("image_url", "data:image/png;base64,image-" + turn));
        }
        SessionModelContext context = contextWith(new MultimodalHistoryCompactor(config), history);

        Object result = context.compressContext(List.of("MultimodalHistoryCompactor"), Map.of())
                .toCompletableFuture()
                .join();

        assertThat(result).isEqualTo(SessionModelContext.ACTIVE_COMPRESSION_RESULT_COMPRESSED);
        assertThat(fullResolutionMediaCount(context.getMessages())).isEqualTo(2);
        assertThat(markerCount(context.getMessages())).isEqualTo(2);
    }

    @Test
    void contextEngineCreatesConfiguredProcessor() {
        Object modelContext = new ContextEngine().createContext(
                "multimodal-compaction-test",
                null,
                List.of(new ContextEngine.ProcessorSpec(
                        "MultimodalHistoryCompactor",
                        new MultimodalHistoryCompactorConfig())),
                List.of(),
                null);
        SessionModelContext context = assertInstanceOf(SessionModelContext.class, modelContext);

        context.addMessages(mediaMessage("image_url", "data:image/png;base64,image"))
                .toCompletableFuture()
                .join();

        assertThat(context.getProcessors()).hasSize(1);
        assertThat(context.getProcessors().get(0)).isInstanceOf(MultimodalHistoryCompactor.class);
    }

    private static SessionModelContext contextWith(MultimodalHistoryCompactor processor) {
        return contextWith(processor, List.of());
    }

    private static SessionModelContext contextWith(MultimodalHistoryCompactor processor, List<BaseMessage> history) {
        return new SessionModelContext(
                "ctx",
                "session",
                new ContextEngineConfig(),
                history,
                List.of(processor),
                messages -> 1);
    }

    private static UserMessage mediaMessage(String partType, String url) {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("url", url);
        Map<String, Object> mediaPart = new LinkedHashMap<>();
        mediaPart.put("type", partType);
        mediaPart.put(partType, media);
        UserMessage message = new UserMessage();
        message.setContent(List.of(mediaPart));
        return message;
    }

    private static int fullResolutionMediaCount(List<BaseMessage> messages) {
        return (int) messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::getContentAsList)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(MultimodalHistoryCompactor::isBase64MultimodalPart)
                .count();
    }

    private static int markerCount(List<BaseMessage> messages) {
        return (int) messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .map(UserMessage::getContentAsList)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(part -> part instanceof Map<?, ?> map && DEFAULT_SUMMARY_MARKER.equals(map.get("text")))
                .count();
    }
}
