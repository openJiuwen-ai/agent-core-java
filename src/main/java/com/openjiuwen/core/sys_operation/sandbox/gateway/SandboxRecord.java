/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code SandboxRecord} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/sandbox_store.py}.
 */
public final class SandboxRecord {

    private String sandboxId;
    private String baseUrl;
    private SandboxStatus status;
    private String launcherType;
    private String sandboxType;
    private String containerConfigHash;
    private double createdTs;
    private double lastUsedTs;
    private Map<String, Object> metadata;

    public SandboxRecord(
            String sandboxId,
            String baseUrl,
            SandboxStatus status,
            String launcherType,
            String sandboxType,
            String containerConfigHash) {
        this(
                sandboxId,
                baseUrl,
                status,
                launcherType,
                sandboxType,
                containerConfigHash,
                currentEpochSeconds(),
                currentEpochSeconds(),
                new LinkedHashMap<>());
    }

    public SandboxRecord(
            String sandboxId,
            String baseUrl,
            SandboxStatus status,
            String launcherType,
            String sandboxType,
            String containerConfigHash,
            double createdTs,
            double lastUsedTs,
            Map<String, Object> metadata) {
        this.sandboxId = sandboxId;
        this.baseUrl = baseUrl;
        this.status = status;
        this.launcherType = launcherType;
        this.sandboxType = sandboxType;
        this.containerConfigHash = containerConfigHash;
        this.createdTs = createdTs;
        this.lastUsedTs = lastUsedTs;
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public String getSandboxId() {
        return sandboxId;
    }

    public void setSandboxId(String sandboxId) {
        this.sandboxId = sandboxId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public SandboxStatus getStatus() {
        return status;
    }

    public void setStatus(SandboxStatus status) {
        this.status = status;
    }

    public String getLauncherType() {
        return launcherType;
    }

    public void setLauncherType(String launcherType) {
        this.launcherType = launcherType;
    }

    public String getSandboxType() {
        return sandboxType;
    }

    public void setSandboxType(String sandboxType) {
        this.sandboxType = sandboxType;
    }

    public String getContainerConfigHash() {
        return containerConfigHash;
    }

    public void setContainerConfigHash(String containerConfigHash) {
        this.containerConfigHash = containerConfigHash;
    }

    public double getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(double createdTs) {
        this.createdTs = createdTs;
    }

    public double getLastUsedTs() {
        return lastUsedTs;
    }

    public void setLastUsedTs(double lastUsedTs) {
        this.lastUsedTs = lastUsedTs;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    private static double currentEpochSeconds() {
        return System.currentTimeMillis() / 1000.0d;
    }
}
