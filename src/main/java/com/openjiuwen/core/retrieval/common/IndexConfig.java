/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Mirrors Python's {@code IndexConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexConfig {

    private static final Set<String> VALID_INDEX_TYPES = Set.of("hybrid", "bm25", "vector");

    @JsonProperty("index_name")
    private String indexName;

    @JsonProperty("index_type")
    @Builder.Default
    private String indexType = "hybrid";

    @JsonProperty("use_caption_for_images")
    @Builder.Default
    private boolean useCaptionForImages = false;

    public IndexConfig(String indexName, String indexType, boolean useCaptionForImages) {
        setIndexName(indexName);
        setIndexType(indexType);
        this.useCaptionForImages = useCaptionForImages;
    }

    public void setIndexName(String indexName) {
        if (indexName == null) {
            throw new IllegalArgumentException("index_name is required");
        }
        this.indexName = indexName;
    }

    public void setIndexType(String indexType) {
        String value = indexType == null ? "hybrid" : indexType;
        if (!VALID_INDEX_TYPES.contains(value)) {
            throw new IllegalArgumentException("index_type must be one of hybrid, bm25, vector");
        }
        this.indexType = value;
    }
}
