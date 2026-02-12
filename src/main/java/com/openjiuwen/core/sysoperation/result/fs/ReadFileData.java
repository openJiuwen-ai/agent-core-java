// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import com.openjiuwen.core.sysoperation.result.FileMode;

/**
 * Data structure for read file result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.ReadFileData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class ReadFileData {

    /**
     * File path of the read file.
     */
    private final String path;

    /**
     * File content (text string or binary bytes as base64 string).
     */
    private final String content;

    /**
     * File read mode: 'text' (string) or 'bytes' (binary).
     */
    private final FileMode mode;

    public ReadFileData(String path, String content, FileMode mode) {
        this.path = path;
        this.content = content;
        this.mode = mode;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public FileMode getMode() {
        return mode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String path;
        private String content;
        private FileMode mode = FileMode.TEXT;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder mode(FileMode mode) {
            this.mode = mode;
            return this;
        }

        public ReadFileData build() {
            return new ReadFileData(path, content, mode);
        }
    }

    @Override
    public String toString() {
        return "ReadFileData{" +
            "path='" + path + '\'' +
            ", contentLength=" + (content != null ? content.length() : 0) +
            ", mode=" + mode +
            '}';
    }
}
