/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.config;

import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's graph memory configuration defaults in
 * {@code openjiuwen/core/memory/config/graph.py}.
 */
class GraphMemoryConfigTest {

    @Test
    void episodeTypeValuesMatchPythonEnum() {
        assertEquals(0, EpisodeType.CONVERSATION.getValue());
        assertEquals(1, EpisodeType.DOCUMENT.getValue());
        assertEquals(2, EpisodeType.JSON.getValue());
        assertEquals(EpisodeType.DOCUMENT, EpisodeType.fromValue(1));
        assertThrows(IllegalArgumentException.class, () -> EpisodeType.fromValue(99));
    }

    @Test
    void strategyDefaultsMatchPythonModels() {
        BaseStrategy base = new BaseStrategy();
        assertEquals(3, base.getTopK());
        assertEquals(0.3d, base.getMinScore(), 1.0e-9);
        assertInstanceOf(RRFRankConfig.class, base.getRankConfig());

        RetrievalStrategy retrieval = new RetrievalStrategy();
        assertFalse(retrieval.isSameKind());

        EpisodeRetrievalStrategy episode = new EpisodeRetrievalStrategy();
        assertFalse(episode.isSameKind());
        assertTrue(episode.isExcludeFutureResults());
        assertEquals(0.025d, episode.getMinScore(), 1.0e-9);
        assertInstanceOf(RRFRankConfig.class, episode.getRankConfig());
    }

    @Test
    void addMemoryStrategyDefaultsMatchPython() {
        AddMemStrategy strategy = new AddMemStrategy();

        assertTrue(strategy.isChineseEntity());
        assertFalse(strategy.isChineseEntityDedupe());
        assertFalse(strategy.isChineseRelation());
        assertFalse(strategy.isSkipUuidDedupe());
        assertEquals(250, strategy.getSummaryTarget());
        assertTrue(strategy.isMergeEntities());
        assertTrue(strategy.isMergeRelations());
        assertTrue(strategy.isMergeFilter());
        assertEquals(0.025d, strategy.getRecallEpisode().getMinScore(), 1.0e-9);
        assertEquals(0.1d, strategy.getRecallEntity().getMinScore(), 1.0e-9);
        assertInstanceOf(WeightedRankConfig.class, strategy.getRecallEntity().getRankConfig());
        WeightedRankConfig weighted = (WeightedRankConfig) strategy.getRecallEntity().getRankConfig();
        assertEquals(0.7d, weighted.getNameDense(), 1.0e-9);
        assertEquals(0.1d, weighted.getContentDense(), 1.0e-9);
        assertEquals(0.2d, weighted.getContentSparse(), 1.0e-9);
        assertEquals(0.02d, strategy.getRecallRelation().getMinScore(), 1.0e-9);
        assertNotNull(GraphMemoryConfig.DEFAULT_STRATEGY);
    }

    @Test
    void searchConfigDefaultsAndValidationMatchPython() {
        SearchConfig config = new SearchConfig();
        assertEquals(3, config.getTopK());
        assertEquals(0.3d, config.getMinScore(), 1.0e-9);
        assertEquals(3, config.getBfsK());
        assertEquals(0, config.getBfsDepth());
        assertFalse(config.isRerank());
        assertEquals("en", config.getLanguage());

        config.setLanguage("cn");
        assertEquals("cn", config.getLanguage());

        assertThrows(IllegalArgumentException.class, () -> config.setTopK(0));
        assertThrows(IllegalArgumentException.class, () -> config.setBfsK(0));
        assertThrows(IllegalArgumentException.class, () -> config.setBfsDepth(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setLanguage("jp"));

        AddMemStrategy add = new AddMemStrategy();
        assertThrows(IllegalArgumentException.class, () -> add.setSummaryTarget(9));
        assertThrows(IllegalArgumentException.class, () -> add.setSummaryTarget(2001));
    }
}
