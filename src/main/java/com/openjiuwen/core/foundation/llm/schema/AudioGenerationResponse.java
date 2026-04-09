/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
 * Audio/Speech generation response.
 * <p>
 * Mirrors Python's {@code AudioGenerationResponse} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudioGenerationResponse extends GenerationResponse {

    /** URL of the generated audio. */
    @JsonProperty("audio_url")
    private String audioUrl;

    /** Binary audio data. */
    @JsonProperty("audio_data")
    private byte[] audioData;

    /** Duration in seconds. */
    private Double duration;

    /** Audio format (mp3, wav, etc.). */
    @Builder.Default
    private String format = "mp3";
}
