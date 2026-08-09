/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supplemental parity tests for LLM workflow component behavior.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/component/test_llm_comp.py}.</p>
 */
class LLMComponentMissingTest {

    private static final String PROVIDER = "OpenAI";
    private Map<String, Object> originalFactories;
    private Map<String, Object> originalInvokers;

    @BeforeEach
    void setUp() throws Exception {
        originalFactories = new LinkedHashMap<>(modelClientFactories());
        originalInvokers = new LinkedHashMap<>(modelInvokers());
        Model.registerClientFactory(PROVIDER, (clientConfig, requestConfig) -> new FakeModelClient());
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreMap(modelClientFactories(), originalFactories);
        restoreMap(modelInvokers(), originalInvokers);
    }

    @Test
    void testInvokeSuccess() {
        LLMExecutable executable = new LLMExecutable(defaultConfig("result"));

        Object output = executable.invoke(fakeInput("pytest"), workflowSession(), null);

        assertEquals(Map.of("result", "mocked response"), output);
    }

    @Test
    void testStreamSuccess() {
        LLMExecutable executable = new LLMExecutable(defaultConfig("result"));

        List<Object> chunks = collectValues(executable.stream(fakeInput("pytest"), workflowSession(), null));

        assertFalse(chunks.isEmpty());
        assertEquals(Map.of("result", "Hello"), chunks.get(0));
    }

    @Test
    void testInvokeLlmException() {
        LLMCompConfig config = new LLMCompConfig();
        config.setModelClientConfig(fakeModelClientConfig());
        config.setModelConfig(fakeModelConfig());
        config.setTemplateContent(List.of(Map.of("role", "user", "content", "Hello {name}")));
        config.setResponseFormat(Map.of("type", "text"));

        BaseError error = assertThrows(BaseError.class, () -> new LLMExecutable(config));

        assertEquals(StatusCode.COMPONENT_LLM_RESPONSE_CONFIG_INVALID, error.getStatus());
    }

    @Test
    void testLlmInWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"));
        workflow.addWorkflowComp("llm", new LLMComponent(defaultConfig("result")),
                Map.of("userFields", Map.of("query", "${start.query}")));
        workflow.setEndComp("end", new End(), Map.of("response", "${llm.result}"));
        workflow.addConnection("start", "llm");
        workflow.addConnection("llm", "end");

        WorkflowOutput result = workflow.invoke(Map.of("query", "pytest"), workflowSession(), null);

