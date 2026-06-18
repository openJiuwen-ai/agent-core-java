/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.chat_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.single_agent.legacy.LegacyBaseAgent;
import com.openjiuwen.core.single_agent.legacy.config.LlmCallConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code ChatAgent} in
 * {@code openjiuwen/dev_tools/tune/chat_agent/chat_agent.py}.
 */
class ChatAgentTest {

    @Test
    void factoryCreatesConfigWithPythonModelField() {
        LlmCallConfig llmCallConfig = llmConfig("model-factory");

        ChatAgentConfig config = ChatAgent.createChatAgentConfig(
                "agent-factory",
                "1.0.0",
                "chat",
                llmCallConfig
        );

        assertEquals("agent-factory", config.getId());
        assertEquals("1.0.0", config.getVersion());
        assertEquals("chat", config.getDescription());
        assertSame(llmCallConfig, config.getLlmCallConfig());
    }

    @Test
    void factoryAddsToolsAndRegistersLlmCall() {
        RecordingChatAgent agent = RecordingChatAgent.create(config("agent-chat-factory", "model-a"));
        EchoTool tool = new EchoTool("chat-tool-factory", "chat-tool-factory");

        ChatAgent created = ChatAgent.createChatAgent(agent.getTypedAgentConfig(), List.of(tool));

        assertEquals(List.of(tool), created.getTools());
        assertTrue(created.getLlmCalls().containsKey("llm_call"));
    }

    @Test
    void invokePopsConversationIdAndReturnsPythonResultShape() {
        RecordingChatAgent agent = RecordingChatAgent.create(config("agent-chat-invoke", "model-invoke"));
        AgentSession session = AgentSession.createAgentSession("external-session", null, null);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("conversation_id", "conv-1");
        inputs.put("query", "hello");

        Object rawResult = agent.invoke(inputs, session).toCompletableFuture().join();

        Map<?, ?> result = assertInstanceOf(Map.class, rawResult);
        assertEquals("ok", result.get("output"));
        List<?> toolCalls = assertInstanceOf(List.class, result.get("tool_calls"));
        ToolCall toolCall = assertInstanceOf(ToolCall.class, toolCalls.getFirst());
        assertEquals("tool-call", toolCall.getName());
        assertFalse(inputs.containsKey("conversation_id"));
        assertEquals("hello", agent.client.invokeInputs.getFirst().get("query"));
        assertSame(session, agent.callbackPayload.get(3));
    }

    @Test
    void invokeFiltersToolsByAgentTagAndToolIds() {
        RecordingChatAgent agent = RecordingChatAgent.create(config("agent-chat-tools", "model-tools"));
        EchoTool included = new EchoTool("chat-tool-included", "included-tool");
        EchoTool excluded = new EchoTool("chat-tool-excluded", "excluded-tool");
        Runner.resourceMgr().addTool(included, List.of("agent-chat-tools"), true);
        Runner.resourceMgr().addTool(excluded, List.of("other-agent-chat-tools"), true);
        agent.addTools(List.of(included));

        agent.invoke(new LinkedHashMap<>(Map.of("query", "hello")), null).toCompletableFuture().join();

        List<?> tools = agent.client.invokeTools.getFirst();
        assertEquals(List.of("included-tool"), tools.stream()
                .map(item -> assertInstanceOf(ToolInfo.class, item).getName())
                .toList());
    }

    @Test
    void streamYieldsPythonChunkShapeAndCleansOwnedSession() {
        RecordingChatAgent agent = RecordingChatAgent.create(config("agent-chat-stream", "model-stream"));
        agent.client.streamChunks = List.of(
                AssistantMessageChunk.builder().content("a").build(),
                AssistantMessageChunk.builder().content("b").build()
        );
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("conversation_id", "stream-session");
        inputs.put("query", "go");

        Iterator<Object> iterator = agent.stream(inputs, null, List.of());
        List<Object> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }

