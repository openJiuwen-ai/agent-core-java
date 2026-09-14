/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output DTO for intent-detection classification results.
 *
 * <p>Mirrors Python's {@code IntentDetectionOutput} in
 * {@code openjiuwen/core/workflow/components/llm/intent_detection_comp.py}.</p>
 */
public class IntentDetectionOutput {

    private int classificationId = -1;
    private String reason = "";
    private String categoryName = "";

    public IntentDetectionOutput() {
    }

    public IntentDetectionOutput(int classificationId, String reason, String categoryName) {
        setClassificationId(classificationId);
        setReason(reason);
        setCategoryName(categoryName);
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
        this.reason = reason == null ? "" : reason;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName == null ? "" : categoryName;
    }

    /**
     * Convert to the same plain dictionary shape as Python's
     * {@code model_dump(exclude_defaults=True)}.
     *
     * @return output map using Python field names
     */
    public Map<String, Object> toMap() {
        Map<String, Object> output = new LinkedHashMap<>();
        if (classificationId != -1) {
            output.put("classification_id", classificationId);
        }
        if (reason != null && !reason.isEmpty()) {
            output.put("reason", reason);
        }
        if (categoryName != null && !categoryName.isEmpty()) {
            output.put("category_name", categoryName);
        }
        return output;
    }
}
