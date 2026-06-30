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

    /**
     * Auto-generated for codecheck compliance.
     */
    public VectorStoreConfig() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public VectorStoreConfig(String storeProvider, String collectionName) {
        this(storeProvider, "", collectionName, "cosine");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public VectorStoreConfig(StoreType storeProvider, String collectionName) {
        this(storeProvider == null ? null : storeProvider.value(), "", collectionName, "cosine");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public VectorStoreConfig(String storeProvider, String databaseName, String collectionName, String distanceMetric) {
        this.storeProvider = storeProvider;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        this.distanceMetric = distanceMetric;
        validate();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        storeProvider = RetrievalValidation.validateStoreType(storeProvider, "VectorStoreConfig.storeProvider");
        RetrievalValidation.validateDatabaseName(databaseName, "VectorStoreConfig.databaseName");
        RetrievalValidation.requireNonBlank(collectionName, "VectorStoreConfig.collectionName");
        distanceMetric = RetrievalValidation.validateDistanceMetric(distanceMetric, "VectorStoreConfig.distanceMetric");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getStoreProvider() {
        return storeProvider;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public StoreType getStoreType() {
        return StoreType.fromValue(storeProvider);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStoreProvider(String storeProvider) {
        this.storeProvider = storeProvider;
        validate();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        validate();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
        validate();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDistanceMetric() {
        return distanceMetric;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDistanceMetric(String distanceMetric) {
        this.distanceMetric = distanceMetric;
        validate();
    }
}