        assertNotNull(result);
    }

    @Test
    void testStartLlmEndInWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setStartComp("s", new Start(), Map.of("query", "${query}"));
        workflow.setEndComp("e", new End(new EndConfig("{{output}}")), Map.of("output", "${llm.output}"));
        workflow.addWorkflowComp("llm", new LLMComponent(defaultConfig("output")),
                Map.of("userFields", Map.of("query", "${s.query}")));
        workflow.addConnection("s", "llm");
        workflow.addConnection("llm", "e");

        WorkflowOutput result = workflow.invoke(Map.of("query", "yzq test query"), workflowSession(), null);

        assertNotNull(result);
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testRealWorkflowAgentStreamStartLlmEndWithStreamWriter() {
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testRealWorkflowInvokeStartLlmEndWithStreamWriter() {
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testRealWorkflowInvokeStartLlmEndWithJsonOutput() {
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testRealWorkflowStreamStartLlmEndWithComponentStreaming() {
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testRealWorkflowStreamStartLlmEndWithComponentStreamingWithJsonOutputSchema() {
    }

    @Disabled("Skipped in Python source: skip system test")
    @Test
    void testRealWorkflowAgentInvokeStartLlmEndWithStreamWriter() {
    }

    @Test
    void testSystemExistsUserMissingAppendsEmptyUser() {
        LLMCompConfig config = defaultConfig("result");
        config.setSystemPromptTemplate(SystemMessage.builder().content("system prompt template").build());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertInstanceOf(SystemMessage.class, modelInputs.get(0));
        assertEquals("system prompt template", modelInputs.get(0).getContent());
        assertInstanceOf(UserMessage.class, modelInputs.get(1));
        assertEquals("", modelInputs.get(1).getContent());
    }

    @Test
    void testSystemExistsUserExistsKeepsUser() {
        LLMCompConfig config = defaultConfig("result");
        config.setSystemPromptTemplate(SystemMessage.builder().content("system prompt template").build());
        config.setUserPromptTemplate(UserMessage.builder().content("user prompt template").build());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertInstanceOf(SystemMessage.class, modelInputs.get(0));
        assertEquals("system prompt template", modelInputs.get(0).getContent());
        assertInstanceOf(UserMessage.class, modelInputs.get(1));
        assertEquals("user prompt template", modelInputs.get(1).getContent());
    }

    @Test
    void testSystemMissingUserExistsKeepsUser() {
        LLMCompConfig config = defaultConfig("result");
        config.setUserPromptTemplate(UserMessage.builder().content("user prompt template").build());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertEquals(1, modelInputs.size());
        assertInstanceOf(UserMessage.class, modelInputs.get(0));
        assertEquals("user prompt template", modelInputs.get(0).getContent());
    }

    @Test
    void testPrepareModelInputsSystemAndUserKeepsBoth() {
        LLMCompConfig config = defaultConfig("result");
        config.setSystemPromptTemplate(SystemMessage.builder().content("system prompt template").build());
        config.setUserPromptTemplate(UserMessage.builder().content("user prompt template").build());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertInstanceOf(SystemMessage.class, modelInputs.get(0));
        assertEquals("system prompt template", modelInputs.get(0).getContent());
        assertInstanceOf(UserMessage.class, modelInputs.get(1));
        assertEquals("user prompt template", modelInputs.get(1).getContent());
    }

    @Test
    void testPrepareModelInputsSystemMissingUserMissing() {
        LLMCompConfig config = defaultConfig("result");
        config.setTemplateContent(List.of(
                Map.of("role", "system", "content", "hello {query}"),
                Map.of("role", "user", "content", "Hello {query}")
        ));

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertInstanceOf(BaseMessage.class, modelInputs.get(0));
        assertEquals("hello {query}", modelInputs.get(0).getContent());
        assertInstanceOf(BaseMessage.class, modelInputs.get(1));
        assertEquals("Hello {query}", modelInputs.get(1).getContent());
    }

    @Test
    void testTemplateEmptySystemExistsUserMissingAddsEmptyUser() {
        LLMCompConfig config = defaultConfig("result");
        config.setTemplateContent(List.of());
        config.setSystemPromptTemplate(SystemMessage.builder().content("system prompt template").build());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertEquals(2, modelInputs.size());
        assertInstanceOf(SystemMessage.class, modelInputs.get(0));
        assertEquals("system prompt template", modelInputs.get(0).getContent());
        assertInstanceOf(UserMessage.class, modelInputs.get(1));
        assertEquals("", modelInputs.get(1).getContent());
    }

    @Test
    void testTemplateEmptySystemMissingUserExistsKeepsUser() {
        LLMCompConfig config = defaultConfig("result");
        config.setTemplateContent(List.of());
        config.setUserPromptTemplate(UserMessage.builder().content("user prompt template").build());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertEquals(1, modelInputs.size());
        assertInstanceOf(UserMessage.class, modelInputs.get(0));
        assertEquals("user prompt template", modelInputs.get(0).getContent());
    }

    @Test
    void testTemplateEmptySystemAndUserExistsKeepsBoth() {
        LLMCompConfig config = defaultConfig("result");
        config.setTemplateContent(List.of());
        config.setSystemPromptTemplate(SystemMessage.builder().content("system prompt template").build());
        config.setUserPromptTemplate(UserMessage.builder().content("user prompt template").build());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertEquals(2, modelInputs.size());
        assertInstanceOf(SystemMessage.class, modelInputs.get(0));
        assertEquals("system prompt template", modelInputs.get(0).getContent());
        assertInstanceOf(UserMessage.class, modelInputs.get(1));
        assertEquals("user prompt template", modelInputs.get(1).getContent());
    }

    @Test
    void testTemplateMissingSystemMissingUserMissing() {
        LLMCompConfig config = defaultConfig("result");
        config.setTemplateContent(new ArrayList<>());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertEquals(1, modelInputs.size());
        assertInstanceOf(UserMessage.class, modelInputs.get(0));
    }

    @Test
    void testTemplateEmptySystemMissingUserMissing() {
        LLMCompConfig config = defaultConfig("result");
        config.setTemplateContent(List.of());

        List<BaseMessage> modelInputs = prepareModelInputs(new LLMExecutable(config));

        assertEquals(1, modelInputs.size());
        assertInstanceOf(UserMessage.class, modelInputs.get(0));
    }

    @Test
    void testWorkflowWithStreamConsumer() {
        LLMCompConfig config = defaultConfig("result");
        config.setCacheStream(true);
        LLMExecutable executable = new LLMExecutable(config);
        Iterator<Object> stream = executable.stream(fakeInput("Hello"), workflowSession(), null);

        Object collected = new StreamConsumerComponent().collect(Map.of("result", stream), workflowSession(), null);
        Map<String, Object> output = executable.getStreamOutput();

        assertEquals(Map.of("chunks_count", 9, "total_length", 41), collected);
        assertEquals("Hello world! This is a streamed response.", output.get("result"));
    }

    @Test
    void testWorkflowWithStaticTemplate() {
        MockLLMComponent llm = new MockLLMComponent();
        End end = new End(new EndConfig("this is end result"));
        List<Object> llmChunks = collectValues(llm.stream(Map.of(), workflowSession(), null));
        List<Object> endChunks = collectValues(end.transform(Map.of("a", llmChunks.iterator()), workflowSession(), null));

        assertEquals(List.of(
                Map.of("output", "chunk 0"),
                Map.of("output", "chunk 1"),
                Map.of("output", "chunk 2")
        ), llmChunks);
        assertEquals(1, endChunks.size());
        OutputSchema output = assertInstanceOf(OutputSchema.class, endChunks.get(0));
        assertEquals(Map.of("response", "this is end result"), output.getPayload());
    }

    private static LLMCompConfig defaultConfig(String outputField) {
        LLMCompConfig config = new LLMCompConfig();
        config.setModelClientConfig(fakeModelClientConfig());
        config.setModelConfig(fakeModelConfig());
        config.setTemplateContent(List.of(Map.of("role", "user", "content", "Hello {query}")));
        config.setResponseFormat(Map.of("type", "text"));
        config.setOutputConfig(Map.of(outputField, Map.of("type", "string", "required", true)));
        return config;
    }

    private static ModelClientConfig fakeModelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(PROVIDER)
                .apiKey("sk-fake")
                .apiBase("http://fake.api.com")
                .timeout(30)
                .maxRetries(3)
                .verifySsl(false)
                .build();
    }

    private static ModelRequestConfig fakeModelConfig() {
        return ModelRequestConfig.builder()
                .modelName("gpt-3.5-turbo")
                .temperature(0.8)
                .topP(0.9)
                .build();
    }

    private static Map<String, Object> fakeInput(String query) {
        return Map.of("userFields", Map.of("query", query));
    }

    private static WorkflowSession workflowSession() {
        return new WorkflowSession();
    }

    @SuppressWarnings("unchecked")
    private static List<BaseMessage> prepareModelInputs(LLMExecutable executable) {
        try {
            Method method = LLMExecutable.class.getDeclaredMethod("prepareModelInputs", Object.class);
            method.setAccessible(true);
            return (List<BaseMessage>) method.invoke(executable, fakeInput("pytest"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<Object> collectValues(Iterator<?> iterator) {
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> modelClientFactories() throws Exception {
        Field field = Model.class.getDeclaredField("CLIENT_FACTORIES");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> modelInvokers() throws Exception {
        Field field = Model.class.getDeclaredField("INVOKERS");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(null);
    }

    private static void restoreMap(Map<String, Object> target, Map<String, Object> original) {
        target.clear();
        target.putAll(original);
    }

    /**
     * Fake model client used by Java tests to mirror Python's {@code FakeModel} in
     * {@code tests/unit_tests/core/component/test_llm_comp.py}.
     */
    private static final class FakeModelClient implements Model.ModelClient {
        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("mocked response"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            List<AssistantMessageChunk> chunks = new ArrayList<>();
            for (String content : List.of("Hello", " ", "world", "!", " This", " is", " a", " streamed", " response.")) {
                chunks.add((AssistantMessageChunk) AssistantMessageChunk.builder().role("assistant").content(content).build());
            }
            return chunks.iterator();
        }
    }

    /**
     * Stream consumer helper mirroring Python's {@code StreamConsumerComponent} in
     * {@code tests/unit_tests/core/component/test_llm_comp.py}.
     */
    private static final class StreamConsumerComponent extends WorkflowComponent<Object, Object> {
        @Override
        @SuppressWarnings("unchecked")
        public Object collect(Object inputs, BaseSession session, ModelContext context) {
            Iterator<Object> streamInput = (Iterator<Object>) ((Map<String, Object>) inputs).get("result");
            List<Object> collectedChunks = LLMComponentMissingTest.collectValues(streamInput);
            int totalLength = collectedChunks.stream()
                    .map(Map.class::cast)
                    .map(map -> map.get("result"))
                    .map(String::valueOf)
                    .mapToInt(String::length)
                    .sum();
            return Map.of("chunks_count", collectedChunks.size(), "total_length", totalLength);
        }
    }

    /**
     * Mock LLM component mirroring Python's {@code MockLLMComponent} in
     * {@code tests/unit_tests/core/component/test_llm_comp.py}.
     */
    private static final class MockLLMComponent extends WorkflowComponent<Object, Object> {
        @Override
        public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
            return List.<Object>of(
                    Map.of("output", "chunk 0"),
                    Map.of("output", "chunk 1"),
                    Map.of("output", "chunk 2")
            ).iterator();
        }

        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            return Map.of("output", "llm");
        }
    }
}
