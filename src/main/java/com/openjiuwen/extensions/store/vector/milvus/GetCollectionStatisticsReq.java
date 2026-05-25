/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.store.vector.milvus;

/**
 * Placeholder for Milvus V2 GetCollectionStatisticsReq.
 */
public class GetCollectionStatisticsReq {
    private String collectionName;
    
    public GetCollectionStatisticsReq() {}
    
    public GetCollectionStatisticsReq(String collectionName) {
        this.collectionName = collectionName;
    }
    
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
}