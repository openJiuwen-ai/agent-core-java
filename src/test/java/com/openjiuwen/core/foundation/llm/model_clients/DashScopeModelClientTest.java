/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ModelError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Focused parity tests for {@link DashScopeModelClient}.
 *
 * <p>Mirrors Python's {@code DashScopeModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.</p>
 */
class DashScopeModelClientTest {

    @Test
    void generateImageBuildsDashScopeMessageAndExtractsImages() throws Exception {
        CapturingTransport transport = new CapturingTransport(Map.of(
                "status_code", 200,
                "output", Map.of(
                        "choices", List.of(
                                Map.of("message", Map.of("content", List.of(
                                        Map.of("image", "https://example.test/a.png"),
                                        Map.of("text", "ignored")))),
                                Map.of("message", Map.of("content", List.of(
                                        Map.of("image", "https://example.test/b.png"))))))));
        DashScopeModelClient client = client(transport);
        Map<String, Object> textThenImage = new LinkedHashMap<>();
        textThenImage.put("text", "style prompt");
        textThenImage.put("image", "ignored-because-python-prefers-text-key");
        List<Object> content = new ArrayList<>();
        content.add("draw a fox");
        content.add(textThenImage);
        content.add(Map.of("image", "https://example.test/input.png"));
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("extra_flag", true);
        kwargs.put("n", 3);

        ImageGenerationResponse response = client.generateImage(
                List.of(UserMessage.builder().content(content).build()),
                null,
                null,
                "low quality",
                1,
                true,
                false,
                42,
                kwargs);

        assertThat(response.getModel()).isEqualTo("dashscope-default");
        assertThat(response.getImages()).containsExactly(
                "https://example.test/a.png",
                "https://example.test/b.png");
        assertThat(transport.lastApi).isEqualTo(DashScopeModelClient.DashScopeApi.MULTIMODAL_CONVERSATION);
        assertThat(transport.lastParams).containsEntry("api_key", "sk-test")
                .containsEntry("model", "dashscope-default")
                .containsEntry("result_format", "message")
                .containsEntry("stream", false)
                .containsEntry("watermark", false)
                .containsEntry("prompt_extend", true)
                .containsEntry("size", "1664*928")
                .containsEntry("negative_prompt", "low quality")
                .containsEntry("seed", 42)
                .containsEntry("n", 3)
                .containsEntry("extra_flag", true);
        List<?> messages = (List<?>) transport.lastParams.get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstMessage = (Map<String, Object>) messages.get(0);
        assertThat(firstMessage).containsEntry("role", "user");
        @SuppressWarnings("unchecked")
        List<Object> dashScopeContent = (List<Object>) firstMessage.get("content");
        assertThat(dashScopeContent).containsExactly(
                Map.<String, Object>of("text", "draw a fox"),
                Map.<String, Object>of("text", "style prompt"),
                Map.<String, Object>of("image", "https://example.test/input.png"));
    }

    @Test
    void generateImageWrapsValidationErrorsLikePythonCatchAll() {
        DashScopeModelClient client = client(new CapturingTransport(Map.of()));

        assertThatThrownBy(() -> client.generateImage(
                List.of(UserMessage.builder().content(List.of(Map.of("audio", "bad"))).build()),
                null,
                null,
                null,
                1,
                true,
                false,
                0,
                null))
                .isInstanceOf(ModelError.class)
                .satisfies(error -> {
                    BaseError baseError = (BaseError) error;
                    assertThat(baseError.getStatus()).isEqualTo(StatusCode.MODEL_CALL_FAILED);
                    assertThat(baseError.getMessage())
                            .contains("Unexpected error during DashScope image generation");
                    BaseError cause = assertInstanceOf(BaseError.class, error.getCause());
                    assertThat(cause.getStatus()).isEqualTo(StatusCode.MODEL_INVOKE_PARAM_ERROR);
                    assertThat(cause.getMessage())
                            .contains("Content dict must contain 'text' or 'image' key");
                });
    }

    @Test
    void generateSpeechPassesVoiceAndLanguageThroughWithoutExtraValidation() throws Exception {
        CapturingTransport transport = new CapturingTransport(Map.of(
                "status_code", 200,
                "output", Map.of("audio", Map.of(
                        "url", "https://example.test/speech.wav",
                        "data", "audio-bytes"))));
        DashScopeModelClient client = client(transport);

        AudioGenerationResponse response = client.generateSpeech(
                List.of(new UserMessage("hello")),
                "cosyvoice-v1",
                "CustomVoice",
                "Klingon",
                Map.of("sample_rate", 24000));

        assertThat(response.getModel()).isEqualTo("cosyvoice-v1");
        assertThat(response.getAudioUrl()).isEqualTo("https://example.test/speech.wav");
        assertThat(response.getAudioData()).isEqualTo("audio-bytes".getBytes(StandardCharsets.UTF_8));
        assertThat(response.getFormat()).isEqualTo("wav");
        assertThat(transport.lastParams).containsEntry("voice", "CustomVoice")
                .containsEntry("language_type", "Klingon")
                .containsEntry("sample_rate", 24000);
    }

