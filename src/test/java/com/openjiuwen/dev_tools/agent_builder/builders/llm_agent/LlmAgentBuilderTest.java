/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.dev_tools.agent_builder.executor.HistoryManager;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderEnums;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused parity tests for the LLM agent builder state machine.
 *
 * <p>Mirrors Python's {@code LlmAgentBuilder} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/builder.py}.</p>
 */
class LlmAgentBuilderTest {
    private static final String LOCAL_ADD_TOOL_ID = "4aebb55e-1571-4a98-b353-41793b4434e3";
    private static final String API_TOOL_ID = "f6448b6e-860b-4a67-98bc-ec10de05832a";

    @Test
    void constructorInitializesStateAndCollaborators() {
        HistoryManager historyManager = new HistoryManager();
        LlmAgentBuilder builder = new LlmAgentBuilder(modelReturning("{\"tool_id_list\": []}"), historyManager);

        assertThat(builder.getLlm()).isNotNull();
        assertThat(builder.getHistoryManager()).isSameAs(historyManager);
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getAgentConfigInfo()).isNull();
        assertThat(builder.getFactorOutputInfo()).isNull();
        assertThat(builder.getDisplayResourceInfo()).isNull();
        assertThat(builder.getProgressReporter()).isNull();
        assertThat(builder.getResource()).isEmpty();
        assertThat(LlmAgentBuilder.RESOURCE_UNIQUE_KEY).containsEntry("plugins", "tool_id");
    }

    @Test
    void initialStateClarifiesRequirementStoresAssistantHistoryAndMovesToProcessing() {
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("创建一个客服助手");
        LlmAgentBuilder builder = new LlmAgentBuilder(modelReturning(
                "agent factors",
                """
                        ## Agent资源规划
                        【选择的插件】
                        [{'tool_id': 'tool-1', 'tool_name': 'Search', 'tool_desc': 'Find docs'}]
                        """), historyManager);
        builder.setResource(Map.of("plugins", List.of(Map.of("tool_id", "tool-1"))));

        String response = builder.handleInitial("创建一个客服助手", historyManager.getHistory());

        assertThat(response).isEqualTo("agent factors\n\n【选择的插件】\n1. Search: Find docs");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
        assertThat(builder.getAgentConfigInfo()).isEqualTo("agent factors");
        assertThat(builder.getFactorOutputInfo()).isEqualTo("agent factors");
        assertThat(builder.getDisplayResourceInfo()).isEqualTo("【选择的插件】\n1. Search: Find docs");
        assertThat(builder.getResourceIdDictInfo()).containsEntry("plugin", List.of("tool-1"));
        Map<String, String> lastMessage = historyManager.getHistory().get(historyManager.getHistory().size() - 1);
        assertThat(lastMessage)
                .containsEntry("role", "assistant")
                .containsEntry("content", response);
    }

    @Test
    void processingStateRefinesWhenIntentDetectorReturnsTrue() {
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("创建助手");
        LlmAgentBuilder builder = new LlmAgentBuilder(modelReturning(
                "initial factors",
                "no resource plan",
                "{\"need_refined\": true}",
                "refined factors",
                "no resource plan"), historyManager);

        builder.handleInitial("创建助手", historyManager.getHistory());
        String response = builder.handleProcessing("把语气调得更专业", historyManager.getHistory());

        assertThat(response).isEqualTo("refined factors");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.PROCESSING);
        assertThat(builder.getAgentConfigInfo()).isEqualTo("refined factors");
        Map<String, String> lastMessage = historyManager.getHistory().get(historyManager.getHistory().size() - 1);
        assertThat(lastMessage)
                .containsEntry("role", "assistant")
                .containsEntry("content", "refined factors");
    }

    @Test
    void processingStateGeneratesDslThenResetsInternalState() {
        HistoryManager historyManager = new HistoryManager();
        historyManager.addUserMessage("生成客服助手");
        LlmAgentBuilder builder = new LlmAgentBuilder(modelReturning(
                "initial factors",
                "no resource plan",
                "{\"need_refined\": false}",
                """
                        <角色名称>客服助手</角色名称>
                        <角色描述>处理用户咨询</角色描述>
                        <提示词>保持礼貌</提示词>
                        <智能体开场白>您好</智能体开场白>
                        <预置问题>怎么退货</预置问题>
                        """), historyManager);
        builder.setResource(Map.of("plugin_dict", Map.of(), "tool_id_map", Map.of()));

        builder.handleInitial("生成客服助手", historyManager.getHistory());
        String dsl = builder.handleProcessing("确认生成", historyManager.getHistory());

        assertThat(dsl).contains("\"name\":\"客服助手\"");
        assertThat(dsl).contains("\"description\":\"处理用户咨询\"");
        assertThat(dsl).contains("\"system_prompt\":\"保持礼貌\"");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
        assertThat(builder.getResource()).isEmpty();
        assertThat(builder.getAgentConfigInfo()).isNull();
        assertThat(builder.getFactorOutputInfo()).isNull();
        assertThat(builder.getDisplayResourceInfo()).isNull();
    }

    @Test
    void completedStateDelegatesBackToProcessing() {
        HistoryManager historyManager = new HistoryManager();
        LlmAgentBuilder builder = new LlmAgentBuilder(modelReturning(
                "{\"need_refined\": false}",
                """
                        <角色名称>助手</角色名称>
                        <角色描述>描述</角色描述>
                        <提示词>提示</提示词>
                        <智能体开场白>开场</智能体开场白>
                        <预置问题>问题</预置问题>
                        """), historyManager);

        String dsl = builder.handleCompleted("异常完成态", historyManager.getHistory());

        assertThat(dsl).contains("\"name\":\"助手\"");
        assertThat(builder.getState()).isEqualTo(AgentBuilderEnums.BuildState.INITIAL);
    }

    @Test
    void resourceRefreshMergesPluginListsByToolIdAndKeepsExistingDuplicate() {
        ExposedBuilder builder = new ExposedBuilder(modelReturning(
                "{\"tool_id_list\": [\"" + LOCAL_ADD_TOOL_ID + "\", \"" + API_TOOL_ID + "\"]}"));
        builder.setResource(Map.of("plugins", List.of(Map.of(
                "tool_id", LOCAL_ADD_TOOL_ID,
                "tool_name", "existing add"))));

        builder.refresh(List.of(Map.of("role", "user", "content", "need tools")));

        List<?> plugins = (List<?>) builder.getResource().get("plugins");
        assertThat(plugins).hasSize(2);
        assertThat(((Map<?, ?>) plugins.get(0)).get("tool_name")).isEqualTo("existing add");
        assertThat(plugins)
                .extracting(item -> String.valueOf(((Map<?, ?>) item).get("tool_id")))
                .containsExactly(LOCAL_ADD_TOOL_ID, API_TOOL_ID);
        assertThat(builder.isWorkflowBuilder()).isFalse();
    }

    private static Model modelReturning(String... responses) {
        ArrayDeque<String> queuedResponses = new ArrayDeque<>(List.of(responses));
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            if (queuedResponses.isEmpty()) {
                throw new AssertionError("No queued model response for messages: " + messageContents(messages));
            }
            return CompletableFuture.completedFuture(new AssistantMessage(queuedResponses.removeFirst()));
        });
    }

    private static List<String> messageContents(List<BaseMessage> messages) {
        List<String> result = new ArrayList<>();
        for (BaseMessage message : messages) {
            result.add(String.valueOf(message.getContent()));
        }
        return result;
    }

    private static final class ExposedBuilder extends LlmAgentBuilder {
        private ExposedBuilder(Model llm) {
            super(llm, new HistoryManager());
        }

        private void refresh(List<Map<String, String>> history) {
            updateResource(history);
        }
    }
}
