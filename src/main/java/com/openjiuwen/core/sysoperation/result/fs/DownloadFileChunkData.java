// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

/**
 * Data structure for chunked file download result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.DownloadFileChunkData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class DownloadFileChunkData {

    private final String sourcePath;
    private final String localPath;
    private final int chunkSize;
    private final int chunkIndex;
    private final boolean lastChunk;

    public DownloadFileChunkData(String sourcePath, String localPath, int chunkSize,
                                 int chunkIndex, boolean lastChunk) {
        this.sourcePath = sourcePath;
        this.localPath = localPath;
        this.chunkSize = chunkSize;
        this.chunkIndex = chunkIndex;
        this.lastChunk = lastChunk;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getLocalPath() {
        return localPath;
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
        private String sourcePath;
        private String localPath;
        private int chunkSize;
        private int chunkIndex;
        private boolean lastChunk;

        public Builder sourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder localPath(String localPath) {
            this.localPath = localPath;
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

        public DownloadFileChunkData build() {
            return new DownloadFileChunkData(sourcePath, localPath, chunkSize, chunkIndex, lastChunk);
        }
    }

    @Override
    public String toString() {
        return "DownloadFileChunkData{" +
            "sourcePath='" + sourcePath + '\'' +
            ", localPath='" + localPath + '\'' +
            ", chunkIndex=" + chunkIndex +
            ", chunkSize=" + chunkSize +
            ", lastChunk=" + lastChunk +
            '}';
    }
}
