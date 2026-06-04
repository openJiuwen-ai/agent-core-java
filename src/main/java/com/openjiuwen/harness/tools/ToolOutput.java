/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

/**
 * Simple tool output wrapper for harness tools.
 *
 * <p>Java-side supporting type for the migration of Python harness tools such
 * as those in {@code openjiuwen.harness.tools.base_tool} and related modules.
 */
public class ToolOutput {

    private final boolean success;
    private final Object data;
    private final String error;

    public ToolOutput(boolean success, Object data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean getSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getError() {
        return error;
    }
}
