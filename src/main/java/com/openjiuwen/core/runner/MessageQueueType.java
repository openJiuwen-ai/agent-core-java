// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

/**
 * 消息队列类型枚举
 * 
 * 对应Python: runner_config.py - MessageQueueType
 */
public enum MessageQueueType {
    PULSAR("pulsar"),
    FAKE("fake");

    private final String value;

    MessageQueueType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值获取枚举
     * 
     * @param value 字符串值
     * @return 对应的枚举，如果不存在返回null
     */
    public static MessageQueueType fromValue(String value) {
        for (MessageQueueType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}

