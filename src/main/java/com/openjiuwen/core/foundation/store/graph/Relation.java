/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.List;

/**
 * Relation graph object representing a relationship between entities.
 * <p>
 * Mirrors Python's {@code Relation} model from
 * <code>foundation/store/graph/graph_object.py</code>.
 */
public class Relation extends NamedGraphObject {

    private String relationType;
    private String sourceEntityId;
    private String targetEntityId;
    private List<String> factIds;

    public Relation() {
        super();
        this.relationType = "";
    }

    public Relation(String name, String relationType, String sourceEntityId, String targetEntityId) {
        super(name);
        this.relationType = relationType;
        this.sourceEntityId = sourceEntityId;
        this.targetEntityId = targetEntityId;
    }

    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }

    public String getSourceEntityId() { return sourceEntityId; }
    public void setSourceEntityId(String sourceEntityId) { this.sourceEntityId = sourceEntityId; }

    public String getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(String targetEntityId) { this.targetEntityId = targetEntityId; }

    public List<String> getFactIds() { return factIds; }
    public void setFactIds(List<String> factIds) { this.factIds = factIds; }

    @Override
    public String getObjType() { return "relation"; }
}
