/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import java.util.Objects;

/**
 * User profile memory unit.
 * Corresponds to Python: manage/mem_model/memory_unit.py - UserProfileUnit
 */
public class UserProfileUnit extends BaseMemoryUnit {

    private final String profileType;
    private final String profileMem;
    private final Double score;
    private final String messageMemId;
    private final String memId;
    private final boolean isImplicit;
    private final String reasoning;
    private final String contextSummary;

    private UserProfileUnit(Builder builder) {
        super(MemoryType.USER_PROFILE, builder.userId, builder.scopeId);
        this.profileType = builder.profileType;
        this.profileMem = builder.profileMem;
        this.score = builder.score;
        this.messageMemId = builder.messageMemId;
        this.memId = builder.memId != null ? builder.memId : "";
        this.isImplicit = builder.isImplicit;
        this.reasoning = builder.reasoning != null ? builder.reasoning : "";
        this.contextSummary = builder.contextSummary != null ? builder.contextSummary : "";
    }

    public String getProfileType() {
        return profileType;
    }

    public String getProfileMem() {
        return profileMem;
    }

    public Double getScore() {
        return score;
    }

    public String getMessageMemId() {
        return messageMemId;
    }

    public String getMemId() {
        return memId;
    }

    public boolean isImplicit() {
        return isImplicit;
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
        private String userId;
        private String scopeId;
        private String profileType;
        private String profileMem;
        private Double score;
        private String messageMemId;
        private String memId = "";
        private boolean isImplicit = false;
        private String reasoning = "";
        private String contextSummary = "";

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

        public Builder score(Double score) {
            this.score = score;
            return this;
        }

        public Builder messageMemId(String messageMemId) {
            this.messageMemId = messageMemId;
            return this;
        }

        public Builder memId(String memId) {
            this.memId = memId;
            return this;
        }

        public Builder isImplicit(boolean isImplicit) {
            this.isImplicit = isImplicit;
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

        public UserProfileUnit build() {
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(scopeId, "scopeId is required");
            Objects.requireNonNull(profileType, "profileType is required");
            Objects.requireNonNull(profileMem, "profileMem is required");
            return new UserProfileUnit(this);
        }
    }

    @Override
    public String toString() {
        return "UserProfileUnit{" +
               "profileType='" + profileType + '\'' +
               ", profileMem='" + profileMem + '\'' +
               ", score=" + score +
               ", memId='" + memId + '\'' +
               ", isImplicit=" + isImplicit +
               ", userId='" + getUserId() + '\'' +
               ", scopeId='" + getScopeId() + '\'' +
               '}';
    }
}

