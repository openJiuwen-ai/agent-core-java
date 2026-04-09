/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.llm.model_clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alibaba Cloud DashScope Model Client.
 * <p>
 * Extends OpenAiCompatibleModelClient to support DashScope-specific multimodal
 * generation APIs (image, speech, video).
 * For chat completions, inherits all functionality from OpenAiCompatibleModelClient
 * since DashScope provides OpenAI-compatible chat API endpoints.
 */
public class DashScopeModelClient extends OpenAiCompatibleModelClient {

    private static final Logger LOG = LoggerFactory.getLogger(DashScopeModelClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> DASHSCOPE_VOICES = Arrays.asList(
            "Cherry", "Serena", "Ethan", "Chelsie", "Momo", "Vivian", "Moon", "Maia", "Kai", "Nofish",
            "Bella", "Jennifer", "Ryan", "Katerina", "Aiden", "Eldric Sage", "Mia", "Mochi", "Bellona",
            "Vincent", "Bunny", "Neil", "Elias", "Arthur", "Nini", "Ebona", "Seren", "Pip", "Stella",
            "Bodega", "Sonrisa", "Alek", "Dolce", "Sohee", "Ono Anna", "Lenn", "Emilien", "Andre",
            "Radio Gol", "Jada", "Dylan", "Li", "Marcus", "Roy", "Peter", "Sunny", "Eric", "Rocky", "Kiki"
    );

    private final HttpClient multiModalHttpClient;

    public DashScopeModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.multiModalHttpClient = buildHttpClient(Math.max(30, modelClientConfig.getTimeout()));
    }

    @Override
    protected String getClientName() {
        return "DashScope client";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                 String model,
                                                 String size,
                                                 String negativePrompt,
                                                 int n,
                                                 boolean promptExtend,
                                                 boolean watermark,
                                                 int seed,
                                                 Map<String, Object> kwargs) throws Exception {
        // Validate messages
        if (messages == null || messages.size() != 1) {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "Image generation requires exactly one message, but got "
                            + (messages == null ? 0 : messages.size()) + ".");
        }
        UserMessage msg = messages.get(0);

        // Build content list
        List<Map<String, Object>> contentList = new ArrayList<>();
        Object content = msg.getContent();
        int textCount = 0;
        int imageCount = 0;

