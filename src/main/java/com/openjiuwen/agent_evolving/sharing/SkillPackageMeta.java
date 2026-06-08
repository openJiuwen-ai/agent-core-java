/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code SkillPackageMeta} in
 * {@code openjiuwen/agent_evolving/sharing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SkillPackageMeta {

    private String skillId;
    private String skillName = "";
    private String description = "";
    private String uploadedAt = Instant.now().toString();

    public SkillPackageMeta() {
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill_id", skillId);
        payload.put("skill_name", skillName);
        payload.put("description", description);
        payload.put("uploaded_at", uploadedAt);
        return payload;
    }

    public static SkillPackageMeta fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.skillId = SharedExperience.stringValue(resolved.get("skill_id"), "");
        meta.skillName = SharedExperience.stringValue(resolved.get("skill_name"), "");
        meta.description = SharedExperience.stringValue(resolved.get("description"), "");
        meta.uploadedAt = SharedExperience.stringValue(resolved.get("uploaded_at"), Instant.now().toString());
        return meta;
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

    public String getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(String uploadedAt) {
        this.uploadedAt = uploadedAt != null ? uploadedAt : Instant.now().toString();
    }
}
