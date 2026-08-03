/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for {@link BaseModelClient}.
 *
 * <p>Mirrors Python's {@code BaseModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/base_model_client.py}.</p>
 */
class BaseModelClientTest {

    @Test
    void validateConfigRequiresApiKey() {
        ModelClientConfig config = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiBase("http://localhost")
                .verifySsl(false)
                .build();

        BaseError error = assertThrows(BaseError.class, () -> new TestModelClient(requestConfig(), config));

        assertTrue(error.getMessage().contains("api_key is required for TestModelClient"));
        assertEquals("model client config api_key is required for TestModelClient.",
                error.getParams().get("error_msg"));
    }

    @Test
    void validateConfigPreservesPythonApiBaseTypo() {
        ModelClientConfig config = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .verifySsl(false)
                .build();

        BaseError error = assertThrows(BaseError.class, () -> new TestModelClient(requestConfig(), config));

        assertTrue(error.getMessage().contains("<missing:error_msg>"));
        assertEquals("model client config api_base is required for TestModelClient.",
                error.getParams().get("rror_msg"));
    }

    @Test
    void validateConfigAllowsDefaultTrustStoreWhenVerifySslIsTrue() {
        ModelClientConfig config = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("https://localhost")
                .verifySsl(true)
                .build();

        assertDoesNotThrow(() -> new TestModelClient(requestConfig(), config));
    }

    @Test
    void convertMessagesMatchesPythonShapes() {
        List<Map<String, Object>> textMessages = BaseModelClient.convertMessagesToDict("hello");
        assertEquals("user", textMessages.get(0).get("role"));
        assertEquals("hello", textMessages.get(0).get("content"));

        AssistantMessage assistant = AssistantMessage.builder()
                .content("answer")
                .reasoningContent("because")
                .toolCalls(List.of(ToolCall.builder()
                        .id("call-1")
                        .type("function")
                        .name("search")
                        .arguments("{\"query\":\"java\"}")
                        .build()))
                .build();
        ToolMessage toolMessage = new ToolMessage("tool-result", "call-1");

        List<Map<String, Object>> converted = BaseModelClient.convertMessagesToDict(List.of(assistant, toolMessage));
        Map<String, Object> assistantDict = converted.get(0);
        assertEquals("assistant", assistantDict.get("role"));
        assertEquals("answer", assistantDict.get("content"));
        assertEquals("because", assistantDict.get("reasoning_content"));

        List<?> toolCalls = (List<?>) assistantDict.get("tool_calls");
        Map<?, ?> firstToolCall = (Map<?, ?>) toolCalls.get(0);
        Map<?, ?> function = (Map<?, ?>) firstToolCall.get("function");
        assertEquals("call-1", firstToolCall.get("id"));
        assertEquals("search", function.get("name"));
        assertEquals("{\"query\":\"java\"}", function.get("arguments"));

        assertEquals("tool", converted.get(1).get("role"));
        assertEquals("call-1", converted.get(1).get("tool_call_id"));
        assertThrows(BaseError.class, () -> BaseModelClient.convertMessagesToDict(List.of()));
        assertThrows(BaseError.class, () -> BaseModelClient.convertMessagesToDict(""));
    }

    @Test
    void convertToolsMatchesOpenAiFunctionShape() {
        ToolInfo toolInfo = ToolInfo.builder()
                .type("function")
                .name("lookup")
                .description("Lookup data")
                .parameters(Map.of("type", "object"))
                .build();

        List<Map<String, Object>> converted = BaseModelClient.convertToolsToDict(List.of(toolInfo));

        assertEquals("function", converted.get(0).get("type"));
        Map<?, ?> function = (Map<?, ?>) converted.get(0).get("function");
        assertEquals("lookup", function.get("name"));
        assertEquals("Lookup data", function.get("description"));
        assertEquals(Map.of("type", "object"), function.get("parameters"));
        assertEquals(converted, BaseModelClient.convertToolsToDict(converted));
        assertEquals(null, BaseModelClient.convertToolsToDict(List.of()));
    }

