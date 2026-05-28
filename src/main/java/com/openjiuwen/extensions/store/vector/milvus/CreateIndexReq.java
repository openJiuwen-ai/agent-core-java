/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.vector.milvus;

import java.util.Map;

/**
 * Placeholder for Milvus V2 CreateIndexReq.
 */
public class CreateIndexReq {
    private String collectionName;
    private String fieldName;
    private Map<String, Object> params;
    
    public CreateIndexReq() {}
    
    public CreateIndexReq(String collectionName, String fieldName) {
        this.collectionName = collectionName;
        this.fieldName = fieldName;
    }
    
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}