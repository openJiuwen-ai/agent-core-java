/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway;

/**
 * Gateway runtime configuration.
 * <p>
 * Mirrors Python's {@code GatewayConfig} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/config.py}.
 */
public class GatewayConfig {

    private int port;
    private String host = "127.0.0.1";
    private String llmUrl = "http://127.0.0.1:18000";
    private String judgeUrl = "http://127.0.0.1:18001";
    private String modelId = "";
    private String judgeModel = "";
    private double requestTimeout = 120.0;
    private String llmApiKey = "";
    private String judgeApiKey = "";
    private String gatewayApiKey = "";
    private String recordDir = "records";
    private String logLevel = "INFO";
    private boolean dumpTokenIds;
    private String loraRepoRoot = "";
    private String redisUrl = "";
    private int upstreamMaxRetries = 2;
    private double upstreamRetryBackoffSec = 0.2;
    private double upstreamRetryMaxBackoffSec = 2.0;
    private boolean disableGatewayTrajectoryCollection;
    private boolean singleUserDefault = true;

    public GatewayConfig() {
    }

    public GatewayConfig(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getLlmUrl() {
        return llmUrl;
    }

    public void setLlmUrl(String llmUrl) {
        this.llmUrl = llmUrl;
    }

    public String getJudgeUrl() {
        return judgeUrl;
    }

    public void setJudgeUrl(String judgeUrl) {
        this.judgeUrl = judgeUrl;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getJudgeModel() {
        return judgeModel;
    }

    public void setJudgeModel(String judgeModel) {
        this.judgeModel = judgeModel;
    }

    public double getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(double requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey;
    }

    public String getJudgeApiKey() {
        return judgeApiKey;
    }

    public void setJudgeApiKey(String judgeApiKey) {
        this.judgeApiKey = judgeApiKey;
    }

    public String getGatewayApiKey() {
        return gatewayApiKey;
    }

    public void setGatewayApiKey(String gatewayApiKey) {
        this.gatewayApiKey = gatewayApiKey;
    }

    public String getRecordDir() {
        return recordDir;
    }

    public void setRecordDir(String recordDir) {
        this.recordDir = recordDir;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public boolean isDumpTokenIds() {
        return dumpTokenIds;
    }

    public void setDumpTokenIds(boolean dumpTokenIds) {
        this.dumpTokenIds = dumpTokenIds;
    }

    public String getLoraRepoRoot() {
        return loraRepoRoot;
    }

    public void setLoraRepoRoot(String loraRepoRoot) {
        this.loraRepoRoot = loraRepoRoot;
    }

    public String getRedisUrl() {
        return redisUrl;
    }

    public void setRedisUrl(String redisUrl) {
        this.redisUrl = redisUrl;
    }

    public int getUpstreamMaxRetries() {
        return upstreamMaxRetries;
    }

    public void setUpstreamMaxRetries(int upstreamMaxRetries) {
        this.upstreamMaxRetries = upstreamMaxRetries;
    }

    public double getUpstreamRetryBackoffSec() {
        return upstreamRetryBackoffSec;
    }

    public void setUpstreamRetryBackoffSec(double upstreamRetryBackoffSec) {
        this.upstreamRetryBackoffSec = upstreamRetryBackoffSec;
    }

    public double getUpstreamRetryMaxBackoffSec() {
        return upstreamRetryMaxBackoffSec;
    }

    public void setUpstreamRetryMaxBackoffSec(double upstreamRetryMaxBackoffSec) {
        this.upstreamRetryMaxBackoffSec = upstreamRetryMaxBackoffSec;
    }

    public boolean isDisableGatewayTrajectoryCollection() {
        return disableGatewayTrajectoryCollection;
    }

    public void setDisableGatewayTrajectoryCollection(boolean disableGatewayTrajectoryCollection) {
        this.disableGatewayTrajectoryCollection = disableGatewayTrajectoryCollection;
    }

    public boolean isSingleUserDefault() {
        return singleUserDefault;
    }

    public void setSingleUserDefault(boolean singleUserDefault) {
        this.singleUserDefault = singleUserDefault;
    }
}
