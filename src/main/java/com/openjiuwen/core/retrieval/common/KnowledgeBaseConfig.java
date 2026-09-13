/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.StatusCode;

/**
 * Knowledge base configuration.
 * 
 * @since 0.1.7
 */
public class KnowledgeBaseConfig {
    private String kbId;
    private String indexType = "hybrid";
    private boolean useGraph = false;
    private int chunkSize = 512;
    private int chunkOverlap = 50;

    /**
     * KnowledgeBaseConfig.
     * 
     * @since 0.1.7
     */
    public KnowledgeBaseConfig() {
    }

    /**
     * KnowledgeBaseConfig.
     * 
     * @param kbId kbId
     * @since 0.1.7
     */
    public KnowledgeBaseConfig(String kbId) {
        this.kbId = kbId;
        validate();
    }

    /**
     * KnowledgeBaseConfig.
     * 
     * @param kbId kbId
     * @param indexType indexType
     * @param useGraph useGraph
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @since 0.1.7
     */
    public KnowledgeBaseConfig(String kbId, String indexType, boolean useGraph, int chunkSize, int chunkOverlap) {
        this.kbId = kbId;
        this.indexType = indexType;
        this.useGraph = useGraph;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        validate();
    }

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        RetrievalValidation.requireNonBlank(kbId, "KnowledgeBaseConfig.kbId");
        this.indexType = RetrievalValidation.validateIndexType(indexType, "KnowledgeBaseConfig.indexType");
        RetrievalValidation.requirePositive(chunkSize, "chunk_size", StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID);
        RetrievalValidation.requireNonNegative(chunkOverlap, "chunk_overlap",
                StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID);
    }

    /**
     * getKbId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getKbId() {
        return kbId;
    }

    /**
     * setKbId.
     * 
     * @param kbId kbId
     * @since 0.1.7
     */
    public void setKbId(String kbId) {
        this.kbId = kbId;
        validate();
    }

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getIndexType() {
        return indexType;
    }

    /**
     * setIndexType.
     * 
     * @param indexType indexType
     * @since 0.1.7
     */
    public void setIndexType(String indexType) {
        this.indexType = indexType;
        validate();
    }

    /**
     * isUseGraph.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isUseGraph() {
        return useGraph;
    }

    /**
     * setUseGraph.
     * 
     * @param useGraph useGraph
     * @since 0.1.7
     */
    public void setUseGraph(boolean useGraph) {
        this.useGraph = useGraph;
    }

    /**
     * getChunkSize.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * setChunkSize.
     * 
     * @param chunkSize chunkSize
     * @since 0.1.7
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
        validate();
    }

    /**
     * getChunkOverlap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getChunkOverlap() {
        return chunkOverlap;
    }

    /**
     * setChunkOverlap.
     * 
     * @param chunkOverlap chunkOverlap
     * @since 0.1.7
     */
    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
        validate();
    }
}
