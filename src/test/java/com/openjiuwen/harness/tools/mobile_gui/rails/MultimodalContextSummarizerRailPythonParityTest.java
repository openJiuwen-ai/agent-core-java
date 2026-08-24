/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's
 * {@code tests.unit_tests.harness.tools.mobile_gui.test_multimodal_context_summarizer_rail} in
 * {@code tests/unit_tests/harness/tools/mobile_gui/test_multimodal_context_summarizer_rail.py}.</p>
 */
class MultimodalContextSummarizerRailPythonParityTest {

    @Test
    void summarizerDoesNotArchiveProtectedSkillReferenceUsers() {
        List<BaseMessage> messages = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            messages.add(minimalImageUser(MultimodalSkillReadRail.MULTIMODAL_SKILL_USER_MESSAGE_NAME));
        }
        CallbackContext ctx = context(messages);

        new MultimodalContextSummarizerRail(3).beforeModelCall(ctx);

        for (BaseMessage message : messages(ctx)) {
            assertThat(message.getName()).isEqualTo(MultimodalSkillReadRail.MULTIMODAL_SKILL_USER_MESSAGE_NAME);
            assertThat(block(message, 1)).containsEntry("type", "image_url");
            assertThat(String.valueOf(((Map<?, ?>) block(message, 1).get("image_url")).get("url"))).contains("base64");
        }
    }

    @Test
    void summarizerArchivesOldestUnnamedScreenshotsKeepsRecentAndProtected() {
        List<BaseMessage> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(minimalImageUser(null));
        }
        messages.add(minimalImageUser(MultimodalSkillReadRail.MULTIMODAL_SKILL_USER_MESSAGE_NAME));
        CallbackContext ctx = context(messages);

        new MultimodalContextSummarizerRail(3).beforeModelCall(ctx);

        List<BaseMessage> updated = messages(ctx);
        assertThat(updated).hasSize(6);
        for (int index : List.of(0, 1)) {
            Map<String, Object> archived = block(updated.get(index), 1);
            assertThat(archived).containsEntry("type", "text");
            assertThat(String.valueOf(archived.get("text")))
                    .contains(MultimodalContextSummarizerRail.ARCHIVED_SCREEN_PLACEHOLDER);
        }
        for (int index : List.of(2, 3, 4)) {
            assertThat(block(updated.get(index), 1)).containsEntry("type", "image_url");
        }
        assertThat(updated.get(updated.size() - 1).getName())
                .isEqualTo(MultimodalSkillReadRail.MULTIMODAL_SKILL_USER_MESSAGE_NAME);
        assertThat(block(updated.get(updated.size() - 1), 1)).containsEntry("type", "image_url");
    }

    @Test
    void summarizerNoopWhenUnnamedCountAtLimit() {
        List<BaseMessage> messages = List.of(
                minimalImageUser(null),
                minimalImageUser(null),
                minimalImageUser(null)
        );
        CallbackContext ctx = context(messages);

        new MultimodalContextSummarizerRail(3).beforeModelCall(ctx);

        for (BaseMessage message : messages(ctx)) {
            assertThat(block(message, 1)).containsEntry("type", "image_url");
        }
    }

    @Test
    void summarizerNoopWhenFewerThanLimit() {
        List<BaseMessage> messages = List.of(
                minimalImageUser(null),
                minimalImageUser(null)
        );
        CallbackContext ctx = context(messages);

        new MultimodalContextSummarizerRail(3).beforeModelCall(ctx);

        for (BaseMessage message : messages(ctx)) {
            assertThat(block(message, 1)).containsEntry("type", "image_url");
        }
    }

    @Test
    void summarizerIgnoresAssistantMessagesWithImages() {
        List<BaseMessage> messages = new ArrayList<>();
        messages.add(assistantImage());
        for (int i = 0; i < 5; i++) {
            messages.add(minimalImageUser(null));
        }
        CallbackContext ctx = context(messages);

        new MultimodalContextSummarizerRail(2).beforeModelCall(ctx);

        List<BaseMessage> updated = messages(ctx);
        assertThat(block(updated.get(0), 1)).containsEntry("type", "image_url");
        for (int index : List.of(1, 2, 3)) {
            assertThat(block(updated.get(index), 1)).containsEntry("type", "text");
        }
        for (int index : List.of(4, 5)) {
            assertThat(block(updated.get(index), 1)).containsEntry("type", "image_url");
        }
    }

    private static UserMessage minimalImageUser(String name) {
        UserMessage message = new UserMessage("");
        message.setName(name);
        message.setContent(List.of(
                new LinkedHashMap<>(Map.of("type", "text", "text", "stub")),
                new LinkedHashMap<>(Map.of(
                        "type", "image_url",
                        "image_url", new LinkedHashMap<>(Map.of("url", "data:image/png;base64,QQ=="))
                ))
        ));
        return message;
    }

    private static AssistantMessage assistantImage() {
        AssistantMessage message = new AssistantMessage("");
        message.setContent(List.of(
                new LinkedHashMap<>(Map.of("type", "text", "text", "thinking")),
                new LinkedHashMap<>(Map.of(
                        "type", "image_url",
                        "image_url", new LinkedHashMap<>(Map.of("url", "data:image/png;base64,AA=="))
                ))
        ));
        return message;
    }

    private static CallbackContext context(List<BaseMessage> messages) {
        return new CallbackContext(new DeepAgent(), new LinkedHashMap<>(Map.of("messages", new ArrayList<>(messages))));
    }

    @SuppressWarnings("unchecked")
    private static List<BaseMessage> messages(CallbackContext ctx) {
        return (List<BaseMessage>) ctx.get("messages");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(BaseMessage message, int index) {
        return (Map<String, Object>) message.getContentAsList().get(index);
    }
}
