/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code BaseClient} in
 * {@code openjiuwen/core/common/clients/client_registry.py}.
 */
public class BaseClient {

    private Map<String, Object> config;

    public BaseClient() {
        this(Map.of());
    }

    public BaseClient(Map<String, Object> kwargs) {
        initialize(kwargs);
    }

    public static String getClientName() {
        return null;
    }

    public static String getClientType() {
        return "common";
    }

    public void initialize(Map<String, Object> kwargs) {
        this.config = kwargs != null ? new LinkedHashMap<>(kwargs) : new LinkedHashMap<>();
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public CompletableFuture<Boolean> close() {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    public BaseClient enter() {
        return this;
    }

    public CompletableFuture<Void> exit(Throwable excType, Throwable excVal, Object excTb) {
        return close().thenApply(ignored -> null);
    }

    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", getClientName());
        metadata.put("client_type", getClientType());
        return metadata;
    }
}
