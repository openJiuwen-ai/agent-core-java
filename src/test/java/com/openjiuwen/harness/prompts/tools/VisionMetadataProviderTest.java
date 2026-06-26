/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VisionMetadataProviderTest {

    @SuppressWarnings("unchecked")
    @Test
    void imageOcrProviderMatchesPythonSchema() {
        ImageOCRMetadataProvider provider = new ImageOCRMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(provider.getName()).isEqualTo("image_ocr");
        assertThat(provider.getDescription("cn")).isEqualTo("读取图片中的可见文本，适合 OCR、票据文本提取和截图文字识别。");
        assertThat((List<String>) schema.get("required")).containsExactly("image_path_or_url");
        assertThat(properties.keySet()).containsExactly("image_path_or_url", "prompt");
        assertThat((Map<String, Object>) properties.get("prompt"))
                .containsEntry("type", "string")
                .containsEntry("description", "Optional custom OCR prompt");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    @Test
    void visualQuestionAnsweringProviderMatchesPythonSchema() {
        VisualQuestionAnsweringMetadataProvider provider = new VisualQuestionAnsweringMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("en");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(provider.getName()).isEqualTo("visual_question_answering");
        assertThat(provider.getDescription("en"))
                .isEqualTo("Understand an image and answer questions, optionally grounding the answer with OCR first.");
        assertThat((List<String>) schema.get("required")).containsExactly("image_path_or_url", "question");
        assertThat(properties.keySet()).containsExactly("image_path_or_url", "question", "include_ocr", "ocr_prompt");
        assertThat((Map<String, Object>) properties.get("include_ocr"))
                .containsEntry("type", "boolean")
                .containsEntry("description", "Whether to run OCR first and inject the result into the VQA prompt, default true");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
