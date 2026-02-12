// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

/**
 * Base model for file/directory common properties.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.FileSystemItem
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class FileSystemItem {

    /**
     * Name of the file/directory.
     */
    private final String name;

    /**
     * Full absolute path of the file/directory.
     */
    private final String path;

    /**
     * Size in bytes.
     */
    private final long size;

    /**
     * Last modification time (ISO format string).
     */
    private final String modifiedTime;

    /**
     * Whether the item is a directory.
     */
    private final boolean directory;

    /**
     * File extension (only for files).
     */
    private final String type;

    public FileSystemItem(String name, String path, long size, String modifiedTime,
                          boolean directory, String type) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.modifiedTime = modifiedTime;
        this.directory = directory;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getModifiedTime() {
        return modifiedTime;
    }

    public boolean isDirectory() {
        return directory;
    }

    public String getType() {
        return type;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String path;
        private long size;
        private String modifiedTime;
        private boolean directory;
        private String type;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder modifiedTime(String modifiedTime) {
            this.modifiedTime = modifiedTime;
            return this;
        }

        public Builder directory(boolean directory) {
            this.directory = directory;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public FileSystemItem build() {
            return new FileSystemItem(name, path, size, modifiedTime, directory, type);
        }
    }

    @Override
    public String toString() {
        return "FileSystemItem{" +
            "name='" + name + '\'' +
            ", path='" + path + '\'' +
            ", size=" + size +
            ", modifiedTime='" + modifiedTime + '\'' +
            ", directory=" + directory +
            ", type='" + type + '\'' +
            '}';
    }
}
