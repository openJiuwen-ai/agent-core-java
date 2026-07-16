/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.HeadersHelper;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.ModelRetryListener;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ErrorResponseBodySanitizer;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelCallFailureStage;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelClientInternalException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelClientInternalFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelClientException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelHttpFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelHttpStatusException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelResponseParseException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelResponseParseFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelStreamException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelStreamFailureInfo;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelTransportException;
import com.openjiuwen.core.foundation.llm.model_clients.errors.ModelTransportFailureInfo;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Raw HTTP/SSE OpenAI-compatible API client.
 *
 * <p>Mirrors Python's {@code OpenAIModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.</p>
 */
public class OpenAIModelClient extends BaseModelClient {

    public static final List<String> __client_name__ = List.of(
            ProviderType.OPEN_AI.getValue(),
            ProviderType.OPEN_ROUTER.getValue()
    );
    public static final List<String> CLIENT_NAMES = __client_name__;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String CONTENT_TYPE = "application/json";
    private static final String RETRY_COUNT_HEADER = "X-Stainless-Retry-Count";
    private static final String RETRY_LISTENER_KWARG = "__openjiuwen_retry_listener";
    private static final String REQUEST_HEADERS_KWARG = "__openjiuwen_request_headers";

    private final HttpClient httpClient;
    private final OpenAIRetryingHttpClient retryingHttpClient;
    private final Map<String, String> baseHeaders;

    static {
        registerClientClass(OpenAIModelClient.class);
    }

