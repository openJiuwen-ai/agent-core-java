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
 * 
 * @since 0.1.7
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

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, String> env = new LinkedHashMap<>();

    /**
     * validate.
     * 
     * @since 0.1.7
     */
    public void validate() {
        VLLMServiceConfig.validateOptionalPort(port, "gateway.port");
        VLLMServiceConfig.validateGreaterThanZero(healthTimeout, "gateway.health_timeout");
    }

    /**
     * getRedis_url.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getRedis_url() {
        return getRedisUrl();
    }

    /**
     * setRedis_url.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setRedis_url(String value) {
        setRedisUrl(value);
    }

    /**
     * getRecord_dir.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getRecord_dir() {
        return getRecordDir();
    }

    /**
     * setRecord_dir.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setRecord_dir(String value) {
        setRecordDir(value);
    }

    /**
     * getLog_level.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLog_level() {
        return getLogLevel();
    }

    /**
     * setLog_level.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setLog_level(String value) {
        setLogLevel(value);
    }

    /**
     * getHealth_timeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getHealth_timeout() {
        return getHealthTimeout();
    }

    /**
     * setHealth_timeout.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setHealth_timeout(double value) {
        setHealthTimeout(value);
    }

    /**
     * isDisable_trajectory_collection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isDisable_trajectory_collection() {
        return isDisableTrajectoryCollection();
    }

    /**
     * setDisable_trajectory_collection.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setDisable_trajectory_collection(boolean value) {
        setDisableTrajectoryCollection(value);
    }
}
