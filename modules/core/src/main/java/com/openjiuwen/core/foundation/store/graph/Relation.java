/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.Map;

/**
 * Edge / Relation entity representing relationships between entity nodes.
 * 
 * @since 0.1.7
 */
public class Relation extends NamedGraphObject {
    private int validSince = -1;
    private int validUntil = -1;
    private int offsetSince = 0;
    private int offsetUntil = 0;
    private Object lhs;
    private Object rhs;

    /**
     * Relation.
     * 
     * @since 0.1.7
     */
    public Relation() {
        setObjType("Relation");
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
     * getValidUntil.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getValidUntil() {
        return validUntil;
    }

    /**
     * setValidUntil.
     * 
     * @param validUntil validUntil
     * @since 0.1.7
     */
    public void setValidUntil(int validUntil) {
        this.validUntil = validUntil;
    }

    /**
     * getOffsetSince.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getOffsetSince() {
        return offsetSince;
    }

    /**
     * setOffsetSince.
     * 
     * @param offsetSince offsetSince
     * @since 0.1.7
     */
    public void setOffsetSince(int offsetSince) {
        this.offsetSince = offsetSince;
    }

    /**
     * getOffsetUntil.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getOffsetUntil() {
        return offsetUntil;
    }

    /**
     * setOffsetUntil.
     * 
     * @param offsetUntil offsetUntil
     * @since 0.1.7
     */
    public void setOffsetUntil(int offsetUntil) {
        this.offsetUntil = offsetUntil;
    }

    /**
     * getLhs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getLhs() {
        return lhs;
    }

    /**
     * setLhs.
     * 
     * @param lhs lhs
     * @since 0.1.7
     */
    public void setLhs(Object lhs) {
        this.lhs = lhs;
    }

    /**
     * getRhs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getRhs() {
        return rhs;
    }

    /**
     * setRhs.
     * 
     * @param rhs rhs
     * @since 0.1.7
     */
    public void setRhs(Object rhs) {
        this.rhs = rhs;
    }

    /**
     * updateConnectedEntities.
     * 
     * @return the result
     * @since 0.1.7
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
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
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

    /**
     * entityId.
     * 
     * @param value value
     * @param fieldName fieldName
     * @return the result
     * @since 0.1.7
     */
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
