package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;
import com.openjiuwen.harness.schema.config.VisionModelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's minimal web/vision/audio tool behavior slices for P1-03.
 */
class MultimodalAndWebToolsTest {

    @Test
    void imageOcrToolEncodesLocalImage(@TempDir Path tempDir) throws Exception {
        Path image = tempDir.resolve("sample.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        VisionModelConfig config = new VisionModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setModel("mock-model");

        ImageOCRTool tool = new ImageOCRTool(config) {
            @Override
            protected String callVisionModel(String imagePathOrUrl, String prompt, VisionModelConfig configuredModel) {
                assertEquals(config, configuredModel);
                return "detected text";
            }
        };

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("image_path_or_url", image.toString()), Map.of());
        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("detected text", data.get("text"));
        @SuppressWarnings("unchecked")
        Map<String, Object> imageContent = (Map<String, Object>) data.get("image_content");
        @SuppressWarnings("unchecked")
        Map<String, Object> imageUrl = (Map<String, Object>) imageContent.get("image_url");
        assertTrue(String.valueOf(imageUrl.get("url")).startsWith("data:image/png;base64,"));
    }

    @Test
    void visualQuestionAnsweringUsesOcrContext() {
        VisionModelConfig config = new VisionModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setModel("mock-model");

        VisualQuestionAnsweringTool tool = new VisualQuestionAnsweringTool(config) {
            private int count = 0;
            @Override
            protected String callVisionModel(String imagePathOrUrl, String prompt, VisionModelConfig configuredModel) {
                count++;
                if (count == 1) {
                    return "SALE 50% OFF";
                }
                assertTrue(prompt.contains("SALE 50% OFF"));
                assertTrue(prompt.contains("What does the sign say?"));
                return "The sign says SALE 50% OFF.";
            }
        };

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "image_path_or_url", "https://example.com/image.png",
                "question", "What does the sign say?"
        ), Map.of());

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("SALE 50% OFF", data.get("ocr_text"));
        assertEquals("The sign says SALE 50% OFF.", data.get("answer"));
    }

    @Test
    void audioToolsReturnTranscriptionQaAndMetadata(@TempDir Path tempDir) throws Exception {
        Path wav = tempDir.resolve("sample.wav");
        writeTestWav(wav, 1);
        AudioModelConfig config = new AudioModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setTranscriptionModel("mock-transcribe");
        config.setQuestionAnsweringModel("mock-audio-qa");

        AudioTranscriptionTool transcriptionTool = new AudioTranscriptionTool(config) {
            @Override
            protected String invokeAudioTranscription(AudioModelConfig cfg, String audioPathArg) {
                assertEquals(config, cfg);
                assertEquals(wav.toString(), audioPathArg);
                return "hello from audio";
            }
        };
        ToolOutput transcription = (ToolOutput) transcriptionTool.invoke(Map.of("audio_path_or_url", wav.toString()), Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> transcriptionData = (Map<String, Object>) transcription.getData();
        assertEquals("hello from audio", transcriptionData.get("text"));
        assertEquals("mock-transcribe", transcriptionData.get("model"));

        AudioQuestionAnsweringTool qaTool = new AudioQuestionAnsweringTool(config) {
            @Override
            protected AudioQaResult invokeAudioQuestionAnswering(AudioModelConfig cfg, String audioPathArg, String question) {
                assertEquals("What is being said?", question);
                return new AudioQaResult("A person says hello.", 1.0);
            }
        };
        ToolOutput qa = (ToolOutput) qaTool.invoke(Map.of(
                "audio_path_or_url", wav.toString(),
                "question", "What is being said?"
        ), Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> qaData = (Map<String, Object>) qa.getData();
        assertEquals("A person says hello.", qaData.get("answer"));
        assertEquals(1.0, qaData.get("duration_seconds"));

        AudioMetadataTool metadataTool = new AudioMetadataTool(config);
        ToolOutput metadata = (ToolOutput) metadataTool.invoke(Map.of("audio_path_or_url", wav.toString()), Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> metadataData = (Map<String, Object>) metadata.getData();
        assertEquals(1.0, metadataData.get("duration_seconds"));
        assertEquals(false, metadataData.get("identified"));
        assertTrue(String.valueOf(metadataData.get("note")).contains("ACR credentials"));
    }

    private void writeTestWav(Path path, int durationSeconds) throws Exception {
        int sampleRate = 16000;
        int frames = sampleRate * durationSeconds;
        byte[] data = new byte[frames * 2];
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(data), format, frames)) {
            AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
}
