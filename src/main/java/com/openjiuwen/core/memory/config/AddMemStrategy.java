/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;

import java.util.Objects;

/**
 * Strategy for adding graph memory.
 * <p>
 * Mirrors Python's {@code AddMemStrategy} in
 * {@code openjiuwen/core/memory/config/graph.py}.
 * </p>
 */
public class AddMemStrategy {

    @JsonProperty("chinese_entity")
    private boolean chineseEntity = true;
    @JsonProperty("chinese_entity_dedupe")
    private boolean chineseEntityDedupe;
    @JsonProperty("chinese_relation")
    private boolean chineseRelation;
    @JsonProperty("skip_uuid_dedupe")
    private boolean skipUuidDedupe;
    @JsonProperty("recall_episode")
    private EpisodeRetrievalStrategy recallEpisode = new EpisodeRetrievalStrategy();
    @JsonProperty("recall_entity")
    private RetrievalStrategy recallEntity = new RetrievalStrategy(
            3,
            0.1d,
            new WeightedRankConfig(0.7d, 0.1d, 0.2d),
            false);
    @JsonProperty("recall_relation")
    private RetrievalStrategy recallRelation = new RetrievalStrategy(3, 0.02d, new RRFRankConfig(), false);
    @JsonProperty("summary_target")
    private int summaryTarget = 250;
    @JsonProperty("merge_entities")
    private boolean mergeEntities = true;
    @JsonProperty("merge_relations")
    private boolean mergeRelations = true;
    @JsonProperty("merge_filter")
    private boolean mergeFilter = true;

    public boolean isChineseEntity() {
        return chineseEntity;
    }

    public void setChineseEntity(boolean chineseEntity) {
        this.chineseEntity = chineseEntity;
    }

    public boolean isChineseEntityDedupe() {
        return chineseEntityDedupe;
    }

    public void setChineseEntityDedupe(boolean chineseEntityDedupe) {
        this.chineseEntityDedupe = chineseEntityDedupe;
    }

    public boolean isChineseRelation() {
        return chineseRelation;
    }

    public void setChineseRelation(boolean chineseRelation) {
        this.chineseRelation = chineseRelation;
    }

    public boolean isSkipUuidDedupe() {
        return skipUuidDedupe;
    }

    public void setSkipUuidDedupe(boolean skipUuidDedupe) {
        this.skipUuidDedupe = skipUuidDedupe;
    }

    public EpisodeRetrievalStrategy getRecallEpisode() {
        return recallEpisode;
    }

    public void setRecallEpisode(EpisodeRetrievalStrategy recallEpisode) {
        this.recallEpisode = Objects.requireNonNull(recallEpisode, "recallEpisode");
    }

    public RetrievalStrategy getRecallEntity() {
        return recallEntity;
    }

    public void setRecallEntity(RetrievalStrategy recallEntity) {
        this.recallEntity = Objects.requireNonNull(recallEntity, "recallEntity");
    }

    public RetrievalStrategy getRecallRelation() {
        return recallRelation;
    }

    public void setRecallRelation(RetrievalStrategy recallRelation) {
        this.recallRelation = Objects.requireNonNull(recallRelation, "recallRelation");
    }

    public int getSummaryTarget() {
        return summaryTarget;
    }

    public void setSummaryTarget(int summaryTarget) {
        if (summaryTarget < 10 || summaryTarget > 2000) {
            throw new IllegalArgumentException("summary_target must be between 10 and 2000");
        }
        this.summaryTarget = summaryTarget;
    }

    public boolean isMergeEntities() {
        return mergeEntities;
    }

    public void setMergeEntities(boolean mergeEntities) {
        this.mergeEntities = mergeEntities;
    }

    public boolean isMergeRelations() {
        return mergeRelations;
    }

    public void setMergeRelations(boolean mergeRelations) {
        this.mergeRelations = mergeRelations;
    }

    public boolean isMergeFilter() {
        return mergeFilter;
    }

    public void setMergeFilter(boolean mergeFilter) {
        this.mergeFilter = mergeFilter;
    }
}
