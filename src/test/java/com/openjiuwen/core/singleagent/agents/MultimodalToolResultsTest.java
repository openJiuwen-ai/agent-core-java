/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for multimodal tool result handling.
 *
 * <p>Mirrors Python's {@code test_multimodal_tool_results.py} in
 * {@code tests/unit_tests/agent/react_agent/}.</p>
 */
@DisplayName("Multimodal tool results")
class MultimodalToolResultsTest {

    @Test
    @DisplayName("tool message content omits multimodal payload")
    void testToolMessageContentOmitsMultimodalPayload() {
        ToolOutput result = new ToolOutput(true, Map.of(
                "content", "Image file read: /tmp/a.png",
                "multimodal", List.of(Map.of(
                        "type", "image",
                        "data_url", "data:image/png;base64,abc"
                ))
        ), null);

        assertThat(AbilityManager.buildToolMessageContent(result))
                .isEqualTo("Image file read: /tmp/a.png");
    }

    @Test
    @DisplayName("ReActAgent builds multimodal user message from tool result")
    void testReactAgentBuildsMultimodalUserMessageFromToolResult() {
        ToolOutput result = new ToolOutput(true, Map.of(
                "content", "Image file read: /tmp/a.png",
                "multimodal", List.of(Map.of(
                        "type", "image",
                        "source_path", "/tmp/a.png",
                        "data_url", "data:image/png;base64,abc"
                ))
        ), null);

        List<UserMessage> messages = ReActAgent.buildMultimodalToolResultMessages(result);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo("user");
        assertThat(messages.get(0).getContentAsList()).hasSize(2);
        assertThat(messages.get(0).getContentAsList().get(0))
                .isEqualTo(Map.of(
                        "type", "text",
                        "text", "Image loaded from read_file: /tmp/a.png"
                ));
        assertThat(messages.get(0).getContentAsList().get(1))
                .isEqualTo(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:image/png;base64,abc")
                ));
    }
}
