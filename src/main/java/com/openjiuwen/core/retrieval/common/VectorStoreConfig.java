/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors Python's {@code VectorStoreConfig} in
 * {@code openjiuwen/core/retrieval/common/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VectorStoreConfig {

    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]*$");

    @JsonProperty("store_provider")
    private StoreType storeProvider;

    @JsonProperty("database_name")
    @Builder.Default
    private String databaseName = "";

    @JsonProperty("collection_name")
    private String collectionName;

    @JsonProperty("distance_metric")
    @Builder.Default
    private String distanceMetric = "cosine";

    public VectorStoreConfig(StoreType storeProvider, String databaseName, String collectionName, String distanceMetric) {
        this.storeProvider = storeProvider;
        setDatabaseName(databaseName == null ? "" : databaseName);
        this.collectionName = collectionName;
        this.distanceMetric = distanceMetric == null ? "cosine" : distanceMetric;
    }

    public void setDatabaseName(String databaseName) {
        String value = databaseName == null ? "" : databaseName;
        if (!DATABASE_NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("database_name must match ^[A-Za-z0-9_]*$");
        }
        this.databaseName = value;
    }
}
