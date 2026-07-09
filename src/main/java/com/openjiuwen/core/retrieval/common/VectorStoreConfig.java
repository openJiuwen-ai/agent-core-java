/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

/**
 * Vector store configuration.
 * 
 * @since 0.1.7
 */
public class VectorStoreConfig {
    private String storeProvider;
    private String databaseName = "";
    private String collectionName;
    private String distanceMetric = "cosine";

    /**
     * VectorStoreConfig.
     * 
     * @since 0.1.7
     */
    public VectorStoreConfig() {
    }

    /**
     * VectorStoreConfig.
     * 
     * @param storeProvider storeProvider
     * @param collectionName collectionName
     * @since 0.1.7
     */
    public VectorStoreConfig(String storeProvider, String collectionName) {
        this(storeProvider, "", collectionName, "cosine");
    }

    /**
     * VectorStoreConfig.
     * 
     * @param storeProvider storeProvider
     * @param collectionName collectionName
     * @since 0.1.7
     */
    public VectorStoreConfig(StoreType storeProvider, String collectionName) {
        this(storeProvider == null ? null : storeProvider.value(), "", collectionName, "cosine");
    }

    /**
     * VectorStoreConfig.
     * 
     * @param storeProvider storeProvider
     * @param databaseName databaseName
     * @param collectionName collectionName
     * @param distanceMetric distanceMetric
     * @since 0.1.7
     */
    public VectorStoreConfig(String storeProvider, String databaseName, String collectionName, String distanceMetric) {
        this.storeProvider = storeProvider;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        this.distanceMetric = distanceMetric;
        validate();
    }

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        storeProvider = RetrievalValidation.validateStoreType(storeProvider, "VectorStoreConfig.storeProvider");
        RetrievalValidation.validateDatabaseName(databaseName, "VectorStoreConfig.databaseName");
        RetrievalValidation.requireNonBlank(collectionName, "VectorStoreConfig.collectionName");
        distanceMetric = RetrievalValidation.validateDistanceMetric(distanceMetric, "VectorStoreConfig.distanceMetric");
    }

    /**
     * getStoreProvider.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getStoreProvider() {
        return storeProvider;
    }

    /**
     * getStoreType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public StoreType getStoreType() {
        return StoreType.fromValue(storeProvider);
    }

    /**
     * setStoreProvider.
     * 
     * @param storeProvider storeProvider
     * @since 0.1.7
     */
    public void setStoreProvider(String storeProvider) {
        this.storeProvider = storeProvider;
        validate();
    }

    /**
     * getDatabaseName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * setDatabaseName.
     * 
     * @param databaseName databaseName
     * @since 0.1.7
     */
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        validate();
    }

    /**
     * getCollectionName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * setCollectionName.
     * 
     * @param collectionName collectionName
     * @since 0.1.7
     */
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
        validate();
    }

    /**
     * getDistanceMetric.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDistanceMetric() {
        return distanceMetric;
    }

    /**
     * setDistanceMetric.
     * 
     * @param distanceMetric distanceMetric
     * @since 0.1.7
     */
    public void setDistanceMetric(String distanceMetric) {
        this.distanceMetric = distanceMetric;
        validate();
    }
}
