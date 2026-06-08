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
 * Mirrors Python's {@code KnowledgeBaseConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeBaseConfig {

    @JsonProperty("kb_id")
    private String kbId;

    @JsonProperty("index_type")
    @Builder.Default
    private String indexType = "hybrid";

    @JsonProperty("use_graph")
    @Builder.Default
    private boolean useGraph = false;

    @JsonProperty("chunk_size")
    @Builder.Default
    private int chunkSize = 512;

    @JsonProperty("chunk_overlap")
    @Builder.Default
    private int chunkOverlap = 50;

    @JsonProperty("use_caption_for_images")
    @Builder.Default
    private boolean useCaptionForImages = false;
}
