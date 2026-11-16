/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.foundation.tool.ToolCard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for tool-call timeout resolution and the declared subagent timeout.
 */
class ToolTimeoutTest {

    @Test
    void cardWithoutResilienceGetsTheGlobalDefault() {
        ToolCard card = ToolCard.builder()
                .id("no-resilience")
                .name("no-resilience")
                .description("card without a resilience block")
                .inputParams(Map.of("type", "object"))
                .build();

        assertThat(AbilityManager.resolveCallTimeout(card)).isEqualTo(300.0D);
    }

    @Test
    void taskToolCardDeclaresItsOwnTimeout() {
        com.openjiuwen.harness.tools.subagent.TaskTool taskTool =
                new com.openjiuwen.harness.tools.subagent.TaskTool(
                        new ToolCard("task_tool", "task_tool", "Run a subagent task."), null);

        Object resilience = taskTool.getCard().getProperties().get("resilience");
        assertThat(resilience).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) resilience).get("timeout_s"))
                .isEqualTo(com.openjiuwen.harness.tools.subagent.TaskTool.DEFAULT_TASK_TIMEOUT_SECONDS);
        assertThat(AbilityManager.resolveCallTimeout(taskTool.getCard())).isEqualTo(720.0D);
    }

    @Test
    void explicitTimeoutOnTheCardOverridesTheTaskToolDefault() {
        ToolCard card = new ToolCard("task_tool", "task_tool", "Run a subagent task.");
        card.setProperties(Map.of("resilience", Map.of("timeout_s", 60)));
        com.openjiuwen.harness.tools.subagent.TaskTool taskTool =
                new com.openjiuwen.harness.tools.subagent.TaskTool(card, null);

        assertThat(AbilityManager.resolveCallTimeout(taskTool.getCard())).isEqualTo(60.0D);
    }

    @Test
    void executionResultExposesPartialResultHook() {
        assertThat(AbilityManager.ExecutionResult.PartialResultProvider.class).isInterface();
    }
}
