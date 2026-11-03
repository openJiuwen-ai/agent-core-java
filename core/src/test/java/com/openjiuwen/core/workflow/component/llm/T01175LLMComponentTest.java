/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity checks for LLM workflow component helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp} in
 * {@code openjiuwen/core/workflow/components/llm/llm_comp.py}.</p>
 */
class T01175LLMComponentTest {

    @Test
    void jsonParserStripsMarkdownFence() {
        Map<String, Object> parsed = JsonParser.parseJsonContent("```json\n{\"answer\":\"ok\"}\n```");

        assertEquals("ok", parsed.get("answer"));
    }

    @Test
    void formatterMapsTextAndJsonOutputs() {
        Map<String, Object> textFormat = Map.of("type", "text");
        Map<String, Object> textOutputs = new LinkedHashMap<>();
        textOutputs.put("answer", Map.of("type", "string", "required", true));

        assertEquals(Map.of("answer", "hello"), OutputFormatter.formatResponse("hello", textFormat, textOutputs));

        Map<String, Object> jsonOutputs = new LinkedHashMap<>();
        jsonOutputs.put("answer", Map.of("type", "string", "required", true));
        jsonOutputs.put("score", Map.of("type", "integer", "required", true));

        Map<String, Object> formatted = OutputFormatter.formatResponse(
                "{\"answer\":\"ok\",\"score\":3,\"extra\":true}",
                Map.of("type", "json"),
                jsonOutputs);

        assertEquals("ok", formatted.get("answer"));
        assertEquals(3, formatted.get("score"));
        assertEquals(2, formatted.size());
    }

    @Test
    void promptFormatterInjectsJsonInstructionIntoLastUserMessage() {
        BaseMessage first = new UserMessage("system question");
        BaseMessage last = new UserMessage("return data");
        List<BaseMessage> history = new java.util.ArrayList<>(List.of(first, last));
        Map<String, Object> outputConfig = Map.of("answer", Map.of("type", "string", "required", true));

        List<BaseMessage> formatted = LLMPromptFormatter.formatPrompt(
                history,
                Map.of("type", "json"),
                outputConfig);

        assertSame(history, formatted);
        assertEquals("system question", first.getContent());
        String prompt = last.getContentAsString();
        assertTrue(prompt.contains("Here is the JSON schema"));
        assertTrue(prompt.contains("\"answer\""));
        assertTrue(prompt.contains("return data"));
    }

    @Test
    void componentCreatesAndCachesExecutable() {
        LLMCompConfig config = new LLMCompConfig();
        config.setResponseFormat(Map.of("type", "text"));
        config.setOutputConfig(Map.of("answer", Map.of("type", "string", "required", true)));
        LLMComponent component = new LLMComponent(config);

        LLMExecutable executable = component.getExecutable();

        assertSame(executable, component.getExecutable());
        assertSame(config, executable.getConfig());
    }
}
