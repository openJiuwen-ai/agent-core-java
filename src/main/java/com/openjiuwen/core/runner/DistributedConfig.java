/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Distributed runner configuration.
 *
 * <p>Mirrors Python's {@code DistributedConfig} in
 * {@code openjiuwen/core/runner/runner_config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DistributedConfig {

    @Builder.Default
    @JsonProperty("request_timeout")
    private double requestTimeout = 30.0;

    @Builder.Default
    @JsonProperty("max_request_concurrency")
    private int maxRequestConcurrency = 10000;

    @Builder.Default
    @JsonProperty("message_queue_config")
    private MessageQueueConfig messageQueueConfig = new MessageQueueConfig();

    @Builder.Default
    @JsonProperty("agent_topic_template")
    private String agentTopicTemplate = "openjiuwen.single_agent.{agent_id}.{version}";

    @Builder.Default
    @JsonProperty("reply_topic_template")
    private String replyTopicTemplate = "openjiuwen.reply.runner.{instance_id}";

    public String getAgentTopicTemplate(String envPrefix) {
        if (envPrefix != null && !envPrefix.isEmpty()) {
            return envPrefix + "." + agentTopicTemplate;
        }
        return agentTopicTemplate;
    }

    public String getReplyTopicTemplate(String envPrefix) {
        if (envPrefix != null && !envPrefix.isEmpty()) {
            return envPrefix + "." + replyTopicTemplate;
        }
        return replyTopicTemplate;
    }

    public DistributedConfig copy() {
        return DistributedConfig.builder()
                .requestTimeout(requestTimeout)
                .maxRequestConcurrency(maxRequestConcurrency)
                .messageQueueConfig(messageQueueConfig == null ? null : messageQueueConfig.copy())
                .agentTopicTemplate(agentTopicTemplate)
                .replyTopicTemplate(replyTopicTemplate)
                .build();
    }
}
