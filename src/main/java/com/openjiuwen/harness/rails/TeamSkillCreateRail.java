/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

/**
 * Legacy flat-package facade; prefer {@code com.openjiuwen.harness.rails.skills.TeamSkillCreateRail}.
 *
 * @since 1.0
 * @deprecated Use {@code com.openjiuwen.harness.rails.skills.TeamSkillCreateRail} instead.
 */
@Deprecated
public class TeamSkillCreateRail extends com.openjiuwen.harness.rails.skills.TeamSkillCreateRail {

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamSkillCreateRail(String skillsDir) {
        super(skillsDir == null ? null : java.nio.file.Path.of(skillsDir));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamSkillCreateRail(String skillsDir, String language, boolean isAutoTrigger, int minTeamMembersForCreate) {
        super(skillsDir == null ? null : java.nio.file.Path.of(skillsDir), language, isAutoTrigger, minTeamMembersForCreate);
    }
}
