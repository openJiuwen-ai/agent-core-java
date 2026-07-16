/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Vision and video tool metadata providers.
 *
 * @since 0.1.7
 */
final class VisionMetadataProviders {
    /**
     * VisionMetadataProviders.
     * 
     * @since 0.1.7
     */
    private VisionMetadataProviders() {
    }

    static final class ImageOCRMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "image_ocr";
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
            return text(language, "读取图片中的可见文本，适合 OCR、票据文本提取和截图文字识别。",
                    "Extract visible text from an image for OCR, screenshot text recognition, and document"
                            + " snippets.");
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
                            ToolSchemaSupport
                                    .properties(
                                            new Object[]{"image_path_or_url",
                                                    ToolSchemaSupport.property("string",
                                                            text(language, "本地图片路径或公网 http(s) 图片 URL",
                                                                    "Local image path or public http(s) image URL")),
                                                    "prompt",
                                                    ToolSchemaSupport.property("string",
                                                            text(language, "可选，自定义 OCR 提示词",
                                                                    "Optional custom OCR prompt"))}),
                            List.of("image_path_or_url"));
        }
    }

    static final class VisualQuestionAnsweringMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "visual_question_answering";
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
            return text(language, "理解图片内容并回答问题，可选先做 OCR 再结合识别到的文字回答。",
                    "Understand an image and answer questions, optionally grounding the answer with OCR" + " first.");
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
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[]{"image_path_or_url", ToolSchemaSupport.property("string",
                            text(language, "本地图片路径或公网 http(s) 图片 URL", "Local image path or public http(s) image URL")),
                            "question",
                            ToolSchemaSupport
                                    .property("string", text(language, "要询问图片的问题", "Question to ask about the image")),
                            "include_ocr",
                            ToolSchemaSupport.property("boolean",
                                    text(language, "是否先执行 OCR 并把结果拼接进问答提示词，默认 true",
                                            "Whether to run OCR first and injec"
                                                    + "t the result into the VQA prompt, " + "default true")),
                            "ocr_prompt",
                            ToolSchemaSupport.property("string",
                                    text(language, "可选，自定义 OCR 提示词，仅在 include_ocr 为 true 时使用",
                                            "Optional custom OCR prompt used only when include_ocr is true"))}),
                    List.of("image_path_or_url", "question"));
        }
    }

    static final class VideoUnderstandingMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "video_understanding";
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
            return text(language, "理解视频内容并回答用户问题，支持远程视频 URL 或本地视频文件路径。",
                    "Understand video content and answer user queries. Supports remote video URLs or local"
                            + " video file paths.");
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
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[]{"query",
                            ToolSchemaSupport.property(
                                    "string", text(language, "用户关于视频内容的问题", "User query about the video content")),
                            "video_path",
                            ToolSchemaSupport.property("string",
                                    text(language, "本地视频路径或远程视频 URL", "Local video path or remote video URL")),
                            "model",
                            ToolSchemaSupport.property("string", text(language, "可选，指定模型名称", "Optional model name")),
                            "max_tokens",
                            ToolSchemaSupport.property(
                                    "integer", text(language, "可选，最大输出 token 数", "Optional maximum output tokens")),
                            "temperature",
                            ToolSchemaSupport
                                    .property("number", text(language, "可选，采样温度", "Optional sampling temperature")),
                            "timeout_seconds",
                            ToolSchemaSupport.property("integer",
                                    text(language, "可选，请求超时时间（秒）", "Optional timeout in seconds"))}),
                    List.of("query", "video_path"));
        }
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
