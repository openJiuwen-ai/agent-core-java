/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import com.openjiuwen.core.memory.manage.memmodel.MemoryType;

/**
 * Parameters for user profile search and storage operations.
 * <p>
 * Corresponds to Python: manage/index/user_profile_manager.py UserProfileSearchParams
 */
public class UserProfileSearchParams {

    private final boolean isImplicit;
    private final String memType;
    private final String userId;
    private final String scopeId;
    private final String profileType;
    private final String profileMem;
    private final String sourceId;
    private final String reasoning;
    private final String contextSummary;

    private UserProfileSearchParams(Builder builder) {
        this.isImplicit = builder.isImplicit;
        this.memType = builder.memType;
        this.userId = builder.userId;
        this.scopeId = builder.scopeId;
        this.profileType = builder.profileType;
        this.profileMem = builder.profileMem;
        this.sourceId = builder.sourceId;
        this.reasoning = builder.reasoning;
        this.contextSummary = builder.contextSummary;
    }

    public boolean isImplicit() {
        return isImplicit;
    }

    public String getMemType() {
        return memType;
    }

    public String getUserId() {
        return userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getProfileType() {
        return profileType;
    }

    public String getProfileMem() {
        return profileMem;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getContextSummary() {
        return contextSummary;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean isImplicit = false;
        private String memType = MemoryType.USER_PROFILE.getValue();
        private String userId;
        private String scopeId;
        private String profileType;
        private String profileMem;
        private String sourceId;
        private String reasoning;
        private String contextSummary = "";

        public Builder isImplicit(boolean isImplicit) {
            this.isImplicit = isImplicit;
            return this;
        }

        public Builder memType(String memType) {
            this.memType = memType;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        public Builder profileType(String profileType) {
            this.profileType = profileType;
            return this;
        }

        public Builder profileMem(String profileMem) {
            this.profileMem = profileMem;
            return this;
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder contextSummary(String contextSummary) {
            this.contextSummary = contextSummary;
            return this;
        }

        public UserProfileSearchParams build() {
            return new UserProfileSearchParams(this);
        }
    }
}

