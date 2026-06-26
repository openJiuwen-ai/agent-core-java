/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import java.util.List;

/**
 * Package facade for agent-team prompt exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.prompts} in
 * {@code openjiuwen/agent_teams/prompts/__init__.py}.</p>
 */
public final class PromptsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/prompts/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "MtimeSectionCache",
            "DEFAULT_TEAM_PLAN_AGENT_SYSTEM_PROMPT",
            "TEAM_PLAN_AGENT_DESC",
            "TEAM_PLAN_AGENT_SYSTEM_PROMPT_CN",
            "TEAM_PLAN_AGENT_SYSTEM_PROMPT_EN",
            "TEAM_PLAN_MODE_PROMPT_CN",
            "TEAM_PLAN_MODE_PROMPT_EN",
            "TeamSectionName",
            "apply_team_plan_agent_prompt",
            "build_system_prompt",
            "build_team_bridge_section",
            "build_team_plan_agent_card",
            "build_team_extra_section",
            "build_team_hitt_section",
            "build_team_info_section",
            "build_team_lifecycle_section",
            "build_team_member_system_prompt",
            "build_team_members_section",
            "build_team_persona_section",
            "build_team_plan_mode_prompt",
            "build_team_plan_mode_prompt_template",
            "build_team_plan_mode_section",
            "build_team_role_section",
            "build_team_static_sections",
            "build_team_workflow_section",
            "get_team_plan_mode_prompt",
            "load_shared_template",
            "load_template",
            "role_policy"
    );

    private PromptsPackage() {
    }

    public static boolean exports(String symbol) {
        return EXPORTED_SYMBOLS.contains(symbol);
    }
}
