/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import com.openjiuwen.core.application.schema.ReActAgentConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class InterruptTestBase {

    static final ModelClientConfig MODEL_CLIENT = ModelClientConfig.builder()
            .clientProvider("OpenAI")
            .apiKey("sk-fake")
            .apiBase("https://mock.openai.com/v1")
            .verifySsl(false)
            .build();

    static final ModelRequestConfig MODEL_REQUEST = ModelRequestConfig.builder()
            .modelName("gpt-4o-mock")
            .temperature(0.0)
            .build();

    private InterruptTestBase() {}

    static ReActAgent makeAgent(String agentId) {
        AgentCard card = AgentCard.builder().id(agentId).build();
        ReActAgent agent = new ReActAgent(card);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .modelClientConfig(MODEL_CLIENT)
                .modelConfigObj(MODEL_REQUEST)
                .promptTemplate(List.of(Map.of("role", "system", "content", "You are a helpful assistant.")))
                .build();
        agent.configure(config);
        return agent;
    }

    static Tool createReadTool() {
        AtomicInteger invokeCount = new AtomicInteger(0);
        return new Tool(ToolCard.builder()
                .name("read")
                .description("Read file content")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("filepath", Map.of("description", "File path", "type", "string")),
                        "required", List.of("filepath")
                ))
                .build()) {
            @Override
            public Object invoke(Object inputs, AgentSessionApi session, Map<String, Object> kwargs) {
                invokeCount.incrementAndGet();
                @SuppressWarnings("unchecked")
                Map<String, Object> inputMap = (Map<String, Object>) inputs;
                String filepath = (String) inputMap.getOrDefault("filepath", "");
                return Map.of("success", true, "content", "Content of file " + filepath, "invoke_count", invokeCount.get());
            }

            @Override
            public Iterable<Object> stream(Object inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, null, kwargs));
            }
        };
    }

    static Tool createWriteTool() {
        return new Tool(ToolCard.builder()
                .name("write")
                .description("Write file content")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "filepath", Map.of("description", "File path", "type", "string"),
                                "content", Map.of("description", "Content to write", "type", "string")),
                        "required", List.of("filepath", "content")
                ))
                .build()) {
            @Override
            public Object invoke(Object inputs, AgentSessionApi session, Map<String, Object> kwargs) {
                return Map.of("success", true, "message", "File written successfully");
            }

            @Override
            public Iterable<Object> stream(Object inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, null, kwargs));
            }
        };
    }

    static Tool createActionTool() {
        return new Tool(ToolCard.builder()
                .name("action")
                .description("Execute an action")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("action", Map.of("description", "Action to execute", "type", "string")),
                        "required", List.of("action")
                ))
                .build()) {
            @Override
            public Object invoke(Object inputs, AgentSessionApi session, Map<String, Object> kwargs) {
                return Map.of("success", true, "result", "Action executed");
            }

            @Override
            public Iterable<Object> stream(Object inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, null, kwargs));
            }
        };
    }
}
