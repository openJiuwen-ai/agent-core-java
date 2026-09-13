/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message queue configuration.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageQueueConfig {
    @Builder.Default
    /**
     * MessageQueueType.PULSAR.getValue.
     * 
     * @since 0.1.7
     */
    private String type = MessageQueueType.PULSAR.getValue();

    private PulsarConfig pulsarConfig;
}
