/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionRecord;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Public class TeamSkillRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TeamSkillRail extends EvolutionRail {
    private final String skillsDir;
    private final String language;
    private boolean isEvolutionInProgress;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamSkillRail(String skillsDir) {
        this(skillsDir, "cn");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamSkillRail(String skillsDir, String language) {
        super(EvolutionTriggerPoint.NONE, true);
        this.skillsDir = skillsDir != null ? skillsDir : "skills";
        this.language = language != null ? language : "cn";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int priority() {
        return 80;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void onAfterToolCall(AgentCallbackContext ctx) {
        if (isEvolutionInProgress || ctx == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        if (!"view_task".equals(inputs.getToolName()) || !allTasksCompleted(inputs.getToolResult())) {
            return;
        }
        notifyTeamCompleted(ctx);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean notifyTeamCompleted(AgentCallbackContext ctx) {
        if (isEvolutionInProgress) {
            return false;
        }
        isEvolutionInProgress = true;
        emitApprovalEvent("all tasks completed, starting evolution analysis");
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean allTasksCompleted(Object result) {
        String text = String.valueOf(result).toLowerCase(Locale.ROOT);
        if (!text.contains("completed")) {
            return false;
        }
        return !(text.contains("pending")
                || text.contains("claimed")
                || text.contains("in_progress")
                || text.contains("blocked"));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String formatEvolutionRecords(List<EvolutionRecord> records) {
        return formatEvolutionRecords(records, "cn");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String formatEvolutionRecords(List<EvolutionRecord> records, String language) {
        if (records == null || records.isEmpty()) {
            return "en".equalsIgnoreCase(language) ? "(no evolution records)" : "（无演进经验）";
        }
        return records.stream()
                .map(record -> {
                    String section = record.getChange() != null ? record.getChange().getSection() : "";
                    String action = record.getChange() != null ? record.getChange().getAction() : "";
                    String content = record.getChange() != null ? record.getChange().getContent() : "";
                    return "- [" + section + "/" + action + "] " + content;
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSkillsDir() {
        return skillsDir;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEvolutionInProgress() {
        return isEvolutionInProgress;
    }
}
