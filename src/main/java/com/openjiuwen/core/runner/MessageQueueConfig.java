// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

/**
 * 消息队列配置
 * 
 * 对应Python: runner_config.py - MessageQueueConfig
 * 
 * @param type 消息队列类型（字符串形式）
 * @param pulsarConfig Pulsar配置（可选）
 */
public record MessageQueueConfig(String type, PulsarConfig pulsarConfig) {
    
    /**
     * 默认构造函数
     * type=pulsar, pulsarConfig=null
     */
    public MessageQueueConfig() {
        this(MessageQueueType.PULSAR.getValue(), null);
    }
}