    @Test
    void generateSpeechMirrorsPythonMessageCountErrorText() {
        DashScopeModelClient client = client(new CapturingTransport(Map.of()));

        assertThatThrownBy(() -> client.generateSpeech(
                List.of(new UserMessage("one"), new UserMessage("two")),
                null,
                null,
                null,
                null))
                .isInstanceOf(ModelError.class)
                .hasMessageContaining("Unexpected error during DashScope speech generation")
                .satisfies(error -> assertThat(error.getCause())
                        .hasMessageContaining("Speech generation requires at least one message, but got 0."));
    }

    @Test
    void generateVideoPreservesPythonSizeResolutionBranching() throws Exception {
        CapturingTransport transport = new CapturingTransport(Map.of(
                "status_code", 200,
                "output", Map.of("video_url", "https://example.test/video.mp4"),
                "usage", Map.of("output_video_duration", 6, "size", "1080P")));
        DashScopeModelClient client = client(transport);

        VideoGenerationResponse imageToVideo = client.generateVideo(
                List.of(new UserMessage("animate this image")),
                "https://example.test/first-frame.png",
                "https://example.test/audio.mp3",
                null,
                "1280*720",
                "1080P",
                6,
                false,
                true,
                "blur",
                7,
                Map.of("style", "cinematic"));

        assertThat(imageToVideo.getModel()).isEqualTo("dashscope-default");
        assertThat(imageToVideo.getVideoUrl()).isEqualTo("https://example.test/video.mp4");
        assertThat(imageToVideo.getDuration()).isEqualTo(6.0D);
        assertThat(imageToVideo.getResolution()).isEqualTo("1080P");
        assertThat(imageToVideo.getFormat()).isEqualTo("mp4");
        assertThat(transport.lastApi).isEqualTo(DashScopeModelClient.DashScopeApi.VIDEO_SYNTHESIS);
        assertThat(transport.lastParams).containsEntry("img_url", "https://example.test/first-frame.png")
                .containsEntry("audio_url", "https://example.test/audio.mp3")
                .containsEntry("resolution", "1080P")
                .containsEntry("duration", 6)
                .containsEntry("prompt_extend", false)
                .containsEntry("watermark", true)
                .containsEntry("negative_prompt", "blur")
                .containsEntry("seed", 7)
                .containsEntry("style", "cinematic");
        assertThat(transport.lastParams).doesNotContainKey("size");

        transport.nextResponse = Map.of(
                "status_code", 200,
                "output", Map.of("video_url", "https://example.test/text-video.mp4"));
        VideoGenerationResponse textToVideo = client.generateVideo(
                List.of(new UserMessage("city at sunrise")),
                null,
                null,
                "wan2.6-t2v",
                null,
                "720P",
                5,
                true,
                false,
                null,
                null,
                null);

        assertThat(textToVideo.getVideoUrl()).isEqualTo("https://example.test/text-video.mp4");
        assertThat(transport.lastParams).containsEntry("resolution", "720P")
                .containsEntry("model", "wan2.6-t2v");
    }

    @Test
    void exposesPythonClientNameLedger() {
        DashScopeModelClient client = client(new CapturingTransport(Map.of()));

        assertThat(DashScopeModelClient.CLIENT_NAME).isEqualTo("DashScope");
        assertThat(client.getClientName()).isEqualTo("DashScope client");
    }

    private static DashScopeModelClient client(CapturingTransport transport) {
        return new DashScopeModelClient(
                ModelRequestConfig.builder().modelName("dashscope-default").build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.DASH_SCOPE)
                        .apiKey("sk-test")
                        .apiBase("https://dashscope.example.test")
                        .verifySsl(false)
                        .build(),
                transport);
    }

    /**
     * Mirrors Python's patched DashScope SDK call object in
     * {@code openjiuwen/core/foundation/llm/model_clients/dashscope_model_client.py}.
     */
    private static final class CapturingTransport implements DashScopeModelClient.DashScopeTransport {
        private Map<String, Object> nextResponse;
        private DashScopeModelClient.DashScopeApi lastApi;
        private Map<String, Object> lastParams;

        private CapturingTransport(Map<String, Object> nextResponse) {
            this.nextResponse = nextResponse;
        }

        @Override
        public Map<String, Object> call(
                DashScopeModelClient.DashScopeApi api,
                Map<String, Object> apiParams,
                ModelClientConfig clientConfig) {
            this.lastApi = api;
            this.lastParams = new LinkedHashMap<>(apiParams);
            return nextResponse;
        }
    }
}
