/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow;

/**
 * Type of workflow edge connection.
 *
 * <p>Mirrors Python's {@code ConnectionType} helper enum.</p>
 */
public enum ConnectionType {
    CONNECTION("connection"),
    STREAM_CONNECTION("stream_connection");

    private final String value;

    ConnectionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
