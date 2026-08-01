/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

/**
 * Public class SessionsListTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class SessionsListTool {
    private final SessionToolkit toolkit;

    /**
     * SessionsListTool.
     * 
     * @param toolkit toolkit
     * @since 0.1.7
     */
    public SessionsListTool(SessionToolkit toolkit) {
        this.toolkit = toolkit;
    }

    /**
     * list.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput list() {
        return ToolOutput.builder().success(true).data(toolkit.listAll()).build();
    }
}
