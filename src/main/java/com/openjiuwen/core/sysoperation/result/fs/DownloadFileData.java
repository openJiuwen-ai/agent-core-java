// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

/**
 * Data structure for download file result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.DownloadFileData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class DownloadFileData {

    private final String sourcePath;
    private final String localPath;
    private final long size;

    public DownloadFileData(String sourcePath, String localPath, long size) {
        this.sourcePath = sourcePath;
        this.localPath = localPath;
        this.size = size;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getLocalPath() {
        return localPath;
    }

    public long getSize() {
        return size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourcePath;
        private String localPath;
        private long size;

        public Builder sourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder localPath(String localPath) {
            this.localPath = localPath;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public DownloadFileData build() {
            return new DownloadFileData(sourcePath, localPath, size);
        }
    }

    @Override
    public String toString() {
        return "DownloadFileData{" +
            "sourcePath='" + sourcePath + '\'' +
            ", localPath='" + localPath + '\'' +
            ", size=" + size +
            '}';
    }
}
