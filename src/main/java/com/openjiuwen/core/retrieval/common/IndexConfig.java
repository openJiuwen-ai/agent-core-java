/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code IndexConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexConfig {

    @JsonProperty("index_name")
    private String indexName;

    @JsonProperty("index_type")
    @Builder.Default
    private String indexType = "hybrid";

    @JsonProperty("use_caption_for_images")
    @Builder.Default
    private boolean useCaptionForImages = false;
}
