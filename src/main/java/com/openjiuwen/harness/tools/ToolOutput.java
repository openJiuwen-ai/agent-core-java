/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class ToolOutput used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class ToolOutput {
    private boolean isSuccess;
    private Object data;
    private String error;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class ToolOutputBuilder {
        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolOutputBuilder success(boolean value) {
            this.isSuccess = value;
            return this;
        }
    }
}
