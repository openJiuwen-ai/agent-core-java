// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ToolExceptionTerminalStateTest {

    private AbilityManager manager = new AbilityManager();

    @AfterEach
    void tearDown() {
        if (manager != null) {
            for (Object card : manager.list()) {
                if (card instanceof ToolCard toolCard) {
                    String id = toolCard.getId() != null ? toolCard.getId() : toolCard.getName();
                    Runner.resourceMgr().removeTool(id, null, TagMatchStrategy.ALL, true);
                }
            }
        }
    }

    @Test
    void defaultConfig_toolExceptionBecomesRecoverableMessageNotFailed() {
        String toolId = "failing-default-" + UUID.randomUUID();
        LocalFunction failingTool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("always fails").build(),
                inputs -> {
                    throw new IllegalStateException("模拟工具执行异常: 数据库连接失败");
                }
        );

        Runner.resourceMgr().addTool(failingTool, null);
        manager.add(failingTool.getCard());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .shouldFailTaskOnToolError(false)
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().config(config).build();

        List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                ctx,
                ToolCall.builder().id("tc-default").name(toolId).arguments("{}").build(),
                null,
                null
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).toolMessage()).isNotNull();
        String content = String.valueOf(results.get(0).toolMessage().getContent());
        assertThat(content).contains("Tool execution error");
        assertThat(content).contains("数据库连接失败");
        assertThat(ctx.hasForceFinishRequest()).isFalse();
    }

    @Test
    void failTaskOnToolErrorEnabled_toolExceptionForceFinishesAsError() {
        String toolId = "failing-force-" + UUID.randomUUID();
        LocalFunction failingTool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("always fails").build(),
                inputs -> {
                    throw new IllegalStateException("模拟工具执行异常: 数据库连接失败");
                }
        );

        Runner.resourceMgr().addTool(failingTool, null);
        manager.add(failingTool.getCard());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .shouldFailTaskOnToolError(true)
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().config(config).build();

        List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                ctx,
                ToolCall.builder().id("tc-fail").name(toolId).arguments("{}").build(),
                null,
                null
        );

        assertThat(results).hasSize(1);
        assertThat(ctx.hasForceFinishRequest()).isTrue();

        Map<String, Object> finish = ctx.consumeForceFinish().getResult();
        assertThat(finish.get("result_type")).isEqualTo("error");
        assertThat(String.valueOf(finish.get("output"))).contains("数据库连接失败");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outcomes = (List<Map<String, Object>>) finish.get("tool_outcomes");
        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).get("status")).isEqualTo("failed");
    }

    @Test
    void handleToolExecutionException_abilityErrorBranchProducesRecoverableMessageByDefault() {
        String toolId = "failing-ability-" + UUID.randomUUID();
        LocalFunction failingTool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("always fails").build(),
                inputs -> {
                    throw new IllegalStateException("模拟工具执行异常: 沙箱不可用");
                }
        );

        Runner.resourceMgr().addTool(failingTool, null);
        manager.add(failingTool.getCard());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .shouldFailTaskOnToolError(false)
                .build();
        ToolCall toolCall = ToolCall.builder().id("tc-ability").name(toolId).arguments("{}").build();
        ToolCallInputs inputs = ToolCallInputs.builder().toolName(toolId).toolArgs("{}").build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().config(config).inputs(inputs).build();

        List<AbilityManager.ToolExecutionEntry> results = manager.execute(ctx, toolCall, null, null);

        assertThat(results).hasSize(1);
        String content = String.valueOf(results.get(0).toolMessage().getContent());
        assertThat(content).contains("Tool execution error");
        assertThat(content).contains("沙箱不可用");
        assertThat(ctx.hasForceFinishRequest()).isFalse();
    }
}
