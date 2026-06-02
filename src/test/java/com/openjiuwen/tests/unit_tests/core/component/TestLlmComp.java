/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.workflow.component.llm.LLMCompConfig;
import com.openjiuwen.core.workflow.component.llm.LLMComponent;
import com.openjiuwen.core.workflow.component.llm.LLMExecutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_llm_comp} in
 * {@code tests.unit_tests.core.component.test_llm_comp}.
 */
@Tag("unit-test")
class TestLlmComp {

    private static final List<Object> CAPTURED_MESSAGES = new ArrayList<>();

    @BeforeEach
    void clearCapturedMessages() {
        CAPTURED_MESSAGES.clear();
    }

    @Test
    @DisplayName("invoke returns formatted text response")
    void testInvokeSuccess() {
        LLMExecutable executable = executable("LLMFakeInvoke", "hello", List.of());

        Object output = executable.invoke(Map.of("query", "hi"), null, null);

        assertEquals(Map.of("response", "hello"), output);
        assertEquals(1, CAPTURED_MESSAGES.size());
    }

    @Test
    @DisplayName("stream returns formatted chunks")
    void testStreamSuccess() {
        LLMExecutable executable = executable("LLMFakeStream", "unused", List.of("he", "llo"));

        List<Object> chunks = collect(executable.stream(Map.of("query", "hi"), null, null));

        assertEquals(List.of(Map.of("response", "he"), Map.of("response", "llo")), chunks);
    }

