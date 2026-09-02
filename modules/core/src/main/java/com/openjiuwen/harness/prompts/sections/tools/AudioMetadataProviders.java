/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Audio tool metadata providers.
 *
 * @since 0.1.7
 */
final class AudioMetadataProviders {
    /**
     * AudioMetadataProviders.
     * 
     * @since 0.1.7
     */
    private AudioMetadataProviders() {
    }

    static final class AudioTranscriptionMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "audio_transcription";
        }

        /**
         * getDescription.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getDescription(String language) {
            return text(language, "转写本地音频文件或公网音频 URL，提取音频中的语音文本内容。",
                    "Transcribe a local audio file or public audio URL into text.");
        }

        /**
         * getInputParams.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getInputParams(String language) {
            return audioPathSchema(language);
        }
    }

    static final class AudioQuestionAnsweringMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "audio_question_answering";
        }

        /**
         * getDescription.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getDescription(String language) {
            return text(language, "理解音频内容并回答问题，适合语音、访谈、播客和普通音频内容分析。",
                    "Understand audio content and answer questions about speech or general audio.");
        }

        /**
         * getInputParams.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getInputParams(String language) {
            return ToolSchemaSupport
                    .objectSchema(
                            ToolSchemaSupport.properties(new Object[]{"audio_path_or_url",
                                    ToolSchemaSupport.property("string", audioPathDescription(language)), "question",
                                    ToolSchemaSupport.property("string",
                                            text(language, "要基于音频内容回答的问题",
                                                    "Question to answer based on the audio content"))}),
                            List.of("audio_path_or_url", "question"));
        }
    }

    static final class AudioMetadataMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "audio_metadata";
        }

        /**
         * getDescription.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getDescription(String language) {
            return text(language, "识别音频时长，并在配置了 ACR 信息时尝试识别歌曲标题、歌手和发布时间。",
                    "Inspect audio duration and optionally identify song metadata when ACR credentials are "
                            + "configured.");
        }

        /**
         * getInputParams.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getInputParams(String language) {
            return audioPathSchema(language);
        }
    }

    /**
     * audioPathSchema.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> audioPathSchema(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[]{"audio_path_or_url",
                        ToolSchemaSupport.property("string", audioPathDescription(language))}),
                List.of("audio_path_or_url"));
    }

    /**
     * audioPathDescription.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static String audioPathDescription(String language) {
        return text(language, "本地音频路径或公网 http(s) 音频 URL，不支持 sandbox-only 路径",
                "Local audio path or public http(s) audio URL; sandbox-only paths are not supported");
    }

    /**
     * text.
     * 
     * @param language language
     * @param cn cn
     * @param en en
     * @return the result
     * @since 0.1.7
     */
    private static String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
