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
 * Mirrors Python's {@code SharingMeta} in
 * {@code openjiuwen/agent_evolving/sharing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SharingMeta {

    private String skillName;
    private String skillVersion = "";
    private String uploadTrigger = "user_approval";
    private String uploadAt = Instant.now().toString();
    private String feedbackExcerpt;
    private String sourceUserId;
    private double confidence = 0.7;
    private String originBundleId;

    public SharingMeta() {
    }

    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skill_name", skillName);
        payload.put("skill_version", skillVersion);
        payload.put("upload_trigger", uploadTrigger);
        payload.put("upload_at", uploadAt);
        payload.put("confidence", confidence);
        if (feedbackExcerpt != null) {
            payload.put("feedback_excerpt", feedbackExcerpt);
        }
        if (sourceUserId != null) {
            payload.put("source_user_id", sourceUserId);
        }
        if (originBundleId != null) {
            payload.put("origin_bundle_id", originBundleId);
        }
        return payload;
    }

    public static SharingMeta fromDict(Map<String, Object> data) {
        Map<String, Object> resolved = data == null ? Map.of() : data;
        SharingMeta meta = new SharingMeta();
        meta.skillName = stringValue(resolved.get("skill_name"), "");
        meta.skillVersion = stringValue(resolved.get("skill_version"), "");
        meta.uploadTrigger = stringValue(resolved.get("upload_trigger"), "user_approval");
        meta.uploadAt = stringValue(resolved.get("upload_at"), Instant.now().toString());
        meta.feedbackExcerpt = stringValue(resolved.get("feedback_excerpt"), null);
        meta.sourceUserId = stringValue(resolved.get("source_user_id"), null);
        meta.confidence = doubleValue(resolved.get("confidence"), 0.7);
        meta.originBundleId = stringValue(resolved.get("origin_bundle_id"), null);
        return meta;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillVersion() {
        return skillVersion;
    }

    public void setSkillVersion(String skillVersion) {
        this.skillVersion = skillVersion != null ? skillVersion : "";
    }

    public String getUploadTrigger() {
        return uploadTrigger;
    }

    public void setUploadTrigger(String uploadTrigger) {
        this.uploadTrigger = uploadTrigger != null ? uploadTrigger : "user_approval";
    }

    public String getUploadAt() {
        return uploadAt;
    }

    public void setUploadAt(String uploadAt) {
        this.uploadAt = uploadAt != null ? uploadAt : Instant.now().toString();
    }

    public String getFeedbackExcerpt() {
        return feedbackExcerpt;
    }

    public void setFeedbackExcerpt(String feedbackExcerpt) {
        this.feedbackExcerpt = feedbackExcerpt;
    }

    public String getSourceUserId() {
        return sourceUserId;
    }

    public void setSourceUserId(String sourceUserId) {
        this.sourceUserId = sourceUserId;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getOriginBundleId() {
        return originBundleId;
    }

    public void setOriginBundleId(String originBundleId) {
        this.originBundleId = originBundleId;
    }
}
