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

/**
 * Tests for TuneUtils and TuneConstant helpers.
 *
 * <p>Mirrors Python's {@code test_utils.py} in
 * {@code tests/unit_tests/agent_evolving}.
 */
class TuneUtilsTest {

    @Test
    void validateDigitalParameterAcceptsBoundaries() {
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(0.0, "param", 0.0, 1.0));
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(1.0, "param", 0.0, 1.0));
    }

    @Test
    void validateDigitalParameterAcceptsMiddleValue() {
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(0.5, "param", 0.0, 1.0));
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
    void validateDigitalParameterRejectsBelowLowerBound() {
        assertThrows(ValidationError.class,
                () -> TuneUtils.validateDigitalParameter(-0.1, "param", 0.0, 1.0));
    }

    @Test
    void validateDigitalParameterRejectsAboveUpperBound() {
        assertThrows(ValidationError.class,
                () -> TuneUtils.validateDigitalParameter(1.1, "param", 0.0, 1.0));
    }

    @Test
    void validateDigitalParameterIncludesCustomParamName() {
        ValidationError error = assertThrows(ValidationError.class,
                () -> TuneUtils.validateDigitalParameter(100, "custom_param", 0, 10));

        assertTrue(error.getMessage().contains("custom_param"));
    }

    @Test
    void validateDigitalParameterAcceptsNegativeBounds() {
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(-5, "param", -10, 0));
    }

    @Test
    void validateDigitalParameterAcceptsIntegerRangeValues() {
        assertDoesNotThrow(() -> TuneUtils.validateDigitalParameter(5, "param", 1, 10));
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
    void parseJsonFromLlmResponseReturnsObjectFields() {
        Object result = TuneUtils.parseJsonFromLlmResponse("```json\n{\"result\": true, \"score\": 0.9}\n```");

        assertInstanceOf(Map.class, result);
        assertEquals(Boolean.TRUE, ((Map<?, ?>) result).get("result"));
        assertEquals(0.9d, (Double) ((Map<?, ?>) result).get("score"));
    }

    @Test
    void parseJsonFromLlmResponseReturnsArrayRoot() {
        assertEquals(List.of(1, 2, 3), TuneUtils.parseJsonFromLlmResponse("```json\n[1, 2, 3]\n```"));
    }

    @Test
    void parseJsonObjectFromLlmResponseRejectsArrayRoot() {
        assertNull(TuneUtils.parseJsonObjectFromLlmResponse("```json\n[1, 2, 3]\n```"));
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
    void parseJsonFromLlmResponseHandlesDecodeError() {
        assertNull(TuneUtils.parseJsonFromLlmResponse("```json\n{\"incomplete\": json}\n```"));
    }

    @Test
    void parseJsonFromLlmResponseHandlesNestedJson() {
        Object result = TuneUtils.parseJsonFromLlmResponse("```json\n{\"outer\":{\"inner\":[1,2,3]}}\n```");

        assertInstanceOf(Map.class, result);
        Object outer = ((Map<?, ?>) result).get("outer");
        assertInstanceOf(Map.class, outer);
        assertEquals(List.of(1, 2, 3), ((Map<?, ?>) outer).get("inner"));
    }

    @Test
    void parseJsonFromLlmResponsePreservesSpecialCharacters() {
        Object result = TuneUtils.parseJsonFromLlmResponse("```json\n{\"text\":\"Hello\\nWorld\"}\n```");

        assertInstanceOf(Map.class, result);
        assertEquals("Hello\nWorld", ((Map<?, ?>) result).get("text"));
    }

    @Test
    void parseJsonFromLlmResponsePreservesNullValues() {
        Object result = TuneUtils.parseJsonFromLlmResponse("```json\n{\"key\":null}\n```");

        assertInstanceOf(Map.class, result);
        assertTrue(((Map<?, ?>) result).containsKey("key"));
        assertNull(((Map<?, ?>) result).get("key"));
    }

    @Test
    void parseListFromLlmResponseReturnsOnlyLists() {
        assertEquals(List.of(1, 2, 3), TuneUtils.parseListFromLlmResponse("```list\n[1, 2, 3]\n```"));
        assertNull(TuneUtils.parseListFromLlmResponse("```list\n{\"key\": \"value\"}\n```"));
    }

    @Test
    void parseListFromLlmResponseRejectsDictPayload() {
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
    void parseListFromLlmResponseRejectsInvalidAndEmptyBlocks() {
        assertNull(TuneUtils.parseListFromLlmResponse("```list\nnot a list\n```"));
        assertNull(TuneUtils.parseListFromLlmResponse("```list\n```"));
    }

    @Test
    void parseListFromLlmResponseHandlesNestedLists() {
        assertEquals(
                List.of(List.of(1, 2), List.of(3, 4)),
                TuneUtils.parseListFromLlmResponse("```list\n[[1, 2], [3, 4]]\n```")
        );
    }

    @Test
    void parseListFromLlmResponseHandlesMixedTypes() {
        List<Object> result = TuneUtils.parseListFromLlmResponse("```list\n[1, \"two\", 3.0, true, null]\n```");

        assertNotNull(result);
        assertEquals(1, result.get(0));
        assertEquals("two", result.get(1));
        assertEquals(3.0d, (Double) result.get(2));
        assertEquals(Boolean.TRUE, result.get(3));
        assertNull(result.get(4));
    }

    @Test
    void parseListFromLlmResponseHandlesNestedDicts() {
        List<Object> result = TuneUtils.parseListFromLlmResponse("```list\n[{\"a\":1}, {\"b\":2}]\n```");

        assertNotNull(result);
        assertEquals(Map.of("a", 1), result.get(0));
        assertEquals(Map.of("b", 2), result.get(1));
    }

    @Test
    void parseListFromLlmResponseRejectsStringScalar() {
        assertNull(TuneUtils.parseListFromLlmResponse("```list\n\"not a list\"\n```"));
    }

    @Test
    void parseListFromLlmResponseRejectsNumberScalar() {
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
    void convertCasesToExamplesReturnsEmptyForEmptyCases() {
        assertEquals("", TuneUtils.convertCasesToExamples(List.of()));
    }

    @Test
    void convertCasesToExamplesFormatsSingleCase() {
        Case caseData = new Case(Map.of("query", "hello"), Map.of("answer", "world"));

        String examples = TuneUtils.convertCasesToExamples(List.of(caseData));

        assertTrue(examples.contains("example 1:"));
        assertTrue(examples.contains("[question]:"));
        assertTrue(examples.contains("[expected answer]:"));
        assertTrue(examples.contains("hello"));
        assertTrue(examples.contains("world"));
    }

    @Test
    void convertCasesToExamplesFormatsMultipleCases() {
        List<Case> cases = List.of(
                new Case(Map.of("q", "a"), Map.of("ans", "1")),
                new Case(Map.of("q", "b"), Map.of("ans", "2"))
        );

        String examples = TuneUtils.convertCasesToExamples(cases);

        assertTrue(examples.contains("example 1:"));
        assertTrue(examples.contains("example 2:"));
        assertTrue(examples.contains("[question]:"));
        assertTrue(examples.contains("[expected answer]:"));
    }

    @Test
    void convertCasesToExamplesFormatsComplexInputs() {
        Case caseData = new Case(
                Map.of("query", "test", "context", "info"),
                Map.of("answer", "result", "confidence", 0.9)
        );

        String examples = TuneUtils.convertCasesToExamples(List.of(caseData));

        assertTrue(examples.contains("query:test"));
        assertTrue(examples.contains("context:info"));
        assertTrue(examples.contains("answer:result"));
        assertTrue(examples.contains("confidence:0.9"));
    }

    @Test
    void getInputStringFromCaseFormatsInputMap() {
        Case caseData = new Case(Map.of("query", "hello", "context", "world"), Map.of("answer", "ok"));

        String formatted = TuneUtils.getInputStringFromCase(caseData);

        assertTrue(formatted.contains("query:hello"));
        assertTrue(formatted.contains("context:world"));
    }

    @Test
    void getInputStringFromCaseHandlesMinimalInputMap() {
        Case caseData = new Case(Map.of("q", "?"), Map.of("a", "!"));

        assertEquals("q:?", TuneUtils.getInputStringFromCase(caseData));
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
    void getOutputStringFromMessageSerializesMultipleToolCalls() {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(
                        ToolCall.builder().name("func1").arguments("{}").id("call_1").build(),
                        ToolCall.builder().name("func2").arguments("{}").id("call_2").build()
                ))
                .build();

        String output = TuneUtils.getOutputStringFromMessage(message);

        assertTrue(output.contains("\"name\":\"func1\""));
        assertTrue(output.contains("\"name\":\"func2\""));
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
    void getOutputStringFromMessageReturnsAssistantContentWithoutToolCalls() {
        assertEquals(
                "Hello, world!",
                TuneUtils.getOutputStringFromMessage(AssistantMessage.builder().content("Hello, world!").build())
        );
    }

    @Test
    void getOutputStringFromMessageReturnsUserContent() {
        assertEquals(
                "Test message",
                TuneUtils.getOutputStringFromMessage(UserMessage.builder().content("Test message").build())
        );
    }

    @Test
    void getOutputStringFromMessageReturnsSystemContent() {
        assertEquals(
                "You are a helpful assistant.",
                TuneUtils.getOutputStringFromMessage(SystemMessage.builder().content("You are a helpful assistant.").build())
        );
    }

    @Test
    void getOutputStringFromMessagePreservesEmptyContent() {
        assertEquals("", TuneUtils.getOutputStringFromMessage(AssistantMessage.builder().content("").build()));
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
    void getContentStringFromTemplateHandlesSingleMessage() {
        PromptTemplate template = PromptTemplate.builder()
                .content(List.of(SystemMessage.builder().content("Only one.").build()))
                .build();

        assertEquals("Only one.", TuneUtils.getContentStringFromTemplate(template));
    }

    @Test
    void getContentStringFromTemplateReturnsEmptyForEmptyTemplate() {
        PromptTemplate template = PromptTemplate.builder()
                .content(List.of())
                .build();

        assertEquals("", TuneUtils.getContentStringFromTemplate(template));
    }

    @Test
    void getContentStringFromTemplatePreservesSpecialCharacters() {
        PromptTemplate template = PromptTemplate.builder()
                .content(List.of(UserMessage.builder().content("Line1\nLine2\tTab").build()))
                .build();

        String content = TuneUtils.getContentStringFromTemplate(template);

        assertTrue(content.contains("Line1"));
        assertTrue(content.contains("Line2\tTab"));
    }
}
