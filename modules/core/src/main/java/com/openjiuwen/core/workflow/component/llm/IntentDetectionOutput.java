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
 * 
 * @since 0.1.7
 */
public class IntentDetectionOutput {
    private int classificationId = -1;
    private String reason = "";
    private String categoryName = "";

    /**
     * IntentDetectionOutput.
     * 
     * @since 0.1.7
     */
    public IntentDetectionOutput() {
    }

    /**
     * IntentDetectionOutput.
     * 
     * @param classificationId classificationId
     * @param reason reason
     * @param categoryName categoryName
     * @since 0.1.7
     */
    public IntentDetectionOutput(int classificationId, String reason, String categoryName) {
        this.classificationId = classificationId;
        this.reason = reason;
        this.categoryName = categoryName;
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
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
     * getClassificationId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getClassificationId() {
        return classificationId;
    }

    /**
     * setClassificationId.
     * 
     * @param classificationId classificationId
     * @since 0.1.7
     */
    public void setClassificationId(int classificationId) {
        this.classificationId = classificationId;
    }

    /**
     * getReason.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getReason() {
        return reason;
    }

    /**
     * setReason.
     * 
     * @param reason reason
     * @since 0.1.7
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * getCategoryName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getCategoryName() {
        return categoryName;
    }

    /**
     * setCategoryName.
     * 
     * @param categoryName categoryName
     * @since 0.1.7
     */
    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
}
