/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent.react_agent;

import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.single_agent.AbilityManager;
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's multimodal tool-result tests in
 * {@code tests/unit_tests/agent/react_agent/test_multimodal_tool_results.py}.
 */
class MultimodalToolResultsPythonParityTest {

    @Test
    void testToolMessageContentOmitsMultimodalPayload() {
        ToolOutput result = ToolOutput.success(new LinkedHashMap<>(Map.of(
                "content", "Image file read: /tmp/a.png",
                "multimodal", List.of(imageItem(null, "data:image/png;base64,abc"))
        )));

        assertThat(AbilityManager.buildToolMessageContent(result)).isEqualTo("Image file read: /tmp/a.png");
    }

    @Test
    void testReActAgentBuildsMultimodalUserMessageFromToolResult() {
        ToolOutput result = imageToolOutput("/tmp/a.png", "data:image/png;base64,abc");

        List<UserMessage> messages = ReActAgent.buildMultimodalToolResultMessages(result);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getRole()).isEqualTo("user");
        List<?> content = content(messages.getFirst());
        assertThat(((Map<?, ?>) content.get(0)).get("type")).isEqualTo("text");
        assertThat(content.get(1)).isEqualTo(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/png;base64,abc")
        ));
    }

    @Test
    void testReActAgentBatchesMultipleMultimodalToolResultsIntoOneUserMessage() {
        ToolOutput first = imageToolOutput("/tmp/a.png", "data:image/png;base64,aaa");
        ToolOutput second = imageToolOutput("/tmp/b.jpg", "data:image/jpeg;base64,bbb");

        UserMessage message = ReActAgent.buildMultimodalToolResultsMessage(List.of(first, second));

        assertThat(message).isNotNull();
        assertThat(message.getRole()).isEqualTo("user");
        List<?> content = content(message);
        assertThat(content).hasSize(5);
        assertThat(((Map<?, ?>) content.get(0)).get("type")).isEqualTo("text");
        assertThat(String.valueOf(((Map<?, ?>) content.get(0)).get("text"))).contains("/tmp/a.png", "/tmp/b.jpg");
        assertThat(content.get(2)).isEqualTo(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/png;base64,aaa")
        ));
        assertThat(content.get(4)).isEqualTo(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/jpeg;base64,bbb")
        ));
    }

    private static ToolOutput imageToolOutput(String sourcePath, String dataUrl) {
        return ToolOutput.success(new LinkedHashMap<>(Map.of(
                "content", "Image file read: " + sourcePath,
                "multimodal", List.of(imageItem(sourcePath, dataUrl))
        )));
    }

    private static Map<String, Object> imageItem(String sourcePath, String dataUrl) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "image");
        if (sourcePath != null) {
            item.put("source_path", sourcePath);
        }
        item.put("data_url", dataUrl);
        return item;
    }

    private static List<?> content(UserMessage message) {
        assertThat(message.getContent()).isInstanceOf(List.class);
        return (List<?>) message.getContent();
    }
}
