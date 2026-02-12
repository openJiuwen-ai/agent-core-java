// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import java.util.List;

/**
 * Data structure for list files and list directories result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.FileSystemData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class FileSystemData {

    /**
     * Total number of items (files/directories).
     */
    private final int totalCount;

    /**
     * List of file/directory details.
     */
    private final List<FileSystemItem> listItems;

    /**
     * Original input directory path.
     */
    private final String rootPath;

    /**
     * Actual recursive status used.
     */
    private final boolean recursive;

    /**
     * Actual maximum recursion depth used.
     */
    private final Integer maxDepth;

    public FileSystemData(int totalCount, List<FileSystemItem> listItems, String rootPath,
                          boolean recursive, Integer maxDepth) {
        this.totalCount = totalCount;
        this.listItems = listItems;
        this.rootPath = rootPath;
        this.recursive = recursive;
        this.maxDepth = maxDepth;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public List<FileSystemItem> getListItems() {
        return listItems;
    }

    public String getRootPath() {
        return rootPath;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int totalCount;
        private List<FileSystemItem> listItems;
        private String rootPath;
        private boolean recursive;
        private Integer maxDepth;

        public Builder totalCount(int totalCount) {
            this.totalCount = totalCount;
            return this;
        }

        public Builder listItems(List<FileSystemItem> listItems) {
            this.listItems = listItems;
            return this;
        }

        public Builder rootPath(String rootPath) {
            this.rootPath = rootPath;
            return this;
        }

        public Builder recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        public Builder maxDepth(Integer maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public FileSystemData build() {
            return new FileSystemData(totalCount, listItems, rootPath, recursive, maxDepth);
        }
    }

    @Override
    public String toString() {
        return "FileSystemData{" +
            "totalCount=" + totalCount +
            ", rootPath='" + rootPath + '\'' +
            ", recursive=" + recursive +
            ", maxDepth=" + maxDepth +
            '}';
    }
}
