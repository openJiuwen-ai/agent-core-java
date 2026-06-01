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
    private long validSince;
    private long validUntil;
    private int offsetSince;
    private int offsetUntil;
    private List<String> factIds;

    public Relation() {
        super();
        this.relationType = "";
        this.validSince = getCreatedAt();
        this.validUntil = -1;
        this.offsetSince = 0;
        this.offsetUntil = 0;
    }

    public Relation(String name, String relationType, String sourceEntityId, String targetEntityId) {
        super(name);
        this.relationType = relationType;
        this.sourceEntityId = sourceEntityId;
        this.targetEntityId = targetEntityId;
        this.validSince = getCreatedAt();
        this.validUntil = -1;
        this.offsetSince = 0;
        this.offsetUntil = 0;
    }

    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }

    public String getSourceEntityId() { return sourceEntityId; }
    public void setSourceEntityId(String sourceEntityId) { this.sourceEntityId = sourceEntityId; }

    public String getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(String targetEntityId) { this.targetEntityId = targetEntityId; }

    public String getLhs() { return sourceEntityId; }
    public void setLhs(String lhs) { this.sourceEntityId = lhs; }
    public void setLhs(BaseGraphObject lhs) { this.sourceEntityId = lhs == null ? null : lhs.getUuid(); }

    public String getRhs() { return targetEntityId; }
    public void setRhs(String rhs) { this.targetEntityId = rhs; }
    public void setRhs(BaseGraphObject rhs) { this.targetEntityId = rhs == null ? null : rhs.getUuid(); }

    public long getValidSince() { return validSince; }
    public void setValidSince(long validSince) { this.validSince = validSince == -1 ? getCreatedAt() : validSince; }

    public long getValidUntil() { return validUntil; }
    public void setValidUntil(long validUntil) { this.validUntil = validUntil; }

    public int getOffsetSince() { return offsetSince; }
    public void setOffsetSince(int offsetSince) { this.offsetSince = offsetSince; }

    public int getOffsetUntil() { return offsetUntil; }
    public void setOffsetUntil(int offsetUntil) { this.offsetUntil = offsetUntil; }

    public List<String> getFactIds() { return factIds; }
    public void setFactIds(List<String> factIds) { this.factIds = factIds; }

    @Override
    public String getObjType() {
        return relationType == null || relationType.isBlank() ? "relation" : relationType;
    }

    public Relation updateConnectedEntities(Entity lhs, Entity rhs) {
        setLhs(lhs);
        setRhs(rhs);
        addRelationReference(lhs);
        addRelationReference(rhs);
        return this;
    }

    private void addRelationReference(Entity entity) {
        if (entity == null || getUuid() == null) {
            return;
        }
        if (!entity.getRelations().contains(getUuid())) {
            entity.getRelations().add(getUuid());
        }
    }
}
