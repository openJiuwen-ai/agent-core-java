/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

/**
 * Public class WorkspaceLockRequest used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class WorkspaceLockRequest {
    private String teamName;
    private String memberName;
    private String action;
    private String filePath;
    private String holderName;
    private Integer timeoutSeconds;

    /**
     * getTeamName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * setTeamName.
     * 
     * @param teamName teamName
     * @since 0.1.7
     */
    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    /**
     * getMemberName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMemberName() {
        return memberName;
    }

    /**
     * setMemberName.
     * 
     * @param memberName memberName
     * @since 0.1.7
     */
    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    /**
     * getAction.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getAction() {
        return action;
    }

    /**
     * setAction.
     * 
     * @param action action
     * @since 0.1.7
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * getFilePath.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * setFilePath.
     * 
     * @param filePath filePath
     * @since 0.1.7
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * getHolderName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getHolderName() {
        return holderName;
    }

    /**
     * setHolderName.
     * 
     * @param holderName holderName
     * @since 0.1.7
     */
    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    /**
     * getTimeoutSeconds.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * setTimeoutSeconds.
     * 
     * @param timeoutSeconds timeoutSeconds
     * @since 0.1.7
     */
    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private final WorkspaceLockRequest request = new WorkspaceLockRequest();

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
        }

        /**
         * teamName.
         * 
         * @param teamName teamName
         * @return the result
         * @since 0.1.7
         */
        public Builder teamName(String teamName) {
            request.setTeamName(teamName);
            return this;
        }

        /**
         * memberName.
         * 
         * @param memberName memberName
         * @return the result
         * @since 0.1.7
         */
        public Builder memberName(String memberName) {
            request.setMemberName(memberName);
            return this;
        }

        /**
         * action.
         * 
         * @param action action
         * @return the result
         * @since 0.1.7
         */
        public Builder action(String action) {
            request.setAction(action);
            return this;
        }

        /**
         * filePath.
         * 
         * @param filePath filePath
         * @return the result
         * @since 0.1.7
         */
        public Builder filePath(String filePath) {
            request.setFilePath(filePath);
            return this;
        }

        /**
         * holderName.
         * 
         * @param holderName holderName
         * @return the result
         * @since 0.1.7
         */
        public Builder holderName(String holderName) {
            request.setHolderName(holderName);
            return this;
        }

        /**
         * timeoutSeconds.
         * 
         * @param timeoutSeconds timeoutSeconds
         * @return the result
         * @since 0.1.7
         */
        public Builder timeoutSeconds(Integer timeoutSeconds) {
            request.setTimeoutSeconds(timeoutSeconds);
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public WorkspaceLockRequest build() {
            return request;
        }
    }
}
