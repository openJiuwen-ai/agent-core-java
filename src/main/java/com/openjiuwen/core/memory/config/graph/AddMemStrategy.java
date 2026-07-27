/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

import com.openjiuwen.core.retrieval.common.RRFRankConfig;
import com.openjiuwen.core.retrieval.common.WeightedRankConfig;
import lombok.Data;

/**
 * Strategy for adding graph memory.
 */
@Data
public class AddMemStrategy {
    private boolean isChineseEntityEnabled = true;
    private boolean isChineseEntityDedupeEnabled = false;
    private boolean isChineseRelationEnabled = false;
    private boolean shouldSkipUuidDedupe = false;
    private EpisodeRetrievalStrategy recallEpisode = new EpisodeRetrievalStrategy();
    private RetrievalStrategy recallEntity = createEntityStrategy();
    private RetrievalStrategy recallRelation = createRelationStrategy();
    private int summaryTarget = 250;
    private boolean isMergeEntitiesEnabled = true;
    private boolean isMergeRelationsEnabled = true;
    private boolean isMergeFilterEnabled = true;

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isChineseEntity() {
        return isChineseEntityEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isChineseEntityDedupe() {
        return isChineseEntityDedupeEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isChineseRelation() {
        return isChineseRelationEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isSkipUuidDedupe() {
        return shouldSkipUuidDedupe;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isMergeEntities() {
        return isMergeEntitiesEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isMergeRelations() {
        return isMergeRelationsEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isMergeFilter() {
        return isMergeFilterEnabled;
    }

    private static RetrievalStrategy createEntityStrategy() {
        RetrievalStrategy strategy = new RetrievalStrategy();
        WeightedRankConfig rankConfig = new WeightedRankConfig();
        rankConfig.setDenseName(0.7);
        rankConfig.setDenseContent(0.1);
        rankConfig.setSparseContent(0.2);
        strategy.setRankConfig(rankConfig);
        strategy.setMinScore(0.1);
        return strategy;
    }

    private static RetrievalStrategy createRelationStrategy() {
        RetrievalStrategy strategy = new RetrievalStrategy();
        strategy.setRankConfig(new RRFRankConfig());
        strategy.setMinScore(0.02);
        return strategy;
    }
}
