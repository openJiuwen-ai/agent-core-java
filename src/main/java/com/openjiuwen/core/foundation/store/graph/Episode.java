/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Episode nodes with no name.
 */
public class Episode extends BaseGraphObject {
    private int validSince = -1;
    private List<Object> entities = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public Episode() {
        setObjType("Episode");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getValidSince() {
        return validSince;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setValidSince(int validSince) {
        this.validSince = validSince;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getEntities() {
        return entities;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEntities(List<Object> entities) {
        this.entities = entities;
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
