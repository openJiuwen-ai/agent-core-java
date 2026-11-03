/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compacts multimodal context windows by replacing older screenshot blocks.
 *
 * <p>Mirrors Python's {@code MultimodalContextSummarizerRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/multimodal_context_summarizer_rail.py}.</p>
 */
public class MultimodalContextSummarizerRail extends DeepAgentRail {

    public static final String ARCHIVED_SCREEN_PLACEHOLDER =
            "Screenshot from an earlier step is no longer attached to save context. "
                    + "The UI may have changed since then; rely on the latest user message with an image for the current screen. "
                    + "If you need to reason about that past state, summarize what you already inferred from it in prior turns.";

    private final int screenshotsToKeep;

    public MultimodalContextSummarizerRail(int screenshotsToKeep) {
        this.screenshotsToKeep = Math.max(1, screenshotsToKeep);
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null || !(ctx.get("messages") instanceof List<?> rawMessages) || rawMessages.isEmpty()) {
            return;
        }
        List<BaseMessage> messages = rawMessages.stream()
                .filter(BaseMessage.class::isInstance)
                .map(BaseMessage.class::cast)
                .toList();
        if (messages.size() != rawMessages.size()) {
            return;
        }
        archiveOldScreenshotImages(messages);
        ctx.put("messages", new ArrayList<>(messages));
    }

    private void archiveOldScreenshotImages(List<BaseMessage> messages) {
        List<Integer> screenshotIndices = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            BaseMessage message = messages.get(i);
            if (!"user".equals(message.getRole())) {
                continue;
            }
            if (MultimodalSkillReadRail.MULTIMODAL_SKILL_USER_MESSAGE_NAME.equals(message.getName())) {
                continue;
            }
            if (hasImageUrl(message)) {
                screenshotIndices.add(i);
            }
        }
        if (screenshotIndices.size() <= screenshotsToKeep) {
            return;
        }
        List<Integer> oldIndices = screenshotIndices.subList(0, screenshotIndices.size() - screenshotsToKeep);
        for (Integer index : oldIndices) {
            replaceArchivedScreenshotImages(messages.get(index));
        }
    }

    private static boolean hasImageUrl(BaseMessage message) {
        List<Object> content = message.getContentAsList();
        if (content == null) {
            return false;
        }
        for (Object block : content) {
            if (block instanceof Map<?, ?> map && "image_url".equals(map.get("type"))) {
                return true;
            }
        }
        return false;
    }

    private static void replaceArchivedScreenshotImages(BaseMessage message) {
        List<Object> content = message.getContentAsList();
        if (content == null) {
            return;
        }
        List<Object> nextContent = new ArrayList<>();
        for (Object block : content) {
            if (block instanceof Map<?, ?> map && "image_url".equals(map.get("type"))) {
                nextContent.add(new LinkedHashMap<>(Map.of(
                        "type", "text",
                        "text", ARCHIVED_SCREEN_PLACEHOLDER
                )));
            } else {
                nextContent.add(block);
            }
        }
        if (nextContent.isEmpty()) {
            nextContent.add(new LinkedHashMap<>(Map.of(
                    "type", "text",
                    "text", ARCHIVED_SCREEN_PLACEHOLDER
            )));
        }
        message.setContent(nextContent);
    }
}
