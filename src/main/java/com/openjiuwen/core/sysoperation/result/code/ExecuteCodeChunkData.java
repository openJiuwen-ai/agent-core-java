// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.code;

import com.openjiuwen.core.sysoperation.result.OutputType;

import java.util.Map;

/**
 * Data structure for chunked execute code result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.code_operation_result.ExecuteCodeChunkData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ExecuteCodeChunkData {

    /**
     * Raw content of the output chunk.
     */
    private final String text;

    /**
     * Type of the output chunk (stdout or stderr).
     */
    private final OutputType type;

    /**
     * Index of current chunk (starting from 0).
     */
    private final int chunkIndex;

    /**
     * Execution exit code.
     */
    private final int exitCode;

    /**
     * Additional metadata for execution.
     */
    private final Map<String, Object> metadata;

    public ExecuteCodeChunkData(String text, OutputType type, int chunkIndex,
                                int exitCode, Map<String, Object> metadata) {
        this.text = text != null ? text : "";
        this.type = type;
        this.chunkIndex = chunkIndex;
        this.exitCode = exitCode;
        this.metadata = metadata;
    }

    public String getText() {
        return text;
    }

    public OutputType getType() {
        return type;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getExitCode() {
        return exitCode;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text = "";
        private OutputType type;
        private int chunkIndex;
        private int exitCode = 0;
        private Map<String, Object> metadata;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder type(OutputType type) {
            this.type = type;
            return this;
        }

        public Builder chunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
            return this;
        }

        public Builder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ExecuteCodeChunkData build() {
            return new ExecuteCodeChunkData(text, type, chunkIndex, exitCode, metadata);
        }
    }

    @Override
    public String toString() {
        return "ExecuteCodeChunkData{" +
            "type=" + type +
            ", chunkIndex=" + chunkIndex +
            ", exitCode=" + exitCode +
            ", textLength=" + text.length() +
            '}';
    }
}
