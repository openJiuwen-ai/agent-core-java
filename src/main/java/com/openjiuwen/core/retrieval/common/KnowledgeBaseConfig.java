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
 * Mirrors Python's {@code KnowledgeBaseConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeBaseConfig {

    private static final Set<String> VALID_INDEX_TYPES = Set.of("hybrid", "bm25", "vector");

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

    public KnowledgeBaseConfig(String kbId) {
        this(kbId, "hybrid", false, 512, 50, false);
    }

    public KnowledgeBaseConfig(String kbId, String indexType, boolean useGraph, int chunkSize, int chunkOverlap) {
        this(kbId, indexType, useGraph, chunkSize, chunkOverlap, false);
    }

    public KnowledgeBaseConfig(
            String kbId,
            String indexType,
            boolean useGraph,
            int chunkSize,
            int chunkOverlap,
            boolean useCaptionForImages
    ) {
        setKbId(kbId);
        setIndexType(indexType);
        this.useGraph = useGraph;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.useCaptionForImages = useCaptionForImages;
    }

    public void validate() {
        if (kbId == null) {
            throw new IllegalArgumentException("kb_id is required");
        }
        setIndexType(indexType);
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk_size must be positive");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunk_overlap must be non-negative");
        }
    }

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("kb_id is required");
        }
        this.kbId = kbId;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        String value = indexType == null ? "hybrid" : indexType;
        if (!VALID_INDEX_TYPES.contains(value)) {
            throw new IllegalArgumentException("index_type must be one of hybrid, bm25, vector");
        }
        this.indexType = value;
    }

    public boolean isUseGraph() {
        return useGraph;
    }

    public void setUseGraph(boolean useGraph) {
        this.useGraph = useGraph;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
