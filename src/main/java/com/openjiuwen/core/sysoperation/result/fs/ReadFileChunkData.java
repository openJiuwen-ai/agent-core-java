// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.FileMode;

/**
 * Data structure for chunked file read result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.ReadFileChunkData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ReadFileChunkData {

    private final String path;
    private final String chunkContent;
    private final FileMode mode;
    private final int chunkSize;
    private final int chunkIndex;
    private final boolean lastChunk;

    public ReadFileChunkData(String path, String chunkContent, FileMode mode,
                             int chunkSize, int chunkIndex, boolean lastChunk) {
        this.path = path;
        this.chunkContent = chunkContent;
        this.mode = mode;
        this.chunkSize = chunkSize;
        this.chunkIndex = chunkIndex;
        this.lastChunk = lastChunk;
    }

    public String getPath() {
        return path;
    }

    public String getChunkContent() {
        return chunkContent;
    }

    public FileMode getMode() {
        return mode;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public boolean isLastChunk() {
        return lastChunk;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String path;
        private String chunkContent;
        private FileMode mode = FileMode.TEXT;
        private int chunkSize;
        private int chunkIndex;
        private boolean lastChunk;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder chunkContent(String chunkContent) {
            this.chunkContent = chunkContent;
            return this;
        }

        public Builder mode(FileMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder chunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        public Builder chunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
            return this;
        }

        public Builder lastChunk(boolean lastChunk) {
            this.lastChunk = lastChunk;
            return this;
        }

        public ReadFileChunkData build() {
            return new ReadFileChunkData(path, chunkContent, mode, chunkSize, chunkIndex, lastChunk);
        }
    }

    @Override
    public String toString() {
        return "ReadFileChunkData{" +
            "path='" + path + '\'' +
            ", chunkIndex=" + chunkIndex +
            ", chunkSize=" + chunkSize +
            ", lastChunk=" + lastChunk +
            ", mode=" + mode +
            '}';
    }
}
