/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for self-evolving utility functions.
 *
 * <p>Mirrors Python's {@code TuneUtils} and module helpers in
 * {@code openjiuwen/agent_evolving/utils.py}.</p>
 *
 * <p>Mirrors Python's test coverage in
 * {@code tests/unit_tests/agent_evolving/test_utils.py}.</p>
 */
class TuneUtilsTest {

    @TestFactory
    Collection<DynamicTest> validateDigitalParameterCases() {
        return List.of(
                DynamicTest.dynamicTest("valid lower boundary", () ->
                        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(0.0d, "param", 0.0d, 1.0d))),
                DynamicTest.dynamicTest("valid upper boundary", () ->
                        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(1.0d, "param", 0.0d, 1.0d))),
                DynamicTest.dynamicTest("valid middle value", () ->
                        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(0.5d, "param", 0.0d, 1.0d))),
                DynamicTest.dynamicTest("invalid below lower", () ->
                        assertThrows(RuntimeException.class,
                                () -> TuneUtils.validateDigitalParameter(-0.1d, "param", 0.0d, 1.0d))),
                DynamicTest.dynamicTest("invalid above upper", () ->
                        assertThrows(RuntimeException.class,
                                () -> TuneUtils.validateDigitalParameter(1.1d, "param", 0.0d, 1.0d))),
                DynamicTest.dynamicTest("valid negative bounds", () ->
                        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(-5.0d, "param", -10.0d, 0.0d)))
        );
    }

    @Test
    void customParameterNameAppearsInValidationError() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> TuneUtils.validateDigitalParameter(100.0d, "custom_param", 0.0d, 10.0d));

        assertTrue(String.valueOf(error).contains("custom_param"));
    }

    @Test
    void integerValuesAreAccepted() {
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(5, "param", 1, 10));
    }

    @TestFactory
    Collection<DynamicTest> parseJsonFromLlmResponseCases() {
        return List.of(
                DynamicTest.dynamicTest("valid json block", () ->
                        assertEquals(Boolean.TRUE, asMap(TuneUtils.parseJsonFromLlmResponse(
                                "```json\n{\"result\": true, \"score\": 0.9}\n```")).get("result"))),
                DynamicTest.dynamicTest("json with whitespace", () ->
                        assertEquals("value", asMap(TuneUtils.parseJsonFromLlmResponse(
                                "```json  \n{\"key\": \"value\"}  \n```")).get("key"))),
                DynamicTest.dynamicTest("missing json marker", () ->
                        assertNull(TuneUtils.parseJsonFromLlmResponse("{\"result\": true}"))),
                DynamicTest.dynamicTest("invalid json content", () ->
                        assertNull(TuneUtils.parseJsonFromLlmResponse("```json\nnot valid json\n```"))),
                DynamicTest.dynamicTest("empty json block", () ->
                        assertNull(TuneUtils.parseJsonFromLlmResponse("```json\n```"))),
                DynamicTest.dynamicTest("json decode error", () ->
                        assertNull(TuneUtils.parseJsonFromLlmResponse("```json\n{\"incomplete\": json}\n```"))),
                DynamicTest.dynamicTest("nested json", () -> {
                    Map<?, ?> outer = asMap(asMap(TuneUtils.parseJsonFromLlmResponse(
                            "```json\n{\"outer\": {\"inner\": [1, 2, 3]}}\n```")).get("outer"));
                    assertEquals(List.of(1, 2, 3), outer.get("inner"));
                }),
                DynamicTest.dynamicTest("json with special characters", () ->
                        assertEquals("Hello\nWorld", asMap(TuneUtils.parseJsonFromLlmResponse(
                                "```json\n{\"text\": \"Hello\\nWorld\"}\n```")).get("text"))),
                DynamicTest.dynamicTest("json null value", () ->
                        assertTrue(asMap(TuneUtils.parseJsonFromLlmResponse("```json\n{\"key\": null}\n```"))
                                .containsKey("key"))),
                DynamicTest.dynamicTest("json array root", () ->
                        assertEquals(List.of(1, 2, 3), TuneUtils.parseJsonFromLlmResponse("```json\n[1, 2, 3]\n```")))
        );
    }

    @TestFactory
    Collection<DynamicTest> parseListFromLlmResponseCases() {
        return List.of(
                DynamicTest.dynamicTest("valid list block", () ->
                        assertEquals(List.of(1, 2, 3), TuneUtils.parseListFromLlmResponse("```list\n[1, 2, 3]\n```"))),
                DynamicTest.dynamicTest("list with whitespace", () ->
                        assertEquals(List.of(1, 2, 3), TuneUtils.parseListFromLlmResponse("```list  \n[1, 2, 3]  \n```"))),
                DynamicTest.dynamicTest("missing list marker", () ->
                        assertNull(TuneUtils.parseListFromLlmResponse("[1, 2, 3]"))),
                DynamicTest.dynamicTest("invalid list content", () ->
                        assertNull(TuneUtils.parseListFromLlmResponse("```list\nnot a list\n```"))),
                DynamicTest.dynamicTest("empty list block", () ->
                        assertNull(TuneUtils.parseListFromLlmResponse("```list\n```"))),
                DynamicTest.dynamicTest("dict is not list", () ->
                        assertNull(TuneUtils.parseListFromLlmResponse("```list\n{\"key\": \"value\"}\n```"))),
                DynamicTest.dynamicTest("nested list", () ->
                        assertEquals(List.of(List.of(1, 2), List.of(3, 4)),
                                TuneUtils.parseListFromLlmResponse("```list\n[[1, 2], [3, 4]]\n```"))),
                DynamicTest.dynamicTest("mixed types", () ->
                        assertEquals(Arrays.asList(1, "two", 3.0d, true, null),
                                TuneUtils.parseListFromLlmResponse("```list\n[1, \"two\", 3.0, true, null]\n```"))),
                DynamicTest.dynamicTest("list with nested dict", () -> {
                    List<Object> result = TuneUtils.parseListFromLlmResponse("```list\n[{\"a\": 1}, {\"b\": 2}]\n```");
                    assertEquals(1, asMap(result.get(0)).get("a"));
                }),
                DynamicTest.dynamicTest("string is not list", () ->
                        assertNull(TuneUtils.parseListFromLlmResponse("```list\n\"not a list\"\n```"))),
                DynamicTest.dynamicTest("number is not list", () ->
                        assertNull(TuneUtils.parseListFromLlmResponse("```list\n42\n```")))
        );
    }

    @TestFactory
    Collection<DynamicTest> convertCasesToExamplesCases() {
        return List.of(
                DynamicTest.dynamicTest("empty cases", () ->
                        assertEquals("", TuneUtils.convertCasesToExamples(List.of()))),
                DynamicTest.dynamicTest("single case format", () -> {
                    String result = TuneUtils.convertCasesToExamples(List.of(caseValue("query", "hello", "answer", "world")));
                    assertTrue(result.contains("example 1:"));
                    assertTrue(result.contains("[question]:"));
                    assertTrue(result.contains("[expected answer]:"));
                    assertTrue(result.contains("hello"));
                    assertTrue(result.contains("world"));
                }),
                DynamicTest.dynamicTest("multiple cases", () -> {
                    String result = TuneUtils.convertCasesToExamples(List.of(
                            caseValue("q", "a", "ans", "1"),
                            caseValue("q", "b", "ans", "2")));
                    assertTrue(result.contains("example 1:"));
                    assertTrue(result.contains("example 2:"));
                }),
                DynamicTest.dynamicTest("complex inputs", () -> {
                    Case caseValue = new Case(Map.of("query", "test", "context", "info"),
                            Map.of("answer", "result", "confidence", 0.9d));
                    assertTrue(TuneUtils.convertCasesToExamples(List.of(caseValue)).contains("query:test"));
                }),
                DynamicTest.dynamicTest("evaluated case", () -> {
                    EvaluatedCase evaluatedCase = new EvaluatedCase(caseValue("q", "test", "a", "ans"));
                    String result = TuneUtils.convertCasesToExamples(List.of(evaluatedCase));
                    assertTrue(result.contains("example 1:"));
                    assertTrue(result.contains("[question]:"));
                })
        );
    }

    @TestFactory
    Collection<DynamicTest> getInputStringFromCaseCases() {
        return List.of(
                DynamicTest.dynamicTest("case with inputs dict", () ->
                        assertTrue(TuneUtils.getInputStringFromCase(caseValue("query", "hello", "answer", "world"))
                                .contains("query:hello"))),
                DynamicTest.dynamicTest("case with minimal inputs", () ->
                        assertTrue(TuneUtils.getInputStringFromCase(caseValue("q", "?", "a", "!")).contains("q:?")))
        );
    }

    @TestFactory
    Collection<DynamicTest> getOutputStringFromMessageCases() {
        return List.of(
                DynamicTest.dynamicTest("assistant without tool calls", () ->
                        assertEquals("Hello, world!", TuneUtils.getOutputStringFromMessage(new AssistantMessage("Hello, world!")))),
                DynamicTest.dynamicTest("assistant with tool calls", () -> {
                    AssistantMessage message = AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(toolCall("test_func", "{\"arg\":\"val\"}", "call_id")))
                            .build();
                    assertTrue(TuneUtils.getOutputStringFromMessage(message).contains("test_func"));
                }),
                DynamicTest.dynamicTest("user message", () ->
                        assertEquals("Test message", TuneUtils.getOutputStringFromMessage(new UserMessage("Test message")))),
                DynamicTest.dynamicTest("system message", () ->
                        assertEquals("You are a helpful assistant.",
                                TuneUtils.getOutputStringFromMessage(new SystemMessage("You are a helpful assistant.")))),
                DynamicTest.dynamicTest("empty assistant content", () ->
                        assertEquals("", TuneUtils.getOutputStringFromMessage(new AssistantMessage("")))),
                DynamicTest.dynamicTest("multiple tool calls", () -> {
                    AssistantMessage message = AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(toolCall("func1", "{}", "call_1"), toolCall("func2", "{}", "call_2")))
                            .build();
                    String result = TuneUtils.getOutputStringFromMessage(message);
                    assertTrue(result.contains("func1"));
                    assertTrue(result.contains("func2"));
                })
        );
    }

    @TestFactory
    Collection<DynamicTest> getContentStringFromTemplateCases() {
        return List.of(
                DynamicTest.dynamicTest("template with messages", () -> {
                    PromptTemplate template = PromptTemplate.builder()
                            .content(List.of(new SystemMessage("You are a helpful assistant."), new UserMessage("Hello!")))
                            .build();
                    String result = TuneUtils.getContentStringFromTemplate(template);
                    assertTrue(result.contains("helpful"));
                    assertTrue(result.contains("Hello!"));
                }),
                DynamicTest.dynamicTest("single message template", () -> {
                    PromptTemplate template = PromptTemplate.builder().content(List.of(new SystemMessage("Only one."))).build();
                    assertEquals("Only one.", TuneUtils.getContentStringFromTemplate(template));
                }),
                DynamicTest.dynamicTest("empty template", () -> {
                    PromptTemplate template = PromptTemplate.builder().content(List.of()).build();
                    assertEquals("", TuneUtils.getContentStringFromTemplate(template));
                }),
                DynamicTest.dynamicTest("template with special chars", () -> {
                    PromptTemplate template = PromptTemplate.builder().content(List.of(new UserMessage("Line1\nLine2\tTab"))).build();
                    assertTrue(TuneUtils.getContentStringFromTemplate(template).contains("Line1"));
                })
        );
    }

    @Test
    void parseTopLevelFrontmatterParsesOnlyScalarTopLevelFields() {
        Map<String, String> result = TuneUtils.parseTopLevelFrontmatter("""
                ---
                name: demo
                description: useful
                  nested: ignored
                - list
                ---
                body
                """);

        assertEquals(Map.of("name", "demo", "description", "useful"), result);
    }

    @Test
    void skillReferenceScoreRankingKeyPreservesPythonPriorityOrder() {
        SkillReferenceScore score = new SkillReferenceScore(1, 2, 3);

        assertEquals(List.of(1, 2, 3), score.rankingKey());
    }

    @Test
    void inferSkillFromPayloadsPrefersSkillToolHitsOverPathHits() {
        String result = TuneUtils.inferSkillFromTexts(
                List.of("alpha", "beta"),
                List.of(Map.of("skill_name", "alpha")),
                List.of("see /skills/beta/SKILL.md")
        );

        assertEquals("alpha", result);
    }

    @Test
    void inferSkillFromTextsUsesPathAndLegacySkillMdPatterns() {
        String result = TuneUtils.inferSkillFromTexts(
                List.of("writer", "reader"),
                List.of(),
                List.of("open /skills/writer/config and /tmp/reader/SKILL.md")
        );

        assertEquals("writer", result);
    }

    @Test
    void findSkillToolMentionsExtractsInlineSkillToolNames() {
        assertEquals(List.of("demo.skill"),
                TuneUtils.findSkillToolMentions("call skill_tool(skill_name='demo.skill') now"));
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> asMap(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<Object, Object>) value;
    }

    private static Case caseValue(String inputKey, Object inputValue, String labelKey, Object labelValue) {
        return new Case(Map.of(inputKey, inputValue), Map.of(labelKey, labelValue));
    }

    private static ToolCall toolCall(String name, String arguments, String id) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(name)
                .arguments(arguments)
                .build();
    }
}
