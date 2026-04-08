/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Image generation response.
 * <p>
 * Mirrors Python's {@code ImageGenerationResponse} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageGenerationResponse extends GenerationResponse {

    /** List of generated image URLs. */
    private List<String> images;

    /** List of generated images in base64 encoding. */
    @JsonProperty("images_base64")
    private List<String> imagesBase64;

    /** Timestamp of creation. */
    private Integer created;
}
