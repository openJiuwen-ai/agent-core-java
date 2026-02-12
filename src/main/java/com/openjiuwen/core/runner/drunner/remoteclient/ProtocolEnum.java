// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.remoteclient;

/**
 * 远程客户端协议枚举
 * 
 * 对应Python: drunner/remote_client/remote_client_config.py - ProtocolEnum
 */
public enum ProtocolEnum {
    MQ("MQ");

    private final String value;

    ProtocolEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

