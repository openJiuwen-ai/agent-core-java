/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.harness.prompts.tools.audio} in
 * {@code openjiuwen/harness/prompts/tools/audio.py}.
 */
public final class AudioPromptToolProviders {

    private static final Map<String, String> AUDIO_TRANSCRIPTION_DESCRIPTION = Map.of(
            "cn", "杞啓鏈湴闊抽鏂囦欢鎴栧叕缃戦煶棰?URL锛屾彁鍙栭煶棰戜腑鐨勮闊虫枃鏈唴瀹广€?",
            "en", "Transcribe a local audio file or public audio URL into text."
    );

    private static final Map<String, String> AUDIO_QUESTION_ANSWERING_DESCRIPTION = Map.of(
            "cn", "鐞嗚В闊抽鍐呭骞跺洖绛旈棶棰橈紝閫傚悎璇煶銆佽璋堛€佹挱瀹㈠拰鏅€氶煶棰戝唴瀹瑰垎鏋愩€?",
            "en", "Understand audio content and answer questions about speech or general audio."
    );

    private static final Map<String, String> AUDIO_METADATA_DESCRIPTION = Map.of(
            "cn", "璇嗗埆闊抽鏃堕暱锛屽苟鍦ㄩ厤缃簡 ACR 淇℃伅鏃跺皾璇曡瘑鍒瓕鏇叉爣棰樸€佹瓕鎵嬪拰鍙戝竷鏃堕棿銆?",
            "en", "Inspect audio duration and optionally identify song metadata when ACR credentials are configured."
    );

    private static final Map<String, Map<String, String>> AUDIO_TRANSCRIPTION_PARAMS = Map.of(
            "audio_path_or_url", Map.of(
                    "cn", "鏈湴闊抽璺緞鎴栧叕缃?http(s) 闊抽 URL锛屼笉鏀寔 sandbox-only 璺緞",
                    "en", "Local audio path or public http(s) audio URL; sandbox-only paths are not supported"
            )
    );

    private static final Map<String, Map<String, String>> AUDIO_QUESTION_ANSWERING_PARAMS = Map.of(
            "audio_path_or_url", Map.of(
                    "cn", "鏈湴闊抽璺緞鎴栧叕缃?http(s) 闊抽 URL锛屼笉鏀寔 sandbox-only 璺緞",
                    "en", "Local audio path or public http(s) audio URL; sandbox-only paths are not supported"
            ),
            "question", Map.of(
                    "cn", "瑕佸熀浜庨煶棰戝唴瀹瑰洖绛旂殑闂",
                    "en", "Question to answer based on the audio content"
            )
    );

    private static final Map<String, Map<String, String>> AUDIO_METADATA_PARAMS = Map.of(
            "audio_path_or_url", Map.of(
                    "cn", "鏈湴闊抽璺緞鎴栧叕缃?http(s) 闊抽 URL锛屼笉鏀寔 sandbox-only 璺緞",
                    "en", "Local audio path or public http(s) audio URL; sandbox-only paths are not supported"
            )
    );

    private AudioPromptToolProviders() {
    }

    public static Map<String, Object> getAudioTranscriptionInputParams(String language) {
        String lang = "en".equals(language) ? "en" : "cn";
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("audio_path_or_url", property(
                "string",
                AUDIO_TRANSCRIPTION_PARAMS.get("audio_path_or_url").get(lang)
        ));
        return schema(properties, List.of("audio_path_or_url"));
    }

    public static Map<String, Object> getAudioQuestionAnsweringInputParams(String language) {
        String lang = "en".equals(language) ? "en" : "cn";
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("audio_path_or_url", property(
                "string",
                AUDIO_QUESTION_ANSWERING_PARAMS.get("audio_path_or_url").get(lang)
        ));
        properties.put("question", property(
                "string",
                AUDIO_QUESTION_ANSWERING_PARAMS.get("question").get(lang)
        ));
        return schema(properties, List.of("audio_path_or_url", "question"));
    }

    public static Map<String, Object> getAudioMetadataInputParams(String language) {
        String lang = "en".equals(language) ? "en" : "cn";
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("audio_path_or_url", property(
                "string",
                AUDIO_METADATA_PARAMS.get("audio_path_or_url").get(lang)
        ));
        return schema(properties, List.of("audio_path_or_url"));
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    /**
     * Mirrors Python's {@code AudioTranscriptionMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/audio.py}.
     */
    public static final class AudioTranscriptionMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "audio_transcription";
        }

        @Override
        public String getDescription(String language) {
            return AUDIO_TRANSCRIPTION_DESCRIPTION.getOrDefault(language, AUDIO_TRANSCRIPTION_DESCRIPTION.get("cn"));
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getAudioTranscriptionInputParams(language);
        }
    }

    /**
     * Mirrors Python's {@code AudioQuestionAnsweringMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/audio.py}.
     */
    public static final class AudioQuestionAnsweringMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "audio_question_answering";
        }

        @Override
        public String getDescription(String language) {
            return AUDIO_QUESTION_ANSWERING_DESCRIPTION.getOrDefault(
                    language,
                    AUDIO_QUESTION_ANSWERING_DESCRIPTION.get("cn")
            );
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getAudioQuestionAnsweringInputParams(language);
        }
    }

    /**
     * Mirrors Python's {@code AudioMetadataMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/audio.py}.
     */
    public static final class AudioMetadataMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "audio_metadata";
        }

        @Override
        public String getDescription(String language) {
            return AUDIO_METADATA_DESCRIPTION.getOrDefault(language, AUDIO_METADATA_DESCRIPTION.get("cn"));
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return getAudioMetadataInputParams(language);
        }
    }
}
