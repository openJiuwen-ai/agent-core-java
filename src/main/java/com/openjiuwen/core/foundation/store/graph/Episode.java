/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Episode graph object representing an episodic memory in the knowledge graph.
 * <p>
 * Mirrors Python's {@code Episode} model from
 * <code>openjiuwen/core/foundation/store/graph/graph_object.py</code>.
 */
public class Episode extends BaseGraphObject {

    private String episodeType;
    private long validSince;
    private List<String> entities;

    public Episode() {
        super();
        this.episodeType = "";
        this.validSince = getCreatedAt();
        this.entities = new ArrayList<>();
    }

    public String getEpisodeType() { return episodeType; }
    public void setEpisodeType(String episodeType) { this.episodeType = episodeType; }

    public long getValidSince() { return validSince; }
    public void setValidSince(long validSince) { this.validSince = validSince == -1 ? getCreatedAt() : validSince; }

    /**
     * Get entities mentioned in this episode.
     * Mirrors Python's {@code entities} field.
     *
     * @return list of entity UUIDs
     */
    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    @Override
    public String getObjType() { return "episode"; }
}
