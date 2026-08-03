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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for the LLM agent generator.
 *
 * <p>Mirrors Python's {@code Generator} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/generator.py}.</p>
 *
 * <p>Also mirrors Python's {@code TestGeneratorIntegration} and
 * {@code TestGeneratorExtractElements} in
 * {@code tests/system_tests/dev_tools/agent_builder/builders/llm_agent/test_generator_integration.py}.</p>
 *
 * <p>Also mirrors Python's {@code TestGenerator} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/llm_agent/test_generator.py}.</p>
 */
class GeneratorTest {

    @Test
    void testGeneratorInitialization() {
        Model model = modelReturning(new ArrayList<>(), "");
        Generator generator = new Generator(model);

        assertSame(model, generator.getLlm());
    }

    @Test
    void testExtractElementsConstant() {
        assertTrue(Generator.EXTRACT_ELEMENTS.containsKey("name"));
        assertTrue(Generator.EXTRACT_ELEMENTS.containsKey("description"));
        assertTrue(Generator.EXTRACT_ELEMENTS.containsKey("prompt"));
        assertTrue(Generator.EXTRACT_ELEMENTS.containsKey("opening_remarks"));
        assertTrue(Generator.EXTRACT_ELEMENTS.containsKey("question"));
    }

    @Test
    void testParseInfoEmpty() {
        Map<String, Object> result = Generator.parseInfo("");

        assertInstanceOf(Map.class, result);
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("description"));
        assertTrue(result.containsKey("prompt"));
    }

    @Test
    void testParseInfoWithContent() {
        String content = """
                <角色名称>Test Agent</角色名称>
                <角色描述>Test Description</角色描述>
                <提示词>Test Prompt</提示词>
                <智能体开场白>Hello</智能体开场白>
                <预置问题>Question?</预置问题>
                """;

        Map<String, Object> result = Generator.parseInfo(content);

        assertEquals("Test Agent", result.get("name"));
        assertEquals("Test Description", result.get("description"));
        assertEquals("Test Prompt", result.get("prompt"));
        assertEquals("Hello", result.get("opening_remarks"));
        assertEquals("Question?", result.get("question"));
    }

    @Test
    void testParseInfoWithQuotes() {
        Map<String, Object> result = Generator.parseInfo("<角色名称>\"Quoted Name\"</角色名称>");

        assertEquals("Quoted Name", result.get("name"));
    }

    @Test
    void parseInfoWithMissingElementKeepsAbsentValuesEmpty() {
        String content = """
                <角色名称>测试助手</角色名称>
                <角色描述>这是一个测试助手</角色描述>
                """;

        Map<String, Object> result = Generator.parseInfo(content);

        assertEquals("测试助手", result.get("name"));
        assertEquals("这是一个测试助手", result.get("description"));
        assertEquals("", result.get("prompt"));
        assertEquals("", result.get("opening_remarks"));
    }

    @Test
    void parseInfoWithPluginListKeepsOriginalListText() {
        String content = """
                <角色名称>测试助手</角色名称>
                <选择的插件列表>["plugin_001", "plugin_002"]</选择的插件列表>
                """;

        Map<String, Object> result = Generator.parseInfo(content);

        assertEquals("[\"plugin_001\", \"plugin_002\"]", result.get("plugin"));
    }

    @Test
    void parseInfoWithKnowledgeListKeepsOriginalListText() {
        String content = """
                <选择的知识库列表>["kb_001"]</选择的知识库列表>
                """;

        Map<String, Object> result = Generator.parseInfo(content);

        assertEquals("[\"kb_001\"]", result.get("knowledge"));
    }

    @Test
    void parseInfoWithWorkflowListKeepsOriginalListText() {
        String content = """
                <选择的工作流列表>["wf_001"]</选择的工作流列表>
                """;

        Map<String, Object> result = Generator.parseInfo(content);

        assertEquals("[\"wf_001\"]", result.get("workflow"));
    }

    @Test
    void parseInfoEmptyContentReturnsAllKnownKeysAsEmptyStrings() {
        Map<String, Object> result = Generator.parseInfo("");

        assertEquals("", result.get("name"));
        assertEquals("", result.get("description"));
        assertEquals("", result.get("prompt"));
        assertEquals("", result.get("plugin"));
        assertEquals("", result.get("knowledge"));
        assertEquals("", result.get("workflow"));
    }

    @Test
    void parseInfoMultilineContentPreservesPromptLines() {
        String content = """
                <提示词>你是一个测试助手。
                你可以帮助用户进行测试。
                请保持友好。</提示词>
                """;

        Map<String, Object> result = Generator.parseInfo(content);

        assertTrue(String.valueOf(result.get("prompt")).contains("你是一个测试助手"));
        assertTrue(String.valueOf(result.get("prompt")).contains("你可以帮助用户进行测试"));
    }

    @Test
    void testNameElement() {
        assertEquals("角色名称", Generator.EXTRACT_ELEMENTS.get("name"));
    }

    @Test
    void testDescriptionElement() {
        assertEquals("角色描述", Generator.EXTRACT_ELEMENTS.get("description"));
    }

    @Test
    void testPromptElement() {
        assertEquals("提示词", Generator.EXTRACT_ELEMENTS.get("prompt"));
    }

    @Test
    void testOpeningRemarksElement() {
        assertEquals("智能体开场白", Generator.EXTRACT_ELEMENTS.get("opening_remarks"));
    }

    @Test
    void testQuestionElement() {
        assertEquals("预置问题", Generator.EXTRACT_ELEMENTS.get("question"));
    }

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
