/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests session tool-token counting.
 *
 * @since 0.1.17
 */
class SessionModelContextToolTokensTest {
    @Test
    void toolTokensUseOnlyTheFilteredToolList() {
        SessionModelContext.ToolTokenCounterPort counter =
                new SessionModelContext.ToolTokenCounterPort() {
                    @Override
                    public int countTokens(List<BaseMessage> messages) {
                        return messages.size();
                    }

                    @Override
                    public int countTools(List<ToolInfo> tools) {
                        return tools.stream()
                                .mapToInt(tool -> "active".equals(tool.getName()) ? 100 : 40)
                                .sum();
                    }
                };
        SessionModelContext context =
                new SessionModelContext(
                        "context",
                        "session",
                        new ContextEngineConfig(),
                        List.of(),
                        List.of(),
                        counter);

        ToolInfo activeTool = tool("active");
        ToolInfo inactiveTool = tool("inactive");

        var fullWindow =
                context.getContextWindow(
                                List.of(),
                                List.of(activeTool, inactiveTool),
                                null,
                                null,
                                java.util.Map.of())
                        .toCompletableFuture()
                        .join();
        var filteredWindow =
                context.getContextWindow(
                                List.of(), List.of(activeTool), null, null, java.util.Map.of())
                        .toCompletableFuture()
                        .join();

        assertThat(fullWindow.getStatistic().getTools()).isEqualTo(2);
        assertThat(fullWindow.getStatistic().getToolTokens()).isEqualTo(140);
        assertThat(filteredWindow.getStatistic().getTools()).isEqualTo(1);
        assertThat(filteredWindow.getStatistic().getToolTokens()).isEqualTo(100);
        assertThat(filteredWindow.getStatistic().getToolTokens())
                .isLessThan(fullWindow.getStatistic().getToolTokens());
    }

    private static ToolInfo tool(String name) {
        return ToolInfo.builder()
                .name(name)
                .description("tool schema for " + name)
                .parameters(java.util.Map.of("type", "object", "properties", java.util.Map.of()))
                .build();
    }
}
