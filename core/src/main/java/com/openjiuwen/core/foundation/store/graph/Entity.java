/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code Entity} in
 * {@code openjiuwen/core/foundation/store/graph/graph_object.py}.
 */
public class Entity extends NamedGraphObject {

    private List<Double> nameEmbedding;
    private final List<Object> relations = new ArrayList<>();
    private final List<String> episodes = new ArrayList<>();
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public Entity() {
        setObjType("Entity");
    }

    @Override
    public List<EmbeddingTask> fetchEmbedTask() {
        return List.of(
                new EmbeddingTask(this, "content_embedding", getContent()),
                new EmbeddingTask(this, "name_embedding", getName())
        );
    }

    public List<String> serializeRelations() {
        return serializeGraphObjectList(relations);
    }

    public List<String> serializeEpisodes() {
        return serializeGraphObjectList(episodes);
    }

    public List<Double> getNameEmbedding() {
        return nameEmbedding == null ? null : new ArrayList<>(nameEmbedding);
    }

    public void setNameEmbedding(List<Double> nameEmbedding) {
        this.nameEmbedding = copyDoubles(nameEmbedding);
    }

    public List<Object> getRelations() {
        return relations;
    }

    public void setRelations(List<?> values) {
        relations.clear();
        if (values != null) {
            relations.addAll(values);
        }
    }

    public List<String> getEpisodes() {
        return episodes;
    }

    public void setEpisodes(List<String> values) {
        episodes.clear();
        if (values != null) {
            episodes.addAll(values);
        }
    }

    public Map<String, Object> getAttributes() {
        return new LinkedHashMap<>(attributes);
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = copyMetadata(attributes);
    }
}
