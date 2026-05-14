/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for code execution.
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCodeResult extends BaseResult<ExecuteCodeData> {

    public static ExecuteCodeResultBuilder builder() {
        return new ExecuteCodeResultBuilder();
    }

    public ExecuteCodeResult(int code, String message, ExecuteCodeData data) {
        super(code, message, data);
    }

    public static final class ExecuteCodeResultBuilder {
        private int code;
        private String message;
        private ExecuteCodeData data;

        public ExecuteCodeResultBuilder code(int code) { this.code = code; return this; }
        public ExecuteCodeResultBuilder message(String message) { this.message = message; return this; }
        public ExecuteCodeResultBuilder data(ExecuteCodeData data) { this.data = data; return this; }

        public ExecuteCodeResult build() {
            return new ExecuteCodeResult(code, message, data);
        }
    }
}
