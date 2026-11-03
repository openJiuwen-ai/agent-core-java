/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Focused parity tests for the requirement clarifier.
 *
 * <p>Mirrors Python's {@code Clarifier} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/clarifier.py}.</p>
 */
class ClarifierTest {

    @Test
    void parseResourceOutputFiltersAvailableResourcesAndBuildsDisplay() {
        Map<String, Object> available = Map.of(
                "plugins", List.of(Map.of("tool_id", "tool-1")),
                "knowledge", List.of(Map.of("knowledge_id", "kb-1"))
        );
        String output = """
                prefix
                ## Agent资源规划
                【选择的插件】
                [{'tool_id': 'tool-1', 'tool_name': 'Search', 'tool_desc': 'Find docs'},
                 {'tool_id': 'missing', 'tool_name': 'Bad', 'tool_desc': 'No'}]
                【选择的知识库】
                [{'knowledge_id': 'kb-1', 'knowledge_name': 'Docs', 'knowledge_desc': 'Knowledge'}]
                """;

        Clarifier.ParseResult result = Clarifier.parseResourceOutput(output, available);

        assertEquals("""
                【选择的插件】
                1. Search: Find docs
                【选择的知识库】
                1. Docs: Knowledge""", result.displayContent());
        assertEquals(List.of("tool-1"), result.resourceIdDict().get("plugin"));
        assertEquals(List.of("kb-1"), result.resourceIdDict().get("knowledge"));
    }

    @Test
    void parseResourceOutputReturnsEmptyWhenNoPlanningHeader() {
        Clarifier.ParseResult result = Clarifier.parseResourceOutput("plain text", Map.of());

        assertEquals("", result.displayContent());
        assertTrue(result.resourceIdDict().isEmpty());
    }

    @Test
    void parseResourceOutputWrapsLiteralParseFailure() {
        try {
            Clarifier.parseResourceOutput("## Agent资源规划\n【选择的插件】\n[{'tool_id': ]",
                    Map.of("plugins", List.of(Map.of("tool_id", "tool-1"))));
            fail("expected ApplicationError");
        } catch (ApplicationError error) {
            assertSame(StatusCode.AGENT_BUILDER_RESOURCE_PARSE_ERROR, error.getStatus());
            assertEquals("plugin", assertMap(error.getDetails()).get("resource_type"));
        }
    }

    @Test
    void clarifyInvokesFactorAndResourcePromptsThenParsesResourcePlan() {
        List<List<BaseMessage>> captured = new ArrayList<>();
        Model model = modelReturning(captured,
                "factor output",
                """
                        ## Agent资源规划
                        【选择的插件】
                        [{'tool_id': 'tool-1', 'tool_name': 'Search', 'tool_desc': 'Find docs'}]
                        """
        );
        Clarifier clarifier = new Clarifier(model);

        Clarifier.ClarifyResult result = clarifier.clarify("build an agent",
                Map.of("plugins", List.of(Map.of("tool_id", "tool-1"))));

        assertEquals("factor output", result.factorOutput());
        assertEquals("【选择的插件】\n1. Search: Find docs", result.displayResource());
        assertEquals(List.of("tool-1"), result.resourceIdDict().get("plugin"));
        assertEquals(2, captured.size());
        assertInstanceOf(SystemMessage.class, captured.get(0).get(0));
        assertEquals(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT, captured.get(0).get(0).getContent());
        assertInstanceOf(SystemMessage.class, captured.get(1).get(0));
        assertEquals(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT, captured.get(1).get(0).getContent());
        assertTrue(String.valueOf(captured.get(1).get(1).getContent()).contains("factor output"));
        assertTrue(String.valueOf(captured.get(1).get(1).getContent()).contains("tool-1"));
    }

    private static Model modelReturning(List<List<BaseMessage>> capturedMessages, String... responses) {
        ArrayDeque<String> queuedResponses = new ArrayDeque<>(List.of(responses));
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.add(new ArrayList<>(messages));
            return CompletableFuture.completedFuture(new AssistantMessage(queuedResponses.removeFirst()));
        });
    }

    private static Map<?, ?> assertMap(Object value) {
        return assertInstanceOf(Map.class, value);
    }
}
