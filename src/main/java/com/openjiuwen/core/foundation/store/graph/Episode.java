/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code Episode} in
 * {@code openjiuwen/core/foundation/store/graph/graph_object.py}.
 */
public class Episode extends BaseGraphObject {

    private long validSince;
    private final List<Object> entities = new ArrayList<>();

    public Episode() {
        setObjType("Episode");
        this.validSince = getCreatedAt();
    }

    public List<String> serializeEntities() {
        return serializeGraphObjectList(entities);
    }

    public long getValidSince() {
        return validSince;
    }

    public void setValidSince(long validSince) {
        this.validSince = validSince == -1 ? getCreatedAt() : validSince;
    }

    public List<Object> getEntities() {
        return entities;
    }

    public void setEntities(List<?> values) {
        entities.clear();
        if (values != null) {
            entities.addAll(values);
        }
    }
}
