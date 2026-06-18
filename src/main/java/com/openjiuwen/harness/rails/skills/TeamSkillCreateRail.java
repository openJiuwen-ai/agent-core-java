/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Independent rail for team-skill creation proposals.
 *
 * <p>Mirrors Python's {@code TeamSkillCreateRail} in
 * {@code openjiuwen/harness/rails/skills/team_skill_create_rail.py}.</p>
 */
public class TeamSkillCreateRail extends EvolutionRail {

    private final Path skillsDir;
    private final String language;
    private final boolean autoTrigger;
    private final int minTeamMembersForCreate;
    private boolean completed;
    private int proposedSpawnCount;

    public TeamSkillCreateRail(Path skillsDir) {
        this(skillsDir, "cn", true, 2);
    }

    public TeamSkillCreateRail(Path skillsDir, String language, boolean autoTrigger, int minTeamMembersForCreate) {
        super(100, EvolutionTriggerPoint.NONE, true, Set.of());
        setPriority(85);
        this.skillsDir = skillsDir;
        this.language = "en".equals(language) ? "en" : "cn";
        this.autoTrigger = autoTrigger;
        this.minTeamMembersForCreate = minTeamMembersForCreate;
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        super.afterTaskIteration(ctx);
        maybeEnqueueCreationFollowUp(ctx);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        super.afterInvoke(ctx);
        maybeEnqueueCreationFollowUp(ctx);
    }

    public boolean notifyTeamCompleted(CallbackContext ctx) {
        if (!autoTrigger) {
            return false;
        }
        completed = true;
        if (ctx != null) {
            maybeEnqueueCreationFollowUp(ctx);
        }
        return true;
    }

    public int countSpawnMemberCalls() {
        int count = 0;
        for (Map<String, Object> step : buildTrajectory()) {
            Object values = step.get("values");
            if (!(values instanceof Map<?, ?> map)) {
                continue;
            }
            Object name = map.get("tool_name");
            if (name != null && String.valueOf(name).contains("spawn_member")) {
                count += 1;
            }
        }
        return count;
    }

    private boolean maybeEnqueueCreationFollowUp(CallbackContext ctx) {
        int spawnCount = countSpawnMemberCalls();
        if (!autoTrigger || !completed || spawnCount < minTeamMembersForCreate || spawnCount <= proposedSpawnCount) {
            return false;
        }
        String prompt = buildFollowUpPrompt();
        ctx.put("team_skill_create_follow_up", prompt);
        if (ctx.getAgent() != null) {
            ctx.getAgent().loopController().enqueueFollowUp(prompt);
        }
        proposedSpawnCount = spawnCount;
        completed = false;
        return true;
    }

    private String buildFollowUpPrompt() {
        String dir = skillsDir == null ? "" : skillsDir.toString();
        if ("en".equals(language)) {
            return "A multi-agent collaboration pattern may be worth creating as a team skill. "
                    + "Confirm with the user first, then invoke team-skill-creator if approved. Save to: " + dir;
        }
        return "A multi-agent collaboration pattern may be worth creating as a team skill. "
                + "Confirm with the user first, then invoke team-skill-creator if approved. Save to: " + dir;
    }
}
