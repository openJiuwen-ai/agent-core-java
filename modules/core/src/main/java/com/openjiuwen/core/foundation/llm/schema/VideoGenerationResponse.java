/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Video generation response.
 * <p>
 * Mirrors Python's {@code VideoGenerationResponse} model.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoGenerationResponse extends GenerationResponse {
    @JsonProperty("video_url")
    private String videoUrl;

    /** Binary video data. */
    @JsonProperty("video_data")
    private byte[] videoData;

    /** Duration in seconds. */
    private Double duration;

    /** Video resolution (e.g., "1920x1080"). */
    private String resolution;

    /** Video format (mp4, avi, etc.). */
    @Builder.Default
    private String format = "mp4";
}
