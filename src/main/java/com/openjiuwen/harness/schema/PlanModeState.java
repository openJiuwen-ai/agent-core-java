/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Plan mode session-scoped state.
 * <p>
 * Mirrors Python's {@code PlanModeState} in {@code openjiuwen.harness.schema.state}.
 * <p>
 * Attributes:
 * <ul>
 *   <li>mode: Current agent mode — "normal" or "plan"</li>
 *   <li>prePlanMode: Mode that was active before</li>
 *   <li>planSlug: Short identifier for the active plan file</li>
 * </ul>
 */
public class PlanModeState {

    /** Current agent mode — "normal" or "plan". */
    private String mode = "normal";
    
    /** Mode that was active before. */
    private String prePlanMode = "normal";
    
    /** Short identifier for the active plan file (e.g. "gleaming-brewing-phoenix"). */
    private String planSlug = null;

    public PlanModeState() {
        // Default constructor with default values
    }

    public PlanModeState(String mode, String prePlanMode, String planSlug) {
        this.mode = mode != null ? mode : "normal";
        this.prePlanMode = prePlanMode != null ? prePlanMode : "normal";
        this.planSlug = planSlug;
    }

    // Getters and setters
    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getPrePlanMode() {
        return prePlanMode;
    }

    public void setPrePlanMode(String prePlanMode) {
        this.prePlanMode = prePlanMode;
    }

    public String getPlanSlug() {
        return planSlug;
    }

    public void setPlanSlug(String planSlug) {
        this.planSlug = planSlug;
    }

    /**
     * Serialize to a JSON-friendly map.
     * <p>
     * Mirrors Python's {@code to_dict()}.
     *
     * @return Map with mode, prePlanMode, and planSlug fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("mode", mode);
        map.put("pre_plan_mode", prePlanMode);
        map.put("plan_slug", planSlug);
        return map;
    }

    /**
     * Restore from a serialized map.
     * <p>
     * Mirrors Python's {@code from_dict()}.
     *
     * @param data Map previously produced by toMap(); null is treated as empty snapshot
     * @return Reconstructed PlanModeState
     */
    public static PlanModeState fromMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new PlanModeState();
        }
        return new PlanModeState(
            (String) data.getOrDefault("mode", "normal"),
            (String) data.getOrDefault("pre_plan_mode", "normal"),
            (String) data.get("plan_slug")
        );
    }

    @Override
    public String toString() {
        return "PlanModeState{" +
                "mode='" + mode + '\'' +
                ", prePlanMode='" + prePlanMode + '\'' +
                ", planSlug='" + planSlug + '\'' +
                '}';
    }
}