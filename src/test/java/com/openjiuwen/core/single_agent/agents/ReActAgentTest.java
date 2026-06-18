/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.agents;

import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the Java ReAct agent translation.
 *
 * <p>Mirrors Python's {@code ReActAgent} behavior in
 * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
 */
class ReActAgentTest {

    @Test
    void configChainsMirrorPythonMutators() {
        ReActAgentConfig config = new ReActAgentConfig()
                .configureModel("qwen")
                .configureModelProvider("openai", "key", "base")
                .configurePrompt("default")
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", "Hello {query}")))
                .configureMemScope("scope")
                .configureMaxIterations(3)
                .configureCustomHeaders(Map.of("X-Test", "1"));

        assertEquals("qwen", config.getModelName());
        assertEquals("openai", config.getModelProvider());
        assertEquals("key", config.getApiKey());
        assertEquals("base", config.getApiBase());
        assertEquals("default", config.getPromptTemplateName());
        assertEquals("scope", config.getMemScopeId());
        assertEquals(3, config.getMaxIterations());
        assertEquals("1", config.getCustomHeaders().get("X-Test"));
    }

    @Test
    void multimodalToolResultsAreAggregatedIntoOneUserMessage() {
        Map<String, Object> first = toolResult("a.png", "data:image/png;base64,aaa");
        Map<String, Object> second = toolResult("b.png", "data:image/png;base64,bbb");

        var message = ReActAgent.buildMultimodalToolResultsMessage(List.of(first, second));

        assertInstanceOf(List.class, message.getContent());
        List<?> content = (List<?>) message.getContent();
        assertEquals(5, content.size());
        assertTrue(String.valueOf(((Map<?, ?>) content.get(0)).get("text")).contains("Images loaded by tool results"));
        assertEquals("image_url", ((Map<?, ?>) content.get(2)).get("type"));
        assertEquals("image_url", ((Map<?, ?>) content.get(4)).get("type"));
    }

    @Test
    void invokeReturnsAssistantAnswerAndCanClearContextMessages() {
        ReActAgent agent = agentWithFakeModel(new AssistantMessage("done"));
        MemorySession session = new MemorySession("session-1");

        Object result = agent.invoke(Map.of("query", "hi"), session).toCompletableFuture().join();

        assertInstanceOf(Map.class, result);
        assertEquals("done", ((Map<?, ?>) result).get("output"));
        assertEquals("answer", ((Map<?, ?>) result).get("result_type"));
        assertTrue(agent.clearContextMessages("session-1", "default_context_id"));
    }

    @Test
    void nonAssistantModelResultIsPassedThrough() {
        ReActAgent agent = new ReActAgent(new AgentCard("agent-1", "agent", "desc")) {
            @Override
            public Object callModel(AgentCallbackContext ctx, ModelContext context,
                                    List<com.openjiuwen.core.foundation.tool.schema.ToolInfo> tools) {
                return Map.of("output", "direct", "result_type", "answer");
            }
        };
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System")));
        config.setMaxIterations(1);
        agent.configure(config);

        Object result = agent.invoke(Map.of("query", "hi"), new MemorySession("session-2"))
                .toCompletableFuture()
                .join();

        assertInstanceOf(Map.class, result);
        assertEquals("direct", ((Map<?, ?>) result).get("output"));
    }

    @Test
    void summarizeToolCallUsesPythonFunctionShape() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "search");
        function.put("arguments", "{\"query\":\"abc\"}");
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("function", function);

        assertEquals("search({\"query\":\"abc\"})", ReActAgent.summarizeToolCall(call));
    }

    private static ReActAgent agentWithFakeModel(AssistantMessage response) {
        ReActAgent agent = new ReActAgent(new AgentCard("agent-1", "agent", "desc"));
        ReActAgentConfig config = new ReActAgentConfig();
        config.setPromptTemplate(List.of(Map.of("role", "system", "content", "System {query}")));
        config.setMaxIterations(2);
        agent.configure(config);
        agent.setLlm(new Model(new Model.ModelClient() {
            @Override
            public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
                return CompletableFuture.completedFuture(response);
            }
        }));
        return agent;
    }

    private static Map<String, Object> toolResult(String sourcePath, String dataUrl) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("type", "image");
        image.put("source_path", sourcePath);
        image.put("data_url", dataUrl);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("multimodal_items", List.of(image));
        return Map.of("data", data);
    }

    /**
     * In-memory session test double for ReActAgent parity checks.
     *
     * <p>Mirrors Python's session interactions used by {@code ReActAgent} in
     * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
     */
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
