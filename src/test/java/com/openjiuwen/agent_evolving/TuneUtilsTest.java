/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TuneUtilsTest {

    @Test
    void validateDigitalParameterAcceptsBoundaries() {
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(0.0, "param", 0.0, 1.0));
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(1.0, "param", 0.0, 1.0));
    }

    @Test
    void tuneConstantMatchesPythonDefaultsAndBounds() {
        assertAll(
                () -> assertEquals(1, TuneConstant.DEFAULT_EXAMPLE_NUM),
                () -> assertEquals(3, TuneConstant.DEFAULT_ITERATION_NUM),
                () -> assertEquals(10, TuneConstant.DEFAULT_MAX_SAMPLED_EXAMPLE_NUM),
                () -> assertEquals(1, TuneConstant.DEFAULT_PARALLEL_NUM),
                () -> assertEquals(10, TuneConstant.DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES),
                () -> assertEquals(1.0f, TuneConstant.DEFAULT_EARLY_STOP_SCORE),
                () -> assertEquals(1, TuneConstant.MIN_ITERATION_NUM),
                () -> assertEquals(20, TuneConstant.MAX_ITERATION_NUM),
                () -> assertEquals(1, TuneConstant.MIN_PARALLEL_NUM),
                () -> assertEquals(20, TuneConstant.MAX_PARALLEL_NUM),
                () -> assertEquals(0, TuneConstant.MIN_EXAMPLE_NUM),
                () -> assertEquals(20, TuneConstant.MAX_EXAMPLE_NUM)
        );
    }

    @Test
    void validateDigitalParameterRejectsOutOfRangeValues() {
        assertThrows(ValidationError.class,
                () -> TuneUtils.validateDigitalParameter(-0.1, "param", 0.0, 1.0));
        assertThrows(ValidationError.class,
                () -> TuneUtils.validateDigitalParameter(1.1, "param", 0.0, 1.0));
    }

    @Test
    void parseJsonFromLlmResponseSupportsObjectAndArrayRoots() {
        Object objectResult = TuneUtils.parseJsonFromLlmResponse("```json\n{\"result\": true, \"score\": 0.9}\n```");
        assertInstanceOf(Map.class, objectResult);
        assertEquals(Boolean.TRUE, ((Map<?, ?>) objectResult).get("result"));

        Object arrayResult = TuneUtils.parseJsonFromLlmResponse("```json\n[1, 2, 3]\n```");
        assertEquals(List.of(1, 2, 3), arrayResult);
    }

    @Test
    void parseJsonFromLlmResponseHandlesWhitespaceAndInvalidPayloads() {
        assertEquals(
                Map.of("key", "value"),
                TuneUtils.parseJsonFromLlmResponse("```json  \n{\"key\": \"value\"}  \n```")
        );
        assertNull(TuneUtils.parseJsonFromLlmResponse("{\"key\": \"value\"}"));
        assertNull(TuneUtils.parseJsonFromLlmResponse("```json\nnot valid json\n```"));
        assertNull(TuneUtils.parseJsonFromLlmResponse("```json\n```"));
    }

    @Test
    void parseListFromLlmResponseReturnsOnlyLists() {
        assertEquals(List.of(1, 2, 3), TuneUtils.parseListFromLlmResponse("```list\n[1, 2, 3]\n```"));
        assertNull(TuneUtils.parseListFromLlmResponse("```list\n{\"key\": \"value\"}\n```"));
    }

    @Test
    void parseListFromLlmResponseHandlesWhitespaceAndRejectsScalarPayloads() {
        assertEquals(
                List.of(1, 2, 3),
                TuneUtils.parseListFromLlmResponse("```list  \n[1, 2, 3]  \n```")
        );
        assertNull(TuneUtils.parseListFromLlmResponse("[1, 2, 3]"));
        assertNull(TuneUtils.parseListFromLlmResponse("```list\n42\n```"));
    }

    @Test
    void convertCasesToExamplesSupportsCaseAndEvaluatedCase() {
        Case caseData = new Case(Map.of("query", "hello"), Map.of("answer", "world"), "case_1");
        EvaluatedCase evaluatedCase = EvaluatedCase.builder().caseData(caseData).score(0.8).build();

        String examples = TuneUtils.convertCasesToExamples(List.of(caseData, evaluatedCase));

        assertTrue(examples.contains("example 1:"));
        assertTrue(examples.contains("[question]: query:hello"));
        assertTrue(examples.contains("[expected answer]: answer:world"));
        assertTrue(examples.contains("example 2:"));
    }

    @Test
    void getInputStringFromCaseFormatsInputMap() {
        Case caseData = new Case(Map.of("query", "hello", "context", "world"), Map.of("answer", "ok"));

        String formatted = TuneUtils.getInputStringFromCase(caseData);

        assertTrue(formatted.contains("query:hello"));
        assertTrue(formatted.contains("context:world"));
    }

    @Test
    void getOutputStringFromMessageSerializesToolCalls() {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder().name("search").arguments("{\"q\":\"hi\"}").build()))
                .build();

        String output = TuneUtils.getOutputStringFromMessage(message);

        assertTrue(output.contains("\"name\":\"search\""));
        assertTrue(output.contains("\"arguments\":\"{\\\"q\\\":\\\"hi\\\"}\""));
    }

    @Test
    void getOutputStringFromMessageReturnsPlainContentWithoutToolCalls() {
        assertEquals(
                "assistant",
                TuneUtils.getOutputStringFromMessage(AssistantMessage.builder().content("assistant").build())
        );
        assertEquals(
                "user",
                TuneUtils.getOutputStringFromMessage(UserMessage.builder().content("user").build())
        );
        assertEquals("", TuneUtils.getOutputStringFromMessage(null));
    }

    @Test
    void getContentStringFromTemplateJoinsMessageContents() {
        PromptTemplate template = PromptTemplate.builder()
                .content(List.of(
                        SystemMessage.builder().content("system").build(),
                        UserMessage.builder().content("user").build()
                ))
                .build();

        assertEquals("system\nuser", TuneUtils.getContentStringFromTemplate(template));
    }

    @Test
    void getContentStringFromTemplateReturnsEmptyForEmptyTemplate() {
        PromptTemplate template = PromptTemplate.builder()
                .content(List.of())
                .build();

        assertEquals("", TuneUtils.getContentStringFromTemplate(template));
    }
}
