/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

/**
 * Mirrors Python's {@code Relation} in
 * {@code openjiuwen/core/foundation/store/graph/graph_object.py}.
 */
public class Relation extends NamedGraphObject {

    private long validSince;
    private long validUntil = -1;
    private int offsetSince;
    private int offsetUntil;
    private Object lhs;
    private Object rhs;

    public Relation() {
        setObjType("Relation");
        this.validSince = getCreatedAt();
    }

    public Relation(Object lhs, Object rhs) {
        this();
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public Relation updateConnectedEntities() {
        appendToConnectedEntity(lhs);
        appendToConnectedEntity(rhs);
        return this;
    }

    public String serializeLhs() {
        return serializeGraphObjectReference(lhs);
    }

    public String serializeRhs() {
        return serializeGraphObjectReference(rhs);
    }

    private void appendToConnectedEntity(Object value) {
        if (value instanceof Entity entity
                && !entity.getRelations().contains(this)
                && !entity.getRelations().contains(getUuid())) {
            entity.getRelations().add(this);
        }
    }

    public long getValidSince() {
        return validSince;
    }

    public void setValidSince(long validSince) {
        this.validSince = validSince == -1 ? getCreatedAt() : validSince;
    }

    public long getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(long validUntil) {
        this.validUntil = validUntil;
    }

    public int getOffsetSince() {
        return offsetSince;
    }

    public void setOffsetSince(int offsetSince) {
        this.offsetSince = offsetSince;
    }

    public int getOffsetUntil() {
        return offsetUntil;
    }

    public void setOffsetUntil(int offsetUntil) {
        this.offsetUntil = offsetUntil;
    }

    public Object getLhs() {
        return lhs;
    }

    public void setLhs(Object lhs) {
        this.lhs = lhs;
    }

    public Object getRhs() {
        return rhs;
    }

    public void setRhs(Object rhs) {
        this.rhs = rhs;
    }
}
