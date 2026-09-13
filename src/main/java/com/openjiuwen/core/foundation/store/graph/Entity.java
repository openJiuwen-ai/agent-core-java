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
 * 
 * @since 0.1.7
 */
public class Entity extends NamedGraphObject {
    private List<Float> nameEmbedding;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Object> relations = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> episodes = new ArrayList<>();

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * Entity.
     * 
     * @since 0.1.7
     */
    public Entity() {
        setObjType("Entity");
    }

    /**
     * getNameEmbedding.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Float> getNameEmbedding() {
        return nameEmbedding;
    }

    /**
     * setNameEmbedding.
     * 
     * @param nameEmbedding nameEmbedding
     * @since 0.1.7
     */
    public void setNameEmbedding(List<Float> nameEmbedding) {
        this.nameEmbedding = nameEmbedding;
    }

    /**
     * getRelations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getRelations() {
        return relations;
    }

    /**
     * setRelations.
     * 
     * @param relations relations
     * @since 0.1.7
     */
    public void setRelations(List<Object> relations) {
        this.relations = relations;
    }

    /**
     * getEpisodes.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getEpisodes() {
        return episodes;
    }

    /**
     * setEpisodes.
     * 
     * @param episodes episodes
     * @since 0.1.7
     */
    public void setEpisodes(List<String> episodes) {
        this.episodes = episodes;
    }

    /**
     * getAttributes.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * setAttributes.
     * 
     * @param attributes attributes
     * @since 0.1.7
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * fetchEmbedTask.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<GraphUtils.EmbedTask> fetchEmbedTask() {
        return List.of(new GraphUtils.EmbedTask(this, "contentEmbedding", getContent()),
                new GraphUtils.EmbedTask(this, "nameEmbedding", getName()));
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
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
