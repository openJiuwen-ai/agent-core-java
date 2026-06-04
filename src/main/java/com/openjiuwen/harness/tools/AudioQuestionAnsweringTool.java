package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;

import java.util.Map;

/**
 * Mirrors Python's {@code AudioQuestionAnsweringTool} in {@code openjiuwen.harness.tools.multimodal.audio}.
 */
public class AudioQuestionAnsweringTool extends AbstractHarnessTool {

    public final AudioModelConfig audioModelConfig;

    public AudioQuestionAnsweringTool() {
        this(null);
    }

    public AudioQuestionAnsweringTool(AudioModelConfig audioModelConfig) {
        super(toolCard("audio_question_answering", "audio_question_answering", "Answer questions about audio."), null);
        this.audioModelConfig = audioModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        AudioSupport.ResolvedAudioPath resolved = null;
        try {
            AudioModelConfig config = AudioSupport.requireAudioModelConfig(audioModelConfig);
            String audioPathOrUrl = String.valueOf(inputs.getOrDefault("audio_path_or_url", ""));
            String question = String.valueOf(inputs.getOrDefault("question", ""));
            resolved = AudioSupport.resolveAudioPath(audioPathOrUrl, config);
            String resolvedPath = resolved.path().toString();
            AudioQaResult result = AudioSupport.callWithRetries(config,
                    () -> invokeAudioQuestionAnswering(config, resolvedPath, question));
            return new ToolOutput(true, Map.of(
                    "answer", result.answer(),
                    "duration_seconds", result.durationSeconds(),
                    "model", config.getQuestionAnsweringModel()
            ), null);
        } catch (Exception exc) {
            return new ToolOutput(false, null, exc.getMessage());
        } finally {
            AudioSupport.deleteIfTemporary(resolved);
        }
    }

    protected AudioQaResult invokeAudioQuestionAnswering(AudioModelConfig config, String audioPathArg, String question)
            throws Exception {
        return AudioSupport.invokeAudioQuestionAnswering(config, audioPathArg, question);
    }

    protected record AudioQaResult(String answer, double durationSeconds) {}
}
