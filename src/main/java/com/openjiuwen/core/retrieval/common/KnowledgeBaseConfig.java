/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.StatusCode;

/**
 * Knowledge base configuration.
 */
public class KnowledgeBaseConfig {

    private String kbId;
    private String indexType = "hybrid";
    private boolean useGraph = false;
    private int chunkSize = 512;
    private int chunkOverlap = 50;

    public KnowledgeBaseConfig() {
    }

    public KnowledgeBaseConfig(String kbId) {
        this.kbId = kbId;
        validate();
    }

    public KnowledgeBaseConfig(String kbId, String indexType, boolean useGraph, int chunkSize, int chunkOverlap) {
        this.kbId = kbId;
        this.indexType = indexType;
        this.useGraph = useGraph;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        validate();
    }

    public void validate() {
        RetrievalValidation.requireNonBlank(kbId, "KnowledgeBaseConfig.kbId");
        this.indexType = RetrievalValidation.validateIndexType(indexType, "KnowledgeBaseConfig.indexType");
        RetrievalValidation.requirePositive(chunkSize, "chunk_size", StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID);
        RetrievalValidation.requireNonNegative(
                chunkOverlap,
                "chunk_overlap",
                StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID);
    }

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
        validate();
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
        validate();
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
        validate();
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
        validate();
    }
}
