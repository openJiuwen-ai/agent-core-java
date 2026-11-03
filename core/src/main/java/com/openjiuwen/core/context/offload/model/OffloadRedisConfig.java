/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Redis connection settings for {@code RedisOffloadStore}
 * (架构文档 §3.4, independent pool — the global Redis pool is not shared).
 *
 * @since 2026-01-01
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OffloadRedisConfig {
    @JsonProperty("host")
    private String host = "localhost";

    @JsonProperty("port")
    private int port = 6379;

    @JsonProperty("password")
    private String password;

    @JsonProperty("database")
    private int database;

    @JsonProperty("connect_timeout_ms")
    private int connectTimeoutMs = 2000;

    @JsonProperty("so_timeout_ms")
    private int soTimeoutMs = 2000;

    @JsonProperty("max_total")
    private int maxTotal = 8;

    @JsonProperty("max_idle")
    private int maxIdle = 8;

    public OffloadRedisConfig() {
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host == null || host.isBlank() ? "localhost" : host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getSoTimeoutMs() {
        return soTimeoutMs;
    }

    public void setSoTimeoutMs(int soTimeoutMs) {
        this.soTimeoutMs = soTimeoutMs;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
    }
}
