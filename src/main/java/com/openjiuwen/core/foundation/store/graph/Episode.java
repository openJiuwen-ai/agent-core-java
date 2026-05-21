/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

/**
 * Episode graph object representing an episodic memory in the knowledge graph.
 * <p>
 * Mirrors Python's {@code Episode} model from
 * <code>foundation/store/graph/graph_object.py</code>.
 */
public class Episode extends BaseGraphObject {

    private String episodeType;

    public Episode() {
        super();
        this.episodeType = "";
    }

    public String getEpisodeType() { return episodeType; }
    public void setEpisodeType(String episodeType) { this.episodeType = episodeType; }

    @Override
    public String getObjType() { return "episode"; }
}
