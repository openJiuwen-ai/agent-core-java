// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

/**
 * Pulsar消息队列配置
 * 
 * 对应Python: runner_config.py - PulsarConfig
 * 
 * @param url Pulsar服务地址
 * @param maxWorkers 最大工作线程数
 */
public record PulsarConfig(String url, int maxWorkers) {
    
    /**
     * 默认构造函数
     * url=null, maxWorkers=8
     */
    public PulsarConfig() {
        this(null, 8);
    }
}

