/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.onlineconfig.GatewayServiceConfig.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayServiceConfig {
    private String host = "127.0.0.1";
    private Integer port;
    private String redisUrl;
    private String recordDir = "records";
    private String logLevel = "info";
    private double healthTimeout = 30.0;
    private boolean isDisableTrajectoryCollection = true;
    private Map<String, String> env = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public void validate() {
        VLLMServiceConfig.validateOptionalPort(port, "gateway.port");
        VLLMServiceConfig.validateGreaterThanZero(healthTimeout, "gateway.health_timeout");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRedis_url() { return getRedisUrl(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRedis_url(String value) { setRedisUrl(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRecord_dir() { return getRecordDir(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRecord_dir(String value) { setRecordDir(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getLog_level() { return getLogLevel(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLog_level(String value) { setLogLevel(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public double getHealth_timeout() { return getHealthTimeout(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setHealth_timeout(double value) { setHealthTimeout(value); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isDisable_trajectory_collection() { return isDisableTrajectoryCollection(); }
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setDisable_trajectory_collection(boolean value) { setDisableTrajectoryCollection(value); }
}
