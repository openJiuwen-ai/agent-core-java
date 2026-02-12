// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

/**
 * Data structure for chunked upload file result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.UploadFileChunkData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class UploadFileChunkData {

    private final String localPath;
    private final String targetPath;
    private final int chunkSize;
    private final int chunkIndex;
    private final boolean lastChunk;

    public UploadFileChunkData(String localPath, String targetPath, int chunkSize,
                               int chunkIndex, boolean lastChunk) {
        this.localPath = localPath;
        this.targetPath = targetPath;
        this.chunkSize = chunkSize;
        this.chunkIndex = chunkIndex;
        this.lastChunk = lastChunk;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getTargetPath() {
        return targetPath;
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
        private String localPath;
        private String targetPath;
        private int chunkSize;
        private int chunkIndex;
        private boolean lastChunk;

        public Builder localPath(String localPath) {
            this.localPath = localPath;
            return this;
        }

        public Builder targetPath(String targetPath) {
            this.targetPath = targetPath;
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

        public UploadFileChunkData build() {
            return new UploadFileChunkData(localPath, targetPath, chunkSize, chunkIndex, lastChunk);
        }
    }

    @Override
    public String toString() {
        return "UploadFileChunkData{" +
            "localPath='" + localPath + '\'' +
            ", targetPath='" + targetPath + '\'' +
            ", chunkIndex=" + chunkIndex +
            ", chunkSize=" + chunkSize +
            ", lastChunk=" + lastChunk +
            '}';
    }
}
