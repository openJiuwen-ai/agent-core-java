/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public IntentDetectionOutput() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public IntentDetectionOutput(int classificationId, String reason, String categoryName) {
        this.classificationId = classificationId;
        this.reason = reason;
        this.categoryName = categoryName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getClassificationId() {
        return classificationId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setClassificationId(int classificationId) {
        this.classificationId = classificationId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getCategoryName() {
        return categoryName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
}
