/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class WorkspaceLockResponse used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class WorkspaceLockResponse {
    private String teamName;
    private String memberName;
    private String filePath;
    private boolean isGranted;
    private Map<String, Object> holder;

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
     * isGranted.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isGranted() {
        return isGranted;
    }

    /**
     * setGranted.
     * 
     * @param isGranted isGranted
     * @since 0.1.7
     */
    public void setGranted(boolean isGranted) {
        this.isGranted = isGranted;
    }

    /**
     * getHolder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getHolder() {
        return holder;
    }

    /**
     * setHolder.
     * 
     * @param holder holder
     * @since 0.1.7
     */
    public void setHolder(Map<String, Object> holder) {
        this.holder = holder != null ? new LinkedHashMap<>(holder) : null;
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
        private final WorkspaceLockResponse response = new WorkspaceLockResponse();

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
            response.setTeamName(teamName);
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
            response.setMemberName(memberName);
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
            response.setFilePath(filePath);
            return this;
        }

        /**
         * isGranted.
         * 
         * @param isGranted isGranted
         * @return the result
         * @since 0.1.7
         */
        public Builder isGranted(boolean isGranted) {
            response.setGranted(isGranted);
            return this;
        }

        /**
         * holder.
         * 
         * @param holder holder
         * @return the result
         * @since 0.1.7
         */
        public Builder holder(Map<String, Object> holder) {
            response.setHolder(holder);
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public WorkspaceLockResponse build() {
            return response;
        }
    }
}
