/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VideoUnderstandingMetadataProviderTest {

    @Test
    void returnsExpectedNameAndDescriptions() {
        VideoUnderstandingMetadataProvider provider = new VideoUnderstandingMetadataProvider();

        assertThat(provider.getName()).isEqualTo("video_understanding");
        assertThat(provider.getDescription("cn")).isEqualTo("理解视频内容并回答用户问题，支持远程视频 URL 或本地视频文件路径。");
        assertThat(provider.getDescription("en"))
                .isEqualTo("Understand video content and answer user queries. Supports remote video URLs or local video file paths.");
        assertThat(provider.getDescription("fr")).isEqualTo(provider.getDescription("cn"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void preservesPythonInputSchemaAndRequiredFields() {
        Map<String, Object> schema = VideoUnderstandingMetadataProvider.getVideoUnderstandingInputParams("en");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        assertThat(schema.get("type")).isEqualTo("object");
        assertThat((List<String>) schema.get("required")).containsExactly("query", "video_path");
        assertThat(properties.keySet()).containsExactly(
                "query",
                "video_path",
                "model",
                "max_tokens",
                "temperature",
                "timeout_seconds"
        );
        assertThat((Map<String, Object>) properties.get("video_path"))
                .containsEntry("type", "string")
                .containsEntry("description", "Local video path or remote video URL");
        assertThat((Map<String, Object>) properties.get("temperature"))
                .containsEntry("type", "number")
                .containsEntry("description", "Optional sampling temperature");
    }

    @Test
    void validatePassesForBothLanguages() {
        VideoUnderstandingMetadataProvider provider = new VideoUnderstandingMetadataProvider();

        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
