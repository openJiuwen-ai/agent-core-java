/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Gateway service configuration.
 * <p>
 * Mirrors Python's {@code GatewayServiceConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.online_config}.
 */
public class GatewayServiceConfig {

    private String host = "127.0.0.1";
    private Integer port;
    private String redisUrl;
    private String recordDir = "records";
    private String logLevel = "info";
    private double healthTimeout = 30.0;
    private boolean disableTrajectoryCollection = true;
    private Map<String, String> env = new HashMap<>();

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { 
        if (port != null && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.port = port; 
    }
    public String getRedisUrl() { return redisUrl; }
    public void setRedisUrl(String redisUrl) { this.redisUrl = redisUrl; }
    public String getRecordDir() { return recordDir; }
    public void setRecordDir(String recordDir) { this.recordDir = recordDir; }
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    public double getHealthTimeout() { return healthTimeout; }
    public void setHealthTimeout(double healthTimeout) { 
        if (healthTimeout <= 0) throw new IllegalArgumentException("healthTimeout must be > 0");
        this.healthTimeout = healthTimeout; 
    }
    public boolean isDisableTrajectoryCollection() { return disableTrajectoryCollection; }
    public void setDisableTrajectoryCollection(boolean disableTrajectoryCollection) { 
        this.disableTrajectoryCollection = disableTrajectoryCollection; 
    }
    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env != null ? new HashMap<>(env) : new HashMap<>(); }
}