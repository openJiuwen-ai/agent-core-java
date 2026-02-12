// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

/**
 * 分布式配置
 * 
 * 对应Python: runner_config.py - DistributedConfig
 */
public class DistributedConfig {
    
    private double requestTimeout = 30.0;
    private int maxRequestConcurrency = 10000;
    private MessageQueueConfig messageQueueConfig = new MessageQueueConfig();
    private String agentTopicTemplate = "openjiuwen.single_agent.{agent_id}.{version}";
    private String replyTopicTemplate = "openjiuwen.reply.runner.{instance_id}";

    public DistributedConfig() {
    }

    /**
     * 获取带环境前缀的Agent主题模板
     * 
     * @param envPrefix 环境前缀
     * @return 主题模板字符串
     */
    public String getAgentTopicTemplate(String envPrefix) {
        if (envPrefix != null && !envPrefix.isEmpty()) {
            return envPrefix + "." + agentTopicTemplate;
        }
        return agentTopicTemplate;
    }

    /**
     * 获取带环境前缀的回复主题模板
     * 
     * @param envPrefix 环境前缀
     * @return 主题模板字符串
     */
    public String getReplyTopicTemplate(String envPrefix) {
        if (envPrefix != null && !envPrefix.isEmpty()) {
            return envPrefix + "." + replyTopicTemplate;
        }
        return replyTopicTemplate;
    }

    // Getters and Setters

    public double getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(double requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxRequestConcurrency() {
        return maxRequestConcurrency;
    }

    public void setMaxRequestConcurrency(int maxRequestConcurrency) {
        this.maxRequestConcurrency = maxRequestConcurrency;
    }

    public MessageQueueConfig getMessageQueueConfig() {
        return messageQueueConfig;
    }

    public void setMessageQueueConfig(MessageQueueConfig messageQueueConfig) {
        this.messageQueueConfig = messageQueueConfig;
    }

    public String getAgentTopicTemplate() {
        return agentTopicTemplate;
    }

    public void setAgentTopicTemplate(String agentTopicTemplate) {
        this.agentTopicTemplate = agentTopicTemplate;
    }

    public String getReplyTopicTemplate() {
        return replyTopicTemplate;
    }

    public void setReplyTopicTemplate(String replyTopicTemplate) {
        this.replyTopicTemplate = replyTopicTemplate;
    }
}