    @Test
    void buildRequestParamsMergesConfigToolsAndKwargsInPythonOrder() {
        ModelRequestConfig modelConfig = ModelRequestConfig.builder()
                .modelName("configured-model")
                .maxTokens(128)
                .stop("DONE")
                .extraFields(new LinkedHashMap<>(Map.of(
                        "presence_penalty", 0.2D,
                        "return_token_ids", true
                )))
                .build();
        TestModelClient client = new TestModelClient(modelConfig, validClientConfig());
        ToolInfo toolInfo = ToolInfo.builder()
                .name("lookup")
                .description("Lookup data")
                .parameters(Map.of("type", "object"))
                .build();
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("presence_penalty", 0.4D);
        kwargs.put("output_parser", "internal");
        kwargs.put("custom", "value");

        Map<String, Object> params = client.requestParams(
                "hello",
                List.of(toolInfo),
                null,
                null,
                "",
                null,
                null,
                true,
                kwargs);

        assertEquals("configured-model", params.get("model"));
        assertEquals(true, params.get("stream"));
        assertEquals(0.95D, params.get("temperature"));
        assertEquals(0.1D, params.get("top_p"));
        assertEquals(128, params.get("max_tokens"));
        assertEquals("DONE", params.get("stop"));
        assertEquals("auto", params.get("tool_choice"));
        assertEquals(true, params.get("return_token_ids"));
        assertEquals(0.4D, params.get("presence_penalty"));
        assertEquals("value", params.get("custom"));
        assertFalse(params.containsKey("output_parser"));

        List<?> tools = (List<?>) params.get("tools");
        assertEquals(1, tools.size());
        List<?> messages = (List<?>) params.get("messages");
        assertEquals("hello", ((Map<?, ?>) messages.get(0)).get("content"));
    }

    @Test
    void buildRequestParamsFallsBackInvalidTypedToolCallsWithoutChangingToolCallObjects() {
        String validRaw = " { \"b\" : 2, \"a\" : 1 } ";
        ToolCall valid = ToolCall.builder().id("call-valid").name("lookup").arguments(validRaw).build();
        ToolCall malformed = ToolCall.builder().id("call-malformed").name("lookup")
                .arguments("{\"query\":[1,2").build();
        ToolCall array = ToolCall.builder().id("call-array").name("lookup").arguments("[]").build();
        AssistantMessage assistant = AssistantMessage.builder().content("")
                .toolCalls(List.of(valid, malformed, array)).build();
        TestModelClient client = new TestModelClient(requestConfig(), validClientConfig());

        Map<String, Object> params = client.requestParams(
                List.of(assistant), null, null, null, null, null, null, false, Map.of());

        assertEquals(List.of(validRaw, "{}", "{}"), requestArguments(params));
        assertEquals(validRaw, valid.getArguments());
        assertEquals("{\"query\":[1,2", malformed.getArguments());
        assertEquals("[]", array.getArguments());
    }

