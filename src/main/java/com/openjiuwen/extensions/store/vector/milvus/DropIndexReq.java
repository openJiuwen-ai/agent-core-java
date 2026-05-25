/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.vector.milvus;

/**
 * Placeholder for Milvus V2 DropIndexReq.
 */
public class DropIndexReq {
    private String collectionName;
    private String fieldName;
    
    public DropIndexReq() {}
    
    public DropIndexReq(String collectionName, String fieldName) {
        this.collectionName = collectionName;
        this.fieldName = fieldName;
    }
    
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
}