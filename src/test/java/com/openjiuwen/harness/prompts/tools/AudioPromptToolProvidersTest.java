/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AudioPromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void audioTranscriptionProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new AudioPromptToolProviders.AudioTranscriptionMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");

        assertThat(provider.getName()).isEqualTo("audio_transcription");
        assertThat(provider.getDescription("en")).contains("Transcribe a local audio file");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactly("audio_path_or_url");
        assertThat(castList(schema.get("required"))).containsExactly("audio_path_or_url");
    }

    @Test
    void audioQuestionAnsweringProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new AudioPromptToolProviders.AudioQuestionAnsweringMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");

        assertThat(provider.getName()).isEqualTo("audio_question_answering");
        assertThat(provider.getDescription("en")).contains("answer questions");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactly("audio_path_or_url", "question");
        assertThat(castList(schema.get("required"))).containsExactly("audio_path_or_url", "question");
    }

    @Test
    void audioMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new AudioPromptToolProviders.AudioMetadataMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");

        assertThat(provider.getName()).isEqualTo("audio_metadata");
        assertThat(provider.getDescription("en")).contains("Inspect audio duration");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactly("audio_path_or_url");
        assertThat(castList(schema.get("required"))).containsExactly("audio_path_or_url");
    }

    @Test
    void validatePassesForAllAudioProviders() {
        assertThatCode(new AudioPromptToolProviders.AudioTranscriptionMetadataProvider()::validate)
                .doesNotThrowAnyException();
        assertThatCode(new AudioPromptToolProviders.AudioQuestionAnsweringMetadataProvider()::validate)
                .doesNotThrowAnyException();
        assertThatCode(new AudioPromptToolProviders.AudioMetadataMetadataProvider()::validate)
                .doesNotThrowAnyException();
    }
}
