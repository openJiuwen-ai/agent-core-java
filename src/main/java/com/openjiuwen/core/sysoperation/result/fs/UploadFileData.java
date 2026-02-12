// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

/**
 * Data structure for upload file result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.UploadFileData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class UploadFileData {

    private final String localPath;
    private final String targetPath;
    private final long size;

    public UploadFileData(String localPath, String targetPath, long size) {
        this.localPath = localPath;
        this.targetPath = targetPath;
        this.size = size;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public long getSize() {
        return size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String localPath;
        private String targetPath;
        private long size;

        public Builder localPath(String localPath) {
            this.localPath = localPath;
            return this;
        }

        public Builder targetPath(String targetPath) {
            this.targetPath = targetPath;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public UploadFileData build() {
            return new UploadFileData(localPath, targetPath, size);
        }
    }

    @Override
    public String toString() {
        return "UploadFileData{" +
            "localPath='" + localPath + '\'' +
            ", targetPath='" + targetPath + '\'' +
            ", size=" + size +
            '}';
    }
}
