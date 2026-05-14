/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Result type for streaming code execution.
 */
@SuperBuilder
@NoArgsConstructor
public class ExecuteCodeStreamResult extends BaseResult<ExecuteCodeChunkData> {

    public static ExecuteCodeStreamResultBuilder builder() {
        return new ExecuteCodeStreamResultBuilder();
    }

    public ExecuteCodeStreamResult(int code, String message, ExecuteCodeChunkData data) {
        super(code, message, data);
    }

    public static final class ExecuteCodeStreamResultBuilder {
        private int code;
        private String message;
        private ExecuteCodeChunkData data;

        public ExecuteCodeStreamResultBuilder code(int code) { this.code = code; return this; }
        public ExecuteCodeStreamResultBuilder message(String message) { this.message = message; return this; }
        public ExecuteCodeStreamResultBuilder data(ExecuteCodeChunkData data) { this.data = data; return this; }

        public ExecuteCodeStreamResult build() {
            return new ExecuteCodeStreamResult(code, message, data);
        }
    }
}
