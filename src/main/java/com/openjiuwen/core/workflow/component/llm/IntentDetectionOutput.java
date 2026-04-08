/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output model for IntentDetection component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.intent_detection_comp.IntentDetectionOutput}.
 */
public class IntentDetectionOutput {

    private int classificationId = -1;
    private String reason = "";
    private String categoryName = "";

    public IntentDetectionOutput() {
    }

    public IntentDetectionOutput(int classificationId, String reason, String categoryName) {
        this.classificationId = classificationId;
        this.reason = reason;
        this.categoryName = categoryName;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (classificationId != -1) {
            result.put("classification_id", classificationId);
        }
        if (reason != null && !reason.isEmpty()) {
            result.put("reason", reason);
        }
        if (categoryName != null && !categoryName.isEmpty()) {
            result.put("category_name", categoryName);
        }
        return result;
    }

    public int getClassificationId() {
        return classificationId;
    }

    public void setClassificationId(int classificationId) {
        this.classificationId = classificationId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
}
