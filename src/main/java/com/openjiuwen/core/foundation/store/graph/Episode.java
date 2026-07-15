/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Episode nodes with no name.
 * 
 * @since 0.1.7
 */
public class Episode extends BaseGraphObject {
    private int validSince = -1;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Object> entities = new ArrayList<>();

    /**
     * Episode.
     * 
     * @since 0.1.7
     */
    public Episode() {
        setObjType("Episode");
    }

    /**
     * getValidSince.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getValidSince() {
        return validSince;
    }

    /**
     * setValidSince.
     * 
     * @param validSince validSince
     * @since 0.1.7
     */
    public void setValidSince(int validSince) {
        this.validSince = validSince;
    }

    /**
     * getEntities.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getEntities() {
        return entities;
    }

    /**
     * setEntities.
     * 
     * @param entities entities
     * @since 0.1.7
     */
    public void setEntities(List<Object> entities) {
        this.entities = entities;
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
        List<String> entityIds = new ArrayList<>();
        for (Object entity : entities) {
            if (entity instanceof String entityId) {
                entityIds.add(entityId);
            } else if (entity instanceof BaseGraphObject graphObject) {
                entityIds.add(graphObject.getUuid());
            } else {
                throw new IllegalArgumentException("entity must be a String or BaseGraphObject");
            }
        }
        result.put("valid_since", validSince);
        result.put("entities", entityIds.stream().distinct().sorted().toList());
        return result;
    }
}
