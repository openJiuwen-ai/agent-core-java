
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.harness.schema.config.AudioModelConfig;
import com.openjiuwen.harness.schema.config.VisionModelConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class HarnessMultimodalToolsCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void visionToolsShouldEncodeImageAndUseOcrContext() throws Exception {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        VisionModelConfig config = VisionModelConfig.builder().apiKey("test-key").baseUrl("https://example.com/v1")
                .model("mock-model").build();

        ImageOCRTool ocrTool = new ImageOCRTool(config, (cfg, prompt, imageContent) -> {
            assertThat(String.valueOf(((Map<?, ?>) imageContent.get("image_url")).get("url"))).startsWith("data:image");
            return "detected text";
        });
        VisualQuestionAnsweringTool vqaTool = new VisualQuestionAnsweringTool(config, (imagePath, prompt, cfg) -> {
            if ("ocr".equals(prompt)) {
                return new VisualQuestionAnsweringTool.VisionResult("SALE 50% OFF", cfg.getModel());
            }
            assertThat(prompt).contains("SALE 50% OFF").contains("What does the sign say?");
            return new VisualQuestionAnsweringTool.VisionResult("The sign says SALE 50% OFF.", cfg.getModel());
        });

        ToolOutput ocr = ocrTool.invoke(Map.of("image_path_or_url", image.toString()));
        ToolOutput vqa = vqaTool.invoke(
                Map.of("image_path_or_url", "https://example.com/image.png", "question", "What does the sign say?"));

        assertThat(ocr.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) ocr.getData()).get("text")).isEqualTo("detected text");
        assertThat(vqa.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) vqa.getData()).get("ocr_text")).isEqualTo("SALE 50% OFF");
        assertThat(((Map<?, ?>) vqa.getData()).get("answer")).isEqualTo("The sign says SALE 50% OFF.");
    }

    @Test
    void audioToolsShouldTranscribeAnswerAndReadMetadata() throws Exception {
        Path audio = tempDir.resolve("sample.wav");
        writeTestWav(audio, 1);
        AudioModelConfig config = AudioModelConfig.builder().apiKey("audio-key").baseUrl("https://audio.example.com/v1")
                .transcriptionModel("mock-transcribe").questionAnsweringModel("mock-audio-qa").build();

        AudioTranscriptionTool transcription = new AudioTranscriptionTool(config, (cfg, path) -> "hello from audio");
        AudioQuestionAnsweringTool qa = new AudioQuestionAnsweringTool(config,
                (cfg, path, question) -> new AudioQuestionAnsweringTool.QaResult("A person says hello.", 1.0));
        AudioMetadataTool metadata = new AudioMetadataTool(config);

        ToolOutput tr = transcription.invoke(Map.of("audio_path_or_url", audio.toString()));
        ToolOutput ans = qa.invoke(Map.of("audio_path_or_url", audio.toString(), "question", "What is being said?"));
        ToolOutput meta = metadata.invoke(Map.of("audio_path_or_url", audio.toString()));

        assertThat(tr.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) tr.getData()).get("text")).isEqualTo("hello from audio");
        assertThat(ans.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) ans.getData()).get("answer")).isEqualTo("A person says hello.");
        assertThat(meta.isSuccess()).isTrue();
        assertThat((Double) ((Map<?, ?>) meta.getData()).get("duration_seconds")).isGreaterThan(0.9);
    }

    @Test
    void multimodalFactoriesAndConfigsShouldExposeExpectedObjects() {
        VisionModelConfig vision = VisionModelConfig.builder().apiKey("v").baseUrl("b").build();
        AudioModelConfig audio = AudioModelConfig.builder().apiKey("a").baseUrl("b").build();

        assertThat(MultimodalToolFactory.createVisionTools(vision)).hasSize(2);
        assertThat(MultimodalToolFactory.createAudioTools(audio)).hasSize(3);
    }

    private static void writeTestWav(Path path, int durationSeconds) throws Exception {
        javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(16000, 16, 1, true, false);
        int frames = 16000 * durationSeconds;
        byte[] data = new byte[frames * 2];
        try (var stream =
            new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(data), format, frames)) {
            javax.sound.sampled.AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
}
