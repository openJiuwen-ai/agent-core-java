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
 * Message queue configuration.
 *
 * <p>Mirrors Python's {@code MessageQueueConfig} in
 * {@code openjiuwen/core/runner/runner_config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageQueueConfig {

    @Builder.Default
    private String type = MessageQueueType.PULSAR.getValue();

    @JsonProperty("pulsar_config")
    private PulsarConfig pulsarConfig;

    public MessageQueueConfig copy() {
        return MessageQueueConfig.builder()
                .type(type)
                .pulsarConfig(pulsarConfig == null ? null : pulsarConfig.copy())
                .build();
    }
}
