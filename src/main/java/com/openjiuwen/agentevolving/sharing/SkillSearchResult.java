/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code SkillSearchResult} in
 * {@code openjiuwen/agent_evolving/sharing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SkillSearchResult {

    private String skillId;
    private String skillName = "";
    private String description = "";
    private int experienceCount;
    private List<String> keywords = List.of();
    private double score;

    public SkillSearchResult() {
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill_id", skillId);
        payload.put("skill_name", skillName);
        payload.put("description", description);
        payload.put("experience_count", experienceCount);
        payload.put("keywords", keywords);
        payload.put("score", score);
        return payload;
    }

    public static SkillSearchResult fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        SkillSearchResult result = new SkillSearchResult();
        result.skillId = SharedExperience.stringValue(resolved.get("skill_id"), "");
        result.skillName = SharedExperience.stringValue(resolved.get("skill_name"), "");
        result.description = SharedExperience.stringValue(resolved.get("description"), "");
        Object experienceCount = resolved.get("experience_count");
        result.experienceCount = experienceCount instanceof Number number ? number.intValue() : 0;
        result.keywords = SharedExperience.stringList(resolved.get("keywords"));
        Object score = resolved.get("score");
        result.score = score instanceof Number number ? number.doubleValue() : 0.0;
        return result;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName != null ? skillName : "";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public int getExperienceCount() {
        return experienceCount;
    }

    public void setExperienceCount(int experienceCount) {
        this.experienceCount = experienceCount;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
