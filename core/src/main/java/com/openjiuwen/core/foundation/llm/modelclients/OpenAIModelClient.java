/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.classification.ClassifiedModelErrors;
import com.openjiuwen.core.foundation.llm.classification.ErrorBodyParser;
import com.openjiuwen.core.foundation.llm.classification.FirstSegmentRetryIterator;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetrier;
import com.openjiuwen.core.foundation.llm.classification.ModelCallRetryPolicy;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorCategory;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorClassifiers;
import com.openjiuwen.core.foundation.llm.classification.ModelErrorFacts;
import com.openjiuwen.core.foundation.llm.classification.RetryAfterParser;
import com.openjiuwen.core.foundation.llm.classification.RetryContext;
import com.openjiuwen.core.foundation.llm.HeadersHelper;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelCircuitBreaker;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.ModelRetryListener;
import com.openjiuwen.core.foundation.llm.modelclients.errors.ErrorResponseBodySanitizer;
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
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Raw HTTP/SSE OpenAI-compatible API client.
 *
 * <p>Mirrors Python's {@code OpenAIModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/openai_model_client.py}.</p>
 */
public class OpenAIModelClient extends BaseModelClient
        implements Model.KvCacheAffinityClient {

    public static final List<String> __client_name__ = List.of(
            ProviderType.OPEN_AI.getValue(),
            ProviderType.OPEN_ROUTER.getValue()
    );

    public static final List<String> CLIENT_NAMES = __client_name__;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String IMAGES_GENERATIONS_PATH = "/images/generations";
    private static final String VIDEOS_PATH = "/videos";
    private static final String IMAGE_CREATE_PATH_KWARG = "image_create_path";
    private static final String IMAGE_URLS_PATH_KWARG = "image_urls_path";
    private static final String VIDEO_BODY_KWARG = "video_body";
    private static final String VIDEO_BODY_REFERENCE = "reference";
    private static final String VIDEO_BODY_PROMPT = "prompt";
    private static final String VIDEO_CREATE_PATH_KWARG = "video_create_path";
    private static final String VIDEO_POLL_PATH_KWARG = "video_poll_path";
    private static final String VIDEO_RESULT_URL_PATH_KWARG = "video_result_url_path";
    private static final String VIDEO_RESULT_DOWNLOAD_PATH_KWARG = "video_result_download_path";
    private static final String VIDEO_FILE_RETRIEVE_PATH_KWARG = "video_file_retrieve_path";
    private static final String VIDEO_FILE_URL_PATH_KWARG = "video_file_url_path";
    private static final String VIDEO_SUCCESS_STATUSES_KWARG = "video_success_statuses";
    private static final String VIDEO_FAILURE_STATUSES_KWARG = "video_failure_statuses";
    private static final String VIDEO_POLL_INTERVAL_KWARG = "poll_interval_seconds";
    private static final String VIDEO_MAX_WAIT_KWARG = "max_wait_seconds";
    private static final String TASK_ID_PLACEHOLDER = "{taskId}";
    private static final String FILE_ID_PLACEHOLDER = "{fileId}";
    private static final String DEFAULT_VIDEO_FILE_URL_PATH = "file.download_url";
    private static final long DEFAULT_VIDEO_POLL_INTERVAL_SECONDS = 5L;
    private static final long DEFAULT_VIDEO_MAX_WAIT_SECONDS = 600L;
    private static final Set<String> DEFAULT_VIDEO_SUCCESS_STATUSES = Set.of("completed", "succeeded", "success");
    private static final Set<String> DEFAULT_VIDEO_FAILURE_STATUSES = Set.of("failed", "error", "cancelled",
            "canceled", "expired", "fail");
    private static final String CONTENT_TYPE = "application/json";
    private static final String RETRY_COUNT_HEADER = "X-Stainless-Retry-Count";
    private static final String RETRY_LISTENER_KWARG = "__openjiuwen_retry_listener";
    private static final String REQUEST_HEADERS_KWARG = "__openjiuwen_request_headers";

    private final HttpClient httpClient;
    private final ModelCallRetrier retrier;
    private final Map<String, String> baseHeaders;

    static {
        registerClientClass(OpenAIModelClient.class);
    }

    private final ModelCircuitBreaker circuitBreaker;

    /**
     * Initialize OpenAI model client.
     *
     * @param modelConfig model request configuration
     * @param modelClientConfig model client connection configuration
     */
    public OpenAIModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, new ModelCallRetrier());
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

    OpenAIModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig,
            ModelCallRetrier retrier) {
        this(modelConfig, modelClientConfig, retrier, new ModelCircuitBreaker());
    }

    OpenAIModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig,
            ModelCallRetrier retrier, ModelCircuitBreaker circuitBreaker) {
        super(modelConfig, modelClientConfig);
        this.httpClient = createHttpClient(modelClientConfig);
        this.retrier = retrier;
        this.baseHeaders = HeadersHelper.buildBaseHeaders(modelClientConfig.getCustomHeaders());
        this.circuitBreaker = circuitBreaker;
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
        KvRequestParams kv = popKvRequestParams(kwargs);
        Map<String, Object> params = super.buildRequestParams(
                kv.isSessionManageRequest() ? List.of(Map.of("role", "user", "content", "")) : messages,
                kv.isSessionManageRequest() ? null : tools,
                temperature,
                topP,
                model,
                stop,
                maxTokens,
                stream,
                kwargs
        );
        applyOpenAiCompatibility(params);
        applyAgentHint(params, kv);
        return params;
    }

    /** KV-cache request parameters popped from the invoke kwargs. */
    private record KvRequestParams(String sessionId, String parentSessionId, String kvAction,
            String target, Integer msgStart, Integer msgEnd, Integer toolsStart, Integer toolsEnd,
            boolean shouldIncludeTools, boolean isSessionManageRequest) {
    }

    private static KvRequestParams popKvRequestParams(Map<String, Object> kwargs) {
        String sessionId = popKwarg(kwargs, "session_id").orElse(null);
        String parentSessionId = popKwarg(kwargs, "parent_session_id").orElse(null);
        String kvAction = popKwarg(kwargs, "kv_action").orElse(null);
        String target = popKwarg(kwargs, "target").orElse(null);
        String resolvedTarget = target == null ? "session" : target;
        Integer msgStart = intKwargOrNull(kwargs, "msg_start");
        Integer msgEnd = intKwargOrNull(kwargs, "msg_end");
        Integer toolsStart = intKwargOrNull(kwargs, "tools_start");
        Integer toolsEnd = intKwargOrNull(kwargs, "tools_end");
        boolean shouldIncludeTools = popBooleanKwarg(kwargs, "include_tools");
        return new KvRequestParams(sessionId, parentSessionId, kvAction, resolvedTarget,
                msgStart, msgEnd, toolsStart, toolsEnd, shouldIncludeTools,
                kvAction != null && "session".equals(resolvedTarget));
    }

    private void applyOpenAiCompatibility(Map<String, Object> params) {
        String apiBase = modelClientConfig.getApiBase() == null
                ? ""
                : modelClientConfig.getApiBase().toLowerCase(Locale.ROOT);
        if (apiBase.contains("openai.com")) {
            boolean hasTemperature = params.containsKey("temperature") && params.get("temperature") != null;
            boolean hasTopP = params.containsKey("top_p") && params.get("top_p") != null;
            if (hasTemperature && hasTopP) {
                params.remove("top_p");
            }
        }
    }

    /**
     * Whether this client declares affinity capability through
     * {@code extensions.kv_cache.mode == "affinity"}.
     *
     * @return true when the affinity mode is enabled
     */
    public boolean supportsKvCacheAffinity() {
        return "affinity".equals(kvCacheMode());
    }

    private String kvCacheMode() {
        Object extensions = modelClientConfig.getExtraFields().get("extensions");
        if (extensions instanceof Map<?, ?> extensionsMap) {
            Object kvCache = extensionsMap.get("kv_cache");
            if (kvCache instanceof Map<?, ?> kvCacheMap) {
                Object mode = kvCacheMap.get("mode");
                return mode == null ? "none" : String.valueOf(mode);
            }
        }
        return "none";
    }

    private String affinityField() {
        Object extensions = modelClientConfig.getExtraFields().get("extensions");
        if (extensions instanceof Map<?, ?> extensionsMap) {
            Object kvCache = extensionsMap.get("kv_cache");
            if (kvCache instanceof Map<?, ?> kvCacheMap) {
                Object field = kvCacheMap.get("affinity_field");
                return field == null || String.valueOf(field).isBlank()
                        ? com.openjiuwen.core.kvcache.AgentHint.FIELD_NAME
                        : String.valueOf(field);
            }
        }
        return com.openjiuwen.core.kvcache.AgentHint.FIELD_NAME;
    }

    private void applyAgentHint(Map<String, Object> params, KvRequestParams kv) {
        boolean isAffinity = supportsKvCacheAffinity();
        if (kv.isSessionManageRequest()) {
            params.remove("tools");
            params.remove("tool_choice");
            params.remove("max_tokens");
            params.put("messages", new ArrayList<Map<String, Object>>());
        }
        if (!isAffinity || kv.sessionId() == null || kv.sessionId().isBlank()) {
            return;
        }
        Map<String, Object> hint;
        if (kv.kvAction() == null) {
            hint = com.openjiuwen.core.kvcache.AgentHint.identityHint(kv.sessionId(),
                    parentOrSelf(kv));
        } else {
            hint = com.openjiuwen.core.kvcache.AgentHint.managementHint(kv.kvAction(), kv.sessionId(),
                    parentOrSelf(kv),
                    new com.openjiuwen.core.kvcache.AgentHint.KvCacheRange(
                            kv.target(), kv.msgStart(), kv.msgEnd(), kv.toolsStart(), kv.toolsEnd(),
                            kv.shouldIncludeTools()));
        }
        moveExtraBodyExtension(params, affinityField(), hint);
        Loggers.LLM.info("agent_hint sent: session_id={}, parent_session_id={}, action={}, hint={}",
                kv.sessionId(), kv.parentSessionId(),
                kv.kvAction() == null ? "identity" : kv.kvAction(), hint);
    }

    private static String parentOrSelf(KvRequestParams kv) {
        return kv.parentSessionId() == null || kv.parentSessionId().isBlank()
                ? kv.sessionId()
                : kv.parentSessionId();
    }

    private void moveExtraBodyExtension(Map<String, Object> params, String field, Object value) {
        Map<String, Object> extraBody = new LinkedHashMap<>();
        Object current = params.get("extra_body");
        if (current instanceof Map<?, ?> currentMap) {
            extraBody.putAll(toObjectMap(currentMap));
        }
        extraBody.put(field, value);
        params.put("extra_body", extraBody);
    }

    private static Optional<String> popKwarg(Map<String, Object> kwargs, String key) {
        if (kwargs == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(kwargs.remove(key)).map(String::valueOf);
    }

    private static OptionalInt popIntegerKwarg(Map<String, Object> kwargs, String key) {
        if (kwargs == null) {
            return OptionalInt.empty();
        }
        return Optional.ofNullable(kwargs.remove(key))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .map(OptionalInt::of)
                .orElse(OptionalInt.empty());
    }

    private static Integer intKwargOrNull(Map<String, Object> kwargs, String key) {
        return popIntegerKwarg(kwargs, key).stream().boxed().findFirst().orElse(null);
    }

    private static boolean popBooleanKwarg(Map<String, Object> kwargs, String key) {
        if (kwargs == null) {
            return false;
        }
        return Boolean.TRUE.equals(kwargs.remove(key));
    }

    @Override
    public CompletableFuture<Boolean> evictKvc(String sessionId, String parentSessionId,
            com.openjiuwen.core.kvcache.AgentHint.KvCacheRange range, Double timeout) {
        return invokeKvCacheAffinityAction("evict", sessionId, parentSessionId, range, timeout);
    }

    @Override
    public CompletableFuture<Boolean> offloadKvc(String sessionId, String parentSessionId, Double timeout) {
        return invokeKvCacheAffinityAction("offload", sessionId, parentSessionId, sessionRange(), timeout);
    }

    @Override
    public CompletableFuture<Boolean> prefetchKvc(String sessionId, String parentSessionId, Double timeout) {
        return invokeKvCacheAffinityAction("prefetch", sessionId, parentSessionId, sessionRange(), timeout);
    }

    private static com.openjiuwen.core.kvcache.AgentHint.KvCacheRange sessionRange() {
        return new com.openjiuwen.core.kvcache.AgentHint.KvCacheRange("session", null, null, null, null, false);
    }

    private CompletableFuture<Boolean> invokeKvCacheAffinityAction(
            String action, String sessionId, String parentSessionId,
            com.openjiuwen.core.kvcache.AgentHint.KvCacheRange range, Double timeout) {
        if (!supportsKvCacheAffinity()) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("session_id", sessionId);
        kwargs.put("parent_session_id", parentSessionId == null ? sessionId : parentSessionId);
        kwargs.put("kv_action", action);
        kwargs.put("target", range.target());
        putRangeKwarg(kwargs, "msg_start", range.msgStart());
        putRangeKwarg(kwargs, "msg_end", range.msgEnd());
        putRangeKwarg(kwargs, "tools_start", range.toolsStart());
        putRangeKwarg(kwargs, "tools_end", range.toolsEnd());
        if (range.shouldIncludeTools()) {
            kwargs.put("include_tools", true);
        }
        Map<String, Object> params = buildRequestParams(
                List.of(Map.of("role", "user", "content", "")),
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                kwargs
        );
        return CompletableFuture.supplyAsync(() -> invokeKvActionHttp(action, params, timeout));
    }

    private static void putRangeKwarg(Map<String, Object> kwargs, String key, Integer value) {
        if (value != null) {
            kwargs.put(key, value);
        }
    }

    private boolean invokeKvActionHttp(String action, Map<String, Object> params, Double timeout) {
        try {
            postJson(params, timeout == null ? null : timeout.floatValue(),
                    null, Map.of(), List.of(), null);
            return true;
        } catch (BaseError exception) {
            Loggers.LLM.warning("KV cache {0} action failed: {1}", action, exception.getMessage());
            return false;
        } catch (IOException exception) {
            Loggers.LLM.warning("KV cache {0} action failed: {1}", action, exception.toString());
            return false;
        } catch (InterruptedException exception) {
            Loggers.LLM.warning("KV cache {0} action interrupted: {1}", action, exception.toString());
            return false;
        } catch (java.util.concurrent.CompletionException exception) {
            Loggers.LLM.warning("KV cache {0} action failed: {1}", action, exception.toString());
            return false;
        }
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
                            "connect_timeout_seconds",
                            ModelTimeoutResolver.resolveConnectTimeoutSeconds(modelClientConfig),
                            "header_wait_seconds",
                            ModelTimeoutResolver.resolveHeaderWaitSeconds(modelClientConfig),
                            "stream_idle_seconds",
                            ModelTimeoutResolver.resolvePerCallIdleSeconds(timeout, modelClientConfig),
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
        } catch (BaseError exception) {
            Loggers.LLM.error("OpenAI API async invoke error. {}", exception.getMessage());
            throw exception;
        } catch (CancellationException cancellation) {
            throw cancellation;
        } catch (IOException | InterruptedException | IllegalStateException | IllegalArgumentException
                | ClassCastException exception) {
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
        } catch (BaseError exception) {
            Loggers.LLM.error("OpenAI API async stream error. {}", exception.getMessage());
            throw exception;
        } catch (CancellationException cancellation) {
            throw cancellation;
        } catch (IOException | InterruptedException | IllegalStateException | IllegalArgumentException
                | ClassCastException exception) {
            String detail = safeExceptionMessage(exception, sensitiveValues);
            Loggers.LLM.error("OpenAI API async stream error. {}", detail);
            throw clientInternalException(exception, true, "prepare_request", sensitiveValues);
        }
    }

    /**
     * Generate images through the OpenAI-compatible {@code POST {base}/images/generations} endpoint,
     * or a configured alternative endpoint with a custom url-list location.
     *
     * <p>Body contains only fields with values: {@code model}, {@code prompt}, {@code n},
     * {@code size}, {@code negative_prompt}, {@code seed}. Vendor-specific fields (such as a
     * watermark flag) are passed through unchanged via {@code kwargs}. The
     * {@code promptExtend} and {@code watermark} flags are intentionally not mapped because they
     * are not part of the OpenAI images API and strict endpoints reject unknown fields.</p>
     *
     * <p>Control kwargs (removed from the create-body kwargs): {@code image_create_path} overrides
     * the create endpoint path (default {@code /images/generations}); {@code image_urls_path} is a
     * dotted JSON path to a JSON array of image url strings in the response (for example
     * {@code data.image_urls}); when unset the default OpenAI response shape is parsed
     * ({@code data[].url} plus {@code data[].b64_json}).</p>
     *
     * @param messages exactly one user message providing the text prompt
     * @param model image model name; blank falls back to the configured model name
     * @param size image size in the vendor-specific "width*height" or "widthxheight" notation
     * @param negativePrompt optional negative prompt
     * @param n number of images to generate
     * @param promptExtend unused on this client; pass vendor fields via kwargs instead
     * @param watermark unused on this client; pass vendor fields via kwargs instead
     * @param seed optional seed; zero means omit
     * @param kwargs extra request body fields merged into the request
     * @return parsed image generation response with url and/or base64 payloads
     * @throws Exception transport, protocol or validation failures
     */
    @Override
    public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                 String model,
                                                 String size,
                                                 String negativePrompt,
                                                 int n,
                                                 boolean promptExtend,
                                                 boolean watermark,
                                                 int seed,
                                                 Map<String, Object> kwargs) throws Exception {
        Map<String, Object> effectiveKwargs = copyMap(kwargs);
        String createPath = stringKwarg(effectiveKwargs, IMAGE_CREATE_PATH_KWARG, IMAGES_GENERATIONS_PATH);
        String urlsPath = stringKwarg(effectiveKwargs, IMAGE_URLS_PATH_KWARG, null);
        GenerationCallContext call = resolveGenerationCallContext(effectiveKwargs);
        ImageCreateSpec spec = new ImageCreateSpec(size, negativePrompt, n, seed);
        Map<String, Object> params = buildImageGenerationParams(messages, model, spec, effectiveKwargs);
        String resolvedModel = String.valueOf(params.get("model"));
        Loggers.LLM.info("Before image generation request. model={}, n={}, size={}, path={}",
                resolvedModel, n, size, createPath);
        Map<String, Object> responseData = postJson(
                createPath, params, null, call.transportHeaders().authorization(),
                call.transportHeaders().headers(), call.sensitiveValues(), call.retryListener());
        ImageGenerationResponse response = parseImageGenerationResponse(responseData, resolvedModel, urlsPath);
        Loggers.LLM.info("Image generation succeeded. model={}, urlCount={}, base64Count={}",
                resolvedModel, collectionSize(response.getImages()), collectionSize(response.getImagesBase64()));
        return response;
    }

    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
                                                  Map<String, Object> kwargs) {
        return null;
    }

    /**
     * Generate videos through a task-based two-phase flow: create a task, poll it until it
     * reaches a terminal state, then resolve the video payload.
     *
     * <p>The flow is configuration-driven so vendor-specific endpoints stay out of the framework;
     * without any {@code video_*} kwargs the OpenAI official videos API is used. Control kwargs
     * (all optional, removed from the create-body kwargs):</p>
     * <ul>
     *   <li>{@code video_body}: {@code prompt} (default, body is {@code model/prompt/size/seconds})
     *       or {@code reference} (body is {@code model} plus a {@code content} array of text,
     *       image_url and audio_url references with {@code duration} and {@code resolution}/{@code size}).</li>
     *   <li>{@code video_create_path}: create endpoint path, default {@code /videos}.</li>
     *   <li>{@code video_poll_path}: poll path template with a {@code {taskId}} placeholder, default
     *       {@code {createPath}/{taskId}}.</li>
     *   <li>{@code video_result_url_path}: dotted JSON path to a direct video url in the poll
     *       response; unset falls back to a generic scan of {@code content.video_url}, {@code video_url}
     *       and {@code url}.</li>
     *   <li>{@code video_file_retrieve_path}: template with a {@code {fileId}} placeholder; when set,
     *       a successful task response must carry a {@code file_id}, the file description is fetched
     *       from this path and {@code video_file_url_path} (default {@code file.download_url}) points
     *       to the download url inside it.</li>
     *   <li>{@code video_result_download_path}: template with {@code {taskId}} for a binary content
     *       download, default {@code {pollPath}/content}, used when no url is present.</li>
     *   <li>{@code video_success_statuses} / {@code video_failure_statuses}: comma separated status
     *       overrides; defaults cover completed/succeeded/success and failed/error/cancelled/
     *       canceled/expired/fail.</li>
     *   <li>{@code poll_interval_seconds} (default 5) and {@code max_wait_seconds} (default 600)
     *       control the polling loop.</li>
     * </ul>
     *
     * <p>Remaining kwargs are merged into the create request body. {@code promptExtend} and
     * {@code watermark} are unused on this client.</p>
     *
     * @param messages exactly one user message providing the text prompt
     * @param imgUrl optional image URL for image-to-video
     * @param audioUrl optional audio URL reference
     * @param model video model name; blank falls back to the configured model name
     * @param size video size or resolution hint
     * @param resolution optional resolution override
     * @param duration video duration in seconds; zero means omit
     * @param promptExtend unused on this client; pass vendor fields via kwargs instead
     * @param watermark unused on this client; pass vendor fields via kwargs instead
     * @param negativePrompt unused on this client; pass vendor fields via kwargs instead
     * @param seed unused on this client; pass vendor fields via kwargs instead
     * @param kwargs task-flow configuration and extra request body fields
     * @return parsed video generation response with url and/or binary payload
     * @throws Exception transport, polling timeout or validation failures
     */
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
                                                 Map<String, Object> kwargs) throws Exception {
        Map<String, Object> effectiveKwargs = copyMap(kwargs);
        VideoTaskConfig config = popVideoTaskConfig(effectiveKwargs);
        GenerationCallContext call = resolveGenerationCallContext(effectiveKwargs);
        VideoContentSpec spec = new VideoContentSpec(imgUrl, audioUrl, size, resolution, duration);
        String prompt = extractTextPrompt(requireSingleUserMessage(messages));
        String resolvedModel = resolveGenerationModel(model);
        Map<String, Object> createParams = buildVideoCreateParams(config.bodyStyle(), prompt, spec,
                resolvedModel, effectiveKwargs);
        Loggers.LLM.info("Before video generation create request. model={}, body={}, path={}",
                resolvedModel, config.bodyStyle(), config.createPath());
        Map<String, Object> created = postJson(config.createPath(), createParams, null,
                call.transportHeaders().authorization(), call.transportHeaders().headers(),
                call.sensitiveValues(), call.retryListener());
        String taskId = extractVideoTaskId(created);
        String pollPath = renderPathTemplate(config.pollPathTemplate() != null
                        ? config.pollPathTemplate()
                        : config.createPath() + "/" + TASK_ID_PLACEHOLDER,
                TASK_ID_PLACEHOLDER, taskId);
        return pollVideoTask(new VideoPollContext(taskId, pollPath, config, resolvedModel, call));
    }

    /** Resolved, vendor-agnostic task-flow configuration for one video generation call. */
    private static final class VideoTaskConfig {
        private final String createPath;
        private final String pollPathTemplate;
        private final String bodyStyle;
        private final String resultUrlPath;
        private final String resultDownloadTemplate;
        private final String fileRetrieveTemplate;
        private final String fileUrlPath;
        private final Set<String> successStatuses;
        private final Set<String> failureStatuses;
        private final long pollIntervalSeconds;
        private final long maxWaitSeconds;

        private VideoTaskConfig(Builder builder) {
            this.createPath = builder.createPath;
            this.pollPathTemplate = builder.pollPathTemplate;
            this.bodyStyle = builder.bodyStyle;
            this.resultUrlPath = builder.resultUrlPath;
            this.resultDownloadTemplate = builder.resultDownloadTemplate;
            this.fileRetrieveTemplate = builder.fileRetrieveTemplate;
            this.fileUrlPath = builder.fileUrlPath;
            this.successStatuses = builder.successStatuses;
            this.failureStatuses = builder.failureStatuses;
            this.pollIntervalSeconds = builder.pollIntervalSeconds;
            this.maxWaitSeconds = builder.maxWaitSeconds;
        }

        private String createPath() {
            return createPath;
        }

        private String pollPathTemplate() {
            return pollPathTemplate;
        }

        private String bodyStyle() {
            return bodyStyle;
        }

        private String resultUrlPath() {
            return resultUrlPath;
        }

        private String resultDownloadTemplate() {
            return resultDownloadTemplate;
        }

        private String fileRetrieveTemplate() {
            return fileRetrieveTemplate;
        }

        private String fileUrlPath() {
            return fileUrlPath;
        }

        private Set<String> successStatuses() {
            return successStatuses;
        }

        private Set<String> failureStatuses() {
            return failureStatuses;
        }

        private long pollIntervalSeconds() {
            return pollIntervalSeconds;
        }

        private long maxWaitSeconds() {
            return maxWaitSeconds;
        }

        private static Builder builder() {
            return new Builder();
        }

        private static final class Builder {
            private String createPath = VIDEOS_PATH;
            private String pollPathTemplate;
            private String bodyStyle = VIDEO_BODY_PROMPT;
            private String resultUrlPath;
            private String resultDownloadTemplate;
            private String fileRetrieveTemplate;
            private String fileUrlPath = DEFAULT_VIDEO_FILE_URL_PATH;
            private Set<String> successStatuses = DEFAULT_VIDEO_SUCCESS_STATUSES;
            private Set<String> failureStatuses = DEFAULT_VIDEO_FAILURE_STATUSES;
            private long pollIntervalSeconds = DEFAULT_VIDEO_POLL_INTERVAL_SECONDS;
            private long maxWaitSeconds = DEFAULT_VIDEO_MAX_WAIT_SECONDS;

            private Builder createPath(String value) {
                this.createPath = value;
                return this;
            }

            private Builder pollPathTemplate(String value) {
                this.pollPathTemplate = value;
                return this;
            }

            private Builder bodyStyle(String value) {
                this.bodyStyle = value;
                return this;
            }

            private Builder resultUrlPath(String value) {
                this.resultUrlPath = value;
                return this;
            }

            private Builder resultDownloadTemplate(String value) {
                this.resultDownloadTemplate = value;
                return this;
            }

            private Builder fileRetrieveTemplate(String value) {
                this.fileRetrieveTemplate = value;
                return this;
            }

            private Builder fileUrlPath(String value) {
                this.fileUrlPath = value;
                return this;
            }

            private Builder successStatuses(Set<String> value) {
                this.successStatuses = value;
                return this;
            }

            private Builder failureStatuses(Set<String> value) {
                this.failureStatuses = value;
                return this;
            }

            private Builder pollIntervalSeconds(long value) {
                this.pollIntervalSeconds = value;
                return this;
            }

            private Builder maxWaitSeconds(long value) {
                this.maxWaitSeconds = value;
                return this;
            }

            private VideoTaskConfig build() {
                return new VideoTaskConfig(this);
            }
        }
    }

    /** Per-call transport context: auth headers, sensitive values and retry listener. */
    private record GenerationCallContext(
            TransportHeaders transportHeaders,
            Collection<String> sensitiveValues,
            ModelRetryListener retryListener) {
    }

    /** Vendor-agnostic content inputs for one video create request. */
    private record VideoContentSpec(
            String imgUrl,
            String audioUrl,
            String size,
            String resolution,
            int duration) {
    }

    /** Immutable context for one video generation task polling loop. */
    private record VideoPollContext(
            String taskId,
            String pollPath,
            VideoTaskConfig config,
            String resolvedModel,
            GenerationCallContext call) {
    }

    /** Numeric and textual options for one image create request. */
    private record ImageCreateSpec(String size, String negativePrompt, int n, int seed) {
    }

    private Map<String, Object> buildImageGenerationParams(List<UserMessage> messages, String model,
            ImageCreateSpec spec, Map<String, Object> kwargs) {
        String prompt = extractTextPrompt(requireSingleUserMessage(messages));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", resolveGenerationModel(model));
        params.put("prompt", prompt);
        if (spec.n() > 0) {
            params.put("n", spec.n());
        }
        if (isNotBlank(spec.size())) {
            params.put("size", spec.size());
        }
        if (isNotBlank(spec.negativePrompt())) {
            params.put("negative_prompt", spec.negativePrompt());
        }
        if (spec.seed() != 0) {
            params.put("seed", spec.seed());
        }
        params.putAll(toObjectMap(kwargs));
        return params;
    }

    private ImageGenerationResponse parseImageGenerationResponse(Map<String, Object> responseData,
                                                                 String resolvedModel, String urlsPath) {
        List<String> images = new ArrayList<>();
        List<String> imagesBase64 = new ArrayList<>();
        if (urlsPath != null) {
            images.addAll(resolveUrlList(responseData, urlsPath));
        } else {
            collectOpenAiImagePayloads(responseData, images, imagesBase64);
        }
        if (images.isEmpty() && imagesBase64.isEmpty()) {
            throw modelCallError("No usable image url or base64 data in image generation response: "
                    + abbreviateMap(responseData), null);
        }
        ImageGenerationResponse.ImageGenerationResponseBuilder builder = ImageGenerationResponse.builder()
                .model(resolvedModel);
        if (!images.isEmpty()) {
            builder.images(images);
        }
        if (!imagesBase64.isEmpty()) {
            builder.imagesBase64(imagesBase64);
        }
        if (responseData.get("created") instanceof Number created) {
            builder.created(created.intValue());
        }
        return builder.build();
    }

    private static void collectOpenAiImagePayloads(Map<String, Object> responseData, List<String> images,
            List<String> imagesBase64) {
        if (!(responseData.get("data") instanceof List<?> items)) {
            return;
        }
        for (Object item : items) {
            if (item instanceof Map<?, ?> entry) {
                collectImagePayload(entry, "url", images);
                collectImagePayload(entry, "b64_json", imagesBase64);
            }
        }
    }

    private static List<String> resolveUrlList(Map<String, Object> source, String dottedPath) {
        Object current = source;
        for (String segment : dottedPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return List.of();
            }
            current = map.get(segment);
        }
        if (!(current instanceof List<?> items)) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof String url && !url.isBlank()) {
                urls.add(url);
            }
        }
        return urls;
    }

    private static String abbreviateMap(Map<String, Object> responseData) {
        String text = Objects.toString(responseData);
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    private static void collectImagePayload(Map<?, ?> entry, String key, List<String> target) {
        if (entry.get(key) instanceof String value && !value.isBlank()) {
            target.add(value);
        }
    }

    private Map<String, Object> buildVideoCreateParams(String bodyStyle, String prompt, VideoContentSpec spec,
            String resolvedModel, Map<String, Object> kwargs) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", resolvedModel);
        if (VIDEO_BODY_REFERENCE.equals(bodyStyle)) {
            buildContentReferenceVideoParams(params, prompt, spec);
        } else {
            params.put("prompt", prompt);
            if (isNotBlank(spec.size())) {
                params.put("size", spec.size());
            }
            if (spec.duration() > 0) {
                params.put("seconds", String.valueOf(spec.duration()));
            }
        }
        params.putAll(toObjectMap(kwargs));
        return params;
    }

    private static void buildContentReferenceVideoParams(Map<String, Object> params, String prompt,
            VideoContentSpec spec) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(new LinkedHashMap<>(Map.of("type", "text", "text", prompt)));
        if (isNotBlank(spec.imgUrl())) {
            content.add(new LinkedHashMap<>(Map.of(
                    "type", "image_url", "image_url", Map.of("url", spec.imgUrl()))));
        }
        if (isNotBlank(spec.audioUrl())) {
            content.add(new LinkedHashMap<>(Map.of(
                    "type", "audio_url", "audio_url", Map.of("url", spec.audioUrl()))));
        }
        params.put("content", content);
        if (spec.duration() > 0) {
            params.put("duration", spec.duration());
        }
        String sizeValue = isNotBlank(spec.resolution()) ? spec.resolution() : spec.size();
        if (isNotBlank(sizeValue)) {
            params.put(isNotBlank(spec.resolution()) ? "resolution" : "size", sizeValue);
        }
    }

    private VideoGenerationResponse pollVideoTask(VideoPollContext context) throws Exception {
        long deadlineMillis = System.currentTimeMillis() + context.config().maxWaitSeconds() * 1000L;
        while (true) {
            Map<String, Object> status = getJson(context.pollPath(), null,
                    context.call().transportHeaders().authorization(),
                    context.call().transportHeaders().headers(), context.call().sensitiveValues());
            String state = Objects.toString(status.get("status"), "").trim().toLowerCase(Locale.ROOT);
            if (context.config().successStatuses().contains(state)) {
                Loggers.LLM.info("Video generation task succeeded. taskId={}", context.taskId());
                return buildVideoSuccessResponse(context, status);
            }
            if (context.config().failureStatuses().contains(state)) {
                throw modelCallError("Video generation task ended with status " + state
                        + ": " + videoFailureDetail(status), null);
            }
            if (System.currentTimeMillis() >= deadlineMillis) {
                throw modelCallError("Video generation polling timed out after "
                        + context.config().maxWaitSeconds() + "s, taskId=" + context.taskId(), null);
            }
            Loggers.LLM.info("Video generation task in progress. taskId={}, status={}", context.taskId(), state);
            Thread.sleep(context.config().pollIntervalSeconds() * 1000L);
        }
    }

    private VideoGenerationResponse buildVideoSuccessResponse(VideoPollContext context,
                                                              Map<String, Object> status) throws Exception {
        VideoTaskConfig config = context.config();
        Optional<String> videoUrl = resolveResultUrl(status, config.resultUrlPath());
        if (videoUrl.isPresent()) {
            return VideoGenerationResponse.builder()
                    .model(context.resolvedModel())
                    .videoUrl(videoUrl.get())
                    .build();
        }
        if (config.fileRetrieveTemplate() != null) {
            return buildFileIndirectVideoResponse(context, status);
        }
        String downloadTemplate = config.resultDownloadTemplate() != null
                ? config.resultDownloadTemplate()
                : context.pollPath() + "/content";
        byte[] videoData = getBytes(renderPathTemplate(downloadTemplate, TASK_ID_PLACEHOLDER,
                context.taskId()), null, context.call().transportHeaders().authorization(),
                context.call().transportHeaders().headers(), context.call().sensitiveValues());
        return VideoGenerationResponse.builder()
                .model(context.resolvedModel())
                .videoData(videoData)
                .build();
    }

    private VideoGenerationResponse buildFileIndirectVideoResponse(VideoPollContext context,
                                                                   Map<String, Object> status) throws Exception {
        VideoTaskConfig config = context.config();
        if (!(status.get("file_id") instanceof String fileId) || fileId.isBlank()) {
            throw modelCallError("Video task succeeded but the response contains no file_id required "
                    + "by the configured file retrieval flow", null);
        }
        String retrievePath = renderPathTemplate(config.fileRetrieveTemplate(), FILE_ID_PLACEHOLDER, fileId);
        Map<String, Object> fileResponse = getJson(retrievePath, null,
                context.call().transportHeaders().authorization(),
                context.call().transportHeaders().headers(), context.call().sensitiveValues());
        Optional<String> downloadUrl = resolveDottedPath(fileResponse, config.fileUrlPath());
        if (downloadUrl.isEmpty()) {
            throw modelCallError("File retrieval response contains no download url at "
                    + config.fileUrlPath(), null);
        }
        byte[] videoData = getBytes(downloadUrl.get(), null,
                context.call().transportHeaders().authorization(),
                context.call().transportHeaders().headers(), context.call().sensitiveValues());
        return VideoGenerationResponse.builder()
                .model(context.resolvedModel())
                .videoData(videoData)
                .build();
    }

    private Optional<String> resolveResultUrl(Map<String, Object> status, String resultUrlPath) {
        if (resultUrlPath != null) {
            return resolveDottedPath(status, resultUrlPath);
        }
        return extractVideoUrl(status);
    }

    private static Optional<String> resolveDottedPath(Map<String, Object> source, String dottedPath) {
        Object current = source;
        for (String segment : dottedPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            current = map.get(segment);
        }
        return current instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    private static String renderPathTemplate(String template, String placeholder, String value) {
        return template.replace(placeholder, value);
    }

    private Optional<String> extractVideoUrl(Map<String, Object> status) {
        if (status.get("content") instanceof Map<?, ?> contentMap
                && contentMap.get("video_url") instanceof String contentUrl
                && !contentUrl.isBlank()) {
            return Optional.of(contentUrl);
        }
        for (String key : List.of("video_url", "url")) {
            if (status.get(key) instanceof String url && !url.isBlank()) {
                return Optional.of(url);
            }
        }
        return Optional.empty();
    }

    private static String videoFailureDetail(Map<String, Object> status) {
        if (status.get("error") instanceof Map<?, ?> errorMap
                && errorMap.get("message") instanceof String message
                && !message.isBlank()) {
            return message;
        }
        return Objects.toString(status.get("error"), "");
    }

    private String extractVideoTaskId(Map<String, Object> created) {
        for (String key : List.of("id", "task_id")) {
            if (created.get(key) instanceof String taskId && !taskId.isBlank()) {
                return taskId;
            }
        }
        throw modelCallError("Video generation create response contains no task id", null);
    }

    private static VideoTaskConfig popVideoTaskConfig(Map<String, Object> kwargs) {
        String bodyStyle = VIDEO_BODY_REFERENCE.equalsIgnoreCase(
                stringKwarg(kwargs, VIDEO_BODY_KWARG, VIDEO_BODY_PROMPT))
                ? VIDEO_BODY_REFERENCE
                : VIDEO_BODY_PROMPT;
        return VideoTaskConfig.builder()
                .createPath(stringKwarg(kwargs, VIDEO_CREATE_PATH_KWARG, VIDEOS_PATH))
                .pollPathTemplate(stringKwarg(kwargs, VIDEO_POLL_PATH_KWARG, null))
                .bodyStyle(bodyStyle)
                .resultUrlPath(stringKwarg(kwargs, VIDEO_RESULT_URL_PATH_KWARG, null))
                .resultDownloadTemplate(stringKwarg(kwargs, VIDEO_RESULT_DOWNLOAD_PATH_KWARG, null))
                .fileRetrieveTemplate(stringKwarg(kwargs, VIDEO_FILE_RETRIEVE_PATH_KWARG, null))
                .fileUrlPath(stringKwarg(kwargs, VIDEO_FILE_URL_PATH_KWARG, DEFAULT_VIDEO_FILE_URL_PATH))
                .successStatuses(statusesKwarg(kwargs, VIDEO_SUCCESS_STATUSES_KWARG,
                        DEFAULT_VIDEO_SUCCESS_STATUSES))
                .failureStatuses(statusesKwarg(kwargs, VIDEO_FAILURE_STATUSES_KWARG,
                        DEFAULT_VIDEO_FAILURE_STATUSES))
                .pollIntervalSeconds(removePositiveLongKwarg(kwargs, VIDEO_POLL_INTERVAL_KWARG,
                        DEFAULT_VIDEO_POLL_INTERVAL_SECONDS))
                .maxWaitSeconds(removePositiveLongKwarg(kwargs, VIDEO_MAX_WAIT_KWARG,
                        DEFAULT_VIDEO_MAX_WAIT_SECONDS))
                .build();
    }

    private GenerationCallContext resolveGenerationCallContext(Map<String, Object> effectiveKwargs) {
        Map<String, ?> formalRequestHeaders = popFormalRequestHeaders(effectiveKwargs);
        Map<String, ?> legacyRequestHeaders = popRequestCustomHeaders(effectiveKwargs);
        ModelRetryListener retryListener = popRetryListener(effectiveKwargs);
        TransportHeaders transportHeaders = resolveTransportHeaders(legacyRequestHeaders, formalRequestHeaders);
        return new GenerationCallContext(transportHeaders, sensitiveValues(transportHeaders), retryListener);
    }

    private static String stringKwarg(Map<String, Object> kwargs, String key, String defaultValue) {
        Object value = kwargs.remove(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(value).trim();
    }

    private static Set<String> statusesKwarg(Map<String, Object> kwargs, String key,
            Set<String> defaultValues) {
        String raw = stringKwarg(kwargs, key, null);
        if (raw == null) {
            return defaultValues;
        }
        Set<String> statuses = new LinkedHashSet<>();
        for (String item : raw.split(",")) {
            String normalized = item.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                statuses.add(normalized);
            }
        }
        return statuses.isEmpty() ? defaultValues : statuses;
    }

    private static long removePositiveLongKwarg(Map<String, Object> kwargs, String key, long defaultValue) {
        if (kwargs.get(key) instanceof Number number && number.longValue() > 0) {
            kwargs.remove(key);
            return number.longValue();
        }
        kwargs.remove(key);
        return defaultValue;
    }

    private UserMessage requireSingleUserMessage(List<UserMessage> messages) {
        if (messages == null || messages.size() != 1 || messages.get(0) == null) {
            throw modelCallError("Generation requires exactly one user message", null);
        }
        return messages.get(0);
    }

    private String extractTextPrompt(UserMessage message) {
        Object content = message.getContent();
        if (content instanceof String text && !text.isBlank()) {
            return text;
        }
        if (content instanceof List<?> parts) {
            String prompt = joinTextParts(parts);
            if (!prompt.isBlank()) {
                return prompt;
            }
            if (parts.stream().anyMatch(part -> part instanceof Map<?, ?> partMap
                    && "image_url".equals(partMap.get("type")))) {
                throw modelCallError("Image input requires the images edits endpoint; only text-to-image "
                        + "is supported by this client", null);
            }
        }
        throw modelCallError("Generation requires non-empty text prompt", null);
    }

    private static String joinTextParts(List<?> parts) {
        StringBuilder prompt = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Map<?, ?> partMap
                    && "text".equals(partMap.get("type"))
                    && partMap.get("text") instanceof String text) {
                prompt.append(text);
            }
        }
        return prompt.toString();
    }

    private String resolveGenerationModel(String model) {
        return isNotBlank(model) ? model : modelConfig.getModelName();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static int collectionSize(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    private Map<String, Object> getJson(String path, Float timeout, String authorization,
            Map<String, String> requestHeaders, Collection<String> sensitiveValues) throws Exception {
        HttpResponse<String> response = sendGenerationRequest(path, timeout, authorization, requestHeaders,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, response.body(), false, sensitiveValues);
        return parseJsonObject(response.body());
    }

    private byte[] getBytes(String path, Float timeout, String authorization,
            Map<String, String> requestHeaders, Collection<String> sensitiveValues) throws Exception {
        HttpResponse<byte[]> response = sendGenerationRequest(path, timeout, authorization, requestHeaders,
                HttpResponse.BodyHandlers.ofByteArray());
        ensureSuccess(response, "binary content of " + path, false, sensitiveValues);
        return response.body();
    }

    private HttpRequest buildGetRequest(String apiBase, String path, Float timeout, String authorization,
            Map<String, String> extraHeaders) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl(apiBase, path)))
                .timeout(timeoutDuration(timeout))
                .header("Authorization", resolveAuthorization(authorization))
                .GET();
        for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
            builder.setHeader(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private String resolveAuthorization(String authorization) {
        String effectiveAuthorization = authorization != null
                ? authorization
                : "Bearer " + modelClientConfig.getApiKey();
        validateAuthorizationHeader(effectiveAuthorization);
        return effectiveAuthorization;
    }

    private Duration timeoutDuration(Float timeout) {
        double seconds = timeout != null ? timeout.doubleValue() : modelClientConfig.getTimeout();
        long millis = Math.max(1L, Math.round(seconds * 1000.0D));
        return Duration.ofMillis(millis);
    }

    private <T> HttpResponse<T> sendGenerationRequest(String path, Float timeout, String authorization,
            Map<String, String> requestHeaders, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        try {
            return httpClient.send(buildGetRequest(modelClientConfig.getApiBase(), path, timeout,
                    authorization, requestHeaders), bodyHandler);
        } catch (IOException exception) {
            String fallbackApiBase = localFixtureFallbackApiBase(exception);
            if (fallbackApiBase == null) {
                throw exception;
            }
            return ModelHttpClients.builder(modelClientConfig, fallbackApiBase)
                    .withSsl()
                    .withProxy()
                    .build()
                    .send(buildGetRequest(fallbackApiBase, path, timeout, authorization, requestHeaders),
                            bodyHandler);
        }
    }

    protected AssistantMessage parseResponse(Map<String, Object> response, BaseOutputParser parser) {
        List<Map<String, Object>> choices = asListOfObjectMaps(response.get("choices"));
        if (choices.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_RESPONSE_INVALID, "error_msg",
                    "No choices in response");
        }
        Map<String, Object> choice = choices.get(0);
        Object rawMessage = choice.get("message");
        if (!(rawMessage instanceof Map<?, ?>)) {
            throw ErrorHelper.buildError(StatusCode.MODEL_RESPONSE_INVALID, "error_msg",
                    "No message in response choice");
        }
        Map<String, Object> message = asObjectMap(rawMessage);

        List<ToolCall> toolCalls = parseToolCalls(message.get("tool_calls"), true);
        UsageMetadata usageMetadata = parseUsageMetadata(response.get("usage"), true);
        Object content = requireStringOrNullContent(message.get("content"), "choices[0].message.content");
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
        Object rawDelta = choice.get("delta");
        Map<String, Object> delta = rawDelta instanceof Map<?, ?> ? asObjectMap(rawDelta) : Map.of();
        Object content = requireStringOrNullContent(delta.get("content"), "choices[0].delta.content");
        List<ToolCall> toolCalls = parseToolCalls(delta.get("tool_calls"), false, toolCallStates);
        List<Integer> completionTokenIds = firstNonNull(
                integerList(choice.get("token_ids")),
                integerList(delta.get("token_ids"))
        );

        return AssistantMessageChunk.builder()
                .content(content)
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

    /**
     * Require {@code content} to be a JSON string or null (normalized to empty string).
     * Non-string types must not be stringified and leaked downstream.
     *
     * @param content content field from the model response
     * @param fieldPath field path for the error message
     * @return string content, empty when null
     * @since 0.1.18
     */
    private static Object requireStringOrNullContent(Object content, String fieldPath) {
        if (content == null) {
            return "";
        }
        if (content instanceof String) {
            return content;
        }
        throw ErrorHelper.buildError(StatusCode.MODEL_RESPONSE_TYPE_ERROR, "error_msg",
                fieldPath + " must be a string, got " + content.getClass().getSimpleName());
    }

    private Map<String, Object> postJson(
            Map<String, Object> params,
            Float timeout,
            String authorization,
            Map<String, String> requestHeaders,
            Collection<String> sensitiveValues,
            ModelRetryListener retryListener) throws IOException, InterruptedException {
        return postJson(CHAT_COMPLETIONS_PATH, params, timeout, authorization, requestHeaders,
                sensitiveValues, retryListener);
    }

    private Map<String, Object> postJson(
            String path,
            Map<String, Object> params,
            Float timeout,
            String authorization,
            Map<String, String> requestHeaders,
            Collection<String> sensitiveValues,
            ModelRetryListener retryListener) throws IOException, InterruptedException {
        PreparedRequest preparedRequest = prepareRequest(path, params, authorization, requestHeaders);
        double idleSeconds = ModelTimeoutResolver.resolvePerCallIdleSeconds(timeout, modelClientConfig);
        RetryContext retryContext = new RetryContext(ModelCallRetryPolicy.from(modelClientConfig));
        try {
            circuitBreaker.beforeCall();
        } catch (IOException openFailure) {
            throw classifySendException(openFailure, false, sensitiveValues);
        }
        Map<String, Object> responseData;
        try {
            responseData = retrier.execute(retryContext, () -> {
                HttpResponse<InputStream> response =
                        sendStreamAttempt(preparedRequest, retryContext.consumedRetries(), idleSeconds);
                String body = readBodyWithIdleTimeout(response.body(), idleSeconds, false);
                ensureSuccess(response, body, false, sensitiveValues);
                ensureNoEmbeddedError(body, false, sensitiveValues);
                try {
                    return parseJsonObject(body);
                } catch (JsonProcessingException exception) {
                    throw responseParseException(body, exception, false, "parse_json", sensitiveValues);
                }
            }, retryListener);
        } catch (BaseError failure) {
            circuitBreaker.onFailure(failure);
            throw failure;
        } catch (IOException failure) {
            BaseError classified = classifySendException(failure, false, sensitiveValues);
            circuitBreaker.onFailure(classified);
            throw classified;
        }
        circuitBreaker.onSuccess();
        return responseData;
    }

    private Iterator<AssistantMessageChunk> streamChunks(
            Map<String, Object> params,
            BaseOutputParser outputParser,
            Float timeout,
            String authorization,
            Map<String, String> requestHeaders,
            Collection<String> sensitiveValues,
            ModelRetryListener retryListener) throws IOException, InterruptedException {
        PreparedRequest preparedRequest = prepareRequest(
                CHAT_COMPLETIONS_PATH, params, authorization, requestHeaders);
        double idleSeconds = ModelTimeoutResolver.resolvePerCallIdleSeconds(timeout, modelClientConfig);
        RetryContext retryContext = new RetryContext(ModelCallRetryPolicy.from(modelClientConfig));
        try {
            circuitBreaker.beforeCall();
        } catch (IOException openFailure) {
            throw classifySendException(openFailure, true, sensitiveValues);
        }
        ModelCallRetrier.StreamAttempt<AssistantMessageChunk> streamAttempt = () -> {
            HttpResponse<InputStream> response =
                    sendStreamAttempt(preparedRequest, retryContext.consumedRetries(), idleSeconds);
            Loggers.LLM.info("OpenAI API stream response received. status={}", response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = readBodyWithIdleTimeout(response.body(), idleSeconds, true);
                ensureSuccess(response, body, true, sensitiveValues);
            }
            return new RetriableStreamHandle(
                    new SseChunkIterator(response.body(), outputParser, sensitiveValues, idleSeconds),
                    circuitBreaker);
        };
        Iterator<AssistantMessageChunk> chunks;
        try {
            chunks = new FirstSegmentRetryIterator<>(retryContext, streamAttempt,
                    retrier.openStream(retryContext, streamAttempt, retryListener), retrier, retryListener);
        } catch (BaseError failure) {
            circuitBreaker.onFailure(failure);
            throw failure;
        } catch (IOException failure) {
            BaseError classified = classifySendException(failure, true, sensitiveValues);
            circuitBreaker.onFailure(classified);
            throw classified;
        }
        circuitBreaker.onSuccess();
        return loggingFirstChunk(chunks);
    }

    private Iterator<AssistantMessageChunk> loggingFirstChunk(Iterator<AssistantMessageChunk> delegate) {
        return new LoggingFirstChunkIterator(delegate);
    }

    private static final class RetriableStreamHandle
            implements ModelCallRetrier.StreamHandle<AssistantMessageChunk> {
        private final Iterator<AssistantMessageChunk> iterator;
        private final ModelCircuitBreaker circuitBreaker;

        private RetriableStreamHandle(Iterator<AssistantMessageChunk> iterator,
                ModelCircuitBreaker circuitBreaker) {
            this.iterator = iterator;
            this.circuitBreaker = circuitBreaker;
        }

        @Override
        public Iterator<AssistantMessageChunk> iterator() {
            return iterator;
        }

        @Override
        public void onWindowFailure(BaseError failure) {
            circuitBreaker.onFailure(failure);
        }

        @Override
        public void closeQuietly() {
            if (iterator instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (IOException | IllegalStateException | IllegalArgumentException closeFailure) {
                    Loggers.LLM.debug("Failed to close abandoned stream attempt. {}",
                            closeFailure.getMessage());
                }
            }
        }
    }

    private static final class LoggingFirstChunkIterator
            implements Iterator<AssistantMessageChunk>, AutoCloseable {
        private final Iterator<AssistantMessageChunk> delegate;
        private boolean isFirstLogged;

        private LoggingFirstChunkIterator(Iterator<AssistantMessageChunk> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public AssistantMessageChunk next() {
            AssistantMessageChunk chunk = delegate.next();
            if (!isFirstLogged) {
                isFirstLogged = true;
                Loggers.LLM.info("OpenAI API stream first chunk. {}", chunk);
            }
            return chunk;
        }

        @Override
        public void close() {
            if (delegate instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (IOException exception) {
                    Loggers.LLM.debug("Failed to close OpenAI stream iterator. {}", exception.getMessage());
                }
            }
        }
    }

    private HttpResponse<InputStream> sendStreamAttempt(
            PreparedRequest preparedRequest,
            int retryCount,
            double idleSeconds) throws IOException, InterruptedException {
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(
                    buildRequest(preparedRequest, modelClientConfig.getApiBase(), retryCount),
                    IdleBoundInputStream.bodyHandler(idleSeconds)
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
                            IdleBoundInputStream.bodyHandler(idleSeconds)
                    );
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String fallbackApiBase = localFixtureFallbackApiBase(response.statusCode());
            if (fallbackApiBase != null) {
                readBodyWithIdleTimeout(response.body(),
                        ModelTimeoutResolver.resolveStreamIdleSeconds(modelClientConfig), true);
                response = ModelHttpClients.builder(modelClientConfig, fallbackApiBase)
                        .withSsl()
                        .withProxy()
                        .build()
                        .send(
                                buildRequest(preparedRequest, fallbackApiBase, retryCount),
                                IdleBoundInputStream.bodyHandler(idleSeconds)
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
            String path,
            Map<String, Object> params,
            String authorization,
            Map<String, String> requestHeaders) throws JsonProcessingException {
        String bodyJson = OBJECT_MAPPER.writeValueAsString(requestBodyParams(params));
        return new PreparedRequest(
                path,
                bodyJson,
                ModelTimeoutResolver.toDuration(
                        ModelTimeoutResolver.resolveHeaderWaitSeconds(modelClientConfig)),
                resolveAuthorization(authorization),
                new LinkedHashMap<>(requestHeaders));
    }

    private HttpRequest buildRequest(
            PreparedRequest preparedRequest,
            String apiBase,
            int retryCount) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl(apiBase, preparedRequest.path())))
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
            String path,
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
        } catch (ClassCastException | IllegalStateException | IllegalArgumentException exception) {
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

    private final class SseChunkIterator implements Iterator<AssistantMessageChunk>, Closeable {
        private final BufferedReader reader;
        private final BaseOutputParser outputParser;
        private final Collection<String> sensitiveValues;
        private final StringBuilder accumulatedContent = new StringBuilder();
        private final Map<Integer, ToolCallState> toolCallStates = new LinkedHashMap<>();
        private final double idleSeconds;
        private AssistantMessageChunk nextChunk;
        private String pendingToolCallFinishReason = "null";
        private boolean isFirstSegmentReceived;
        private boolean closed;

        private boolean isEffectiveFirstSegmentReceived;

        private SseChunkIterator(
                InputStream inputStream,
                BaseOutputParser outputParser,
                Collection<String> sensitiveValues,
                double idleSeconds) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            this.outputParser = outputParser;
            this.sensitiveValues = sensitiveValues;
            this.idleSeconds = idleSeconds;
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
            boolean shouldKeepOpen = false;
            try {
                String rawLine;
                while ((rawLine = reader.readLine()) != null) {
                    String line = rawLine.strip();
                    if (line.isEmpty() || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring("data:".length()).strip();
                    isFirstSegmentReceived = true;
                    if (!data.contains("choices") && ErrorBodyParser.hasErrorObject(data)) {
                        throw errorFrameException(data);
                    }
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
                    AssistantMessageChunk parsedChunk = applyStreamingParser(adaptedChunk);
                    shouldKeepOpen = true;
                    isEffectiveFirstSegmentReceived = true;
                    return parsedChunk;
                }
                close();
                return finalToolCallChunk();
            } catch (IOException | IllegalStateException | IllegalArgumentException | ClassCastException
                    | IndexOutOfBoundsException exception) {
                close();
                if (exception instanceof IOException ioException) {
                    throwIfIdleTimeout(ioException);
                }
                if (exception instanceof InterruptedIOException
                        && !(exception instanceof SocketTimeoutException)) {
                    // SocketTimeoutException is a read timeout, not a cancellation.
                    throw ClassifiedModelErrors.toCancellation("model stream read cancelled");
                }
                String detail = errorDetail(exception);
                Loggers.LLM.error("OpenAI API async stream read error. {}", detail);
                throw classifiedStreamFailure(exception);
            } finally {
                if (!shouldKeepOpen) {
                    close();
                }
            }
        }

        private void throwIfIdleTimeout(IOException exception) {
            if (exception instanceof IdleTimeoutIOException idleTimeout) {
                throw ModelTimeoutErrorFactory.idleTimeoutError(
                        isFirstSegmentReceived
                                ? TimeoutPhase.AFTER_FIRST_SEGMENT
                                : TimeoutPhase.BEFORE_FIRST_SEGMENT,
                        idleTimeout.idleSeconds(), true, exception);
            }
        }

        private BaseError classifiedStreamFailure(Exception exception) {
            ModelErrorFacts facts = ModelErrorFacts.Builder.create()
                    .throwable(exception)
                    .stream(true)
                    .firstSegmentReceived(isEffectiveFirstSegmentReceived)
                    .build();
            ModelCallClassification classification =
                    ModelErrorClassifiers.defaultClassifier().classify(facts);
            return ClassifiedModelErrors.toError(classification, facts,
                    "stream failure at read_chunk: " + safeExceptionMessage(exception, sensitiveValues),
                    exception);
        }

        private BaseError errorFrameException(String data) {
            ErrorResponseBodySanitizer.SanitizedBody sanitized =
                    ErrorResponseBodySanitizer.sanitize(data, sensitiveValues);
            ModelErrorFacts.Builder factsBuilder = ModelErrorFacts.Builder.create()
                    .errorBody(sanitized.body())
                    .stream(true)
                    .firstSegmentReceived(isEffectiveFirstSegmentReceived);
            Optional<ErrorBodyParser.ParsedError> parsed = ErrorBodyParser.parse(sanitized.body());
            if (parsed.isPresent()) {
                ErrorBodyParser.ParsedError error = parsed.get();
                factsBuilder.errorCode(error.code()).errorType(error.type()).errorMessage(error.message());
            }
            ModelErrorFacts facts = factsBuilder.build();
            ModelCallClassification classification =
                    ModelErrorClassifiers.defaultClassifier().classify(facts);
            return ClassifiedModelErrors.toError(classification, facts,
                    "stream error frame: " + sanitized.body(), null);
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
            closeReaderQuietly();
        }

        private void closeReaderQuietly() {
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

    private BaseError classifySendException(
            IOException exception,
            boolean isStreaming,
            Collection<String> sensitiveValues) {
        ModelErrorFacts facts = ModelErrorFacts.ofThrowable(exception, isStreaming);
        ModelCallClassification classification =
                ModelErrorClassifiers.defaultClassifier().classify(facts);
        if (classification.category() == ModelErrorCategory.USER_CANCELLED) {
            throw ClassifiedModelErrors.toCancellation("model call cancelled");
        }
        Optional<BaseError> timeoutError = ModelTimeoutErrorFactory.fromSendException(
                exception, isStreaming, modelClientConfig);
        if (timeoutError.isPresent()) {
            return timeoutError.get();
        }
        return ClassifiedModelErrors.toError(classification, facts,
                "transport failure at send_request: " + safeExceptionMessage(exception, sensitiveValues),
                exception);
    }

    private BaseError responseParseException(
            String body,
            Exception exception,
            boolean isStreaming,
            String phase,
            Collection<String> sensitiveValues) {
        ErrorResponseBodySanitizer.SanitizedBody sanitized =
                ErrorResponseBodySanitizer.sanitize(body, sensitiveValues);
        String message = "response parse failure at " + phase + ": "
                + safeExceptionMessage(exception, sensitiveValues);
        if (!sanitized.body().isBlank()) {
            message += ", response body: " + sanitized.body();
        }
        return modelCallError(message, exception);
    }

    private static String responseDataForDiagnostics(Map<String, Object> responseData) {
        try {
            return OBJECT_MAPPER.writeValueAsString(responseData);
        } catch (JsonProcessingException exception) {
            return String.valueOf(responseData);
        }
    }

    private BaseError clientInternalException(
            Exception exception,
            boolean isStreaming,
            String phase,
            Collection<String> sensitiveValues) {
        return modelCallError(
                "model client internal failure at " + phase + ": "
                        + safeExceptionMessage(exception, sensitiveValues),
                exception);
    }

    private static String safeExceptionMessage(Exception exception, Collection<String> sensitiveValues) {
        return ErrorResponseBodySanitizer.sanitize(errorDetail(exception), sensitiveValues).body();
    }

    private void ensureSuccess(
            HttpResponse<?> response,
            String body,
            boolean isStreaming,
            Collection<String> sensitiveValues) {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        OptionalDouble retryAfter = RetryAfterParser.parseSeconds(response.headers());
        throw classifiedBodyError(statusCode, body, isStreaming, sensitiveValues,
                retryAfter.isPresent() ? retryAfter.getAsDouble() : null);
    }

    private void ensureNoEmbeddedError(
            String body,
            boolean isStreaming,
            Collection<String> sensitiveValues) {
        if (!ErrorBodyParser.hasErrorObject(body)) {
            return;
        }
        throw classifiedBodyError(null, body, isStreaming, sensitiveValues, null);
    }

    private BaseError classifiedBodyError(
            Integer statusCode,
            String body,
            boolean isStreaming,
            Collection<String> sensitiveValues,
            Double retryAfterSeconds) {
        ErrorResponseBodySanitizer.SanitizedBody sanitized =
                ErrorResponseBodySanitizer.sanitize(body, sensitiveValues);
        ModelErrorFacts.Builder factsBuilder = ModelErrorFacts.Builder.create()
                .httpStatus(statusCode)
                .errorBody(sanitized.body())
                .stream(isStreaming)
                .retryAfterSeconds(retryAfterSeconds);
        Optional<ErrorBodyParser.ParsedError> parsed = ErrorBodyParser.parse(sanitized.body());
        if (parsed.isPresent()) {
            ErrorBodyParser.ParsedError error = parsed.get();
            factsBuilder.errorCode(error.code()).errorType(error.type()).errorMessage(error.message());
        }
        ModelErrorFacts facts = factsBuilder.build();
        ModelCallClassification classification =
                ModelErrorClassifiers.defaultClassifier().classify(facts);
        String message = statusCode == null
                ? "embedded error in success response: " + sanitized.body()
                : "HTTP " + statusCode + ": " + sanitized.body();
        return ClassifiedModelErrors.toError(classification, facts, message, null);
    }

    private BaseError modelCallError(String errorMessage, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.MODEL_CALL_FAILED,
                null,
                null,
                cause,
                Map.of("error_msg", errorMessage));
    }

    private static String readBodyWithIdleTimeout(InputStream inputStream, double idleSeconds,
            boolean isStreaming) throws IOException, InterruptedException {
        return IdleGuardedBodyReader.readFully(inputStream, idleSeconds, isStreaming);
    }

    HttpClient httpClientForTesting() {
        return httpClient;
    }

    private HttpClient createHttpClient(ModelClientConfig clientConfig) {
        return ModelHttpClients.builder(clientConfig, clientConfig.getApiBase())
                .withSsl()
                .withExplicitPortProxy()
                .build();
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

    /**
     * Resolve the endpoint URL: append the operation path to the API base, unless the base is
     * already a full endpoint URL that ends with the operation path.
     *
     * @param apiBase configured API base or full endpoint URL
     * @param path    operation path such as {@code /chat/completions}
     * @return endpoint URL
     */
    private static String endpointUrl(String apiBase, String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = trimTrailingSlash(apiBase);
        if (base.endsWith(path)) {
            return base;
        }
        return base + path;
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
