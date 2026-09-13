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
 * @since 0.1.7
 */
public class TeamSkillRail extends EvolutionRail {
    private final String skillsDir;
    private final String language;
    private boolean isEvolutionInProgress;

    /**
     * TeamSkillRail.
     * 
     * @param skillsDir skillsDir
     * @since 0.1.7
     */
    public TeamSkillRail(String skillsDir) {
        this(skillsDir, "cn");
    }

    /**
     * TeamSkillRail.
     * 
     * @param skillsDir skillsDir
     * @param language language
     * @since 0.1.7
     */
    public TeamSkillRail(String skillsDir, String language) {
        super(EvolutionTriggerPoint.NONE, true);
        this.skillsDir = skillsDir != null ? skillsDir : "skills";
        this.language = language != null ? language : "cn";
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 80;
    }

    /**
     * onAfterToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
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
     * notifyTeamCompleted.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
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
     * allTasksCompleted.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    public static boolean allTasksCompleted(Object result) {
        String text = String.valueOf(result).toLowerCase(Locale.ROOT);
        if (!text.contains("completed")) {
            return false;
        }
        return !(text.contains("pending") || text.contains("claimed") || text.contains("in_progress")
                || text.contains("blocked"));
    }

    /**
     * formatEvolutionRecords.
     * 
     * @param records records
     * @return the result
     * @since 0.1.7
     */
    public static String formatEvolutionRecords(List<EvolutionRecord> records) {
        return formatEvolutionRecords(records, "cn");
    }

    /**
     * formatEvolutionRecords.
     * 
     * @param records records
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static String formatEvolutionRecords(List<EvolutionRecord> records, String language) {
        if (records == null || records.isEmpty()) {
            return "en".equalsIgnoreCase(language) ? "(no evolution records)" : "（无演进经验）";
        }
        return records.stream().map(record -> {
            String section = record.getChange() != null ? record.getChange().getSection() : "";
            String action = record.getChange() != null ? record.getChange().getAction() : "";
            String content = record.getChange() != null ? record.getChange().getContent() : "";
            return "- [" + section + "/" + action + "] " + content;
        }).collect(Collectors.joining("\n"));
    }

    /**
     * getSkillsDir.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSkillsDir() {
        return skillsDir;
    }

    /**
     * getLanguage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLanguage() {
        return language;
    }

    /**
     * isEvolutionInProgress.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEvolutionInProgress() {
        return isEvolutionInProgress;
    }
}
