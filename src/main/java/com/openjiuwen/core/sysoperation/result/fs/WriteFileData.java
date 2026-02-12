// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.FileMode;

/**
 * Data structure for write file result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.WriteFileData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class WriteFileData {

    private final String path;
    private final long size;
    private final FileMode mode;

    public WriteFileData(String path, long size, FileMode mode) {
        this.path = path;
        this.size = size;
        this.mode = mode;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public FileMode getMode() {
        return mode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String path;
        private long size;
        private FileMode mode = FileMode.TEXT;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder mode(FileMode mode) {
            this.mode = mode;
            return this;
        }

        public WriteFileData build() {
            return new WriteFileData(path, size, mode);
        }
    }

    @Override
    public String toString() {
        return "WriteFileData{" +
            "path='" + path + '\'' +
            ", size=" + size +
            ", mode=" + mode +
            '}';
    }
}