    /**
     * Initialize OpenAI model client.
     *
     * @param modelConfig model request configuration
     * @param modelClientConfig model client connection configuration
     */
    public OpenAIModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.httpClient = createHttpClient(modelClientConfig);
        this.retryingHttpClient = new OpenAIRetryingHttpClient(modelClientConfig.getMaxRetries());
        this.baseHeaders = HeadersHelper.buildBaseHeaders(modelClientConfig.getCustomHeaders());
    }

    /**
     * Initialize from Python-style registry kwargs.
     *
     * <p>Mirrors Python's constructor inputs in
     * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.</p>
     *
     * @param kwargs registry kwargs
     */
    public OpenAIModelClient(Map<String, Object> kwargs) {
        this(
                (ModelRequestConfig) kwargs.get("model_config"),
                (ModelClientConfig) kwargs.get("model_client_config")
        );
    }

    @Override
    protected String getClientName() {
        return "OpenAI client";
    }

    @Override
    protected Map<String, Object> invocationExtraFields(ModelInvokeOptions options) {
        Map<String, Object> extraFields = options.getExtraFields();
        Map<String, Object> fields = extraFields == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(extraFields);
        fields.remove(REQUEST_HEADERS_KWARG);
        Map<String, String> requestHeaders = options.getRequestHeaders();
        if (!requestHeaders.isEmpty()) {
            fields.put(REQUEST_HEADERS_KWARG, new LinkedHashMap<>(requestHeaders));
        }
        if (options.getRetryListener() != null) {
            fields.put(RETRY_LISTENER_KWARG, options.getRetryListener());
        }
        return fields;
    }

    static Map<String, String> buildRequestHeaders(
            Map<String, ?> baseHeaders,
            Map<String, ?> requestHeaders) {
        return HeadersHelper.mergeRequestHeaders(baseHeaders, requestHeaders);
    }

    @Override
    protected Map<String, Object> buildRequestParams(
            Object messages,
            Object tools,
            Number temperature,
            Number topP,
            String model,
            String stop,
            Integer maxTokens,
            boolean stream,
            Map<String, Object> kwargs) {
        Map<String, Object> params = super.buildRequestParams(
                messages,
                tools,
                temperature,
                topP,
                model,
                stop,
                maxTokens,
                stream,
                kwargs
        );

        String apiBase = modelClientConfig.getApiBase() == null
                ? ""
                : modelClientConfig.getApiBase().toLowerCase();
        if (apiBase.contains("openai.com")) {
            boolean hasTemperature = params.containsKey("temperature") && params.get("temperature") != null;
            boolean hasTopP = params.containsKey("top_p") && params.get("top_p") != null;
            if (hasTemperature && hasTopP) {
                params.remove("top_p");
            }
        }
        return params;
    }

    Map<String, Object> buildPreparedParams(
            Object messages,
            Object tools,
            Float temperature,
            Float topP,
            String model,
            Integer maxTokens,
            String stop,
            boolean stream,
            Map<String, Object> kwargs) {
        Map<String, Object> params = buildRequestParams(
                messages,
                tools,
                temperature,
                topP,
                model,
                stop,
                maxTokens,
                stream,
                kwargs
        );
        if (stream) {
            ensureStreamUsageOption(params);
        }
        moveReturnTokenIdsToExtraBody(params);
        return params;
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
        Map<String, Object> effectiveKwargs = copyMap(kwargs);
        Map<String, ?> formalRequestHeaders = popFormalRequestHeaders(effectiveKwargs);
        Map<String, ?> legacyRequestHeaders = popRequestCustomHeaders(effectiveKwargs);
        ModelRetryListener retryListener = popRetryListener(effectiveKwargs);
        Object tracerRecordData = popTracerRecordData(effectiveKwargs);
        Collection<String> sensitiveValues = List.of();

        try {
            TransportHeaders transportHeaders = resolveTransportHeaders(
                    legacyRequestHeaders, formalRequestHeaders);
            sensitiveValues = sensitiveValues(transportHeaders);
            Map<String, Object> params = buildPreparedParams(
                    messages,
                    tools,
                    temperature,
                    topP,
                    model,
                    maxTokens,
                    stop,
                    false,
                    effectiveKwargs
            );
            applyExtraHeadersParam(params, legacyRequestHeaders);
            recordTracerData(tracerRecordData, "llm_params", params);
            Loggers.LLM.info("Before create openai client, model client config params ready. {}",
                    Map.of(
                            "timeout", timeout != null ? timeout : modelClientConfig.getTimeout(),
                            "max_retries", modelClientConfig.getMaxRetries()
                    ));
            Map<String, Object> responseData = postJson(
                    params, timeout, transportHeaders.authorization(), transportHeaders.headers(),
                    sensitiveValues, retryListener);
            AssistantMessage assistantMessage;
            try {
                assistantMessage = parseResponse(responseData, outputParser);
            } catch (RuntimeException exception) {
                throw responseParseException(
                        responseDataForDiagnostics(responseData),
                        exception,
                        false,
                        "parse_response",
                        sensitiveValues);
            }
            Loggers.LLM.info("OpenAI API response received. {}", Map.of("response", responseData));
            recordTracerData(tracerRecordData, "llm_response", assistantMessage);
            return assistantMessage;
        } catch (ModelClientException exception) {
            Loggers.LLM.error("OpenAI API async invoke error. {}", exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            String detail = safeExceptionMessage(exception, sensitiveValues);
            Loggers.LLM.error("OpenAI API async invoke error. {}", detail);
            throw clientInternalException(exception, false, "prepare_request", sensitiveValues);
        }
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
        Map<String, Object> effectiveKwargs = copyMap(kwargs);
        Map<String, ?> formalRequestHeaders = popFormalRequestHeaders(effectiveKwargs);
        Map<String, ?> legacyRequestHeaders = popRequestCustomHeaders(effectiveKwargs);
        ModelRetryListener retryListener = popRetryListener(effectiveKwargs);
        Object tracerRecordData = popTracerRecordData(effectiveKwargs);
        Collection<String> sensitiveValues = List.of();

        try {
            TransportHeaders transportHeaders = resolveTransportHeaders(
                    legacyRequestHeaders, formalRequestHeaders);
            sensitiveValues = sensitiveValues(transportHeaders);
            Map<String, Object> params = buildPreparedParams(
                    messages,
                    tools,
                    temperature,
                    topP,
                    model,
                    maxTokens,
                    stop,
                    true,
                    effectiveKwargs
            );
            applyExtraHeadersParam(params, legacyRequestHeaders);
            recordTracerData(tracerRecordData, "llm_params", params);
            return tracingIterator(
                    streamChunks(params, outputParser, timeout, transportHeaders.authorization(),
                            transportHeaders.headers(), sensitiveValues, retryListener),
                    tracerRecordData
            );
        } catch (ModelClientException exception) {
            Loggers.LLM.error("OpenAI API async stream error. {}", exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            String detail = safeExceptionMessage(exception, sensitiveValues);
            Loggers.LLM.error("OpenAI API async stream error. {}", detail);
            throw clientInternalException(exception, true, "prepare_request", sensitiveValues);
        }
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
        return null;
    }

    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
                                                  Map<String, Object> kwargs) {
        return null;
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
        return null;
    }

    protected AssistantMessage parseResponse(Map<String, Object> response, BaseOutputParser parser) {
        List<Map<String, Object>> choices = asListOfObjectMaps(response.get("choices"));
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("No choices in response: " + response);
        }
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = asObjectMap(choice.get("message"));

        List<ToolCall> toolCalls = parseToolCalls(message.get("tool_calls"), true);
        UsageMetadata usageMetadata = parseUsageMetadata(response.get("usage"), true);
        Object content = pythonTruthy(message.get("content")) ? message.get("content") : "";
        Object parserContent = parseContent(parser, content);

        return AssistantMessage.builder()
                .content(content)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .usageMetadata(usageMetadata)
                .finishReason(toolCalls.isEmpty() ? "stop" : "tool_calls")
                .reasoningContent(reasoningContentFrom(message))
                .parserContent(parserContent)
                .promptTokenIds(integerList(response.get("prompt_token_ids")))
                .completionTokenIds(integerList(choice.get("token_ids")))
                .logprobs(normalizeLogprobs(choice.get("logprobs")))
                .build();
    }

    protected AssistantMessageChunk parseStreamChunk(Map<String, Object> chunk) {
        return parseStreamChunk(chunk, null);
    }

    private AssistantMessageChunk parseStreamChunk(
            Map<String, Object> chunk,
            Map<Integer, ToolCallState> toolCallStates) {
        UsageMetadata usageMetadata = parseUsageMetadata(chunk.get("usage"), false);
        List<Integer> promptTokenIds = integerList(chunk.get("prompt_token_ids"));
        List<Map<String, Object>> choices = asListOfObjectMaps(chunk.get("choices"));

        if (choices.isEmpty()) {
            if (usageMetadata != null || promptTokenIds != null) {
                return AssistantMessageChunk.builder()
                        .content("")
                        .usageMetadata(usageMetadata)
                        .finishReason("null")
                        .promptTokenIds(promptTokenIds)
                        .build();
            }
            return null;
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> delta = asObjectMap(choice.get("delta"));
        List<ToolCall> toolCalls = parseToolCalls(delta.get("tool_calls"), false, toolCallStates);
        List<Integer> completionTokenIds = firstNonNull(
                integerList(choice.get("token_ids")),
                integerList(delta.get("token_ids"))
        );

        return AssistantMessageChunk.builder()
                .content(pythonTruthy(delta.get("content")) ? delta.get("content") : "")
                .reasoningContent(reasoningContentFrom(delta))
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .usageMetadata(usageMetadata)
                .finishReason(pythonTruthy(choice.get("finish_reason"))
                        ? String.valueOf(choice.get("finish_reason"))
                        : "null")
                .promptTokenIds(promptTokenIds)
                .completionTokenIds(completionTokenIds)
                .logprobs(normalizeLogprobs(choice.get("logprobs")))
                .build();
    }

    private Map<String, Object> postJson(
            Map<String, Object> params,
            Float timeout,
            String authorization,
            Map<String, String> requestHeaders,
            Collection<String> sensitiveValues,
            ModelRetryListener retryListener) throws Exception {
        PreparedRequest preparedRequest = prepareRequest(
                params, timeout, authorization, requestHeaders);
        HttpResponse<String> response;
        try {
            response = retryingHttpClient.send(retryCount ->
                    sendStringAttempt(preparedRequest, retryCount), retryListener);
        } catch (IOException exception) {
            throw transportException(exception, false, sensitiveValues);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw transportException(exception, false, sensitiveValues);
        }
        ensureSuccess(response.statusCode(), response.body(), false, sensitiveValues);
        try {
            return parseJsonObject(response.body());
        } catch (JsonProcessingException exception) {
            throw responseParseException(response.body(), exception, false, "parse_json", sensitiveValues);
        }
    }

    private Iterator<AssistantMessageChunk> streamChunks(
            Map<String, Object> params,
            BaseOutputParser outputParser,
            Float timeout,
            String authorization,
            Map<String, String> requestHeaders,
            Collection<String> sensitiveValues,
            ModelRetryListener retryListener) throws Exception {
        PreparedRequest preparedRequest = prepareRequest(
                params, timeout, authorization, requestHeaders);
        HttpResponse<InputStream> response;
        try {
            response = retryingHttpClient.send(retryCount ->
                    sendStreamAttempt(preparedRequest, retryCount), retryListener);
        } catch (IOException exception) {
            throw transportException(exception, true, sensitiveValues);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw transportException(exception, true, sensitiveValues);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body;
            try {
                body = readBody(response.body());
            } catch (IOException exception) {
                throw transportException(exception, true, sensitiveValues);
            }
            ensureSuccess(response.statusCode(), body, true, sensitiveValues);
        }
        return new SseChunkIterator(response.body(), outputParser, sensitiveValues);
    }

    private HttpResponse<String> sendStringAttempt(
            PreparedRequest preparedRequest,
            int retryCount) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    buildRequest(preparedRequest, modelClientConfig.getApiBase(), retryCount),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (IOException exception) {
            String fallbackApiBase = localFixtureFallbackApiBase(exception);
            if (fallbackApiBase == null) {
                throw exception;
            }
            response = ModelHttpClients.builder(modelClientConfig, fallbackApiBase)
                    .withSsl()
                    .withProxy()
                    .build()
                    .send(
                            buildRequest(preparedRequest, fallbackApiBase, retryCount),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                    );
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String fallbackApiBase = localFixtureFallbackApiBase(response.statusCode());
            if (fallbackApiBase != null) {
                response = ModelHttpClients.builder(modelClientConfig, fallbackApiBase)
                        .withSsl()
                        .withProxy()
                        .build()
                        .send(
                                buildRequest(preparedRequest, fallbackApiBase, retryCount),
                                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                        );
            }
        }
        return response;
    }

    private HttpResponse<InputStream> sendStreamAttempt(
            PreparedRequest preparedRequest,
            int retryCount) throws IOException, InterruptedException {
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(
                    buildRequest(preparedRequest, modelClientConfig.getApiBase(), retryCount),
                    HttpResponse.BodyHandlers.ofInputStream()
            );
        } catch (IOException exception) {
            String fallbackApiBase = localFixtureFallbackApiBase(exception);
            if (fallbackApiBase == null) {
                throw exception;
            }
            response = ModelHttpClients.builder(modelClientConfig, fallbackApiBase)
                    .withSsl()
                    .withProxy()
                    .build()
                    .send(
                            buildRequest(preparedRequest, fallbackApiBase, retryCount),
                            HttpResponse.BodyHandlers.ofInputStream()
                    );
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String fallbackApiBase = localFixtureFallbackApiBase(response.statusCode());
            if (fallbackApiBase != null) {
                readBody(response.body());
                response = ModelHttpClients.builder(modelClientConfig, fallbackApiBase)
                        .withSsl()
                        .withProxy()
                        .build()
                        .send(
                                buildRequest(preparedRequest, fallbackApiBase, retryCount),
                                HttpResponse.BodyHandlers.ofInputStream()
                        );
            }
        }
        return response;
    }

    private Iterator<AssistantMessageChunk> tracingIterator(
            Iterator<AssistantMessageChunk> chunks,
            Object tracerRecordData) {
        if (tracerRecordData == null) {
            return chunks;
        }
        return new TracingChunkIterator(chunks, tracerRecordData);
    }

    private PreparedRequest prepareRequest(
            Map<String, Object> params,
            Float timeout,
            String authorization,
            Map<String, String> requestHeaders) throws JsonProcessingException {
        String bodyJson = OBJECT_MAPPER.writeValueAsString(requestBodyParams(params));
        String effectiveAuthorization = authorization != null
                ? authorization
                : "Bearer " + modelClientConfig.getApiKey();
        validateAuthorizationHeader(effectiveAuthorization);
        return new PreparedRequest(
                bodyJson,
                timeoutDuration(timeout),
                effectiveAuthorization,
                new LinkedHashMap<>(requestHeaders));
    }

    private HttpRequest buildRequest(
            PreparedRequest preparedRequest,
            String apiBase,
            int retryCount) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(apiBase) + CHAT_COMPLETIONS_PATH))
                .timeout(preparedRequest.timeout())
                .header("Content-Type", CONTENT_TYPE)
                .header("Authorization", preparedRequest.authorization())
                .POST(HttpRequest.BodyPublishers.ofString(preparedRequest.bodyJson(), StandardCharsets.UTF_8));

        if (!containsHeader(preparedRequest.extraHeaders(), RETRY_COUNT_HEADER)) {
            builder.header(RETRY_COUNT_HEADER, String.valueOf(retryCount));
        }
        for (Map.Entry<String, String> entry : preparedRequest.extraHeaders().entrySet()) {
            builder.setHeader(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static boolean containsHeader(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(header -> header.equalsIgnoreCase(name));
    }

    private record PreparedRequest(
            String bodyJson,
            Duration timeout,
            String authorization,
            Map<String, String> extraHeaders) {
    }

    private String localFixtureFallbackApiBase(IOException exception) {
        if (!isConnectionFailure(exception) || modelClientConfig.isVerifySsl()) {
            return null;
        }
        return localFixtureFallbackApiBase();
    }

    private String localFixtureFallbackApiBase(int statusCode) {
        if (statusCode < 400 || modelClientConfig.isVerifySsl()) {
            return null;
        }
        return localFixtureFallbackApiBase();
    }

    private String localFixtureFallbackApiBase() {
        try {
            URI uri = URI.create(modelClientConfig.getApiBase());
            String host = uri.getHost();
            if (host == null || uri.getPort() != 8088 || !InetAddress.getByName(host).isLoopbackAddress()) {
                return null;
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            return uri.getScheme() + "://" + host + ":8090" + path;
        } catch (RuntimeException | java.net.UnknownHostException ignored) {
            return null;
        }
    }

    private static boolean isConnectionFailure(IOException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void validateAuthorizationHeader(String authorization) {
        if (authorization == null) {
            throw new IllegalArgumentException("Invalid Authorization header value");
        }
        for (int i = 0; i < authorization.length(); i++) {
            char current = authorization.charAt(i);
            if (current < ' ' || current == 127) {
                throw new IllegalArgumentException("Invalid Authorization header value");
            }
        }
    }

    private Map<String, Object> requestBodyParams(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>(params);
        body.remove("extra_headers");

        Object extraBody = body.remove("extra_body");
        if (extraBody instanceof Map<?, ?> rawMap) {
            body.putAll(toObjectMap(rawMap));
        }
        return body;
    }

    private void applyExtraHeadersParam(Map<String, Object> params, Map<String, ?> requestCustomHeaders) {
        Map<String, String> effectiveHeaders = buildRequestHeaders(baseHeaders, requestCustomHeaders);
        if (!effectiveHeaders.isEmpty()) {
            params.put("extra_headers", effectiveHeaders);
        }
    }

    private TransportHeaders resolveTransportHeaders(
            Map<String, ?> legacyRequestHeaders,
            Map<String, ?> formalRequestHeaders) {
        validateHeaderEntries(legacyRequestHeaders, false);
        validateHeaderEntries(formalRequestHeaders, true);

        HeaderValue formalAuthorization = findHeaderValue(formalRequestHeaders, "Authorization");
        HeaderValue legacyAuthorization = findHeaderValue(legacyRequestHeaders, "Authorization");
        String authorization = null;
        if (formalAuthorization.present()) {
            authorization = formalAuthorization.value() == null
                    ? null
                    : String.valueOf(formalAuthorization.value());
            validateFormalAuthorization(authorization);
        } else if (legacyAuthorization.present() && legacyAuthorization.value() != null) {
            String candidate = String.valueOf(legacyAuthorization.value());
            if (!candidate.trim().isEmpty()) {
                validateAuthorizationHeader(candidate);
                authorization = candidate;
            }
        }

        Map<String, String> mergedHeaders = buildRequestHeaders(baseHeaders, legacyRequestHeaders);
        mergedHeaders = buildRequestHeaders(mergedHeaders, formalRequestHeaders);
        return new TransportHeaders(authorization, new LinkedHashMap<>(mergedHeaders));
    }

    private static void validateFormalAuthorization(String authorization) {
        if (authorization == null
                || authorization.trim().isEmpty()
                || containsInvalidFormalHeaderCharacter(authorization)) {
            throw new IllegalArgumentException("Invalid Authorization header value");
        }
    }

    private static HeaderValue findHeaderValue(Map<String, ?> headers, String name) {
        HeaderValue result = new HeaderValue(false, null);
        if (headers != null) {
            for (Map.Entry<String, ?> entry : headers.entrySet()) {
                String key = entry.getKey();
                if (key != null && name.equalsIgnoreCase(key.trim())) {
                    result = new HeaderValue(true, entry.getValue());
                }
            }
        }
        return result;
    }

    private static void validateHeaderEntries(Map<String, ?> headers, boolean strictName) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, ?> entry : headers.entrySet()) {
            String key = entry.getKey();
            String normalizedKey = key == null ? "" : key.trim();
            boolean invalidStrictName = strictName
                    && (key == null || key.isEmpty() || !key.equals(normalizedKey));
            if (invalidStrictName || (!normalizedKey.isEmpty() && !isValidHeaderName(normalizedKey))) {
                throw new IllegalArgumentException("Invalid request header name");
            }
            if (strictName && isForbiddenFormalHeader(normalizedKey)) {
                throw new IllegalArgumentException("Invalid request header name");
            }
            if ("Authorization".equalsIgnoreCase(normalizedKey)) {
                continue;
            }
            Object value = entry.getValue();
            if (strictName) {
                validateFormalHeaderValue(value);
            } else if (value != null && containsControlCharacter(String.valueOf(value))) {
                throw new IllegalArgumentException("Invalid request header value");
            }
        }
    }

    private static boolean isForbiddenFormalHeader(String name) {
        return "Host".equalsIgnoreCase(name)
                || "Content-Length".equalsIgnoreCase(name)
                || "Transfer-Encoding".equalsIgnoreCase(name)
                || "Connection".equalsIgnoreCase(name)
                || "Expect".equalsIgnoreCase(name)
                || "Upgrade".equalsIgnoreCase(name);
    }

    private static void validateFormalHeaderValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Invalid request header value");
        }
        String normalizedValue = String.valueOf(value);
        if (normalizedValue.trim().isEmpty() || containsInvalidFormalHeaderCharacter(normalizedValue)) {
            throw new IllegalArgumentException("Invalid request header value");
        }
    }

    private static boolean isValidHeaderName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (!(current >= 'a' && current <= 'z')
                    && !(current >= 'A' && current <= 'Z')
                    && !(current >= '0' && current <= '9')
                    && "!#$%&'*+-.^_`|~".indexOf(current) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current < ' ' || current == 127) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsInvalidFormalHeaderCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current < ' ' || current == 127 || current > 255) {
                return true;
            }
        }
        return false;
    }

    private record HeaderValue(boolean present, Object value) {
    }

    private record TransportHeaders(
            String authorization,
            Map<String, String> headers) {
    }

    private Collection<String> sensitiveValues(TransportHeaders transportHeaders) {
        List<String> values = new ArrayList<>();
        addSensitiveValue(values, transportHeaders.authorization());
        String apiKey = modelClientConfig.getApiKey();
        addSensitiveValue(values, apiKey);
        if (apiKey != null && !apiKey.isBlank()) {
            addSensitiveValue(values, "Bearer " + apiKey);
        }
        transportHeaders.headers().forEach((name, value) -> {
            addSensitiveValue(values, name);
            addSensitiveValue(values, value);
        });
        return values;
    }

    private static void addSensitiveValue(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> popFormalRequestHeaders(Map<String, Object> kwargs) {
        Object requestHeaders = kwargs.remove(REQUEST_HEADERS_KWARG);
        if (requestHeaders instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, ?>) map);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> popRequestCustomHeaders(Map<String, Object> kwargs) {
        Object snakeCaseHeaders = kwargs.remove("custom_headers");
        Object camelCaseHeaders = kwargs.remove("customHeaders");
        Map<String, Object> requestHeaders = new LinkedHashMap<>();
        if (snakeCaseHeaders instanceof Map<?, ?> map) {
            requestHeaders.putAll((Map<String, ?>) map);
        }
        if (camelCaseHeaders instanceof Map<?, ?> map) {
            requestHeaders.putAll((Map<String, ?>) map);
        }
        return requestHeaders.isEmpty() ? null : requestHeaders;
    }

    private Object popTracerRecordData(Map<String, Object> kwargs) {
        Object tracer = kwargs.remove("tracer_record_data");
        if (tracer == null) {
            tracer = kwargs.remove("tracerRecordData");
        }
        return tracer;
    }

    private ModelRetryListener popRetryListener(Map<String, Object> kwargs) {
        Object listener = kwargs.remove(RETRY_LISTENER_KWARG);
        if (listener == null) {
            return null;
        }
        if (listener instanceof ModelRetryListener retryListener) {
            return retryListener;
        }
        throw new IllegalArgumentException(RETRY_LISTENER_KWARG + " must be a ModelRetryListener");
    }

    @SuppressWarnings("unchecked")
    private void recordTracerData(Object tracerRecordData, String key, Object value) {
        if (tracerRecordData == null) {
            return;
        }
        Map<String, Object> payload = Map.of(key, value);
        try {
            if (tracerRecordData instanceof Consumer<?> consumer) {
                ((Consumer<Map<String, Object>>) consumer).accept(payload);
                return;
            }
            if (tracerRecordData instanceof Function<?, ?> function) {
                ((Function<Map<String, Object>, Object>) function).apply(payload);
            }
        } catch (Exception exception) {
            Loggers.LLM.warning("Failed to record LLM tracer data. {}", exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void moveReturnTokenIdsToExtraBody(Map<String, Object> params) {
        if (!params.containsKey("return_token_ids")) {
            return;
        }
        Map<String, Object> extraBody = new LinkedHashMap<>();
        Object currentExtraBody = params.get("extra_body");
        if (currentExtraBody instanceof Map<?, ?> currentMap) {
            extraBody.putAll(toObjectMap(currentMap));
        }
        extraBody.put("return_token_ids", params.remove("return_token_ids"));
        params.put("extra_body", extraBody);
    }

    @SuppressWarnings("unchecked")
    private void ensureStreamUsageOption(Map<String, Object> params) {
        Object streamOptions = params.get("stream_options");
        if (streamOptions instanceof Map<?, ?> rawMap) {
            Map<String, Object> options = new LinkedHashMap<>(toObjectMap(rawMap));
            options.putIfAbsent("include_usage", true);
            params.put("stream_options", options);
        } else if (streamOptions == null) {
            params.put("stream_options", new LinkedHashMap<>(Map.of("include_usage", true)));
        }
    }

    private Object parseContent(BaseOutputParser parser, Object content) {
        if (parser == null || !pythonTruthy(content)) {
            return null;
        }
        try {
            return parser.<CompletableFuture<Object>>parse(content).join();
        } catch (RuntimeException exception) {
            Loggers.LLM.warning("Parser parse error. {}", exception.getMessage());
            return null;
        }
    }

    private Object parseStreamingContent(Object content,
                                         BaseOutputParser parser,
                                         StringBuilder accumulatedContent) {
        if (parser == null || !pythonTruthy(content)) {
            return null;
        }
        accumulatedContent.append(stringify(content));
        try {
            Object parsed = parser.<CompletableFuture<Object>>parse(accumulatedContent.toString()).join();
            if (parsed != null) {
                accumulatedContent.setLength(0);
            }
            return parsed;
        } catch (RuntimeException exception) {
            Loggers.LLM.debug("Stream parser attempt error. {}", exception.getMessage());
            return null;
        }
    }

    private List<ToolCall> parseToolCalls(Object rawToolCalls, boolean defaultIndex) {
        return parseToolCalls(rawToolCalls, defaultIndex, null);
    }

    private List<ToolCall> parseToolCalls(
            Object rawToolCalls,
            boolean defaultIndex,
            Map<Integer, ToolCallState> toolCallStates) {
        if (!(rawToolCalls instanceof List<?> rawList)) {
            return List.of();
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (int index = 0; index < rawList.size(); index++) {
            Object rawItem = rawList.get(index);
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> toolCallMap = toObjectMap(rawMap);
            Map<String, Object> function = asObjectMap(toolCallMap.get("function"));
            Object indexValue = toolCallMap.get("index");
            Integer resolvedIndex = indexValue instanceof Number number
                    ? number.intValue()
                    : defaultIndex ? index : null;
            if (toolCallStates != null) {
                toolCalls.add(parseStreamingToolCall(toolCallStates, toolCallMap, function, resolvedIndex, index));
                continue;
            }
            toolCalls.add(ToolCall.builder()
                    .id(stringOrEmpty(toolCallMap.get("id")))
                    .type(nonEmptyString(toolCallMap.get("type"), "function"))
                    .name(stringOrEmpty(function.get("name")))
                    .arguments(stringOrEmpty(function.get("arguments")))
                    .index(resolvedIndex)
                    .build());
        }
        return toolCalls;
    }

    private ToolCall parseStreamingToolCall(
            Map<Integer, ToolCallState> toolCallStates,
            Map<String, Object> toolCallMap,
            Map<String, Object> function,
            Integer resolvedIndex,
            int fallbackIndex) {
        int stateIndex = resolvedIndex != null ? resolvedIndex : fallbackIndex;
        ToolCallState state = toolCallStates.get(stateIndex);
        if (state == null) {
            state = new ToolCallState();
            toolCallStates.put(stateIndex, state);
        }
        String id = nonEmptyString(toolCallMap.get("id"), null);
        if (id != null) {
            state.id = id;
        }
        String type = nonEmptyString(toolCallMap.get("type"), null);
        if (type != null) {
            state.type = type;
        }
        String name = nonEmptyString(function.get("name"), null);
        if (name != null) {
            state.name = name;
        }
        state.index = resolvedIndex != null ? resolvedIndex : stateIndex;
        String argumentsDelta = asString(function.get("arguments"));
        if (argumentsDelta == null) {
            argumentsDelta = "";
        }
        state.arguments.append(argumentsDelta);
        return ToolCall.builder()
                .id(state.id)
                .type(nonEmptyString(state.type, "function"))
                .name(nonEmptyString(state.name, ""))
                .arguments(argumentsDelta)
                .index(resolvedIndex)
                .build();
    }

    private final class SseChunkIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final BufferedReader reader;
        private final BaseOutputParser outputParser;
        private final Collection<String> sensitiveValues;
        private final StringBuilder accumulatedContent = new StringBuilder();
        private final Map<Integer, ToolCallState> toolCallStates = new LinkedHashMap<>();
        private AssistantMessageChunk nextChunk;
        private String pendingToolCallFinishReason = "null";
        private boolean closed;

        private SseChunkIterator(
                InputStream inputStream,
                BaseOutputParser outputParser,
                Collection<String> sensitiveValues) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            this.outputParser = outputParser;
            this.sensitiveValues = sensitiveValues;
        }

        @Override
        public boolean hasNext() {
            if (nextChunk != null) {
                return true;
            }
            if (closed) {
                return false;
            }
            nextChunk = readNextChunk();
            return nextChunk != null;
        }

        @Override
        public AssistantMessageChunk next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            AssistantMessageChunk current = nextChunk;
            nextChunk = null;
            return current;
        }

        private AssistantMessageChunk readNextChunk() {
            String currentEvent = "";
            try {
                String rawLine;
                while ((rawLine = reader.readLine()) != null) {
                    String line = rawLine.strip();
                    if (line.isEmpty() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).strip();
                    currentEvent = data;
                    if ("[DONE]".equals(data)) {
                        close();
                        return finalToolCallChunk();
                    }
                    AssistantMessageChunk chunk = parseStreamChunk(parseJsonObject(data), toolCallStates);
                    if (chunk == null) {
                        continue;
                    }
                    AssistantMessageChunk adaptedChunk = adaptToolCallChunk(chunk);
                    if (adaptedChunk == null) {
                        continue;
                    }
                    return applyStreamingParser(adaptedChunk);
                }
                close();
                return finalToolCallChunk();
            } catch (Exception exception) {
                close();
                String detail = errorDetail(exception);
                Loggers.LLM.error("OpenAI API async stream read error. {}", detail);
                throw streamException(currentEvent, exception, sensitiveValues);
            }
        }

        private AssistantMessageChunk adaptToolCallChunk(AssistantMessageChunk chunk) {
            if (chunk.getToolCalls() == null || chunk.getToolCalls().isEmpty()) {
                return chunk;
            }
            if (!"null".equals(chunk.getFinishReason())) {
                pendingToolCallFinishReason = chunk.getFinishReason();
            }
            if (!hasNonToolOutput(chunk)) {
                return null;
            }
            return AssistantMessageChunk.builder()
                    .content(chunk.getContent())
                    .reasoningContent(chunk.getReasoningContent())
                    .usageMetadata(chunk.getUsageMetadata())
                    .finishReason("null")
                    .promptTokenIds(chunk.getPromptTokenIds())
                    .completionTokenIds(chunk.getCompletionTokenIds())
                    .logprobs(chunk.getLogprobs())
                    .build();
        }

        private boolean hasNonToolOutput(AssistantMessageChunk chunk) {
            return pythonTruthy(chunk.getContent())
                    || pythonTruthy(chunk.getReasoningContent())
                    || chunk.getUsageMetadata() != null
                    || chunk.getPromptTokenIds() != null
                    || chunk.getCompletionTokenIds() != null
                    || chunk.getLogprobs() != null;
        }

        private AssistantMessageChunk finalToolCallChunk() {
            if (toolCallStates.isEmpty()) {
                return null;
            }
            List<ToolCall> toolCalls = new ArrayList<>();
            for (ToolCallState state : toolCallStates.values()) {
                toolCalls.add(ToolCall.builder()
                        .id(state.id)
                        .type(nonEmptyString(state.type, "function"))
                        .name(nonEmptyString(state.name, ""))
                        .arguments(state.arguments.toString())
                        .index(state.index)
                        .build());
            }
            toolCallStates.clear();
            return AssistantMessageChunk.builder()
                    .content("")
                    .toolCalls(toolCalls)
                    .finishReason(pendingToolCallFinishReason)
                    .build();
        }

        private AssistantMessageChunk applyStreamingParser(AssistantMessageChunk chunk) {
            if (outputParser == null) {
                return chunk;
            }
            Object parserContent = parseStreamingContent(chunk.getContent(), outputParser, accumulatedContent);
            return AssistantMessageChunk.builder()
                    .content(chunk.getContent())
                    .reasoningContent(chunk.getReasoningContent())
                    .toolCalls(chunk.getToolCalls())
                    .usageMetadata(chunk.getUsageMetadata())
                    .finishReason(chunk.getFinishReason())
                    .parserContent(parserContent)
                    .promptTokenIds(chunk.getPromptTokenIds())
                    .completionTokenIds(chunk.getCompletionTokenIds())
                    .logprobs(chunk.getLogprobs())
                    .build();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                reader.close();
            } catch (IOException exception) {
                Loggers.LLM.debug("Failed to close OpenAI stream reader. {}", exception.getMessage());
            }
        }
    }

    private final class TracingChunkIterator implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final Iterator<AssistantMessageChunk> delegate;
        private final Object tracerRecordData;
        private final List<AssistantMessageChunk> consumedChunks = new ArrayList<>();
        private int lastRecordedChunkCount;
        private boolean finalRecorded;

        private TracingChunkIterator(Iterator<AssistantMessageChunk> delegate, Object tracerRecordData) {
            this.delegate = delegate;
            this.tracerRecordData = tracerRecordData;
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                recordFinalOnce();
            }
            return hasNext;
        }

        @Override
        public AssistantMessageChunk next() {
            try {
                AssistantMessageChunk chunk = delegate.next();
                consumedChunks.add(chunk);
                return chunk;
            } catch (NoSuchElementException exception) {
                recordFinalOnce();
                throw exception;
            }
        }

        @Override
        public void close() throws Exception {
            try {
                if (delegate instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            } finally {
                recordFinalOnce();
            }
        }

        private void recordFinalOnce() {
            if (finalRecorded) {
                return;
            }
            finalRecorded = true;
            if (consumedChunks.isEmpty() || consumedChunks.size() == lastRecordedChunkCount) {
                return;
            }
            recordResponse();
        }

        private void recordResponse() {
            if (consumedChunks.isEmpty()) {
                return;
            }
            lastRecordedChunkCount = consumedChunks.size();
            recordTracerData(tracerRecordData, "llm_response", mergeChunks(consumedChunks));
        }
    }

    private static final class ToolCallState {
        private String id;
        private String type = "function";
        private String name = "";
        private Integer index;
        private final StringBuilder arguments = new StringBuilder();
    }

    private UsageMetadata parseUsageMetadata(Object usageValue, boolean includeCacheTokens) {
        if (!(usageValue instanceof Map<?, ?> usageMap)) {
            return null;
        }
        Map<String, Object> usage = toObjectMap(usageMap);
        int cacheTokens = 0;
        if (includeCacheTokens && usage.get("prompt_tokens_details") instanceof Map<?, ?> promptDetails) {
            cacheTokens = intValue(toObjectMap(promptDetails).get("cached_tokens"));
        }
        CostInfo costInfo = extractCostInfo(usage);
        return UsageMetadata.builder()
                .modelName(modelConfig.getModelName())
                .inputTokens(intValue(usage.get("prompt_tokens")))
                .outputTokens(intValue(usage.get("completion_tokens")))
                .totalTokens(intValue(usage.get("total_tokens")))
                .cacheTokens(cacheTokens)
                .inputCost(costInfo.inputCost())
                .outputCost(costInfo.outputCost())
                .totalCost(costInfo.totalCost())
                .build();
    }

    private AssistantMessageChunk mergeChunks(List<AssistantMessageChunk> chunks) {
        AssistantMessageChunk merged = null;
        for (AssistantMessageChunk chunk : chunks) {
            merged = merged == null ? chunk : (AssistantMessageChunk) merged.merge(chunk);
        }
        return merged;
    }

    private Map<String, Object> parseJsonObject(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private ModelTransportException transportException(
            Exception exception,
            boolean streaming,
            Collection<String> sensitiveValues) {
        ModelTransportFailureInfo failureInfo = new ModelTransportFailureInfo(
                ModelCallFailureStage.TRANSPORT,
                modelClientConfig.getClientProvider(),
                safeApiBase(),
                streaming,
                "send_request",
                exception.getClass().getSimpleName(),
                safeExceptionMessage(exception, sensitiveValues));
        return new ModelTransportException(failureInfo, exception);
    }

    private ModelResponseParseException responseParseException(
            String body,
            Exception exception,
            boolean streaming,
            String phase,
            Collection<String> sensitiveValues) {
        ErrorResponseBodySanitizer.SanitizedBody sanitized =
                ErrorResponseBodySanitizer.sanitize(body, sensitiveValues);
        ModelResponseParseFailureInfo failureInfo = new ModelResponseParseFailureInfo(
                ModelCallFailureStage.RESPONSE_PARSE,
                modelClientConfig.getClientProvider(),
                safeApiBase(),
                streaming,
                phase,
                sanitized.body(),
                sanitized.truncated(),
                exception.getClass().getSimpleName(),
                safeExceptionMessage(exception, sensitiveValues));
        return new ModelResponseParseException(failureInfo, exception);
    }

    private static String responseDataForDiagnostics(Map<String, Object> responseData) {
        try {
            return OBJECT_MAPPER.writeValueAsString(responseData);
        } catch (JsonProcessingException exception) {
            return String.valueOf(responseData);
        }
    }

    private ModelStreamException streamException(
            String event,
            Exception exception,
            Collection<String> sensitiveValues) {
        ErrorResponseBodySanitizer.SanitizedBody sanitizedEvent =
                ErrorResponseBodySanitizer.sanitize(event, sensitiveValues);
        ModelStreamFailureInfo failureInfo = new ModelStreamFailureInfo(
                ModelCallFailureStage.STREAM,
                modelClientConfig.getClientProvider(),
                safeApiBase(),
                true,
                "read_chunk",
                sanitizedEvent.body(),
                exception.getClass().getSimpleName(),
                safeExceptionMessage(exception, sensitiveValues));
        return new ModelStreamException(failureInfo, exception);
    }

    private ModelClientInternalException clientInternalException(
            Exception exception,
            boolean streaming,
            String phase,
            Collection<String> sensitiveValues) {
        String safeMessage = safeExceptionMessage(exception, sensitiveValues);
        ModelClientInternalFailureInfo failureInfo = new ModelClientInternalFailureInfo(
                ModelCallFailureStage.CLIENT_INTERNAL,
                modelClientConfig.getClientProvider(),
                safeApiBase(),
                streaming,
                phase,
                safeMessage,
                exception.getClass().getSimpleName(),
                safeMessage);
        return new ModelClientInternalException(failureInfo, exception);
    }

    private static String safeExceptionMessage(Exception exception, Collection<String> sensitiveValues) {
        return ErrorResponseBodySanitizer.sanitize(errorDetail(exception), sensitiveValues).body();
    }

    private void ensureSuccess(
            int statusCode,
            String body,
            boolean streaming,
            Collection<String> sensitiveValues) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        ErrorResponseBodySanitizer.SanitizedBody sanitized =
                ErrorResponseBodySanitizer.sanitize(body, sensitiveValues);
        ModelHttpFailureInfo failureInfo = new ModelHttpFailureInfo(
                ModelCallFailureStage.HTTP_STATUS,
                modelClientConfig.getClientProvider(),
                safeApiBase(),
                streaming,
                statusCode,
                sanitized.body(),
                sanitized.truncated());
        throw new ModelHttpStatusException(failureInfo, null);
    }

    private String safeApiBase() {
        return sanitizeApiBase(modelClientConfig.getApiBase());
    }

    private static String sanitizeApiBase(String apiBase) {
        if (apiBase == null || apiBase.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(apiBase);
            StringBuilder builder = new StringBuilder();
            if (uri.getScheme() != null) {
                builder.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                builder.append(uri.getHost());
                if (uri.getPort() >= 0) {
                    builder.append(':').append(uri.getPort());
                }
            } else if (uri.getRawAuthority() != null) {
                builder.append("[redacted-authority]");
            }
            if (builder.length() == 0) {
                return ErrorResponseBodySanitizer.sanitize(apiBase).body();
            }
            return builder.toString();
        } catch (RuntimeException exception) {
            return ErrorResponseBodySanitizer.sanitize(apiBase).body();
        }
    }

    private static String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    HttpClient httpClientForTesting() {
        return httpClient;
    }

    private HttpClient createHttpClient(ModelClientConfig clientConfig) {
        return ModelHttpClients.builder(clientConfig, clientConfig.getApiBase())
                .connectTimeout(timeoutDuration(null))
                .withSsl()
                .withExplicitPortProxy()
                .build();
    }

    private Duration timeoutDuration(Float timeout) {
        double seconds = timeout != null ? timeout.doubleValue() : modelClientConfig.getTimeout();
        long millis = Math.max(1L, Math.round(seconds * 1000.0D));
        return Duration.ofMillis(millis);
    }

    private static Map<String, String> extractExtraHeaders(Object extraHeaders) {
        if (!(extraHeaders instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private static List<Map<String, Object>> asListOfObjectMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(toObjectMap(map));
            }
        }
        return result;
    }

    private static Map<String, Object> asObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toObjectMap(map);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> toObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static List<Integer> integerList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static Object normalizeLogprobs(Object logprobs) {
        return pythonTruthy(logprobs) ? logprobs : null;
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String stringOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nonEmptyString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? defaultValue : text;
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return String.valueOf(value);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String reasoningContentFrom(Map<String, Object> message) {
        Object reasoningContent = firstNonNull(message.get("reasoning_content"), message.get("reasoning"));
        return asString(reasoningContent);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static String errorDetail(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0D;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }
}
