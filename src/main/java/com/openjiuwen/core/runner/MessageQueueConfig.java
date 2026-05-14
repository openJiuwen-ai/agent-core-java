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

    public static MessageQueueConfigBuilder builder() {
        return new MessageQueueConfigBuilder();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public PulsarConfig getPulsarConfig() {
        return pulsarConfig;
    }

    public void setPulsarConfig(PulsarConfig pulsarConfig) {
        this.pulsarConfig = pulsarConfig;
    }

    public static final class MessageQueueConfigBuilder {
        private String type = MessageQueueType.PULSAR.getValue();
        private PulsarConfig pulsarConfig;

        public MessageQueueConfigBuilder type(String type) {
            this.type = type;
            return this;
        }

        public MessageQueueConfigBuilder pulsarConfig(PulsarConfig pulsarConfig) {
            this.pulsarConfig = pulsarConfig;
            return this;
        }

        public MessageQueueConfig build() {
            MessageQueueConfig config = new MessageQueueConfig();
            config.setType(type);
            config.setPulsarConfig(pulsarConfig);
            return config;
        }
    }
}
