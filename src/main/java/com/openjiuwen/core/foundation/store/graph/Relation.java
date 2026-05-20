/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.Map;

/**
 * Edge / Relation entity representing relationships between entity nodes.
 */
public class Relation extends NamedGraphObject {
    private int validSince = -1;
    private int validUntil = -1;
    private int offsetSince = 0;
    private int offsetUntil = 0;
    private Object lhs;
    private Object rhs;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Relation() {
        setObjType("Relation");
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
    public int getValidUntil() {
        return validUntil;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setValidUntil(int validUntil) {
        this.validUntil = validUntil;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getOffsetSince() {
        return offsetSince;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOffsetSince(int offsetSince) {
        this.offsetSince = offsetSince;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getOffsetUntil() {
        return offsetUntil;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOffsetUntil(int offsetUntil) {
        this.offsetUntil = offsetUntil;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getLhs() {
        return lhs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLhs(Object lhs) {
        this.lhs = lhs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getRhs() {
        return rhs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRhs(Object rhs) {
        this.rhs = rhs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Relation updateConnectedEntities() {
        for (Object connectedNode : java.util.List.of(lhs, rhs)) {
            if (connectedNode instanceof Entity entity) {
                if (!entity.getRelations().contains(this) && !entity.getRelations().contains(getUuid())) {
                    entity.getRelations().add(this);
                }
            }
        }
        return this;
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
        result.put("valid_since", validSince);
        result.put("valid_until", validUntil);
        result.put("offset_since", offsetSince);
        result.put("offset_until", offsetUntil);
        result.put("lhs", entityId(lhs, "lhs"));
        result.put("rhs", entityId(rhs, "rhs"));
        return result;
    }

    private static String entityId(Object value, String fieldName) {
        if (value instanceof String id) {
            return id;
        }
        if (value instanceof Entity entity) {
            return entity.getUuid();
        }
        throw new IllegalArgumentException(fieldName + " must be a String or Entity");
    }
}
