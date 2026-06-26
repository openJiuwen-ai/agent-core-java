/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.ModelError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

/**
 * Alibaba Cloud DashScope model client.
 *
 * <p>Mirrors Python's {@code DashScopeModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.</p>
 */
public class DashScopeModelClient extends BaseModelClient {

    public static final String __client_name__ = ProviderType.DASH_SCOPE.getValue();
    public static final String CLIENT_NAME = __client_name__;

    private static final Logger LOG = LoggerFactory.getLogger(DashScopeModelClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String OPEN_AI_MODEL_CLIENT_CLASS =
            "com.openjiuwen.core.foundation.llm.model_clients.OpenAIModelClient";
    private static final String DEFAULT_IMAGE_SIZE = "1664*928";

    private final DashScopeTransport transport;
    private final Object chatCompletionDelegate;

    public DashScopeModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, new HttpDashScopeTransport());
    }

    DashScopeModelClient(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            DashScopeTransport transport) {
        super(modelConfig, modelClientConfig);
        this.transport = transport;
        this.chatCompletionDelegate = createChatCompletionDelegate(modelConfig, modelClientConfig);
    }

    @Override
    protected String getClientName() {
        return "DashScope client";
    }

    @Override
    public AssistantMessage invoke(
            Object messages,
            Object tools,
            Float temperature,
            Float topP,
            String model,
            Integer maxTokens,
            String stop,
            BaseOutputParser outputParser,
            Float timeout,
            Map<String, Object> kwargs) throws Exception {
        return AssistantMessage.class.cast(callChatCompletionDelegate(
                "invoke",
                new Class<?>[] {
                        Object.class,
                        Object.class,
                        Float.class,
                        Float.class,
                        String.class,
                        Integer.class,
                        String.class,
                        BaseOutputParser.class,
                        Float.class,
                        Map.class
                },
                messages, tools, temperature, topP, model, maxTokens, stop, outputParser, timeout, kwargs));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<AssistantMessageChunk> stream(
            Object messages,
            Object tools,
            Float temperature,
            Float topP,
            String model,
            Integer maxTokens,
            String stop,
            BaseOutputParser outputParser,
            Float timeout,
            Map<String, Object> kwargs) throws Exception {
        return (Iterator<AssistantMessageChunk>) callChatCompletionDelegate(
                "stream",
                new Class<?>[] {
                        Object.class,
                        Object.class,
                        Float.class,
                        Float.class,
                        String.class,
                        Integer.class,
                        String.class,
                        BaseOutputParser.class,
                        Float.class,
                        Map.class
                },
                messages, tools, temperature, topP, model, maxTokens, stop, outputParser, timeout, kwargs);
    }

    @Override
    public ImageGenerationResponse generateImage(
            List<UserMessage> messages,
            String model,
            String size,
            String negativePrompt,
            int n,
            boolean promptExtend,
            boolean watermark,
            int seed,
            Map<String, Object> kwargs) throws Exception {
        try {
            UserMessage message = requireSingleUserMessageForImage(messages);
            List<Map<String, Object>> contentList = new ArrayList<>();
            ImageContentCounts counts = collectImageContent(message.getContent(), contentList);
            if (counts.textCount() == 0) {
                throw validationError("Image generation requires at least one text prompt.");
            }
            if (counts.imageCount() > 3) {
                throw validationError("Image generation supports at most 3 input images, but got "
                        + counts.imageCount() + ".");
            }

            String resolvedModel = model != null ? model : modelConfig.getModelName();
            Map<String, Object> apiParams = new LinkedHashMap<>();
            apiParams.put("api_key", modelClientConfig.getApiKey());
            apiParams.put("model", resolvedModel);
            apiParams.put("messages", List.of(dashScopeMessage(message.getRole(), contentList)));
            apiParams.put("result_format", "message");
            apiParams.put("stream", false);
            apiParams.put("watermark", watermark);
            apiParams.put("prompt_extend", promptExtend);
            apiParams.put("size", size != null ? size : DEFAULT_IMAGE_SIZE);
            apiParams.put("n", n);
            if (negativePrompt != null && !negativePrompt.isEmpty()) {
                apiParams.put("negative_prompt", negativePrompt);
            }
            if (seed != 0) {
                apiParams.put("seed", seed);
            }
            putKwargs(apiParams, kwargs);

            LOG.info("Calling DashScope image generation API with model: {}, size: {}",
                    resolvedModel, apiParams.get("size"));
            Map<String, Object> response = transport.call(DashScopeApi.MULTIMODAL_CONVERSATION,
                    apiParams, modelClientConfig);
            ensureOkResponse(response, "image generation");

            List<String> imageUrls = extractImageUrls(response);
            if (imageUrls.isEmpty()) {
                throw modelCallError("No images returned from DashScope API.");
            }

            LOG.info("DashScope image generation succeeded. Generated {} image(s).", imageUrls.size());
            return ImageGenerationResponse.builder()
                    .model(resolvedModel)
                    .images(imageUrls)
                    .created(null)
                    .build();
        } catch (Exception ex) {
            throw wrapUnexpected("image generation", ex);
        }
    }

    @Override
    public AudioGenerationResponse generateSpeech(
            List<UserMessage> messages,
            String model,
            String voice,
            String languageType,
            Map<String, Object> kwargs) throws Exception {
        try {
            UserMessage message = requireSingleUserMessageForSpeech(messages);
            Object content = message.getContent();
            if (!(content instanceof String text) || text.isBlank()) {
                throw validationError("Speech generation requires non-empty text content.");
            }

            String resolvedModel = model != null ? model : modelConfig.getModelName();
            String resolvedVoice = voice != null ? voice : "Cherry";
            String resolvedLanguageType = languageType != null ? languageType : "Auto";

            Map<String, Object> apiParams = new LinkedHashMap<>();
            apiParams.put("api_key", modelClientConfig.getApiKey());
            apiParams.put("model", resolvedModel);
            apiParams.put("text", text);
            apiParams.put("voice", resolvedVoice);
            apiParams.put("language_type", resolvedLanguageType);
            putKwargs(apiParams, kwargs);

            LOG.info("Calling DashScope speech generation API with model: {}, voice: {}, language: {}",
                    resolvedModel, resolvedVoice, resolvedLanguageType);
            Map<String, Object> response = transport.call(DashScopeApi.MULTIMODAL_CONVERSATION,
                    apiParams, modelClientConfig);
            ensureOkResponse(response, "speech generation");

            AudioPayload audioPayload = extractAudioPayload(response);
            if (audioPayload.audioUrl() == null && audioPayload.audioData() == null) {
                throw modelCallError("No audio URL or data returned from DashScope API.");
            }

            LOG.info("DashScope speech generation succeeded. Audio format: {}, URL present: {}, Data present: {}",
                    audioPayload.format() != null ? audioPayload.format() : "unknown",
                    audioPayload.audioUrl() != null,
                    audioPayload.audioData() != null);
            return AudioGenerationResponse.builder()
                    .model(resolvedModel)
                    .audioUrl(audioPayload.audioUrl())
                    .audioData(audioPayload.audioData())
                    .format(audioPayload.format())
                    .build();
        } catch (Exception ex) {
            throw wrapUnexpected("speech generation", ex);
        }
    }

    @Override
    public VideoGenerationResponse generateVideo(
            List<UserMessage> messages,
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
        return generateVideoInternal(messages, imgUrl, audioUrl, model, size, resolution, duration,
                promptExtend, watermark, negativePrompt, seed, kwargs);
    }

    private VideoGenerationResponse generateVideoInternal(
            List<UserMessage> messages,
            String imgUrl,
            String audioUrl,
            String model,
            String size,
            String resolution,
            Integer duration,
            boolean promptExtend,
            boolean watermark,
            String negativePrompt,
            Integer seed,
            Map<String, Object> kwargs) throws Exception {
        try {
            UserMessage message = requireSingleUserMessageForVideo(messages);
            Object content = message.getContent();
            if (!(content instanceof String prompt) || prompt.isBlank()) {
                throw validationError("Video generation requires non-empty text content.");
            }

            String resolvedModel = model != null ? model : modelConfig.getModelName();
            Map<String, Object> apiParams = new LinkedHashMap<>();
            apiParams.put("api_key", modelClientConfig.getApiKey());
            apiParams.put("model", resolvedModel);
            apiParams.put("prompt", prompt);
            apiParams.put("prompt_extend", promptExtend);
            apiParams.put("watermark", watermark);
            if (duration != null) {
                apiParams.put("duration", duration);
            }
            if (negativePrompt != null && !negativePrompt.isEmpty()) {
                apiParams.put("negative_prompt", negativePrompt);
            }
            if (seed != null) {
                apiParams.put("seed", seed);
            }
            if (audioUrl != null && !audioUrl.isEmpty()) {
                apiParams.put("audio_url", audioUrl);
            }

            if (imgUrl != null && !imgUrl.isEmpty()) {
                apiParams.put("img_url", imgUrl);
                if (resolution != null && !resolution.isEmpty()) {
                    apiParams.put("resolution", resolution);
                } else if (size != null && !size.isEmpty()) {
                    apiParams.put("size", size);
                }
                LOG.info("Calling DashScope image-to-video generation API with model: {}, resolution: {}, duration: {}",
                        resolvedModel, resolution != null ? resolution : size, duration);
            } else {
                if (size != null && !size.isEmpty()) {
                    apiParams.put("size", size);
                } else if (resolution != null && !resolution.isEmpty()) {
                    apiParams.put("resolution", resolution);
                }
                LOG.info("Calling DashScope text-to-video generation API with model: {}, size: {}, duration: {}",
                        resolvedModel, size != null ? size : resolution, duration);
            }
            putKwargs(apiParams, kwargs);

            Map<String, Object> response = transport.call(DashScopeApi.VIDEO_SYNTHESIS, apiParams, modelClientConfig);
            ensureOkResponse(response, "video generation");

            VideoPayload videoPayload = extractVideoPayload(response);
            if (videoPayload.videoUrl() == null) {
                throw modelCallError("No video URL returned from DashScope API.");
            }

            LOG.info("DashScope video generation succeeded. Video URL: {}",
                    abbreviate(videoPayload.videoUrl(), 100));
            return VideoGenerationResponse.builder()
                    .model(resolvedModel)
                    .videoUrl(videoPayload.videoUrl())
                    .duration(videoPayload.duration())
                    .resolution(videoPayload.resolution())
                    .format("mp4")
                    .build();
        } catch (Exception ex) {
            throw wrapUnexpected("video generation", ex);
        }
    }

    private static UserMessage requireSingleUserMessageForImage(List<UserMessage> messages) {
        if (messages == null || messages.size() != 1) {
            throw validationError("Image generation requires exactly one message, but got "
                    + (messages == null ? 0 : messages.size()) + ".");
        }
        Object firstMessage = messages.get(0);
        if (!(firstMessage instanceof UserMessage message)) {
            throw validationError("Image generation requires a UserMessage, but got "
                    + pythonTypeName(firstMessage) + ".");
        }
        return message;
    }

    private static UserMessage requireSingleUserMessageForSpeech(List<UserMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.size() > 1) {
            throw validationError("Speech generation requires at least one message, but got 0.");
        }
        Object firstMessage = messages.get(0);
        if (!(firstMessage instanceof UserMessage message)) {
            throw validationError("Speech generation requires UserMessage types, but message at index 0 is "
                    + pythonTypeName(firstMessage) + ".");
        }
        return message;
    }

    private static UserMessage requireSingleUserMessageForVideo(List<UserMessage> messages) {
        if (messages == null || messages.size() != 1) {
            throw validationError("Video generation requires exactly one message, but got "
                    + (messages == null ? 0 : messages.size()) + ".");
        }
        Object firstMessage = messages.get(0);
        if (!(firstMessage instanceof UserMessage message)) {
            throw validationError("Video generation requires UserMessage type, but got "
                    + pythonTypeName(firstMessage) + ".");
        }
        return message;
    }

    private static ImageContentCounts collectImageContent(Object content, List<Map<String, Object>> contentList) {
        int textCount = 0;
        int imageCount = 0;
        if (content instanceof String text) {
            contentList.add(singleEntry("text", text));
            textCount = 1;
        } else if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String text) {
                    contentList.add(singleEntry("text", text));
                    textCount++;
                } else if (item instanceof Map<?, ?> map) {
                    if (map.containsKey("text")) {
                        contentList.add(singleEntry("text", map.get("text")));
                        textCount++;
                    } else if (map.containsKey("image")) {
                        contentList.add(singleEntry("image", map.get("image")));
                        imageCount++;
                    } else {
                        throw validationError("Content dict must contain 'text' or 'image' key, but got: "
                                + map.keySet());
                    }
                } else {
                    throw validationError("Content item must be string or dict, but got "
                            + pythonTypeName(item) + ".");
                }
            }
        } else {
            throw validationError("Message content must be string or list, but got "
                    + pythonTypeName(content) + ".");
        }
        return new ImageContentCounts(textCount, imageCount);
    }

    private static Map<String, Object> dashScopeMessage(String role, List<Map<String, Object>> contentList) {
        Map<String, Object> dashScopeMessage = new LinkedHashMap<>();
        dashScopeMessage.put("role", role);
        dashScopeMessage.put("content", contentList);
        return dashScopeMessage;
    }

    private static Map<String, Object> singleEntry(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private static void putKwargs(Map<String, Object> apiParams, Map<String, Object> kwargs) {
        if (kwargs != null) {
            apiParams.putAll(kwargs);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractImageUrls(Map<String, Object> response) {
        List<String> imageUrls = new ArrayList<>();
        Object outputObj = response.get("output");
        if (!(outputObj instanceof Map<?, ?> output)) {
            return imageUrls;
        }
        Object choicesObj = output.get("choices");
        if (!(choicesObj instanceof Iterable<?> choices)) {
            return imageUrls;
        }
        for (Object choiceObj : choices) {
            if (!(choiceObj instanceof Map<?, ?> choice)) {
                continue;
            }
            Object messageObj = choice.get("message");
            if (!(messageObj instanceof Map<?, ?> message)) {
                continue;
            }
            Object contentObj = message.get("content");
            if (!(contentObj instanceof Iterable<?> contentItems)) {
                continue;
            }
            for (Object contentItemObj : contentItems) {
                if (contentItemObj instanceof Map<?, ?> contentItem && contentItem.containsKey("image")) {
                    imageUrls.add(String.valueOf(contentItem.get("image")));
                }
            }
        }
        return imageUrls;
    }

    @SuppressWarnings("unchecked")
    private static AudioPayload extractAudioPayload(Map<String, Object> response) {
        String audioUrl = null;
        byte[] audioData = null;
        String format = null;
        Object outputObj = response.get("output");
        if (outputObj instanceof Map<?, ?> output) {
            Object audioObj = output.get("audio");
            if (audioObj instanceof Map<?, ?> audioInfo) {
                Object urlObj = audioInfo.get("url");
                audioUrl = urlObj == null ? null : String.valueOf(urlObj);
                Object dataObj = audioInfo.get("data");
                if (dataObj instanceof String data) {
                    audioData = data.getBytes(StandardCharsets.UTF_8);
                } else if (dataObj instanceof byte[] bytes) {
                    audioData = bytes;
                }
                if (audioUrl != null) {
                    if (audioUrl.endsWith(".wav")) {
                        format = "wav";
                    } else if (audioUrl.endsWith(".mp3")) {
                        format = "mp3";
                    } else if (audioUrl.endsWith(".pcm")) {
                        format = "pcm";
                    }
                }
            }
        }
        return new AudioPayload(audioUrl, audioData, format);
    }

    private static VideoPayload extractVideoPayload(Map<String, Object> response) {
        String videoUrl = null;
        Double duration = null;
        String resolution = null;
        Object output = response.get("output");
        Object videoUrlObj = attribute(output, "video_url", "videoUrl");
        if (videoUrlObj != null) {
            videoUrl = String.valueOf(videoUrlObj);
        }
        Object usage = response.get("usage");
        Object durationObj = firstNonNull(attribute(usage, "duration"),
                attribute(usage, "output_video_duration", "outputVideoDuration"));
        if (durationObj instanceof Number number) {
            duration = number.doubleValue();
        }
        Object resolutionObj = attribute(usage, "size");
        if (resolutionObj != null) {
            resolution = String.valueOf(resolutionObj);
        }
        return new VideoPayload(videoUrl, duration, resolution);
    }

    private static void ensureOkResponse(Map<String, Object> response, String operation) {
        Object statusCode = firstNonNull(response.get("status_code"), response.get("statusCode"));
        if (statusCode instanceof Number number && number.intValue() != 200) {
            throw modelCallError("DashScope " + operation + " failed. HTTP status: "
                    + number.intValue() + ", Error code: " + response.get("code")
                    + ", Error message: " + response.get("message"));
        }
    }

    private static BaseError validationError(String message) {
        return ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR, "error_msg", message);
    }

    private static ModelError modelCallError(String message) {
        return new ModelError(StatusCode.MODEL_CALL_FAILED, null, null, null, Map.of("error_msg", message));
    }

    private static ModelError wrapUnexpected(String operation, Exception ex) {
        String message = "Unexpected error during DashScope " + operation + ": " + ex.getMessage();
        return new ModelError(StatusCode.MODEL_CALL_FAILED, message, null, ex, Map.of("error_msg", message));
    }

    private Object callChatCompletionDelegate(String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        if (chatCompletionDelegate == null) {
            throw modelCallError("OpenAIModelClient dependency is unavailable for DashScope chat completions.");
        }
        try {
            Method method = chatCompletionDelegate.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(chatCompletionDelegate, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    private static Object createChatCompletionDelegate(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig) {
        try {
            Class<?> type = Class.forName(OPEN_AI_MODEL_CLIENT_CLASS);
            Constructor<?> constructor = type.getConstructor(ModelRequestConfig.class, ModelClientConfig.class);
            return constructor.newInstance(modelConfig, modelClientConfig);
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to initialize DashScope chat completion delegate.", ex);
        }
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static Object attribute(Object target, String... names) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            for (String name : names) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
            }
            return null;
        }
        for (String name : names) {
            Object value = readBeanAttribute(target, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object readBeanAttribute(Object target, String name) {
        for (String methodName : List.of(accessorName("get", name), accessorName("is", name))) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String accessorName(String prefix, String name) {
        String camel = snakeToCamel(name);
        return prefix + Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    private static String snakeToCamel(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(current) : current);
            upperNext = false;
        }
        return builder.toString();
    }

    private static String pythonTypeName(Object value) {
        if (value == null) {
            return "NoneType";
        }
        if (value instanceof String) {
            return "str";
        }
        if (value instanceof Map<?, ?>) {
            return "dict";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return "int";
        }
        if (value instanceof Float || value instanceof Double) {
            return "float";
        }
        if (value instanceof Boolean) {
            return "bool";
        }
        return value.getClass().getSimpleName();
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    /**
     * Mirrors Python's selected DashScope SDK call families in
     * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.
     */
    enum DashScopeApi {
        MULTIMODAL_CONVERSATION,
        VIDEO_SYNTHESIS
    }

    /**
     * Mirrors Python's DashScope SDK call boundary in
     * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.
     */
    interface DashScopeTransport {
        Map<String, Object> call(
                DashScopeApi api,
                Map<String, Object> apiParams,
                ModelClientConfig clientConfig) throws Exception;
    }

    /**
     * Mirrors Python's DashScope SDK-backed HTTP call boundary in
     * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.
     */
    private static final class HttpDashScopeTransport implements DashScopeTransport {
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> call(
                DashScopeApi api,
                Map<String, Object> apiParams,
                ModelClientConfig clientConfig) throws Exception {
            String body = OBJECT_MAPPER.writeValueAsString(apiParams);
            String endpoint = switch (api) {
                case MULTIMODAL_CONVERSATION -> "/services/aigc/multimodal-generation/generation";
                case VIDEO_SYNTHESIS -> "/services/aigc/video-generation/video-synthesis";
            };
            long timeoutSeconds = Math.max(1L, Math.round(clientConfig.getTimeout()));
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlashes(clientConfig.getApiBase()) + endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (clientConfig.getApiKey() != null && !clientConfig.getApiKey().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + clientConfig.getApiKey());
            }
            if (clientConfig.getCustomHeaders() != null) {
                clientConfig.getCustomHeaders().forEach((name, value) -> {
                    if (value != null) {
                        requestBuilder.header(name, String.valueOf(value));
                    }
                });
            }
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            HttpResponse<String> response = client.send(
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> responseMap = OBJECT_MAPPER.readValue(response.body(), Map.class);
            responseMap.putIfAbsent("http_status", response.statusCode());
            return responseMap;
        }

        private static String stripTrailingSlashes(String value) {
            if (value == null) {
                return "";
            }
            return value.replaceAll("/+$", "");
        }
    }

    /**
     * Mirrors Python's text/image content counters in
     * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.
     */
    private record ImageContentCounts(int textCount, int imageCount) {
    }

    /**
     * Mirrors Python's extracted audio response fields in
     * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.
     */
    private record AudioPayload(String audioUrl, byte[] audioData, String format) {
    }

    /**
     * Mirrors Python's extracted video response fields in
     * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.
     */
    private record VideoPayload(String videoUrl, Double duration, String resolution) {
    }
}
