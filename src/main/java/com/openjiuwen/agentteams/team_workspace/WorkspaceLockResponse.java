/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class WorkspaceLockResponse used by the Java parity implementation.
 *
 * @since 1.0
 */
public class WorkspaceLockResponse {
    private String teamName;
    private String memberName;
    private String filePath;
    private boolean isGranted;
    private Map<String, Object> holder;

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMemberName() {
        return memberName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isGranted() {
        return isGranted;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setGranted(boolean isGranted) {
        this.isGranted = isGranted;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getHolder() {
        return holder;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setHolder(Map<String, Object> holder) {
        this.holder = holder != null ? new LinkedHashMap<>(holder) : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private final WorkspaceLockResponse response = new WorkspaceLockResponse();

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder teamName(String teamName) {
            response.setTeamName(teamName);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder memberName(String memberName) {
            response.setMemberName(memberName);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder filePath(String filePath) {
            response.setFilePath(filePath);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder isGranted(boolean isGranted) {
            response.setGranted(isGranted);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder holder(Map<String, Object> holder) {
            response.setHolder(holder);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public WorkspaceLockResponse build() {
            return response;
        }
    }
}
