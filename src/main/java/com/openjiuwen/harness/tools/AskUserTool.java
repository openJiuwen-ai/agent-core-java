/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * Minimal ask_user tool payload wrapper.
 * 
 * @since 0.1.7
 */
public class AskUserTool {
    /**
     * invoke.
     * 
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput invoke(Map<String, Object> payload) {
        return ToolOutput.builder().success(true).data(payload != null ? payload : Map.of()).build();
    }
}
