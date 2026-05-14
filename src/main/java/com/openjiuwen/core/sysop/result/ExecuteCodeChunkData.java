/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Data structure for chunked code execution output.
 * <p>
 * Mirrors Python's {@code ExecuteCodeChunkData}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeChunkData {

    /** Raw content of the output chunk. */
    @Builder.Default
    private String text = "";

    /** Type of the output chunk: "stdout" or "stderr". */
    private String type;

    /** Index of current chunk (starting from 0). */
    private int chunkIndex;

    /** Execution exit code. */
    private Integer exitCode;

    /** Data for execution. */
    private Map<String, Object> metadata;

    public static ExecuteCodeChunkDataBuilder builder() {
        return new ExecuteCodeChunkDataBuilder();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public static final class ExecuteCodeChunkDataBuilder {
        private String text = "";
        private String type;
        private int chunkIndex;
        private Integer exitCode;
        private Map<String, Object> metadata;

        public ExecuteCodeChunkDataBuilder text(String text) { this.text = text; return this; }
        public ExecuteCodeChunkDataBuilder type(String type) { this.type = type; return this; }
        public ExecuteCodeChunkDataBuilder chunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; return this; }
        public ExecuteCodeChunkDataBuilder exitCode(Integer exitCode) { this.exitCode = exitCode; return this; }
        public ExecuteCodeChunkDataBuilder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public ExecuteCodeChunkData build() {
            ExecuteCodeChunkData data = new ExecuteCodeChunkData();
            data.setText(text);
            data.setType(type);
            data.setChunkIndex(chunkIndex);
            data.setExitCode(exitCode);
            data.setMetadata(metadata);
            return data;
        }
    }
}
