/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test LlmAgentBuilder intention detector functionality.
 * <p>
 * Mirrors Python's {@code test_intention_detector.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_intention_detector.py}.
 */
class TestIntentionDetector {

    @Test
    void testExtractIntentWithJsonBlock() {
        Map<String, Object> result = IntentionDetector.extractIntent("```json\n{\"need_refined\": true}\n```");

        assertThat(result).containsEntry("need_refined", true);
    }

    @Test
    void testExtractIntentWithoutJsonBlock() {
        Map<String, Object> result = IntentionDetector.extractIntent("{\"need_refined\": false}");

        assertThat(result).containsEntry("need_refined", false);
    }

    @Test
    void testExtractIntentWithMultilineJson() {
        String input = """
                ```json
                {
                  "need_refined": true,
                  "reason": "test"
                }
                ```""";

        Map<String, Object> result = IntentionDetector.extractIntent(input);

        assertThat(result).containsEntry("need_refined", true);
        assertThat(result).containsEntry("reason", "test");
    }

    @Test
    void testDetectRefineIntentEmptyQuery() {
        IntentionDetector detector = new IntentionDetector(mock(Model.class));

        assertThat(detector.detectRefineIntent("", "some config")).isFalse();
    }

    @Test
    void testDetectRefineIntentNoneQuery() {
        IntentionDetector detector = new IntentionDetector(mock(Model.class));

        assertThat(detector.detectRefineIntent(null, "some config")).isFalse();
    }

    @Test
    void testDetectRefineIntentReturnsTrue() throws Exception {
        Model llm = mock(Model.class);
        when(llm.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssistantMessage("```json\n{\"need_refined\": true}\n```"));
        IntentionDetector detector = new IntentionDetector(llm);

        boolean result = detector.detectRefineIntent("修改配置", "current config");

        assertThat(result).isTrue();
    }

    @Test
    void testDetectRefineIntentReturnsFalse() throws Exception {
        Model llm = mock(Model.class);
        when(llm.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssistantMessage("```json\n{\"need_refined\": false}\n```"));
        IntentionDetector detector = new IntentionDetector(llm);

        boolean result = detector.detectRefineIntent("确认", "current config");

        assertThat(result).isFalse();
    }

    @Test
    void testDetectRefineIntentHandlesException() throws Exception {
        Model llm = mock(Model.class);
        when(llm.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Test error"));
        IntentionDetector detector = new IntentionDetector(llm);

        assertThatThrownBy(() -> detector.detectRefineIntent("test query", "config"))
                .isInstanceOf(ApplicationError.class)
                .hasMessageContaining("Test error");
    }
}
