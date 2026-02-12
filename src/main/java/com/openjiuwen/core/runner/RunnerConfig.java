// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import java.util.UUID;

/**
 * Runner全局配置
 * 
 * 对应Python: runner_config.py - RunnerConfig
 */
public class RunnerConfig {
    
    /**
     * 默认Runner配置
     * distributed_mode=false, message_queue_type=fake
     */
    public static final RunnerConfig DEFAULT_RUNNER_CONFIG;
    
    static {
        DistributedConfig distConfig = new DistributedConfig();
        distConfig.setRequestTimeout(30.0);
        MessageQueueConfig mqConfig = new MessageQueueConfig(MessageQueueType.FAKE.getValue(), null);
        distConfig.setMessageQueueConfig(mqConfig);
        
        DEFAULT_RUNNER_CONFIG = new RunnerConfig();
        DEFAULT_RUNNER_CONFIG.distributedMode = false;
        DEFAULT_RUNNER_CONFIG.distributedConfig = distConfig;
    }
    
    private static RunnerConfig globalConfig = null;
    
    private boolean distributedMode = true;
    private DistributedConfig distributedConfig = new DistributedConfig();
    private String envPrefix = "";
    private String instanceId = UUID.randomUUID().toString();

    public RunnerConfig() {
    }

    /**
     * 获取带环境前缀的Agent主题模板
     * 
     * @return 主题模板字符串
     */
    public String agentTopicTemplate() {
        return distributedConfig.getAgentTopicTemplate(envPrefix);
    }

    /**
     * 获取带环境前缀的回复主题模板
     * 
     * @return 主题模板字符串
     */
    public String replyTopicTemplate() {
        return distributedConfig.getReplyTopicTemplate(envPrefix);
    }

    /**
     * 设置全局配置
     * 
     * @param cfg 配置对象
     */
    public static void setRunnerConfig(RunnerConfig cfg) {
        globalConfig = cfg;
    }

    /**
     * 获取全局配置
     * 如果未设置，返回默认配置
     * 
     * @return 全局配置
     */
    public static RunnerConfig getRunnerConfig() {
        if (globalConfig == null) {
            globalConfig = DEFAULT_RUNNER_CONFIG;
        }
        return globalConfig;
    }

    // Getters and Setters

    public boolean isDistributedMode() {
        return distributedMode;
    }

    public void setDistributedMode(boolean distributedMode) {
        this.distributedMode = distributedMode;
    }

    public DistributedConfig getDistributedConfig() {
        return distributedConfig;
    }

    public void setDistributedConfig(DistributedConfig distributedConfig) {
        this.distributedConfig = distributedConfig;
    }

    public String getEnvPrefix() {
        return envPrefix;
    }

    public void setEnvPrefix(String envPrefix) {
        this.envPrefix = envPrefix;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}

