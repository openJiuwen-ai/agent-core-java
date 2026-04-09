/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

/**
 * Utility class for workflow LLM operations.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.WorkflowLLMUtils}.
 */
public final class WorkflowLLMUtils {

    private WorkflowLLMUtils() {
    }

    /**
     * Extract content string from an LLM response object.
     *
     * @param response the response object
     * @return the content string
     */
    public static String extractContent(Object response) {
        if (response == null) {
            return "";
        }
        try {
            java.lang.reflect.Method getContent = response.getClass().getMethod("getContent");
            Object content = getContent.invoke(response);
            return content != null ? content.toString() : "";
        } catch (Exception e) {
            return response.toString();
        }
    }
}
