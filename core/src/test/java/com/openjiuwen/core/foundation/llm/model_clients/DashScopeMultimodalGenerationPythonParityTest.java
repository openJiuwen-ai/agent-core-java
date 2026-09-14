/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.exception.ModelError;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <p>Mirrors Python's {@code test_dashscope_multimodal_generation} in
 * {@code tests/system_tests/foundation/llm/test_dashscope_multimodal_generation.py}.</p>
 */
class DashScopeMultimodalGenerationPythonParityTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: require network and API key";

    @Nested
    @DisplayName("TestDashScopeImageGeneration")
    class ImageGenerationTests {

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateImageBasic() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateImageWithSize() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateImageWithNegativePrompt() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateImageWithReferenceImage() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateImageBatchGeneration() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateImageMultipleReferenceImages() {
        }

        @Test
        void testGenerateImageEmptyMessagesValidation() {
            DashScopeModelClient client = client("qwen-image-max", new FailingTransport());

            assertModelError(() -> client.generateImage(List.of(), null, "1664*928", null, 1,
                    true, false, 0, null));
        }

        @Test
        void testGenerateImageTooManyImagesValidation() {
            DashScopeModelClient client = client("wan2.6-image", new FailingTransport());
            UserMessage message = UserMessage.builder().content(List.of(
                    Map.of("text", "fusion style"),
                    Map.of("image", "https://example.com/img1.png"),
                    Map.of("image", "https://example.com/img2.png"),
                    Map.of("image", "https://example.com/img3.png"),
                    Map.of("image", "https://example.com/img4.png")
            )).build();

            assertThatThrownBy(() -> client.generateImage(List.of(message), null, "1664*928", null, 1,
                    true, false, 0, null))
                    .isInstanceOf(ModelError.class)
                    .hasMessageContaining("at most 3");
        }

        @Test
        void testGenerateImageNonUserMessageValidation() {
            DashScopeModelClient client = client("qwen-image-max", new FailingTransport());

            assertModelError(() -> client.generateImage(rawUserMessages(new SystemMessage("generate an image")),
                    null, "1664*928", null, 1, true, false, 0, null));
        }

        @Test
        void testGenerateImageMixedStringAndDictContent() {
            DashScopeModelClient client = client("wan2.6-image", new FailingTransport());

            assertModelError(() -> client.generateImage(List.of(
                    new UserMessage("first text description"),
                    new UserMessage("second text description"),
                    UserMessage.builder().content(List.of(Map.of(
                            "text", "text with image 1",
                            "image", "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png"
                    ))).build(),
                    UserMessage.builder().content(List.of(Map.of(
                            "text", "text with image 2",
                            "image", "https://img.alicdn.com/imgextra/i3/O1CN01SfG4J41UYn9WNt4X1"
                                    + "_!!6000000002530-49-tps-1696-960.webp"
                    ))).build()
            ), null, "1664*928", null, 1, true, false, 0, null));
        }

        @Test
        void testGenerateImageExtraKeysInDictValidation() {
            DashScopeModelClient client = client("wan2.6-image", new FailingTransport());
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("text", "generate image");
            content.put("image", "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png");
            content.put("extra_key", "extra_value");
            content.put("another_key", 123);

            assertModelError(() -> client.generateImage(
                    List.of(UserMessage.builder().content(List.of(content)).build()),
                    null, "1664*928", null, 1, true, false, 0, null));
        }
    }

    @Nested
    @DisplayName("TestDashScopeSpeechGeneration")
    class SpeechGenerationTests {

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateSpeechBasic() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateSpeechWithCustomVoice() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateSpeechLongText() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateSpeechEnglish() {
        }

        @Test
        void testGenerateSpeechEmptyContentValidation() {
            DashScopeModelClient client = client("qwen3-tts-flash", new FailingTransport());

            assertThatThrownBy(() -> client.generateSpeech(List.of(new UserMessage("")), null, "Cherry",
                    "Auto", null))
                    .isInstanceOf(ModelError.class)
                    .hasMessageContaining("non-empty");
        }

        @Test
        void testGenerateSpeechNonUserMessageValidation() {
            DashScopeModelClient client = client("qwen3-tts-flash", new FailingTransport());

            assertModelError(() -> client.generateSpeech(rawUserMessages(new AssistantMessage("hello")),
                    null, "Cherry", "Auto", null));
        }

        @Test
        void testGenerateSpeechInvalidVoiceValidation() {
            DashScopeModelClient client = client("qwen3-tts-flash", new FailingTransport());

            assertModelError(() -> client.generateSpeech(List.of(new UserMessage("test text")),
                    null, "InvalidVoiceName", "Auto", null));
        }

        @Test
        void testGenerateSpeechInvalidLanguageTypeValidation() {
            DashScopeModelClient client = client("qwen3-tts-flash", new FailingTransport());

            assertModelError(() -> client.generateSpeech(List.of(new UserMessage("test text")),
                    null, "Cherry", "InvalidLanguage", null));
        }
    }

    @Nested
    @DisplayName("TestDashScopeVideoGeneration")
    class VideoGenerationTests {

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateVideoTextToVideoBasic() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateVideoWithCustomSizeAndDuration() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateVideoImageToVideo() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateVideoWithNegativePrompt() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateVideoWithAudio() {
        }

        @Test
        @Disabled(PYTHON_SKIP_REASON)
        void testGenerateVideoImageToVideoWithAudio() {
        }

        @Test
        void testGenerateVideoEmptyMessagesValidation() {
            DashScopeModelClient client = client("wan2.6-t2v", new FailingTransport());

            assertModelError(() -> client.generateVideo(List.of(), null, null, null, null, null,
                    5, true, false, null, null, null));
        }

        @Test
        void testGenerateVideoEmptyContentValidation() {
            DashScopeModelClient client = client("wan2.6-t2v", new FailingTransport());

            assertThatThrownBy(() -> client.generateVideo(List.of(new UserMessage("")), null, null, null,
                    null, null, 5, true, false, null, null, null))
                    .isInstanceOf(ModelError.class)
                    .hasMessageContaining("non-empty");
        }

        @Test
        void testGenerateVideoNonUserMessageValidation() {
            DashScopeModelClient client = client("wan2.6-t2v", new FailingTransport());

            assertModelError(() -> client.generateVideo(rawUserMessages(new SystemMessage(
                            "generate a white rabbit running on grass")),
                    null, null, null, null, null, 5, true, false, null, null, null));
        }

        @Test
        void testGenerateVideoTextToVideoWithResolutionInsteadOfSize() {
            DashScopeModelClient client = client("wan2.6-t2v", new FailingTransport());

            assertModelError(() -> client.generateVideo(List.of(new UserMessage("sunset over the sea")),
                    null, null, null, null, "720P", 5, true, false, null, null, null));
        }

        @Test
        void testGenerateVideoImageToVideoWithSizeInsteadOfResolution() {
            DashScopeModelClient client = client("wan2.6-i2v-flash", new FailingTransport());

            assertModelError(() -> client.generateVideo(List.of(new UserMessage("animate the umbrella scene")),
                    "https://cdn.wanx.aliyuncs.com/tmp/pressure/umbrella1.png",
                    null, null, "1280*720", null, 5, true, false, null, null, null));
        }
    }

    private static DashScopeModelClient client(String modelName, DashScopeModelClient.DashScopeTransport transport) {
        return new DashScopeModelClient(
                ModelRequestConfig.builder().modelName(modelName).build(),
                ModelClientConfig.builder()
                        .clientProvider(ProviderType.DASH_SCOPE)
                        .apiKey("sk-your-api-key")
                        .apiBase("dashscope-url")
                        .verifySsl(false)
                        .build(),
                transport);
    }

    private static void assertModelError(ThrowingCall call) {
        assertThatThrownBy(call::run).isInstanceOf(ModelError.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<UserMessage> rawUserMessages(Object message) {
        return (List<UserMessage>) (List) List.of(message);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    /**
     * <p>Mirrors Python's rejected DashScope API response path in
     * {@code tests/system_tests/foundation/llm/test_dashscope_multimodal_generation.py}.</p>
     */
    private static final class FailingTransport implements DashScopeModelClient.DashScopeTransport {

        @Override
        public Map<String, Object> call(DashScopeModelClient.DashScopeApi api,
                                        Map<String, Object> apiParams,
                                        ModelClientConfig clientConfig) {
            return Map.of(
                    "status_code", 400,
                    "code", "InvalidParameter",
                    "message", "simulated DashScope validation rejection"
            );
        }
    }
}
