/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Role-aware prompt and policy helpers.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_teams/prompts/policy.py}.</p>
 */
public final class PromptPolicy {

    private static final Map<String, Map<String, String>> I18N_LABELS = Map.of(
            "cn", Map.of(
                    "persona", "当前人设",
                    "member_name_label", "你的成员名（member_name）",
                    "team_info_heading", "团队信息",
                    "team_name_label", "团队名（team_name）",
                    "display_name_label", "显示名（display_name）",
                    "team_desc", "团队目标与指令",
                    "relationships_heading", "成员关系"
            ),
            "en", Map.of(
                    "persona", "Current Persona",
                    "member_name_label", "Your member_name",
                    "team_info_heading", "Team Info",
                    "team_name_label", "team_name",
                    "display_name_label", "display_name",
                    "team_desc", "Team Goal & Directives",
                    "relationships_heading", "Relationships"
            )
    );

    private static final Map<String, String> WORKFLOW_TEMPLATES = Map.of(
            "default", "leader_workflow",
            "predefined", "leader_workflow_predefined",
            "hybrid", "leader_workflow_hybrid"
    );

    private PromptPolicy() {
    }

    public static String rolePolicy(TeamRole role) {
        return rolePolicy(role, PromptLoader.DEFAULT_LANGUAGE);
    }

    public static String rolePolicy(TeamRole role, String language) {
        String name = role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        return String.valueOf(PromptLoader.loadTemplate(name, language).getContent());
    }

    public static String buildSystemPrompt(
            TeamRole role,
            String persona,
            String basePrompt,
            Map<String, ?> teamInfo,
            List<? extends Map<String, String>> teamMembers,
            String memberName,
            String lifecycle,
            String language,
            String teamMode
    ) {
        return buildTeamPolicy(
                role,
                persona,
                basePrompt,
                teamInfo,
                teamMembers,
                memberName,
                lifecycle == null ? "temporary" : lifecycle,
                language == null ? PromptLoader.DEFAULT_LANGUAGE : language,
                teamMode == null ? "default" : teamMode
        );
    }

    public static String buildSystemPrompt(TeamRole role, String persona) {
        return buildSystemPrompt(role, persona, null, null, null, null, "temporary", "cn", "default");
    }

    private static String formatTeamInfo(Map<String, ?> teamInfo, Map<String, String> labels) {
        StringBuilder builder = new StringBuilder("\n## ").append(labels.get("team_info_heading"));
        Object teamName = teamInfo.get("team_name");
        if (isPresent(teamName)) {
            builder.append("\n- ").append(labels.get("team_name_label")).append(": ").append(teamName);
        }
        Object displayName = teamInfo.get("display_name");
        if (isPresent(displayName)) {
            builder.append("\n- ").append(labels.get("display_name_label")).append(": ").append(displayName);
        }
        Object desc = teamInfo.get("desc");
        if (isPresent(desc)) {
            builder.append("\n- ").append(labels.get("team_desc")).append(": ").append(desc);
        }
        return builder.toString();
    }

    private static String formatTeamMembers(
            List<? extends Map<String, String>> teamMembers,
            Map<String, String> labels,
            String selfMemberName
    ) {
        StringBuilder builder = new StringBuilder("\n## ").append(labels.get("relationships_heading"));
        for (Map<String, String> member : teamMembers) {
            String memberName = member.getOrDefault("member_name", "");
            if (memberName.equals(selfMemberName)) {
                continue;
            }
            String displayName = member.getOrDefault("display_name", "unknown");
            String desc = member.getOrDefault("desc", "");
            builder.append("\n- member_name=").append(memberName).append(" display_name=").append(displayName);
            if (!desc.isEmpty()) {
                builder.append(" :: ").append(desc);
            }
        }
        return builder.toString();
    }

    private static String buildTeamPolicy(
            TeamRole role,
            String persona,
            String basePrompt,
            Map<String, ?> teamInfo,
            List<? extends Map<String, String>> teamMembers,
            String memberName,
            String lifecycle,
            String language,
            String teamMode
    ) {
        Map<String, String> labels = I18N_LABELS.getOrDefault(language, I18N_LABELS.get("cn"));
        String policyName = role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        String rolePolicyText = String.valueOf(PromptLoader.loadTemplate(policyName, language).getContent());
        String memberNameSection = isPresent(memberName)
                ? labels.get("member_name_label") + ": " + memberName + "\n"
                : "";

        String workflowSection = "";
        if (role == TeamRole.LEADER) {
            String workflowName = WORKFLOW_TEMPLATES.getOrDefault(teamMode, "leader_workflow");
            workflowSection = String.valueOf(PromptLoader.loadTemplate(workflowName, language).getContent());
        }

        String lifecycleSection = "";
        if (role == TeamRole.LEADER) {
            String lifecycleName = "persistent".equals(lifecycle) ? "lifecycle_persistent" : "lifecycle_temporary";
            lifecycleSection = String.valueOf(PromptLoader.loadTemplate(lifecycleName, language).getContent());
        }

        String teamInfoSection = teamInfo == null || teamInfo.isEmpty() ? "" : formatTeamInfo(teamInfo, labels);
        String teamMembersSection = teamMembers == null || teamMembers.isEmpty()
                ? ""
                : formatTeamMembers(teamMembers, labels, memberName);
        String basePromptSection = isPresent(basePrompt) ? "\n" + basePrompt : "";

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("member_name_section", memberNameSection);
        values.put("role_policy", rolePolicyText);
        values.put("workflow_section", workflowSection);
        values.put("lifecycle_section", lifecycleSection);
        values.put("persona_label", labels.get("persona"));
        values.put("persona", persona);
        values.put("team_info_section", teamInfoSection);
        values.put("team_members_section", teamMembersSection);
        values.put("base_prompt_section", basePromptSection);
        return String.valueOf(PromptLoader.loadSharedTemplate("system_prompt").format(values).getContent());
    }

    private static boolean isPresent(Object value) {
        return value != null && !String.valueOf(value).isEmpty();
    }
}
