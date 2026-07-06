/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

/**
 * Public class WorkspaceLockRequest used by the Java parity implementation.
 *
 * @since 1.0
 */
public class WorkspaceLockRequest {
    private String teamName;
    private String memberName;
    private String action;
    private String filePath;
    private String holderName;
    private Integer timeoutSeconds;

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
    public String getAction() {
        return action;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setAction(String action) {
        this.action = action;
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
    public String getHolderName() {
        return holderName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
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
        private final WorkspaceLockRequest request = new WorkspaceLockRequest();

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder teamName(String teamName) {
            request.setTeamName(teamName);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder memberName(String memberName) {
            request.setMemberName(memberName);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder action(String action) {
            request.setAction(action);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder filePath(String filePath) {
            request.setFilePath(filePath);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder holderName(String holderName) {
            request.setHolderName(holderName);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder timeoutSeconds(Integer timeoutSeconds) {
            request.setTimeoutSeconds(timeoutSeconds);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public WorkspaceLockRequest build() {
            return request;
        }
    }
}
