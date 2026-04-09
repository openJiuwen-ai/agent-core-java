/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
}