        if (content instanceof String s) {
            contentList.add(Map.of("text", s));
            textCount = 1;
        } else if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    contentList.add(Map.of("text", s));
                    textCount++;
                } else if (item instanceof Map<?, ?> map) {
                    if (map.containsKey("text")) {
                        contentList.add(Map.of("text", map.get("text")));
                        textCount++;
                    } else if (map.containsKey("image")) {
                        contentList.add(Map.of("image", map.get("image")));
                        imageCount++;
                    } else {
                        throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                                "error_msg", "Content dict must contain 'text' or 'image' key.");
                    }
                } else {
                    throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                            "error_msg", "Content item must be string or map.");
                }
            }
        } else {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "Message content must be string or list.");
        }

        if (textCount == 0) {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "Image generation requires at least one text prompt.");
        }
        if (imageCount > 3) {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "Image generation supports at most 3 input images, but got " + imageCount + ".");
        }

        String resolvedModel = model != null ? model : modelConfig.getModelName();

        // Build API params
        Map<String, Object> apiParams = new LinkedHashMap<>();
        apiParams.put("model", resolvedModel);
        apiParams.put("messages", List.of(Map.of("role", "user", "content", contentList)));
        apiParams.put("result_format", "message");
        apiParams.put("stream", false);
        apiParams.put("watermark", watermark);
        apiParams.put("prompt_extend", promptExtend);
        apiParams.put("size", size != null ? size : "1664*928");
        apiParams.put("n", n);
        if (negativePrompt != null) {
            apiParams.put("negative_prompt", negativePrompt);
        }
        if (seed != 0) {
            apiParams.put("seed", seed);
        }
        if (kwargs != null) {
            apiParams.putAll(kwargs);
        }

        LOG.info("Calling DashScope image generation API with model: {}, size: {}", resolvedModel, apiParams.get("size"));

        Map<String, Object> responseMap = callDashScopeApi(apiParams);

        // Extract image URLs
        List<String> imageUrls = new ArrayList<>();
        Map<String, Object> output = (Map<String, Object>) responseMap.get("output");
        if (output != null) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
            if (choices != null) {
                for (Map<String, Object> choice : choices) {
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message != null) {
                        List<Map<String, Object>> contentItems = (List<Map<String, Object>>) message.get("content");
                        if (contentItems != null) {
                            for (Map<String, Object> ci : contentItems) {
                                if (ci.containsKey("image")) {
                                    imageUrls.add(ci.get("image").toString());
                                }
                            }
                        }
                    }
                }
            }
        }

        if (imageUrls.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_CALL_FAILED,
                    "error_msg", "No images returned from DashScope API.");
        }

        LOG.info("DashScope image generation succeeded. Generated {} image(s).", imageUrls.size());

        return ImageGenerationResponse.builder()
                .model(resolvedModel)
                .images(imageUrls)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
                                                  Map<String, Object> kwargs) throws Exception {
        if (messages == null || messages.isEmpty() || messages.size() > 1) {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "Speech generation requires exactly one message.");
        }

        UserMessage msg = messages.get(0);
        Object content = msg.getContent();
        if (!(content instanceof String text) || text.isBlank()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "Speech generation requires non-empty text content.");
        }

        String resolvedModel = model != null ? model : modelConfig.getModelName();
        String resolvedVoice = voice != null ? voice : "Cherry";
        String resolvedLanguage = languageType != null ? languageType : "Auto";

        Map<String, Object> apiParams = new LinkedHashMap<>();
        apiParams.put("model", resolvedModel);
        apiParams.put("text", text);
        apiParams.put("voice", resolvedVoice);
        apiParams.put("language_type", resolvedLanguage);
        if (kwargs != null) {
            apiParams.putAll(kwargs);
        }

        LOG.info("Calling DashScope speech generation API with model: {}, voice: {}, language: {}",
                resolvedModel, resolvedVoice, resolvedLanguage);

        Map<String, Object> responseMap = callDashScopeApi(apiParams);

        // Extract audio
        String audioUrl = null;
        byte[] audioData = null;
        String audioFormat = null;

        Map<String, Object> output = (Map<String, Object>) responseMap.get("output");
        if (output != null) {
            Map<String, Object> audioInfo = (Map<String, Object>) output.get("audio");
            if (audioInfo != null) {
                audioUrl = (String) audioInfo.get("url");
                Object dataObj = audioInfo.get("data");
                if (dataObj instanceof String s) {
                    audioData = s.getBytes(StandardCharsets.UTF_8);
                } else if (dataObj instanceof byte[] b) {
                    audioData = b;
                }
                if (audioUrl != null) {
                    if (audioUrl.endsWith(".wav")) {
                        audioFormat = "wav";
                    } else if (audioUrl.endsWith(".mp3")) {
                        audioFormat = "mp3";
                    } else if (audioUrl.endsWith(".pcm")) {
                        audioFormat = "pcm";
                    }
                }
            }
        }

        if (audioUrl == null && audioData == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_CALL_FAILED,
                    "error_msg", "No audio URL or data returned from DashScope API.");
        }

        LOG.info("DashScope speech generation succeeded. Audio format: {}, URL present: {}, Data present: {}",
                audioFormat != null ? audioFormat : "unknown", audioUrl != null, audioData != null);

        return AudioGenerationResponse.builder()
                .model(resolvedModel)
                .audioUrl(audioUrl)
                .audioData(audioData)
                .format(audioFormat)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
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
        if (messages == null || messages.size() != 1) {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "Video generation requires exactly one message, but got "
                            + (messages == null ? 0 : messages.size()) + ".");
        }

        UserMessage msg = messages.get(0);
        Object content = msg.getContent();
        if (!(content instanceof String prompt) || prompt.isBlank()) {
            throw ErrorHelper.buildError(StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg", "Video generation requires non-empty text content.");
        }

        String resolvedModel = model != null ? model : modelConfig.getModelName();

        Map<String, Object> apiParams = new LinkedHashMap<>();
        apiParams.put("model", resolvedModel);
        apiParams.put("prompt", prompt);
        apiParams.put("prompt_extend", promptExtend);
        apiParams.put("watermark", watermark);
        apiParams.put("duration", duration);

        if (negativePrompt != null) {
            apiParams.put("negative_prompt", negativePrompt);
        }
        if (seed != null) {
            apiParams.put("seed", seed);
        }
        if (audioUrl != null) {
            apiParams.put("audio_url", audioUrl);
        }

        if (imgUrl != null) {
            apiParams.put("img_url", imgUrl);
            if (resolution != null) {
                apiParams.put("resolution", resolution);
            } else if (size != null) {
                apiParams.put("size", size);
            }
            LOG.info("Calling DashScope image-to-video generation API with model: {}, resolution: {}, duration: {}",
                    resolvedModel, resolution != null ? resolution : size, duration);
        } else {
            if (size != null) {
                apiParams.put("size", size);
            } else if (resolution != null) {
                apiParams.put("resolution", resolution);
            }
            LOG.info("Calling DashScope text-to-video generation API with model: {}, size: {}, duration: {}",
                    resolvedModel, size != null ? size : resolution, duration);
        }

        if (kwargs != null) {
            apiParams.putAll(kwargs);
        }

        Map<String, Object> responseMap = callDashScopeApi(apiParams);

        // Extract video info
        String videoUrl = null;
        Double videoDuration = null;
        String videoResolution = null;

        Map<String, Object> output = (Map<String, Object>) responseMap.get("output");
        if (output != null) {
            videoUrl = (String) output.get("video_url");
        }

        Map<String, Object> usage = (Map<String, Object>) responseMap.get("usage");
        if (usage != null) {
            Object dur = usage.get("duration");
            if (dur == null) {
                dur = usage.get("output_video_duration");
            }
            if (dur instanceof Number num) {
                videoDuration = num.doubleValue();
            }
            videoResolution = (String) usage.get("size");
        }

        if (videoUrl == null) {
            throw ErrorHelper.buildError(StatusCode.MODEL_CALL_FAILED,
                    "error_msg", "No video URL returned from DashScope API.");
        }

        LOG.info("DashScope video generation succeeded.");

        return VideoGenerationResponse.builder()
                .model(resolvedModel)
                .videoUrl(videoUrl)
                .duration(videoDuration)
                .resolution(videoResolution)
                .format("mp4")
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callDashScopeApi(Map<String, Object> apiParams) throws Exception {
        String body = MAPPER.writeValueAsString(apiParams);
        String apiBase = modelClientConfig.getApiBase().replaceAll("/+$", "");

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/services/aigc/multimodal-generation/generation"))
                .timeout(Duration.ofSeconds(120));
        applyConfiguredHeaders(request, true);
        request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        HttpResponse<String> response = multiModalHttpClient.send(request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        Map<String, Object> responseMap = MAPPER.readValue(response.body(), Map.class);

        // Check for error in response
        Object statusCode = responseMap.get("status_code");
        if (statusCode instanceof Number num && num.intValue() != 200) {
            String errorMsg = "DashScope API failed. HTTP status: " + num.intValue()
                    + ", Error code: " + responseMap.get("code")
                    + ", Error message: " + responseMap.get("message");
            LOG.error(errorMsg);
            throw ErrorHelper.buildError(StatusCode.MODEL_CALL_FAILED,
                    "error_msg", errorMsg);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw ErrorHelper.buildError(StatusCode.MODEL_CALL_FAILED,
                    "error_msg", "DashScope API HTTP error: " + response.statusCode() + " - " + response.body());
        }

        return responseMap;
    }
}
