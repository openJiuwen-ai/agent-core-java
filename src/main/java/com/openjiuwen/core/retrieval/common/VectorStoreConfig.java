/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

/**
 * Vector store configuration.
 */
public class VectorStoreConfig {

    private String storeProvider;
    private String databaseName = "";
    private String collectionName;
    private String distanceMetric = "cosine";

    public VectorStoreConfig() {
    }

    public VectorStoreConfig(String storeProvider, String collectionName) {
        this(storeProvider, "", collectionName, "cosine");
    }

    public VectorStoreConfig(StoreType storeProvider, String collectionName) {
        this(storeProvider == null ? null : storeProvider.value(), "", collectionName, "cosine");
    }

    public VectorStoreConfig(String storeProvider, String databaseName, String collectionName, String distanceMetric) {
        this.storeProvider = storeProvider;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        this.distanceMetric = distanceMetric;
        validate();
    }

    public void validate() {
        storeProvider = RetrievalValidation.validateStoreType(storeProvider, "VectorStoreConfig.storeProvider");
        RetrievalValidation.validateDatabaseName(databaseName, "VectorStoreConfig.databaseName");
        RetrievalValidation.requireNonBlank(collectionName, "VectorStoreConfig.collectionName");
        distanceMetric = RetrievalValidation.validateDistanceMetric(distanceMetric, "VectorStoreConfig.distanceMetric");
    }

    public String getStoreProvider() {
        return storeProvider;
    }

    public StoreType getStoreType() {
        return StoreType.fromValue(storeProvider);
    }

    public void setStoreProvider(String storeProvider) {
        this.storeProvider = RetrievalValidation.validateStoreType(storeProvider, "VectorStoreConfig.storeProvider");
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        RetrievalValidation.validateDatabaseName(databaseName, "VectorStoreConfig.databaseName");
        this.databaseName = databaseName;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        RetrievalValidation.requireNonBlank(collectionName, "VectorStoreConfig.collectionName");
        this.collectionName = collectionName;
    }

    public String getDistanceMetric() {
        return distanceMetric;
    }

    public void setDistanceMetric(String distanceMetric) {
        this.distanceMetric = RetrievalValidation.validateDistanceMetric(
                distanceMetric, "VectorStoreConfig.distanceMetric");
    }
}
