/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Audio multimodal tool helpers.
 *
 * <p>Mirrors Python's audio helpers and tool classes in
 * {@code openjiuwen/harness/tools/multimodal/audio.py}.</p>
 */
public final class AudioTools {
    private static final String SANDBOX_PATH_MARKER = "home/user";

    private AudioTools() {
    }

    public static boolean isHttpUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    public static String getAudioExtension(String url, String contentType) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.contains("mpeg")) {
            return ".mp3";
        }
        if (normalizedContentType.contains("wav")) {
            return ".wav";
        }
        if (normalizedContentType.contains("ogg")) {
            return ".ogg";
        }
        String path = isHttpUrl(url) ? URI.create(url).getPath() : String.valueOf(url);
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot) : ".mp3";
    }

    public static EncodedAudio encodeAudioFile(Path audioPath) throws Exception {
        byte[] bytes = Files.readAllBytes(audioPath);
        return new EncodedAudio(Base64.getEncoder().encodeToString(bytes), getAudioExtension(audioPath.toString(), ""));
    }

    public static List<Tool> createAudioTools(AudioInvoker invoker) {
        return createAudioTools("cn", null, invoker);
    }

    public static List<Tool> createAudioTools(String language,
                                              DeepAgentConfig.AudioModelConfig audioModelConfig,
                                              AudioInvoker invoker) {
        String resolvedLanguage = language == null || language.isBlank() ? "cn" : language;
        return List.of(
                new AudioTranscriptionTool(resolvedLanguage, audioModelConfig, invoker),
                new AudioQuestionAnsweringTool(resolvedLanguage, audioModelConfig, invoker),
                new AudioMetadataTool(resolvedLanguage, audioModelConfig, invoker)
        );
    }

    /**
     * Mirrors Python's encoded audio tuple in {@code openjiuwen/harness/tools/multimodal/audio.py}.
     */
    public record EncodedAudio(String base64, String extension) {
    }

    public interface AudioInvoker {
        Map<String, Object> transcribe(String audioPath, Map<String, Object> inputs) throws Exception;

        Map<String, Object> questionAnswer(String audioPath, String question, Map<String, Object> inputs)
                throws Exception;

        Map<String, Object> metadata(String audioPath, Map<String, Object> inputs) throws Exception;
    }

    /**
     * Mirrors Python's {@code AudioTranscriptionTool} in
     * {@code openjiuwen/harness/tools/multimodal/audio.py}.
     */
    public static class AudioTranscriptionTool extends AbstractHarnessTool {
        private final DeepAgentConfig.AudioModelConfig audioModelConfig;
        private final AudioInvoker invoker;

        public AudioTranscriptionTool() {
            this(null, null);
        }

        public AudioTranscriptionTool(AudioInvoker invoker) {
            this(null, invoker);
        }

        public AudioTranscriptionTool(DeepAgentConfig.AudioModelConfig audioModelConfig, AudioInvoker invoker) {
            this("cn", audioModelConfig, invoker);
        }

        public AudioTranscriptionTool(String language,
                                      DeepAgentConfig.AudioModelConfig audioModelConfig,
                                      AudioInvoker invoker) {
            super(toolCard("audio_transcription", "AudioTranscriptionTool", "Transcribe audio."));
            String ignored = language;
            this.invoker = invoker;
            this.audioModelConfig = audioModelConfig;
        }

        public DeepAgentConfig.AudioModelConfig getAudioModelConfig() {
            return audioModelConfig;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            try {
                DeepAgentConfig.AudioModelConfig config = requireAudioModelConfig(audioModelConfig);
                String audioPath = resolveAudioPath(audioPathOrUrl(inputs), config);
                if (invoker == null) {
                    throw new IllegalStateException("Audio invoker is not configured.");
                }
                Map<String, Object> payload = invoker.transcribe(audioPath, inputs == null ? Map.of() : inputs);
                Map<String, Object> data = linkedMap();
                data.put("text", payload.get("text"));
                data.put("model", config.getTranscriptionModel());
                return ToolOutput.success(data);
            } catch (Exception exception) {
                return ToolOutput.failure(exception.getMessage());
            }
        }
    }

    /**
     * Mirrors Python's {@code AudioQuestionAnsweringTool} in
     * {@code openjiuwen/harness/tools/multimodal/audio.py}.
     */
    public static class AudioQuestionAnsweringTool extends AbstractHarnessTool {
        private final DeepAgentConfig.AudioModelConfig audioModelConfig;
        private final AudioInvoker invoker;

        public AudioQuestionAnsweringTool() {
            this(null, null);
        }

        public AudioQuestionAnsweringTool(AudioInvoker invoker) {
            this(null, invoker);
        }

        public AudioQuestionAnsweringTool(DeepAgentConfig.AudioModelConfig audioModelConfig, AudioInvoker invoker) {
            this("cn", audioModelConfig, invoker);
        }

        public AudioQuestionAnsweringTool(String language,
                                          DeepAgentConfig.AudioModelConfig audioModelConfig,
                                          AudioInvoker invoker) {
            super(toolCard("audio_question_answering", "AudioQuestionAnsweringTool", "Answer a question about audio."));
            String ignored = language;
            this.invoker = invoker;
            this.audioModelConfig = audioModelConfig;
        }

        public DeepAgentConfig.AudioModelConfig getAudioModelConfig() {
            return audioModelConfig;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            try {
                DeepAgentConfig.AudioModelConfig config = requireAudioModelConfig(audioModelConfig);
                String audioPath = resolveAudioPath(audioPathOrUrl(inputs), config);
                String question = requiredString(inputs, "question");
                if (invoker == null) {
                    throw new IllegalStateException("Audio invoker is not configured.");
                }
                Map<String, Object> payload =
                        invoker.questionAnswer(audioPath, question, inputs == null ? Map.of() : inputs);
                Map<String, Object> data = linkedMap();
                data.put("answer", payload.get("answer"));
                data.put("duration_seconds", payload.get("duration_seconds"));
                data.put("model", config.getQuestionAnsweringModel());
                return ToolOutput.success(data);
            } catch (Exception exception) {
                return ToolOutput.failure(exception.getMessage());
            }
        }
    }

    /**
     * Mirrors Python's {@code AudioMetadataTool} in
     * {@code openjiuwen/harness/tools/multimodal/audio.py}.
     */
    public static class AudioMetadataTool extends AbstractHarnessTool {
        private final DeepAgentConfig.AudioModelConfig audioModelConfig;
        private final AudioInvoker invoker;

        public AudioMetadataTool() {
            this(null, null);
        }

        public AudioMetadataTool(AudioInvoker invoker) {
            this(null, invoker);
        }

        public AudioMetadataTool(DeepAgentConfig.AudioModelConfig audioModelConfig, AudioInvoker invoker) {
            this("cn", audioModelConfig, invoker);
        }

        public AudioMetadataTool(String language,
                                 DeepAgentConfig.AudioModelConfig audioModelConfig,
                                 AudioInvoker invoker) {
            super(toolCard("audio_metadata", "AudioMetadataTool", "Inspect audio metadata."));
            String ignored = language;
            this.invoker = invoker;
            this.audioModelConfig = audioModelConfig;
        }

        public DeepAgentConfig.AudioModelConfig getAudioModelConfig() {
            return audioModelConfig;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            try {
                DeepAgentConfig.AudioModelConfig config = requireAudioModelConfig(audioModelConfig);
                String audioPath = resolveAudioPath(audioPathOrUrl(inputs), config);
                if (invoker != null) {
                    return ToolOutput.success(invoker.metadata(audioPath, inputs == null ? Map.of() : inputs));
                }
                return ToolOutput.success(localMetadata(config, Path.of(audioPath)));
            } catch (Exception exception) {
                return ToolOutput.failure(exception.getMessage());
            }
        }
    }

    private static DeepAgentConfig.AudioModelConfig requireAudioModelConfig(
            DeepAgentConfig.AudioModelConfig audioModelConfig
    ) {
        if (audioModelConfig == null) {
            throw new IllegalArgumentException(
                    "Audio model config is not set. Pass DeepAgentConfig.audio_model_config "
                            + "or construct the tool with AudioModelConfig."
            );
        }
        if (audioModelConfig.getBaseUrl() == null || audioModelConfig.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Audio model config missing base_url.");
        }
        return audioModelConfig;
    }

    private static String audioPathOrUrl(Map<String, Object> inputs) {
        String value = stringValueLocal(inputs == null ? null : inputs.get("audio_path_or_url")).trim();
        if (value.isEmpty()) {
            value = stringValueLocal(inputs == null ? null : inputs.get("audio_path")).trim();
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("audio_path_or_url is required");
        }
        return value;
    }

    private static String stringValueLocal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String resolveAudioPath(String audioPathOrUrl, DeepAgentConfig.AudioModelConfig config)
            throws Exception {
        if (audioPathOrUrl.contains(SANDBOX_PATH_MARKER)) {
            throw new IllegalArgumentException(
                    "Audio tools cannot access sandbox-only paths. Use a local path outside the sandbox or an https URL."
            );
        }
        if (isHttpUrl(audioPathOrUrl)) {
            return audioPathOrUrl;
        }
        Path audioPath = Path.of(audioPathOrUrl).toAbsolutePath().normalize();
        if (!Files.exists(audioPath) || !Files.isRegularFile(audioPath)) {
            throw new FileNotFoundException("Audio path does not exist or is not a file: " + audioPathOrUrl);
        }
        String ignoredBaseUrl = config.getBaseUrl();
        return audioPath.toString();
    }

    private static Map<String, Object> localMetadata(DeepAgentConfig.AudioModelConfig config, Path audioPath)
            throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("duration_seconds", roundedDuration(audioPath));
        result.put("title", null);
        result.put("artist", null);
        result.put("release_date", null);
        result.put("score", null);
        result.put("identified", false);
        if (config.getAcrAccessKey() == null || config.getAcrAccessKey().isBlank()
                || config.getAcrAccessSecret() == null || config.getAcrAccessSecret().isBlank()) {
            result.put("note", "Title and artist identification is disabled because ACR credentials are not configured.");
            return result;
        }
        result.put("note", "No metadata found for the given audio file.");
        return result;
    }

    private static double roundedDuration(Path audioPath) throws Exception {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(audioPath.toFile())) {
            AudioFormat format = stream.getFormat();
            float frameRate = format.getFrameRate();
            if (frameRate <= 0) {
                throw new IllegalArgumentException("Unable to determine audio duration: no parser succeeded");
            }
            double duration = stream.getFrameLength() / frameRate;
            return Math.round(duration * 100.0) / 100.0;
        }
    }
}