        assertEquals(List.of("a", "b"), chunks.stream()
                .map(item -> assertInstanceOf(Map.class, item).get("output"))
                .toList());
        assertFalse(inputs.containsKey("conversation_id"));
        assertEquals("llm_call", agent.callbackPayload.get(0));
        assertEquals("go", agent.callbackPayload.get(1));
        assertEquals("ab", agent.callbackPayload.get(2));
        assertInstanceOf(AgentSession.class, agent.callbackPayload.get(3));
    }

    @Test
    void copyUsesSameConfigWithoutToolsLikePythonFactoryCall() {
        RecordingChatAgent agent = RecordingChatAgent.create(config("agent-chat-copy", "model-copy"));
        agent.addTools(List.of(new EchoTool("chat-tool-copy", "copy-tool")));

        LegacyBaseAgent copied = agent.copy();

        ChatAgent chatCopy = assertInstanceOf(ChatAgent.class, copied);
        assertSame(agent.getTypedAgentConfig(), chatCopy.getTypedAgentConfig());
        assertTrue(chatCopy.getTools().isEmpty());
    }

    private static ChatAgentConfig config(String agentId, String modelName) {
        return ChatAgent.createChatAgentConfig(agentId, "1.0", "chat", llmConfig(modelName));
    }

    private static LlmCallConfig llmConfig(String modelName) {
        Model.registerClientFactory("openai", (clientConfig, modelConfig) -> new RecordingClient());
        LlmCallConfig config = new LlmCallConfig();
        config.setModel(ModelRequestConfig.builder().modelName(modelName).build());
        config.setModelClient(ModelClientConfig.builder().clientProvider("openai").apiKey("test").build());
        config.setSystemPrompt(List.of(Map.of("role", "system", "content", "system {{query}}")));
        config.setUserPrompt(List.of(Map.of("role", "user", "content", "user {{query}}")));
        return config;
    }

    private static final class RecordingChatAgent extends ChatAgent {
        private static final ThreadLocal<RecordingClient> NEXT_CLIENT = new ThreadLocal<>();

        private final RecordingClient client;
        private final List<Object> callbackPayload = new ArrayList<>();

        private static RecordingChatAgent create(ChatAgentConfig agentConfig) {
            RecordingClient client = new RecordingClient();
            NEXT_CLIENT.set(client);
            try {
                return new RecordingChatAgent(agentConfig, client);
            } finally {
                NEXT_CLIENT.remove();
            }
        }

        private RecordingChatAgent(ChatAgentConfig agentConfig, RecordingClient client) {
            super(agentConfig);
            this.client = client;
            LLMCall call = getLlmCalls().get("llm_call");
            call.setOptimizerCallback((id, inputs, response, session) -> {
                callbackPayload.add(id);
                callbackPayload.add(inputs.get("query"));
                callbackPayload.add(response);
                callbackPayload.add(session);
            });
        }

        @Override
        protected Model initModel(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            return new Model(NEXT_CLIENT.get());
        }
    }

    private static final class RecordingClient implements Model.ModelClient {
        private final List<Map<String, Object>> invokeInputs = new ArrayList<>();
        private final List<List<?>> invokeTools = new ArrayList<>();
        private final List<List<BaseMessage>> invokeMessages = new ArrayList<>();
        private List<AssistantMessageChunk> streamChunks = List.of();

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            invokeMessages.add(messages);
            invokeTools.add(options.getTools());
            invokeInputs.add(extractInputs(messages));
            AssistantMessage message = new AssistantMessage("ok");
            message.setToolCalls(List.of(ToolCall.builder().name("tool-call").arguments("{}").build()));
            return CompletableFuture.completedFuture(message);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            return streamChunks.iterator();
        }

        private static Map<String, Object> extractInputs(List<BaseMessage> messages) {
            Map<String, Object> inputs = new LinkedHashMap<>();
            for (BaseMessage message : messages) {
                if (message.getContentAsString().contains("hello")) {
                    inputs.put("query", "hello");
                }
            }
            return inputs;
        }
    }

    private static final class EchoTool extends Tool {
        private EchoTool(String id, String name) {
            super(new ToolCard(id, name, "Echo"));
        }
    }
}
