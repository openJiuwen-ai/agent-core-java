/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code VectorStoreConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VectorStoreConfig {

    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]*$");

    private static final Set<String> VALID_DISTANCE_METRICS = Set.of("cosine", "euclidean", "dot");

    @JsonProperty("store_provider")
    private StoreType storeProvider;

    @JsonProperty("database_name")
    private String databaseName = "";

    @JsonProperty("collection_name")
    private String collectionName;

    @JsonProperty("distance_metric")
    private String distanceMetric = "cosine";

    public static VectorStoreConfigBuilder builder() {
        return new VectorStoreConfigBuilder();
    }

    public VectorStoreConfig(Object storeProvider, String collectionName) {
        this(toStoreType(storeProvider), "", collectionName, "cosine");
    }

    public VectorStoreConfig(StoreType storeProvider, String collectionName) {
        this(storeProvider, "", collectionName, "cosine");
    }

    public VectorStoreConfig(Object storeProvider, String databaseName, String collectionName, String distanceMetric) {
        this(toStoreType(storeProvider), databaseName, collectionName, distanceMetric);
    }

    public VectorStoreConfig(StoreType storeProvider, String databaseName, String collectionName, String distanceMetric) {
        this.storeProvider = storeProvider;
        setDatabaseName(databaseName == null ? "" : databaseName);
        setCollectionName(collectionName);
        setDistanceMetric(distanceMetric);
    }

    public void validate() {
        if (storeProvider == null) {
            throw new IllegalArgumentException("store_provider is required");
        }
        setDatabaseName(databaseName);
        setCollectionName(collectionName);
        setDistanceMetric(distanceMetric);
    }

    public StoreType getStoreType() {
        return storeProvider;
    }

    public void setStoreProvider(String storeProvider) {
        this.storeProvider = StoreType.fromValue(storeProvider);
    }

    private static StoreType toStoreType(Object storeProvider) {
        if (storeProvider instanceof StoreType type) {
            return type;
        }
        return StoreType.fromValue(String.valueOf(storeProvider));
    }

    public void setDatabaseName(String databaseName) {
        String value = databaseName == null ? "" : databaseName;
        if (!DATABASE_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("database_name must match ^[A-Za-z0-9_]*$");
        }
        this.databaseName = value;
    }

    public void setCollectionName(String collectionName) {
        if (collectionName == null) {
            throw new IllegalArgumentException("collection_name is required");
        }
        this.collectionName = collectionName;
    }

    public void setDistanceMetric(String distanceMetric) {
        String value = distanceMetric == null ? "cosine" : distanceMetric;
        if (!VALID_DISTANCE_METRICS.contains(value)) {
            throw new IllegalArgumentException("distance_metric must be one of cosine, euclidean, dot");
        }
        this.distanceMetric = value;
    }

    public static final class VectorStoreConfigBuilder {
        private StoreType storeProvider;
        private String databaseName = "";
        private String collectionName;
        private String distanceMetric = "cosine";

        private VectorStoreConfigBuilder() {
        }

        public VectorStoreConfigBuilder storeProvider(StoreType storeProvider) {
            this.storeProvider = storeProvider;
            return this;
        }

        public VectorStoreConfigBuilder storeProvider(String storeProvider) {
            this.storeProvider = StoreType.fromValue(storeProvider);
            return this;
        }

        public VectorStoreConfigBuilder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public VectorStoreConfigBuilder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public VectorStoreConfigBuilder distanceMetric(String distanceMetric) {
            this.distanceMetric = distanceMetric;
            return this;
        }

        public VectorStoreConfig build() {
            String activeCollectionName = collectionName;
            if (activeCollectionName == null && storeProvider == StoreType.CHROMA) {
                activeCollectionName = "";
            }
            return new VectorStoreConfig(storeProvider, databaseName, activeCollectionName, distanceMetric);
        }
    }
}
