/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Audio multimodal tool helpers.
 *
 * <p>Mirrors Python's audio helpers and tool classes in
 * {@code openjiuwen/harness/tools/multimodal/audio.py}.</p>
 */
public final class AudioTools {

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
        return List.of(
                new AudioTranscriptionTool(invoker),
                new AudioQuestionAnsweringTool(invoker),
                new AudioMetadataTool(invoker)
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
        private final AudioInvoker invoker;

        public AudioTranscriptionTool(AudioInvoker invoker) {
            super(toolCard("audio_transcription", "AudioTranscriptionTool", "Transcribe audio."));
            this.invoker = invoker;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            String audioPath = requiredString(inputs, "audio_path");
            if (invoker == null) {
                return ToolOutput.failure("audio model config is not configured");
            }
            return ToolOutput.success(invoker.transcribe(audioPath, inputs == null ? Map.of() : inputs));
        }
    }

    /**
     * Mirrors Python's {@code AudioQuestionAnsweringTool} in
     * {@code openjiuwen/harness/tools/multimodal/audio.py}.
     */
    public static class AudioQuestionAnsweringTool extends AbstractHarnessTool {
        private final AudioInvoker invoker;

        public AudioQuestionAnsweringTool(AudioInvoker invoker) {
            super(toolCard("audio_question_answering", "AudioQuestionAnsweringTool", "Answer a question about audio."));
            this.invoker = invoker;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            String audioPath = requiredString(inputs, "audio_path");
            String question = requiredString(inputs, "question");
            if (invoker == null) {
                return ToolOutput.failure("audio model config is not configured");
            }
            return ToolOutput.success(invoker.questionAnswer(audioPath, question, inputs == null ? Map.of() : inputs));
        }
    }

    /**
     * Mirrors Python's {@code AudioMetadataTool} in
     * {@code openjiuwen/harness/tools/multimodal/audio.py}.
     */
    public static class AudioMetadataTool extends AbstractHarnessTool {
        private final AudioInvoker invoker;

        public AudioMetadataTool(AudioInvoker invoker) {
            super(toolCard("audio_metadata", "AudioMetadataTool", "Inspect audio metadata."));
            this.invoker = invoker;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            String audioPath = requiredString(inputs, "audio_path");
            if (invoker == null) {
                Path path = Path.of(audioPath);
                return ToolOutput.success(Map.of(
                        "path", audioPath,
                        "exists", Files.exists(path),
                        "size", Files.exists(path) ? Files.size(path) : 0
                ));
            }
            return ToolOutput.success(invoker.metadata(audioPath, inputs == null ? Map.of() : inputs));
        }
    }
}
