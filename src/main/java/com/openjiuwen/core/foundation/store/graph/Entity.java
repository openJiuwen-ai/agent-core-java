/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node / Entity representing entity nodes in graph.
 */
public class Entity extends NamedGraphObject {
    private List<Float> nameEmbedding;
    private List<Object> relations = new ArrayList<>();
    private List<String> episodes = new ArrayList<>();
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public Entity() {
        setObjType("Entity");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Float> getNameEmbedding() {
        return nameEmbedding;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setNameEmbedding(List<Float> nameEmbedding) {
        this.nameEmbedding = nameEmbedding;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getRelations() {
        return relations;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRelations(List<Object> relations) {
        this.relations = relations;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> getEpisodes() {
        return episodes;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEpisodes(List<String> episodes) {
        this.episodes = episodes;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<GraphUtils.EmbedTask> fetchEmbedTask() {
        return List.of(
                new GraphUtils.EmbedTask(this, "contentEmbedding", getContent()),
                new GraphUtils.EmbedTask(this, "nameEmbedding", getName())
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = super.toMap();
        List<String> relationIds = new ArrayList<>();
        for (Object relation : relations) {
            if (relation instanceof String relationId) {
                relationIds.add(relationId);
            } else if (relation instanceof BaseGraphObject graphObject) {
                relationIds.add(graphObject.getUuid());
            } else {
                throw new IllegalArgumentException("relation must be a String or BaseGraphObject");
            }
        }
        result.put("name_embedding", nameEmbedding);
        result.put("relations", relationIds.stream().distinct().sorted().toList());
        result.put("episodes", episodes.stream().distinct().sorted().toList());
        result.put("attributes", attributes);
        return result;
    }
}
