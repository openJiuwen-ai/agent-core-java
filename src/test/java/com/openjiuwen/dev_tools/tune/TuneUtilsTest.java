/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TuneUtils}.
 *
 * <p>Mirrors Python's {@code TuneUtils} in
 * {@code openjiuwen/dev_tools/tune/utils.py}.</p>
 */
class TuneUtilsTest {

    @Test
    void validateDigitalParameterUsesInclusiveBounds() {
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(0.0d, "ratio", 0.0d, 1.0d));
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(1.0d, "ratio", 0.0d, 1.0d));
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> TuneUtils.validateDigitalParameter(2.0d, "ratio", 0.0d, 1.0d));
        assertTrue(String.valueOf(error).contains("ratio"));
    }

    @Test
    void getInputStringFromCaseSerializesMessagesAndVariables() {
        MessageCase caseValue = new MessageCase(
                List.of(new BaseMessage("user", "hello"), assistantWithToolCall()),
                Map.of("name", "Ada"));

        String result = TuneUtils.getInputStringFromCase(caseValue);

        assertTrue(result.contains("[user]: hello"));
        assertTrue(result.contains("[assistant]:"));
        assertTrue(result.contains("\"name\":\"search\""));
        assertTrue(result.contains("variables: {name=Ada}"));
    }

    @Test
    void getInputStringRequiresPythonMessagesAttribute() {
        Case caseValue = new Case(Map.of("query", "hello"), Map.of("answer", "world"));

        assertThrows(IllegalArgumentException.class, () -> TuneUtils.getInputStringFromCase(caseValue));
    }

    @Test
    void getOutputStringFromMessageUsesToolNameAndArgumentsOnly() {
        String result = TuneUtils.getOutputStringFromMessage(assistantWithToolCall());

        assertTrue(result.contains("\"name\":\"search\""));
        assertTrue(result.contains("\"arguments\":\"{}\""));
        assertTrue(!result.contains("\"id\""));
    }

    @Test
    void getContentStringFromTemplateJoinsMessageContent() {
        PromptTemplate template = PromptTemplate.builder()
                .content(List.of(new BaseMessage("system", "rules"), new UserMessage("question")))
                .build();

        assertEquals("rules\nquestion", TuneUtils.getContentStringFromTemplate(template));
    }

    @Test
    void parseJsonFromLlmResponseExtractsJsonBlock() {
        Map<String, Object> result = TuneUtils.parseJsonFromLlmResponse("x ```json\n{\"score\": 1}\n``` y");

        assertEquals(1, result.get("score"));
        assertNull(TuneUtils.parseJsonFromLlmResponse("{\"score\": 1}"));
        assertNull(TuneUtils.parseJsonFromLlmResponse("```json\nbad\n```"));
    }

    @Test
    void parseListFromLlmResponseAcceptsJsonAndPythonListBlocks() {
        assertEquals(List.of(1, 2), TuneUtils.parseListFromLlmResponse("```list\n[1, 2]\n```"));
        assertEquals(Arrays.asList("a", true, null),
                TuneUtils.parseListFromLlmResponse("```list\n['a', True, None]\n```"));
        assertNull(TuneUtils.parseListFromLlmResponse("[1, 2]"));
        assertNull(TuneUtils.parseListFromLlmResponse("```list\n{\"a\": 1}\n```"));
    }

    @Test
    void convertCasesToExamplesFormatsCasesAndEvaluatedCases() {
        Case caseValue = new Case(Map.of("query", "hello"), Map.of("answer", "world"));
        EvaluatedCase evaluatedCase = new EvaluatedCase(caseValue);

        String result = TuneUtils.convertCasesToExamples(List.of(caseValue, evaluatedCase));

        assertTrue(result.contains("example 1:"));
        assertTrue(result.contains("example 2:"));
        assertTrue(result.contains("[question]: query:hello"));
        assertTrue(result.contains("[expected answer]: answer:world"));
    }

    private static AssistantMessage assistantWithToolCall() {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id("call-1")
                        .type("function")
                        .name("search")
                        .arguments("{}")
                        .build()))
                .build();
    }

    private static final class MessageCase extends Case {
        private final List<BaseMessage> messages;
        private final Map<String, Object> variables;

        private MessageCase(List<BaseMessage> messages, Map<String, Object> variables) {
            super(Map.of("placeholder", true), Map.of("answer", true));
            this.messages = messages;
            this.variables = variables;
        }

        public List<BaseMessage> getMessages() {
            return messages;
        }

        public Map<String, Object> getVariables() {
            return variables;
        }
    }
}
