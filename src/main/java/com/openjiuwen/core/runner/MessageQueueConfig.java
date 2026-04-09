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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageQueueConfig {

    @Builder.Default
    private String type = MessageQueueType.PULSAR.getValue();

    private PulsarConfig pulsarConfig;
}
