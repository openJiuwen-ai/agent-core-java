/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for IntentionDetector module.
 * <p>
 * Mirrors Python's {@code test_intention_detector_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.builders.workflow}.
 */
class TestIntentionDetectorIntegration {

    private static final class RecordingLlm {
        private Object lastMessages;
        private int callCount;
        private String responseContent = "";

        public AssistantMessage invoke(Object messages) {
            this.lastMessages = messages;
            this.callCount += 1;
            return new AssistantMessage(responseContent);
        }
    }

    @Nested
    class TestWorkflowIntentionDetectorIntegration {

        @Test
        void testIntentionDetectorInitialization() {
            RecordingLlm llm = new RecordingLlm();
            IntentionDetector detector = new IntentionDetector(llm);

            assertThat(detector.getLlm()).isSameAs(llm);
        }

        @Test
        void testDetectInitialInstructionEmptyHistory() {
            IntentionDetector detector = new IntentionDetector(new RecordingLlm());

            assertThat(detector.detectInitialInstruction(List.of())).isFalse();
        }

        @Test
        void testDetectInitialInstructionWithHistory() {
            RecordingLlm llm = new RecordingLlm();
            llm.responseContent = "{\"provide_process\": true}";
            IntentionDetector detector = new IntentionDetector(llm);

            boolean result = detector.detectInitialInstruction(List.of(Map.of("role", "user", "content", "create workflow")));

            assertThat(result).isTrue();
            assertThat(llm.callCount).isEqualTo(1);
            assertThat(llm.lastMessages).isInstanceOf(List.class);
        }

        @Test
        void testDetectRefineIntentEmptyHistory() {
            IntentionDetector detector = new IntentionDetector(new RecordingLlm());

            assertThat(detector.detectRefineIntent(List.of(), "mermaid code")).isFalse();
        }

        @Test
        void testDetectRefineIntentWithHistory() {
            RecordingLlm llm = new RecordingLlm();
            llm.responseContent = "{\"need_refined\": true}";
            IntentionDetector detector = new IntentionDetector(llm);

            boolean result = detector.detectRefineIntent(
                    List.of(Map.of("role", "user", "content", "modify workflow")),
                    "graph TD"
            );

            assertThat(result).isTrue();
            assertThat(llm.callCount).isEqualTo(1);
        }
    }

    @Nested
    class TestWorkflowIntentionDetectorExtractIntent {

        @Test
        void testExtractIntentWithJsonBlock() {
            Map<String, Object> result = IntentionDetector.extractIntent("```json\n{\"provide_process\": true}\n```");

            assertThat(result).isInstanceOf(Map.class);
            assertThat(result.get("provide_process")).isEqualTo(true);
        }

        @Test
        void testExtractIntentWithoutJsonBlock() {
            Map<String, Object> result = IntentionDetector.extractIntent("{\"provide_process\": false}");

            assertThat(result).isInstanceOf(Map.class);
            assertThat(result.get("provide_process")).isEqualTo(false);
        }
    }

    @Nested
    class TestWorkflowIntentionDetectorFormatDialogHistory {

        @Test
        void testFormatDialogHistoryUser() {
            String result = IntentionDetector.formatDialogHistory(List.of(Map.of("role", "user", "content", "test message")));

            assertThat(result).contains("User: test message");
        }

        @Test
        void testFormatDialogHistoryAssistant() {
            String result = IntentionDetector.formatDialogHistory(List.of(Map.of("role", "assistant", "content", "response")));

            assertThat(result).contains("Assistant: response");
        }

        @Test
        void testFormatDialogHistoryMixed() {
            String result = IntentionDetector.formatDialogHistory(List.of(
                    Map.of("role", "user", "content", "question"),
                    Map.of("role", "assistant", "content", "answer")
            ));

            assertThat(result).contains("User: question");
            assertThat(result).contains("Assistant: answer");
        }
    }
}
