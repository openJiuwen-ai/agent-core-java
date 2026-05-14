package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;

import java.util.Map;

/**
 * Mirrors Python's {@code AudioQuestionAnsweringTool} in {@code openjiuwen.harness.tools.multimodal.audio}.
 */
public class AudioQuestionAnsweringTool extends AbstractHarnessTool {

    public final AudioModelConfig audioModelConfig;

    public AudioQuestionAnsweringTool(AudioModelConfig audioModelConfig) {
        super(toolCard("audio_question_answering", "audio_question_answering", "Answer questions about audio."), null);
        this.audioModelConfig = audioModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String audioPathOrUrl = String.valueOf(inputs.getOrDefault("audio_path_or_url", ""));
        String question = String.valueOf(inputs.getOrDefault("question", ""));
        AudioQaResult result = invokeAudioQuestionAnswering(audioModelConfig, audioPathOrUrl, question);
        return new ToolOutput(true, Map.of(
                "answer", result.answer(),
                "duration_seconds", result.durationSeconds(),
                "model", audioModelConfig.getQuestionAnsweringModel()
        ), null);
    }

    protected AudioQaResult invokeAudioQuestionAnswering(AudioModelConfig config, String audioPathArg, String question) {
        return new AudioQaResult("", 0.0);
    }

    protected record AudioQaResult(String answer, double durationSeconds) {}
}
