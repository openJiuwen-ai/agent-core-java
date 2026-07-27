/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code RebuildRequest} in
 * {@code openjiuwen/agent_evolving/experience/lifecycle.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RebuildRequest {

    private String skillName;
    private String userIntent;
    private double minScore = 0.5;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public RebuildRequest() {
    }

    public RebuildRequest(String skillName) {
        this.skillName = skillName;
    }

    public RebuildRequest(String skillName, String userIntent, double minScore, Map<String, Object> metadata) {
        this.skillName = skillName;
        this.userIntent = userIntent;
        this.minScore = minScore;
        setMetadata(metadata);
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getUserIntent() {
        return userIntent;
    }

    public void setUserIntent(String userIntent) {
        this.userIntent = userIntent;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public Map<String, Object> getMetadata() {
        return new LinkedHashMap<>(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
