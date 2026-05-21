/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for multimodal tool result handling.
 *
 * <p>Mirrors Python's {@code test_multimodal_tool_results.py} in
 * {@code tests/unit_tests/agent/react_agent/}.
 */
class MultimodalToolResultsTest {

    @Test
    @DisplayName("tool message content omits multimodal payload")
    @Disabled("AbilityManager.buildToolMessageContent not yet implemented")
    void testToolMessageContentOmitsMultimodalPayload() {
        ToolOutput result = new ToolOutput(true, Map.of(
                "content", "Image file read: /tmp/a.png",
                "multimodal", List.of(Map.of(
                        "type", "image",
                        "data_url", "data:image/png;base64,abc"
                ))
        ), null);

        String content = AbilityManager.buildToolMessageContent(result);
        assertThat(content).isEqualTo("Image file read: /tmp/a.png");
    }

    @Test
    @DisplayName("ReActAgent builds multimodal user message from tool result")
    @Disabled("ReActAgent.buildMultimodalToolResultMessages not yet implemented")
    void testReActAgentBuildsMultimodalUserMessageFromToolResult() {
        ToolOutput result = new ToolOutput(true, Map.of(
                "content", "Image file read: /tmp/a.png",
                "multimodal", List.of(Map.of(
                        "type", "image",
                        "source_path", "/tmp/a.png",
                        "data_url", "data:image/png;base64,abc"
                ))
        ), null);

        var messages = ReActAgent.buildMultimodalToolResultMessages(result);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo("user");
    }
}