    @Test
    void buildRequestParamsFallsBackInvalidMapMessagesWithoutChangingCallerNestedMaps() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "lookup");
        function.put("arguments", "{\"query\": bare}");
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call-map");
        toolCall.put("type", "function");
        toolCall.put("function", function);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", "");
        message.put("tool_calls", List.of(toolCall));
        TestModelClient client = new TestModelClient(requestConfig(), validClientConfig());

        Map<String, Object> params = client.requestParams(
                List.of(message), null, null, null, null, null, null, false, Map.of());

        assertEquals(List.of("{}"), requestArguments(params));
        assertEquals("{\"query\": bare}", function.get("arguments"));
        assertEquals(function, ((Map<?, ?>) toolCall.get("function")));
    }

    @Test
    void buildRequestParamsDoesNotCopyValidMapToolCallArguments() {
        String validRaw = " { \"a\" : 1 } ";
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "lookup");
        function.put("arguments", validRaw);
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call-valid-map");
        toolCall.put("type", "function");
        toolCall.put("function", function);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", "");
        message.put("tool_calls", List.of(toolCall));
        TestModelClient client = new TestModelClient(requestConfig(), validClientConfig());

        Map<String, Object> params = client.requestParams(
                List.of(message), null, null, null, null, null, null, false, Map.of());

        List<Map<String, Object>> requestToolCalls = requestToolCalls(params);
        assertEquals(List.of(validRaw), requestArguments(params));
        assertEquals(validRaw, function.get("arguments"));
        assertSame(toolCall, requestToolCalls.get(0));
        assertSame(function, requestToolCalls.get(0).get("function"));
    }

    @Test
    void buildRequestParamsOnlyCopiesMapToolCallsWithChangedArguments() {
        String validRaw = " { \"a\" : 1 } ";
        Map<String, Object> validFunction = new LinkedHashMap<>();
        validFunction.put("name", "lookup");
        validFunction.put("arguments", validRaw);
        Map<String, Object> validToolCall = new LinkedHashMap<>();
        validToolCall.put("id", "call-valid-map");
        validToolCall.put("type", "function");
        validToolCall.put("function", validFunction);

        Map<String, Object> invalidFunction = new LinkedHashMap<>();
        invalidFunction.put("name", "lookup");
        invalidFunction.put("arguments", "{\"query\": bare}");
        Map<String, Object> invalidToolCall = new LinkedHashMap<>();
        invalidToolCall.put("id", "call-invalid-map");
        invalidToolCall.put("type", "function");
        invalidToolCall.put("function", invalidFunction);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", "");
        message.put("tool_calls", List.of(validToolCall, invalidToolCall));
        TestModelClient client = new TestModelClient(requestConfig(), validClientConfig());

        Map<String, Object> params = client.requestParams(
                List.of(message), null, null, null, null, null, null, false, Map.of());

        List<Map<String, Object>> requestToolCalls = requestToolCalls(params);
        assertEquals(List.of(validRaw, "{}"), requestArguments(params));
        assertEquals(validRaw, validFunction.get("arguments"));
        assertEquals("{\"query\": bare}", invalidFunction.get("arguments"));
        assertSame(validToolCall, requestToolCalls.get(0));
        assertSame(validFunction, requestToolCalls.get(0).get("function"));
        assertNotSame(invalidToolCall, requestToolCalls.get(1));
        assertNotSame(invalidFunction, requestToolCalls.get(1).get("function"));
    }

    @Test
    void buildRequestParamsPreservesPythonDecimalShapeForFloatModelParams() {
        TestModelClient client = new TestModelClient(requestConfig(), validClientConfig());
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("temperature", (double) 0.2f);
        kwargs.put("top_p", 0.4f);

        Map<String, Object> params = client.requestParams(
                "hello",
                null,
                0.2f,
                0.4f,
                null,
                null,
                null,
                false,
                kwargs);

        assertEquals(0.2D, params.get("temperature"));
        assertEquals(0.4D, params.get("top_p"));
    }

    @Test
    void extractCostInfoSupportsNumericObjectsUsageCostAndDetails() {
        BaseModelClient.CostInfo numeric = BaseModelClient.extractCostInfo(Map.of("cost", 1.25D));
        assertEquals(0.0D, numeric.inputCost());
        assertEquals(0.0D, numeric.outputCost());
        assertEquals(1.25D, numeric.totalCost());

        BaseModelClient.CostInfo costObject = BaseModelClient.extractCostInfo(Map.of(
                "cost",
                Map.of("input_cost", 0.4D, "completion_cost", 0.6D)));
        assertEquals(0.4D, costObject.inputCost());
        assertEquals(0.6D, costObject.outputCost());
        assertEquals(1.0D, costObject.totalCost());

        Map<String, Object> usageCostFallback = new LinkedHashMap<>();
        usageCostFallback.put("cost", 0.0D);
        usageCostFallback.put("usage_cost", Map.of("prompt_cost", 0.1D, "output_cost", 0.2D, "total_cost", 0.5D));
        BaseModelClient.CostInfo usageCost = BaseModelClient.extractCostInfo(usageCostFallback);
        assertEquals(0.1D, usageCost.inputCost());
        assertEquals(0.2D, usageCost.outputCost());
        assertEquals(0.5D, usageCost.totalCost());

        BaseModelClient.CostInfo details = BaseModelClient.extractCostInfo(Map.of(
                "cost_details",
                Map.of(
                        "upstream_inference_prompt_cost", 0.7D,
                        "upstream_inference_completions_cost", 0.8D,
                        "upstream_inference_cost", 2.0D)));
        assertIterableEquals(List.of(0.7D, 0.8D, 2.0D),
                List.of(details.inputCost(), details.outputCost(), details.totalCost()));
    }

    @Test
    void textInvokeAndStreamDoNotDeclareCheckedExceptions() throws NoSuchMethodException {
        Method invoke = BaseModelClient.class.getDeclaredMethod(
                "invoke",
                Object.class,
                Object.class,
                Float.class,
                Float.class,
                String.class,
                Integer.class,
                String.class,
                BaseOutputParser.class,
                Float.class,
                Map.class);
        Method stream = BaseModelClient.class.getDeclaredMethod(
                "stream",
                Object.class,
                Object.class,
                Float.class,
                Float.class,
                String.class,
                Integer.class,
                String.class,
                BaseOutputParser.class,
                Float.class,
                Map.class);

        assertEquals(0, invoke.getExceptionTypes().length);
        assertEquals(0, stream.getExceptionTypes().length);
    }

    @Test
    void typedInvokeAndStreamUseProviderExtraFieldExtensionWithoutForwardingListenerByDefault() {
        TestModelClient client = new TestModelClient(requestConfig(), validClientConfig(), true);
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .retryListener(event -> { })
                .requestHeaders(Map.of())
                .extraFields(new LinkedHashMap<>(Map.of("caller_field", "value")))
                .build();

        client.invoke(List.of(new UserMessage("hello")), options).toCompletableFuture().join();
        assertEquals(Map.of("caller_field", "value", "provider_field", "private"), client.lastKwargs);

        client.stream(List.of(new UserMessage("hello")), options);
        assertEquals(Map.of("caller_field", "value", "provider_field", "private"), client.lastKwargs);
    }

    @Test
    void typedInvokeRejectsUnsupportedRequestHeadersWithoutExposingHeaderData() {
        TestModelClient client = new TestModelClient(requestConfig(), validClientConfig());
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .requestHeaders(Map.of("X-Private-Key", "private-value"))
                .build();

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> client.invoke(List.of(new UserMessage("hello")), options));

        assertTrue(error.getMessage().contains(
                "TestModelClient does not support request-level headers"));
        assertFalse(error.getMessage().contains("X-Private-Key"));
        assertFalse(error.getMessage().contains("private-value"));
    }

    @Test
    void typedStreamRejectsUnsupportedRequestHeadersWithoutExposingHeaderData() {
        TestModelClient client = new TestModelClient(requestConfig(), validClientConfig());
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .requestHeaders(Map.of("X-Private-Key", "private-value"))
                .build();

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> client.stream(List.of(new UserMessage("hello")), options));

        assertTrue(error.getMessage().contains(
                "TestModelClient does not support request-level headers"));
        assertFalse(error.getMessage().contains("X-Private-Key"));
        assertFalse(error.getMessage().contains("private-value"));
    }

    private static ModelRequestConfig requestConfig() {
        return ModelRequestConfig.builder().modelName("test-model").build();
    }

    private static ModelClientConfig validClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("sk-test")
                .apiBase("http://localhost")
                .verifySsl(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requestArguments(Map<String, Object> params) {
        return requestToolCalls(params).stream()
                .map(call -> (Object) ((Map<?, ?>) call.get("function")).get("arguments"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> requestToolCalls(Map<String, Object> params) {
        List<Map<String, Object>> messages = (List<Map<String, Object>>) params.get("messages");
        return (List<Map<String, Object>>) messages.get(0).get("tool_calls");
    }

    /**
     * Mirrors Python's local concrete subclasses of {@code BaseModelClient} in
     * {@code openjiuwen/core/foundation/llm/model_clients/base_model_client.py}.
     */
    private static final class TestModelClient extends BaseModelClient {

        private final boolean addProviderField;
        private Map<String, Object> lastKwargs;

        private TestModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            this(modelConfig, modelClientConfig, false);
        }

        private TestModelClient(
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                boolean addProviderField) {
            super(modelConfig, modelClientConfig);
            this.addProviderField = addProviderField;
        }

        @Override
        protected Map<String, Object> invocationExtraFields(ModelInvokeOptions options) {
            Map<String, Object> fields = super.invocationExtraFields(options);
            if (addProviderField) {
                fields.put("provider_field", "private");
            }
            return fields;
        }

        private Map<String, Object> requestParams(
                Object messages,
                Object tools,
                Number temperature,
                Number topP,
                String model,
                String stop,
                Integer maxTokens,
                boolean stream,
                Map<String, Object> kwargs) {
            return buildRequestParams(messages, tools, temperature, topP, model, stop, maxTokens, stream, kwargs);
        }

        @Override
        public AssistantMessage invoke(Object messages,
                                       Object tools,
                                       Float temperature,
                                       Float topP,
                                       String model,
                                       Integer maxTokens,
                                       String stop,
                                       BaseOutputParser outputParser,
                                       Float timeout,
                                       Map<String, Object> kwargs) {
            lastKwargs = new LinkedHashMap<>(kwargs);
            return AssistantMessage.builder().content("").build();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages,
                                                      Object tools,
                                                      Float temperature,
                                                      Float topP,
                                                      String model,
                                                      Integer maxTokens,
                                                      String stop,
                                                      BaseOutputParser outputParser,
                                                      Float timeout,
                                                      Map<String, Object> kwargs) {
            lastKwargs = new LinkedHashMap<>(kwargs);
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                     String model,
                                                     String size,
                                                     String negativePrompt,
                                                     int n,
                                                     boolean promptExtend,
                                                     boolean watermark,
                                                     int seed,
                                                     Map<String, Object> kwargs) {
            return ImageGenerationResponse.builder().build();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                      String model,
                                                      String voice,
                                                      String languageType,
                                                      Map<String, Object> kwargs) {
            return AudioGenerationResponse.builder().build();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages,
                                                     String imgUrl,
                                                     String audioUrl,
                                                     String model,
                                                     String size,
                                                     String resolution,
                                                     int duration,
                                                     boolean promptExtend,
                                                     boolean watermark,
                                                     String negativePrompt,
                                                     Integer seed,
                                                     Map<String, Object> kwargs) {
            return VideoGenerationResponse.builder().build();
        }
    }
}
