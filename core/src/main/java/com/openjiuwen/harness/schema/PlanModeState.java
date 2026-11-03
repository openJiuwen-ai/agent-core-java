/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan-mode session-scoped state.
 *
 * <p>Mirrors Python's {@code PlanModeState} in
 * {@code openjiuwen/harness/schema/state.py}.</p>
 */
public final class PlanModeState {

    private String mode = "normal";
    private String prePlanMode = "normal";
    private String planSlug;
    private String promptContext;

    public PlanModeState() {
    }

    public PlanModeState(String mode, String prePlanMode, String planSlug, String promptContext) {
        this.mode = mode == null ? "normal" : mode;
        this.prePlanMode = prePlanMode == null ? "normal" : prePlanMode;
        this.planSlug = planSlug;
        this.promptContext = promptContext;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? "normal" : mode;
    }

    public String getPrePlanMode() {
        return prePlanMode;
    }

    public void setPrePlanMode(String prePlanMode) {
        this.prePlanMode = prePlanMode == null ? "normal" : prePlanMode;
    }

    public String getPlanSlug() {
        return planSlug;
    }

    public void setPlanSlug(String planSlug) {
        this.planSlug = planSlug;
    }

    public String getPromptContext() {
        return promptContext;
    }

    public void setPromptContext(String promptContext) {
        this.promptContext = promptContext;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", mode);
        map.put("pre_plan_mode", prePlanMode);
        map.put("plan_slug", planSlug);
        map.put("prompt_context", promptContext);
        return map;
    }

    public static PlanModeState fromMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new PlanModeState();
        }
        return new PlanModeState(
                stringValue(data.get("mode"), "normal"),
                stringValue(data.get("pre_plan_mode"), "normal"),
                stringOrNull(data.get("plan_slug")),
                stringOrNull(data.get("prompt_context"))
        );
    }

    private static String stringValue(Object value, String fallback) {
        String text = stringOrNull(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
