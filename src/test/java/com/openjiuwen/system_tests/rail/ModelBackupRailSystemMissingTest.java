/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.rail;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.core.single_agent.rail.ModelBackupRail;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestModelBackupRailMock} in
 * {@code tests/system_tests/rail/test_model_backup_rail.py}.
 */
class ModelBackupRailSystemMissingTest {
    private static final String FINAL_ANSWER = "根据计算结果，1+2=3";

    @Test
    void middlewareExecutesWhenReActAgentInvoke() {
        ReActAgent agent = new ReActAgent(new AgentCard(
                unique("model-backup-agent"),
                "model_backup_agent",
                "数学计算助手"
        ));
        agent.configure(new ReActAgentConfig()
                .configurePromptTemplate(List.of(Map.of(
                        "role",
                        "system",
                        "content",
                        "你是一个数学计算助手，在适当的时候调用工具来完成计算任务。"
                )))
                .configureMaxIterations(3));
        FailingModelClient primaryClient = new FailingModelClient();
        ScriptedModelClient backupClient = new ScriptedModelClient(
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-1")
                                .type("function")
                                .name("add")
                                .arguments("{\"a\": 1, \"b\": 2}")
                                .build()))
                        .build(),
                new AssistantMessage(FINAL_ANSWER)
        );
        ModelBackupRail backupRail = new ModelBackupRail(List.of(new Model(backupClient)));
        agent.setLlm(new Model(primaryClient));
        agent.registerRail(backupRail).toCompletableFuture().join();
        agent.getAbilityManager().add(new ToolCard("add", "add", "加法运算", addToolSchema()));

        try {
            Map<String, Object> result = invoke(agent, Map.of(
                    "conversation_id", "test_session",
                    "query", "计算1+2"
            ));

            assertThat(String.valueOf(result.get("output"))).contains(FINAL_ANSWER);
            assertThat(primaryClient.invocationCount()).isEqualTo(1);
            assertThat(backupClient.invocationCount()).isEqualTo(2);
            assertThat(backupRail.getIndex()).isEqualTo(1);
        } finally {
            agent.unregisterRail(backupRail).toCompletableFuture().join();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invoke(ReActAgent agent, Map<String, Object> inputs) {
        return (Map<String, Object>) agent.invoke(inputs, new MemorySession("test_session"))
                .toCompletableFuture()
                .join();
    }

    private static Map<String, Object> addToolSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("description", "第一个加数", "type", "number"),
                        "b", Map.of("description", "第二个加数", "type", "number")
                ),
                "required", List.of("a", "b")
        );
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static final class FailingModelClient implements Model.ModelClient {
        private int invocationCount;

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invocationCount++;
            return CompletableFuture.failedFuture(new IllegalStateException("primary model unavailable"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        private int invocationCount() {
            return invocationCount;
        }
    }

    private static final class ScriptedModelClient implements Model.ModelClient {
        private final List<AssistantMessage> responses;
        private int invocationCount;

        private ScriptedModelClient(AssistantMessage... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            int index = Math.min(invocationCount, responses.size() - 1);
            invocationCount++;
            return CompletableFuture.completedFuture(responses.get(index));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        private int invocationCount() {
            return invocationCount;
        }
    }

    private static final class MemorySession implements AgentSessionApi, ContextEngine.SessionPort {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private MemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
