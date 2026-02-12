// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.result.fs;

import java.util.List;

/**
 * Data structure for search files result.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.result.fs_operation_result.SearchFilesData
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class SearchFilesData {

    /**
     * Total number of files matching the search pattern.
     */
    private final int totalMatches;

    /**
     * List of matching files.
     */
    private final List<FileSystemItem> matchingFiles;

    /**
     * Original base path used for the search.
     */
    private final String searchPath;

    /**
     * Original search pattern used.
     */
    private final String searchPattern;

    /**
     * Original exclude patterns used.
     */
    private final List<String> excludePatterns;

    public SearchFilesData(int totalMatches, List<FileSystemItem> matchingFiles,
                           String searchPath, String searchPattern, List<String> excludePatterns) {
        this.totalMatches = totalMatches;
        this.matchingFiles = matchingFiles;
        this.searchPath = searchPath;
        this.searchPattern = searchPattern;
        this.excludePatterns = excludePatterns;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public List<FileSystemItem> getMatchingFiles() {
        return matchingFiles;
    }

    public String getSearchPath() {
        return searchPath;
    }

    public String getSearchPattern() {
        return searchPattern;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int totalMatches;
        private List<FileSystemItem> matchingFiles;
        private String searchPath;
        private String searchPattern;
        private List<String> excludePatterns;

        public Builder totalMatches(int totalMatches) {
            this.totalMatches = totalMatches;
            return this;
        }

        public Builder matchingFiles(List<FileSystemItem> matchingFiles) {
            this.matchingFiles = matchingFiles;
            return this;
        }

        public Builder searchPath(String searchPath) {
            this.searchPath = searchPath;
            return this;
        }

        public Builder searchPattern(String searchPattern) {
            this.searchPattern = searchPattern;
            return this;
        }

        public Builder excludePatterns(List<String> excludePatterns) {
            this.excludePatterns = excludePatterns;
            return this;
        }

        public SearchFilesData build() {
            return new SearchFilesData(totalMatches, matchingFiles, searchPath, searchPattern, excludePatterns);
        }
    }

    @Override
    public String toString() {
        return "SearchFilesData{" +
            "totalMatches=" + totalMatches +
            ", searchPath='" + searchPath + '\'' +
            ", searchPattern='" + searchPattern + '\'' +
            ", excludePatterns=" + excludePatterns +
            '}';
    }
}
