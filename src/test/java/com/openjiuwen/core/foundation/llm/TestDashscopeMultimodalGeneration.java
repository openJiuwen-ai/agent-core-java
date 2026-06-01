/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System tests for DashScope multimodal generation.
 * <p>
 * Mirrors Python's {@code test_dashscope_multimodal_generation.py} in
 * {@code tests.system_tests.foundation.llm}.
 */
class TestDashscopeMultimodalGeneration {

    @Test
    @Disabled("require network and API key")
    void testGenerateImageBasic() throws Exception {
        Model model = createModel("qwen-image-max");
        ImageGenerationResponse response = model.generateImage(
                List.of(new UserMessage("portrait of a girl in a flower field")),
                null, "1664*928", null, 1, true, false, 0, null);

        assertThat(response).isNotNull();
        assertThat(response.getImages()).isNotEmpty();
        assertThat(response.getModel()).isEqualTo("qwen-image-max");
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateImageWithSize() throws Exception {
        Model model = createModel("qwen-image-max");
        ImageGenerationResponse response = model.generateImage(
                List.of(new UserMessage("a cute kitten playing in sunlight")),
                null, "1024*1024", null, 1, true, false, 0, null);

        assertThat(response).isNotNull();
        assertThat(response.getImages()).isNotEmpty();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateImageWithNegativePrompt() throws Exception {
        Model model = createModel("qwen-image-max");
        ImageGenerationResponse response = model.generateImage(
                List.of(new UserMessage("beautiful mountain and river landscape painting")),
                null, "1664*928", "blur, low quality, watermark", 1, true, false, 12345, null);

        assertThat(response).isNotNull();
        assertThat(response.getImages()).isNotEmpty();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateImageWithReferenceImage() throws Exception {
        Model model = createModel("wan2.6-image");
        ImageGenerationResponse response = model.generateImage(
                List.of(userMultimodal(
                        "convert this image to watercolor style",
                        java.util.Map.of("image", "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png"))),
                null, "1664*928", null, 1, true, false, 0, null);

        assertThat(response).isNotNull();
        assertThat(response.getImages()).isNotEmpty();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateImageBatchGeneration() throws Exception {
        Model model = createModel("qwen-image-max");
        ImageGenerationResponse response = model.generateImage(
                List.of(new UserMessage("cute cartoon small animal")),
                null, "1664*928", null, 1, true, false, 0, null);

        assertThat(response).isNotNull();
        assertThat(response.getImages()).isNotEmpty();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateImageMultipleReferenceImages() throws Exception {
        Model model = createModel("wan2.6-image");
        ImageGenerationResponse response = model.generateImage(
                List.of(userMultimodal(
                        "blend the style elements from these images",
                        java.util.Map.of("image", "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png"),
                        java.util.Map.of("image", "https://img.alicdn.com/imgextra/i3/O1CN01SfG4J41UYn9WNt4X1_!!6000000002530-49-tps-1696-960.webp"))),
                null, "1664*928", null, 1, true, false, 0, null);

        assertThat(response).isNotNull();
        assertThat(response.getImages()).isNotEmpty();
    }

    @Test
    void testGenerateImageEmptyMessagesValidation() throws Exception {
        Model model = createModel("qwen-image-max");

        assertThatThrownBy(() -> model.generateImage(List.of(), null, "1664*928",
                null, 1, true, false, 0, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("exactly one message");
    }

    @Test
    void testGenerateImageTooManyImagesValidation() throws Exception {
        Model model = createModel("wan2.6-image");

        assertThatThrownBy(() -> model.generateImage(List.of(userMultimodal(
                java.util.Map.of("text", "style blend"),
                java.util.Map.of("image", "https://example.com/img1.png"),
                java.util.Map.of("image", "https://example.com/img2.png"),
                java.util.Map.of("image", "https://example.com/img3.png"),
                java.util.Map.of("image", "https://example.com/img4.png"))),
                null, "1664*928", null, 1, true, false, 0, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("at most 3");
    }

    @Test
    void testGenerateImageNonUserMessageValidation() throws Exception {
        Model model = createModel("qwen-image-max");

        @SuppressWarnings({"rawtypes", "unchecked"})
        List<UserMessage> messages = (List) List.of(new SystemMessage("generate an image"));

        assertThatThrownBy(() -> model.generateImage(messages, null, "1664*928",
                null, 1, true, false, 0, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("UserMessage");
    }

    @Test
    void testGenerateImageMixedStringAndDictContent() throws Exception {
        Model model = createModel("wan2.6-image");

        assertThatThrownBy(() -> model.generateImage(List.of(
                new UserMessage("first text description"),
                new UserMessage("second text description"),
                userMultimodal(java.util.Map.of(
                        "text", "text with image 1",
                        "image", "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png")),
                userMultimodal(java.util.Map.of(
                        "text", "text with image 2",
                        "image", "https://img.alicdn.com/imgextra/i3/O1CN01SfG4J41UYn9WNt4X1_!!6000000002530-49-tps-1696-960.webp"))),
                null, "1664*928", null, 1, true, false, 0, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("exactly one message");
    }

    @Test
    void testGenerateImageExtraKeysInDictValidation() throws Exception {
        Model model = createModel("wan2.6-image");

        assertThatThrownBy(() -> model.generateImage(List.of(userMultimodal(java.util.Map.of(
                "text", "generate image",
                "image", "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png",
                "extra_key", "extra_value",
                "another_key", 123))),
                null, "1664*928", null, 1, true, false, 0, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("exactly one of 'text' or 'image'");
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateSpeechBasic() throws Exception {
        Model model = createModel("qwen3-tts-flash");
        AudioGenerationResponse response = model.generateSpeech(
                List.of(new UserMessage("Hello, this is the Qianwen text-to-speech service.")),
                null, "Cherry", "Auto", null);

        assertThat(response).isNotNull();
        assertThat(response.getAudioUrl() != null || response.getAudioData() != null).isTrue();
        assertThat(response.getModel()).isEqualTo("qwen3-tts-flash");
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateSpeechWithCustomVoice() throws Exception {
        Model model = createModel("qwen3-tts-flash");
        AudioGenerationResponse response = model.generateSpeech(
                List.of(new UserMessage("This is a speech synthesis test with a custom voice.")),
                null, "Serena", "Chinese", null);

        assertThat(response).isNotNull();
        assertThat(response.getAudioUrl() != null || response.getAudioData() != null).isTrue();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateSpeechLongText() throws Exception {
        Model model = createModel("qwen3-tts-flash");
        AudioGenerationResponse response = model.generateSpeech(
                List.of(new UserMessage("""
                        This is a longer text used to verify text-to-speech behavior.
                        It contains multiple sentences to simulate realistic input.
                        """)),
                null, "Cherry", "Auto", null);

        assertThat(response).isNotNull();
        assertThat(response.getAudioUrl() != null || response.getAudioData() != null).isTrue();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateSpeechEnglish() throws Exception {
        Model model = createModel("qwen3-tts-flash");
        AudioGenerationResponse response = model.generateSpeech(
                List.of(new UserMessage("Hello, welcome to use the Qianwen text-to-speech service.")),
                null, "Ethan", "English", null);

        assertThat(response).isNotNull();
        assertThat(response.getAudioUrl() != null || response.getAudioData() != null).isTrue();
    }

    @Test
    void testGenerateSpeechEmptyContentValidation() throws Exception {
        Model model = createModel("qwen3-tts-flash");

        assertThatThrownBy(() -> model.generateSpeech(List.of(new UserMessage("")), null,
                "Cherry", "Auto", null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void testGenerateSpeechNonUserMessageValidation() throws Exception {
        Model model = createModel("qwen3-tts-flash");

        @SuppressWarnings({"rawtypes", "unchecked"})
        List<UserMessage> messages = (List) List.of(new com.openjiuwen.core.foundation.llm.schema.AssistantMessage("hello"));

        assertThatThrownBy(() -> model.generateSpeech(messages, null, "Cherry", "Auto", null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("UserMessage");
    }

    @Test
    void testGenerateSpeechInvalidVoiceValidation() throws Exception {
        Model model = createModel("qwen3-tts-flash");

        assertThatThrownBy(() -> model.generateSpeech(List.of(new UserMessage("this is test text")), null,
                "InvalidVoiceName", "Auto", null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("voice");
    }

    @Test
    void testGenerateSpeechInvalidLanguageTypeValidation() throws Exception {
        Model model = createModel("qwen3-tts-flash");

        assertThatThrownBy(() -> model.generateSpeech(List.of(new UserMessage("this is test text")), null,
                "Cherry", "InvalidLanguage", null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("language_type");
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateVideoTextToVideoBasic() throws Exception {
        Model model = createModel("wan2.6-t2v");
        VideoGenerationResponse response = model.generateVideo(
                List.of(new UserMessage("generate a video of a white rabbit running on grass")),
                null, null, null, "1280*720", null, 5, true, false, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getVideoUrl()).isNotNull();
        assertThat(response.getModel()).isEqualTo("wan2.6-t2v");
        assertThat(response.getFormat()).isEqualTo("mp4");
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateVideoWithCustomSizeAndDuration() throws Exception {
        Model model = createModel("wan2.6-t2v");
        VideoGenerationResponse response = model.generateVideo(
                List.of(new UserMessage("a sunset beach with waves gently hitting the sand")),
                null, null, null, "1280*720", null, 5, true, false, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getVideoUrl()).isNotNull();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateVideoImageToVideo() throws Exception {
        Model model = createModel("wan2.6-i2v-flash");
        VideoGenerationResponse response = model.generateVideo(
                List.of(new UserMessage("animate the umbrella in the image")),
                "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png", null, null,
                null, "720P", 5, true, false, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getVideoUrl()).isNotNull();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateVideoWithNegativePrompt() throws Exception {
        Model model = createModel("wan2.6-t2v");
        VideoGenerationResponse response = model.generateVideo(
                List.of(new UserMessage("a small bird flying in the blue sky")),
                null, null, null, null, null, 5, true, false, "blur, low quality, shaking", 42, null);

        assertThat(response).isNotNull();
        assertThat(response.getVideoUrl()).isNotNull();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateVideoWithAudio() throws Exception {
        Model model = createModel("wan2.6-t2v");
        VideoGenerationResponse response = model.generateVideo(
                List.of(new UserMessage("a person singing on a stage")),
                null,
                "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250925/ozwpvi/rap.mp3",
                null, null, null, 10, true, false, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getVideoUrl()).isNotNull();
    }

    @Test
    @Disabled("require network and API key")
    void testGenerateVideoImageToVideoWithAudio() throws Exception {
        Model model = createModel("wan2.6-i2v-flash");
        VideoGenerationResponse response = model.generateVideo(
                List.of(new UserMessage("animate the scene in the image with music rhythm")),
                "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png",
                "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250925/ozwpvi/rap.mp3",
                null, null, "720P", 8, true, false, null, null, null);

        assertThat(response).isNotNull();
        assertThat(response.getVideoUrl()).isNotNull();
    }

    @Test
    void testGenerateVideoEmptyMessagesValidation() throws Exception {
        Model model = createModel("wan2.6-t2v");

        assertThatThrownBy(() -> model.generateVideo(List.of(), null, null, null,
                "1280*720", null, 5, true, false, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("exactly one message");
    }

    @Test
    void testGenerateVideoEmptyContentValidation() throws Exception {
        Model model = createModel("wan2.6-t2v");

        assertThatThrownBy(() -> model.generateVideo(List.of(new UserMessage("")), null, null, null,
                "1280*720", null, 5, true, false, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void testGenerateVideoNonUserMessageValidation() throws Exception {
        Model model = createModel("wan2.6-t2v");

        @SuppressWarnings({"rawtypes", "unchecked"})
        List<UserMessage> messages = (List) List.of(new SystemMessage("generate a rabbit running video"));

        assertThatThrownBy(() -> model.generateVideo(messages, null, null, null,
                "1280*720", null, 5, true, false, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("UserMessage");
    }

    @Test
    void testGenerateVideoTextToVideoWithResolutionInsteadOfSize() throws Exception {
        Model model = createModel("wan2.6-t2v");

        assertThatThrownBy(() -> model.generateVideo(List.of(new UserMessage("a sunset beach with waves")),
                null, null, null, null, "720P", 5, true, false, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("size");
    }

    @Test
    void testGenerateVideoImageToVideoWithSizeInsteadOfResolution() throws Exception {
        Model model = createModel("wan2.6-i2v-flash");

        assertThatThrownBy(() -> model.generateVideo(List.of(new UserMessage("animate the umbrella in the image")),
                "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png", null, null,
                "1280*720", null, 5, true, false, null, null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("resolution");
    }

    private static UserMessage userMultimodal(Object... content) {
        return UserMessage.builder()
                .content(List.of(content))
                .build();
    }

    private Model createModel(String modelName) {
        return new Model(
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.DashScope.getValue())
                        .apiKey(API_KEY)
                        .apiBase(API_BASE)
                        .verifySsl(false)
                        .build(),
                ModelRequestConfig.builder()
                        .modelName(modelName)
                        .build());
    }

    private static final String API_KEY = System.getenv().getOrDefault("DASHSCOPE_API_KEY", "sk-your-api-key");
    private static final String API_BASE = System.getenv().getOrDefault(
            "DASHSCOPE_API_BASE", "https://dashscope.aliyuncs.com/compatible-mode/v1");
}
