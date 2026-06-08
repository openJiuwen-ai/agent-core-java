/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors Python's {@code SharedSkillBundle} in
 * {@code openjiuwen/agent_evolving/sharing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SharedSkillBundle {

    private String bundleId = newBundleId();
    private String skillId = "";
    private String skillName = "";
    private String skillVersion = "";
    private List<String> keywordsAggregate = new ArrayList<>();
    private String summaryAggregate = "";
    private List<SharedExperience> experiences = new ArrayList<>();
    private String createdAt = Instant.now().toString();

    public SharedSkillBundle() {
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bundle_id", bundleId);
        payload.put("skill_id", skillId);
        payload.put("skill_name", skillName);
        payload.put("skill_version", skillVersion);
        payload.put("keywords_aggregate", new ArrayList<>(keywordsAggregate));
        payload.put("summary_aggregate", summaryAggregate);
        payload.put("experiences", experiences.stream().map(SharedExperience::toDict).toList());
        payload.put("created_at", createdAt);
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static SharedSkillBundle fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        SharedSkillBundle bundle = new SharedSkillBundle();
        String skillId = SharedExperience.stringValue(resolved.get("skill_id"), "");
        if (skillId.isEmpty()) {
            skillId = SharedExperience.stringValue(resolved.get("skill_content_hash"), "");
        }
        bundle.bundleId = SharedExperience.stringValue(resolved.get("bundle_id"), newBundleId());
        bundle.skillId = skillId;
        bundle.skillName = SharedExperience.stringValue(resolved.get("skill_name"), "");
        bundle.skillVersion = SharedExperience.stringValue(resolved.get("skill_version"), "");
        bundle.keywordsAggregate = SharedExperience.stringList(resolved.get("keywords_aggregate"));
        bundle.summaryAggregate = SharedExperience.stringValue(resolved.get("summary_aggregate"), "");
        Object experiences = resolved.get("experiences");
        if (experiences instanceof List<?> list) {
            bundle.experiences = list.stream()
                .filter(Map.class::isInstance)
                .map(item -> SharedExperience.fromDict((Map<String, Object>) item))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } else {
            bundle.experiences = new ArrayList<>();
        }
        bundle.createdAt = SharedExperience.stringValue(resolved.get("created_at"), Instant.now().toString());
        return bundle;
    }

    public static SharedSkillBundle make(String skillName, List<SharedExperience> experiences) {
        return make(skillName, experiences, "", "");
    }

    public static SharedSkillBundle make(
            String skillName,
            List<SharedExperience> experiences,
            String skillVersion,
            String summaryAggregate
    ) {
        List<SharedExperience> safeExperiences = experiences == null ? List.of() : List.copyOf(experiences);
        List<String> seen = new ArrayList<>();
        for (SharedExperience experience : safeExperiences) {
            for (String keyword : experience.getKeywords()) {
                if (keyword != null && !keyword.isEmpty() && !seen.contains(keyword)) {
                    seen.add(keyword);
                }
            }
        }
        SharedSkillBundle bundle = new SharedSkillBundle();
        bundle.skillName = skillName;
        bundle.skillVersion = skillVersion != null ? skillVersion : "";
        bundle.keywordsAggregate = seen;
        bundle.summaryAggregate = summaryAggregate != null && !summaryAggregate.isEmpty()
            ? summaryAggregate
            : safeExperiences.stream()
                .map(SharedExperience::getSummary)
                .filter(summary -> summary != null && !summary.isEmpty())
                .collect(java.util.stream.Collectors.joining("; "));
        bundle.experiences = new ArrayList<>(safeExperiences);
        return bundle;
    }

    private static String newBundleId() {
        return "sb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public String getBundleId() {
        return bundleId;
    }

    public void setBundleId(String bundleId) {
        this.bundleId = bundleId != null ? bundleId : newBundleId();
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId != null ? skillId : "";
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName != null ? skillName : "";
    }

    public String getSkillVersion() {
        return skillVersion;
    }

    public void setSkillVersion(String skillVersion) {
        this.skillVersion = skillVersion != null ? skillVersion : "";
    }

    public List<String> getKeywordsAggregate() {
        return new ArrayList<>(keywordsAggregate);
    }

    public void setKeywordsAggregate(List<String> keywordsAggregate) {
        this.keywordsAggregate = keywordsAggregate == null ? new ArrayList<>() : new ArrayList<>(keywordsAggregate);
    }

    public String getSummaryAggregate() {
        return summaryAggregate;
    }

    public void setSummaryAggregate(String summaryAggregate) {
        this.summaryAggregate = summaryAggregate != null ? summaryAggregate : "";
    }

    public List<SharedExperience> getExperiences() {
        return new ArrayList<>(experiences);
    }

    public void setExperiences(List<SharedExperience> experiences) {
        this.experiences = experiences == null ? new ArrayList<>() : new ArrayList<>(experiences);
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt != null ? createdAt : Instant.now().toString();
    }
}
