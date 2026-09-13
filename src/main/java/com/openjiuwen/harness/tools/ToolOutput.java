/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class ToolOutput used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolOutput {
    private boolean isSuccess;
    private Object data;
    private String error;

    /**
     * ToolOutputBuilder.
     * 
     * @since 0.1.7
     */
    public static class ToolOutputBuilder {
        /**
         * success.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public ToolOutputBuilder success(boolean value) {
            this.isSuccess = value;
            return this;
        }
    }
}
