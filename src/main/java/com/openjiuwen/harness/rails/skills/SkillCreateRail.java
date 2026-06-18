/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Independent rail for one-dimensional skill creation proposals.
 *
 * <p>Mirrors Python's {@code SkillCreateRail} in
 * {@code openjiuwen/harness/rails/skills/skill_create_rail.py}.</p>
 */
public class SkillCreateRail extends EvolutionRail {

    private final Path skillsDir;
    private final String language;
    private final boolean autoTrigger;
    private final int toolCallThreshold;
    private final int toolDiversityThreshold;
    private boolean proposalSent;

    public SkillCreateRail(Path skillsDir) {
        this(skillsDir, "cn", true, 10, 5);
    }

    public SkillCreateRail(
            Path skillsDir,
            String language,
            boolean autoTrigger,
            int toolCallThreshold,
            int toolDiversityThreshold
    ) {
        super(100, EvolutionTriggerPoint.NONE, true, Set.of());
        setPriority(85);
        this.skillsDir = skillsDir;
        this.language = "en".equals(language) ? "en" : "cn";
        this.autoTrigger = autoTrigger;
        this.toolCallThreshold = toolCallThreshold;
        this.toolDiversityThreshold = toolDiversityThreshold;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        super.beforeInvoke(ctx);
        proposalSent = false;
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        super.afterTaskIteration(ctx);
        if (!autoTrigger || proposalSent || !shouldProposeNewSkill()) {
            return;
        }
        String prompt = buildFollowUpPrompt();
        ctx.put("skill_create_follow_up", prompt);
        if (ctx.getAgent() != null) {
            ctx.getAgent().loopController().enqueueFollowUp(prompt);
        }
        proposalSent = true;
    }

    public boolean shouldProposeNewSkill() {
        Set<String> uniqueTools = new HashSet<>();
        int totalCalls = 0;
        for (Map<String, Object> step : buildTrajectory()) {
            Object values = step.get("values");
            if (!(values instanceof Map<?, ?> map)) {
                continue;
            }
            Object name = map.get("tool_name");
            if (name == null || String.valueOf(name).isBlank()) {
                continue;
            }
            totalCalls += 1;
            uniqueTools.add(String.valueOf(name));
        }
        return totalCalls >= toolCallThreshold && uniqueTools.size() >= toolDiversityThreshold;
    }

    public Path getSkillsDir() {
        return skillsDir;
    }

    public boolean isProposalSent() {
        return proposalSent;
    }

    private String buildFollowUpPrompt() {
        String dir = skillsDir == null ? "" : skillsDir.toString();
        if ("en".equals(language)) {
            return "A reusable pattern may be worth creating as a new skill. Confirm with the user first, "
                    + "then invoke skill-creator if approved. Save the new skill to: " + dir;
        }
        return "A reusable pattern may be worth creating as a new skill. Confirm with the user first, "
                + "then invoke skill-creator if approved. Save the new skill to: " + dir;
    }
}
