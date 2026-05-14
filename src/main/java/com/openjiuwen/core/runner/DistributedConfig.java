/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Distributed system configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributedConfig {

    @Builder.Default
    private double requestTimeout = 30.0;

    @Builder.Default
    private int maxRequestConcurrency = 10000;

    @Builder.Default
    private MessageQueueConfig messageQueueConfig = new MessageQueueConfig();

    @Builder.Default
    private String agentTopicTemplate = "openjiuwen.single_agent.{agent_id}.{version}";

    @Builder.Default
    private String replyTopicTemplate = "openjiuwen.reply.runner.{instance_id}";

    /**
     * Get agent topic template with environment prefix.
     *
     * @param envPrefix Optional environment prefix
     * @return Topic template with prefix
     */
    public String getAgentTopicTemplate(String envPrefix) {
        if (envPrefix != null && !envPrefix.isEmpty()) {
            return envPrefix + "." + agentTopicTemplate;
        }
        return agentTopicTemplate;
    }

    /**
     * Get reply topic template with environment prefix.
     *
     * @param envPrefix Optional environment prefix
     * @return Topic template with prefix
     */
    public String getReplyTopicTemplate(String envPrefix) {
        if (envPrefix != null && !envPrefix.isEmpty()) {
            return envPrefix + "." + replyTopicTemplate;
        }
        return replyTopicTemplate;
    }

    public static DistributedConfigBuilder builder() {
        return new DistributedConfigBuilder();
    }

    public double getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(double requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public MessageQueueConfig getMessageQueueConfig() {
        return messageQueueConfig;
    }

    public void setMessageQueueConfig(MessageQueueConfig messageQueueConfig) {
        this.messageQueueConfig = messageQueueConfig;
    }

    public int getMaxRequestConcurrency() {
        return maxRequestConcurrency;
    }

    public void setMaxRequestConcurrency(int maxRequestConcurrency) {
        this.maxRequestConcurrency = maxRequestConcurrency;
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

    public static final class DistributedConfigBuilder {
        private double requestTimeout = 30.0;
        private int maxRequestConcurrency = 10000;
        private MessageQueueConfig messageQueueConfig = new MessageQueueConfig();
        private String agentTopicTemplate = "openjiuwen.single_agent.{agent_id}.{version}";
        private String replyTopicTemplate = "openjiuwen.reply.runner.{instance_id}";

        public DistributedConfigBuilder requestTimeout(double requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public DistributedConfigBuilder maxRequestConcurrency(int maxRequestConcurrency) {
            this.maxRequestConcurrency = maxRequestConcurrency;
            return this;
        }

        public DistributedConfigBuilder messageQueueConfig(MessageQueueConfig messageQueueConfig) {
            this.messageQueueConfig = messageQueueConfig;
            return this;
        }

        public DistributedConfigBuilder agentTopicTemplate(String agentTopicTemplate) {
            this.agentTopicTemplate = agentTopicTemplate;
            return this;
        }

        public DistributedConfigBuilder replyTopicTemplate(String replyTopicTemplate) {
            this.replyTopicTemplate = replyTopicTemplate;
            return this;
        }

        public DistributedConfig build() {
            DistributedConfig config = new DistributedConfig();
            config.setRequestTimeout(requestTimeout);
            config.maxRequestConcurrency = maxRequestConcurrency;
            config.setMessageQueueConfig(messageQueueConfig);
            config.agentTopicTemplate = agentTopicTemplate;
            config.replyTopicTemplate = replyTopicTemplate;
            return config;
        }
    }
}
