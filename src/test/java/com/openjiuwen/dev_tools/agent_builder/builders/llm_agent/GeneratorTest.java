/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the LLM agent generator.
 *
 * <p>Mirrors Python's {@code Generator} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/generator.py}.</p>
 */
class GeneratorTest {

    @Test
    void parseInfoExtractsTagsAndRemovesWrappingQuotes() {
        String content = """
                <角色名称>"客服助手"</角色名称>
                <角色描述>Handles support</角色描述>
                <提示词>Be helpful</提示词>
                <智能体开场白>"Hello"</智能体开场白>
                <选择的插件列表>[{'tool_id': 'tool-1'}]</选择的插件列表>
                <选择的知识库列表>[]</选择的知识库列表>
                """;

        Map<String, Object> parsed = Generator.parseInfo(content);

        assertEquals("客服助手", parsed.get("name"));
        assertEquals("Handles support", parsed.get("description"));
        assertEquals("Be helpful", parsed.get("prompt"));
        assertEquals("Hello", parsed.get("opening_remarks"));
        assertEquals("", parsed.get("question"));
        assertEquals("[{'tool_id': 'tool-1'}]", parsed.get("plugin"));
        assertEquals("[]", parsed.get("knowledge"));
        assertEquals("", parsed.get("workflow"));
    }

    @Test
    void generateBuildsPromptAndOverridesResourceIds() {
        List<List<BaseMessage>> captured = new ArrayList<>();
        Generator generator = new Generator(modelReturning(captured, """
                <角色名称>客服助手</角色名称>
                <角色描述>Handles support</角色描述>
                <提示词>Be helpful</提示词>
                <智能体开场白>Hello</智能体开场白>
                <预置问题>How can I help?</预置问题>
                <选择的插件列表>unused</选择的插件列表>
                <选择的知识库列表>unused</选择的知识库列表>
                <选择的工作流列表>unused</选择的工作流列表>
                """));

        Map<String, Object> result = generator.generate(
                "build support agent",
                "factor",
                "resources",
                Map.of("plugin", List.of("tool-1"), "knowledge", List.of("kb-1"))
        );

        assertEquals("客服助手", result.get("name"));
        assertEquals("How can I help?", result.get("question"));
        assertEquals(List.of("tool-1"), result.get("plugin"));
        assertEquals(List.of("kb-1"), result.get("knowledge"));
        assertEquals(List.of(), result.get("workflow"));
        assertEquals(1, captured.size());
        assertInstanceOf(SystemMessage.class, captured.get(0).get(0));
        assertEquals(LlmAgentPrompts.GENERATE_SYSTEM_PROMPT, captured.get(0).get(0).getContent());
        String userPrompt = String.valueOf(captured.get(0).get(1).getContent());
        assertTrue(userPrompt.contains("build support agent"));
        assertTrue(userPrompt.contains("factor"));
        assertTrue(userPrompt.contains("resources"));
    }

    private static Model modelReturning(List<List<BaseMessage>> capturedMessages, String response) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.add(new ArrayList<>(messages));
            return CompletableFuture.completedFuture(new AssistantMessage(response));
        });
    }
}