    @Test
    @DisplayName("invoke wraps model exceptions")
    void testInvokeLlmException() {
        registerProvider("LLMFakeError", "unused", List.of(), true);
        LLMExecutable executable = new LLMExecutable(textConfig("LLMFakeError"));

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("query", "hi"), null, null));

        assertEquals(StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED.getCode(), error.getCode());
    }

    @Test
    @DisplayName("LLM component creates executable")
    void testLlmInWorkflow() {
        LLMComponent component = new LLMComponent(textConfig("LLMFakeComponent"));
        registerProvider("LLMFakeComponent", "component ok", List.of(), false);

        assertInstanceOf(LLMExecutable.class, component.getExecutable());
    }

    @Test
    @DisplayName("component invoke can feed an End-style output")
    void testStartLlmEndInWorkflow() {
        LLMExecutable executable = executable("LLMFakeStartEnd", "workflow ok", List.of());

        Object output = executable.invoke(Map.of("content", "hello"), null, null);

        assertEquals("workflow ok", ((Map<?, ?>) output).get("response"));
    }

    @Test
    @DisplayName("stream writer path with cache returns final output")
    void testRealWorkflowAgentStreamStartLlmEndWithStreamWriter() {
        LLMCompConfig config = textConfig("LLMFakeCacheStream");
        config.setCacheStream(true);
        registerProvider("LLMFakeCacheStream", "unused", List.of("a", "b"), false);
        LLMExecutable executable = new LLMExecutable(config);

        collect(executable.stream(Map.of("query", "hi"), null, null));

        assertEquals(Map.of("response", "ab"), executable.getStreamOutput());
    }

    @Test
    @DisplayName("invoke path produces text result")
    void testRealWorkflowInvokeStartLlmEndWithStreamWriter() {
        LLMExecutable executable = executable("LLMFakeInvokeWorkflow", "invoke ok", List.of());

        assertEquals(Map.of("response", "invoke ok"), executable.invoke(Map.of(), null, null));
    }

    @Test
    @DisplayName("invoke path supports json output")
    void testRealWorkflowInvokeStartLlmEndWithJsonOutput() {
        LLMCompConfig config = jsonConfig("LLMFakeJsonInvoke");
        registerProvider("LLMFakeJsonInvoke", "{\"answer\":\"yes\"}", List.of(), false);

        assertEquals(Map.of("answer", "yes"), new LLMExecutable(config).invoke(Map.of(), null, null));
    }

    @Test
    @DisplayName("component streaming produces one map per chunk")
    void testRealWorkflowStreamStartLlmEndWithComponentStreaming() {
        LLMExecutable executable = executable("LLMFakeComponentStreaming", "unused", List.of("one", "two"));

        assertEquals(List.of(Map.of("response", "one"), Map.of("response", "two")),
                collect(executable.stream(Map.of(), null, null)));
    }

    @Test
    @DisplayName("component streaming with json output uses invoke-style parsing")
    void testRealWorkflowStreamStartLlmEndWithComponentStreamingWithJsonOutputSchema() {
        LLMCompConfig config = jsonConfig("LLMFakeJsonStream");
        config.setCacheStream(true);
        registerProvider("LLMFakeJsonStream", "{\"answer\":\"json\"}", List.of("ignored"), false);

        List<Object> chunks = collect(new LLMExecutable(config).stream(Map.of(), null, null));

        assertEquals(List.of(Map.of("answer", "json")), chunks);
    }

    @Test
    @DisplayName("agent invoke style uses same executable output")
    void testRealWorkflowAgentInvokeStartLlmEndWithStreamWriter() {
        LLMExecutable executable = executable("LLMFakeAgentInvoke", "agent invoke", List.of());

        assertEquals(Map.of("response", "agent invoke"), executable.invoke(Map.of("query", "x"), null, null));
    }

    @Test
    @DisplayName("system prompt without user prompt appends empty user")
    void testSystemExistsUserMissingAppendsEmptyUser() {
        LLMCompConfig config = textConfig("LLMFakeSystemOnly");
        config.setTemplateContent(List.of(Map.of("role", "system", "content", "sys")));
        registerProvider("LLMFakeSystemOnly", "ok", List.of(), false);

        new LLMExecutable(config).invoke(Map.of(), null, null);

        List<?> messages = capturedMessages();
        assertEquals("system", ((BaseMessage) messages.get(0)).getRole());
        assertEquals("user", ((BaseMessage) messages.get(1)).getRole());
        assertEquals("", ((BaseMessage) messages.get(1)).getContent());
    }

    @Test
    @DisplayName("system and user prompts are preserved")
    void testSystemExistsUserExistsKeepsUser() {
        LLMCompConfig config = textConfig("LLMFakeSystemUser");
        config.setTemplateContent(List.of(
                Map.of("role", "system", "content", "sys"),
                Map.of("role", "user", "content", "usr")));
        registerProvider("LLMFakeSystemUser", "ok", List.of(), false);

        new LLMExecutable(config).invoke(Map.of(), null, null);

        List<?> messages = capturedMessages();
        assertEquals("sys", ((BaseMessage) messages.get(0)).getContent());
        assertEquals("usr", ((BaseMessage) messages.get(1)).getContent());
    }

    @Test
    @DisplayName("user prompt without system prompt is kept")
    void testSystemMissingUserExistsKeepsUser() {
        LLMCompConfig config = textConfig("LLMFakeUserOnly");
        config.setTemplateContent(List.of(Map.of("role", "user", "content", "usr")));
        registerProvider("LLMFakeUserOnly", "ok", List.of(), false);

        new LLMExecutable(config).invoke(Map.of(), null, null);

        List<?> messages = capturedMessages();
        assertEquals(1, messages.size());
        assertEquals("usr", ((BaseMessage) messages.get(0)).getContent());
    }

    @Test
    @DisplayName("explicit system and user prompt templates are both used")
    void testPrepareModelInputsSystemAndUserKeepsBoth() {
        LLMCompConfig config = textConfig("LLMFakePromptTemplates");
        config.setSystemPromptTemplate(new SystemMessage("sys template"));
        config.setUserPromptTemplate(new UserMessage("user template"));
        registerProvider("LLMFakePromptTemplates", "ok", List.of(), false);

        new LLMExecutable(config).invoke(Map.of(), null, null);

        List<?> messages = capturedMessages();
        assertEquals("sys template", ((BaseMessage) messages.get(0)).getContent());
        assertEquals("user template", ((BaseMessage) messages.get(1)).getContent());
    }

    @Test
    @DisplayName("missing system and user defaults to empty user message")
    void testPrepareModelInputsSystemMissingUserMissing() {
        LLMExecutable executable = executable("LLMFakeNoPrompts", "ok", List.of());

        executable.invoke(Map.of(), null, null);

        List<?> messages = capturedMessages();
        assertEquals(1, messages.size());
        assertEquals("user", ((BaseMessage) messages.get(0)).getRole());
    }

    @Test
    @DisplayName("empty system template plus missing user appends empty user")
    void testTemplateEmptySystemExistsUserMissingAddsEmptyUser() {
        LLMCompConfig config = textConfig("LLMFakeEmptySystemOnly");
        config.setSystemPromptTemplate(new SystemMessage(""));
        registerProvider("LLMFakeEmptySystemOnly", "ok", List.of(), false);

        new LLMExecutable(config).invoke(Map.of(), null, null);

        assertEquals(2, capturedMessages().size());
    }

    @Test
    @DisplayName("empty system missing plus user template keeps user")
    void testTemplateEmptySystemMissingUserExistsKeepsUser() {
        LLMCompConfig config = textConfig("LLMFakeUserTemplateOnly");
        config.setUserPromptTemplate(new UserMessage("user only"));
        registerProvider("LLMFakeUserTemplateOnly", "ok", List.of(), false);

        new LLMExecutable(config).invoke(Map.of(), null, null);

        assertEquals("user only", ((BaseMessage) capturedMessages().get(0)).getContent());
    }

    @Test
    @DisplayName("empty system and user prompt templates keep both")
    void testTemplateEmptySystemAndUserExistsKeepsBoth() {
        LLMCompConfig config = textConfig("LLMFakeEmptyBoth");
        config.setSystemPromptTemplate(new SystemMessage(""));
        config.setUserPromptTemplate(new UserMessage(""));
        registerProvider("LLMFakeEmptyBoth", "ok", List.of(), false);

        new LLMExecutable(config).invoke(Map.of(), null, null);

        assertEquals(2, capturedMessages().size());
    }

    @Test
    @DisplayName("missing template content creates an empty user message")
    void testTemplateMissingSystemMissingUserMissing() {
        LLMExecutable executable = executable("LLMFakeMissingTemplates", "ok", List.of());

        executable.invoke(Map.of(), null, null);

        assertEquals("", ((BaseMessage) capturedMessages().get(0)).getContent());
    }

    @Test
    @DisplayName("empty template content creates an empty user message")
    void testTemplateEmptySystemMissingUserMissing() {
        LLMCompConfig config = textConfig("LLMFakeEmptyTemplateList");
        config.setTemplateContent(new ArrayList<>());
        registerProvider("LLMFakeEmptyTemplateList", "ok", List.of(), false);

        new LLMExecutable(config).invoke(Map.of(), null, null);

        assertEquals("", ((BaseMessage) capturedMessages().get(0)).getContent());
    }

    @Test
    @DisplayName("workflow with stream consumer has no final output when cache is disabled")
    void testWorkflowWithStreamConsumer() {
        LLMExecutable executable = executable("LLMFakeNoCache", "unused", List.of("a"));

        collect(executable.stream(Map.of(), null, null));

        assertNull(executable.getStreamOutput());
    }

    @Test
    @DisplayName("static template style output is formatted as text")
    void testWorkflowWithStaticTemplate() {
        LLMExecutable executable = executable("LLMFakeStaticTemplate", "static response", List.of());

        assertEquals("static response",
                ((Map<?, ?>) executable.invoke(Map.of("query", "x"), null, null)).get("response"));
    }

    @Test
    @DisplayName("invalid template order is rejected")
    void testInvalidTemplateOrderRejected() {
        LLMCompConfig config = textConfig("LLMFakeInvalidTemplate");
        config.setTemplateContent(List.of(
                Map.of("role", "user", "content", "usr"),
                Map.of("role", "system", "content", "sys")));

        assertThrows(BaseError.class, () -> new LLMExecutable(config));
    }

    @Test
    @DisplayName("invalid response format is rejected")
    void testInvalidResponseFormatRejected() {
        LLMCompConfig config = textConfig("LLMFakeInvalidResponse");
        config.setResponseFormat(Map.of("type", "xml"));

        assertThrows(BaseError.class, () -> new LLMExecutable(config));
    }

    private static LLMExecutable executable(String provider, String invokeContent, List<String> streamChunks) {
        registerProvider(provider, invokeContent, streamChunks, false);
        return new LLMExecutable(textConfig(provider));
    }

    private static LLMCompConfig textConfig(String provider) {
        LLMCompConfig config = new LLMCompConfig();
        config.setModelClientConfig(clientConfig(provider));
        config.setModelConfig(ModelRequestConfig.builder().modelName("fake-model").build());
        config.setResponseFormat(Map.of("type", "text"));
        config.setOutputConfig(Map.of("response", Map.of("type", "string")));
        return config;
    }

    private static LLMCompConfig jsonConfig(String provider) {
        LLMCompConfig config = textConfig(provider);
        config.setResponseFormat(Map.of("type", "json"));
        config.setOutputConfig(Map.of("answer", Map.of("type", "string")));
        return config;
    }

    private static ModelClientConfig clientConfig(String provider) {
        return ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey("sk-fake")
                .apiBase("mock://api.openai.com/v1")
                .verifySsl(false)
                .build();
    }

    private static void registerProvider(String provider, String invokeContent,
                                         List<String> streamChunks, boolean throwOnInvoke) {
        Model.registerFactory(new Model.ModelClientFactory() {
            @Override
            public String providerName() {
                return provider;
            }

            @Override
            public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                return new FakeModelClient(modelConfig, clientConfig, invokeContent, streamChunks, throwOnInvoke);
            }
        });
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static List<?> capturedMessages() {
        assertFalse(CAPTURED_MESSAGES.isEmpty());
        return (List<?>) CAPTURED_MESSAGES.get(CAPTURED_MESSAGES.size() - 1);
    }

    private static class FakeModelClient extends BaseModelClient {
        private final String invokeContent;
        private final List<String> streamChunks;
        private final boolean throwOnInvoke;

        FakeModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig,
                        String invokeContent, List<String> streamChunks, boolean throwOnInvoke) {
            super(modelConfig, clientConfig);
            this.invokeContent = invokeContent;
            this.streamChunks = streamChunks;
            this.throwOnInvoke = throwOnInvoke;
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                                       Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) throws Exception {
            CAPTURED_MESSAGES.add(messages);
            if (throwOnInvoke) {
                throw new Exception("model failed");
            }
            return AssistantMessage.builder().role("assistant").content(invokeContent).build();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                                                      String model, Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            CAPTURED_MESSAGES.add(messages);
            List<AssistantMessageChunk> chunks = new ArrayList<>();
            for (String chunk : streamChunks) {
                chunks.add(AssistantMessageChunk.builder().role("assistant").content(chunk).build());
            }
            return chunks.iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                                                     String negativePrompt, int n, boolean promptExtend,
                                                     boolean watermark, int seed, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                                                      String languageType, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                                                     String model, String size, String resolution, int duration,
                                                     boolean promptExtend, boolean watermark, String negativePrompt,
                                                     Integer seed, Map<String, Object> kwargs) {
            return null;
        }
    }
}
