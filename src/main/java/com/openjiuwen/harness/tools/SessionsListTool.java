/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

/**
 * Public class SessionsListTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SessionsListTool {
    private final SessionToolkit toolkit;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionsListTool(SessionToolkit toolkit) {
        this.toolkit = toolkit;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput list() {
        return ToolOutput.builder().success(true).data(toolkit.listAll()).build();
    }
}
