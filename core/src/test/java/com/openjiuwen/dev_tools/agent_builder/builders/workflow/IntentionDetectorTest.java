/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python behavior in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/intention_detector.py}.
 */
class IntentionDetectorTest {

    @Test
    void formatDialogHistoryUsesRoleMapAndUserFallback() {
        String formatted = IntentionDetector.formatDialogHistory(List.of(
                Map.of("role", "user", "content", "hi"),
                Map.of("role", "assistant", "content", "hello"),
                Map.of("role", "unknown", "content", "fallback")
        ));

        assertThat(formatted).isEqualTo("User: hi\nAssistant: hello\nUser: fallback");
    }

    @Test
    void extractIntentReadsJsonCodeBlock() {
        Map<String, Object> result = IntentionDetector.extractIntent("""
                ```json
                {"provide_process": true}
                ```
                """);

        assertThat(result).containsEntry("provide_process", true);
    }

    @Test
    void detectMethodsReturnFalseForEmptyMessagesAndInvokeModelForContent() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<BaseMessage>> lastMessages = new AtomicReference<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            lastMessages.set(messages);
            int call = calls.incrementAndGet();
            String content = call == 1
                    ? "{\"provide_process\": true}"
                    : "{\"need_refined\": true}";
            return CompletableFuture.completedFuture(new AssistantMessage(content));
        });
        IntentionDetector detector = new IntentionDetector(model);

        assertThat(detector.detectInitialInstruction(List.of())).isFalse();
        assertThat(detector.detectInitialInstruction(List.of(Map.of("role", "user", "content", "make a flow"))))
                .isTrue();
        assertThat(lastMessages.get()).hasSize(2);
        assertThat(lastMessages.get().get(0).getRole()).isEqualTo("system");
        assertThat(lastMessages.get().get(1).getContentAsString()).contains("make a flow");

        assertThat(detector.detectRefineIntent(List.of(Map.of("role", "user", "content", "change it")), "A --> B"))
                .isTrue();
        assertThat(lastMessages.get().get(1).getContentAsString()).contains("A --> B");
    }
}
