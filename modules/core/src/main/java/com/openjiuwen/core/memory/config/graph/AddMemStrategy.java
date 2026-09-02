/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config.graph;

import com.openjiuwen.core.retrieval.common.RRFRankConfig;
import com.openjiuwen.core.retrieval.common.WeightedRankConfig;

import lombok.Data;

/**
 * Strategy for adding graph memory.
 * 
 * @since 0.1.7
 */
@Data
public class AddMemStrategy {
    private boolean isChineseEntityEnabled = true;
    private boolean isChineseEntityDedupeEnabled = false;
    private boolean isChineseRelationEnabled = false;
    private boolean shouldSkipUuidDedupe = false;

    /**
     * EpisodeRetrievalStrategy.
     * 
     * @since 0.1.7
     */
    private EpisodeRetrievalStrategy recallEpisode = new EpisodeRetrievalStrategy();

    /**
     * createEntityStrategy.
     * 
     * @since 0.1.7
     */
    private RetrievalStrategy recallEntity = createEntityStrategy();

    /**
     * createRelationStrategy.
     * 
     * @since 0.1.7
     */
    private RetrievalStrategy recallRelation = createRelationStrategy();
    private int summaryTarget = 250;
    private boolean isMergeEntitiesEnabled = true;
    private boolean isMergeRelationsEnabled = true;
    private boolean isMergeFilterEnabled = true;

    /**
     * isChineseEntity.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isChineseEntity() {
        return isChineseEntityEnabled;
    }

    /**
     * isChineseEntityDedupe.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isChineseEntityDedupe() {
        return isChineseEntityDedupeEnabled;
    }

    /**
     * isChineseRelation.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isChineseRelation() {
        return isChineseRelationEnabled;
    }

    /**
     * isSkipUuidDedupe.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isSkipUuidDedupe() {
        return shouldSkipUuidDedupe;
    }

    /**
     * isMergeEntities.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isMergeEntities() {
        return isMergeEntitiesEnabled;
    }

    /**
     * isMergeRelations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isMergeRelations() {
        return isMergeRelationsEnabled;
    }

    /**
     * isMergeFilter.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isMergeFilter() {
        return isMergeFilterEnabled;
    }

    /**
     * createEntityStrategy.
     * 
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * createRelationStrategy.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static RetrievalStrategy createRelationStrategy() {
        RetrievalStrategy strategy = new RetrievalStrategy();
        strategy.setRankConfig(new RRFRankConfig());
        strategy.setMinScore(0.02);
        return strategy;
    }
}
