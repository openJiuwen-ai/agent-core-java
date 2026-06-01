/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Role-aware prompt and policy helpers.
 *
 * <p>Mirrors Python's {@code policy.py} in
 * {@code openjiuwen.agent_teams.agent.policy}.
 */
public class AgentPolicy {

    private static final Map<String, Map<String, String>> I18N_LABELS = new HashMap<>();

    static {
        Map<String, String> cnLabels = new HashMap<>();
        cnLabels.put("persona", "\u5f53\u524d\u4eba\u8bbe");
        cnLabels.put("member_name_label", "\u4f60\u7684\u6210\u5458\u540d\uff08member_name\uff09");
        cnLabels.put("team_info_heading", "\u56e2\u961f\u4fe1\u606f");
        cnLabels.put("team_name_label", "\u56e2\u961f\u540d\uff08team_name\uff09");
        cnLabels.put("display_name_label", "\u663e\u793a\u540d\uff08display_name\uff09");
        cnLabels.put("team_desc", "\u56e2\u961f\u76ee\u6807\u4e0e\u6307\u4ee4");
        cnLabels.put("relationships_heading", "\u6210\u5458\u5173\u7cfb");
        I18N_LABELS.put("cn", cnLabels);

        Map<String, String> enLabels = new HashMap<>();
        enLabels.put("persona", "Current Persona");
        enLabels.put("member_name_label", "Your member_name");
        enLabels.put("team_info_heading", "Team Info");
        enLabels.put("team_name_label", "team_name");
        enLabels.put("display_name_label", "display_name");
        enLabels.put("team_desc", "Team Goal & Directives");
        enLabels.put("relationships_heading", "Relationships");
        I18N_LABELS.put("en", enLabels);
    }

    private static final Map<String, String> WORKFLOW_TEMPLATES = new HashMap<>();

    static {
        WORKFLOW_TEMPLATES.put("default", "leader_workflow");
        WORKFLOW_TEMPLATES.put("predefined", "leader_workflow_predefined");
        WORKFLOW_TEMPLATES.put("hybrid", "leader_workflow_hybrid");
    }

    public static String rolePolicy(TeamRole role, String language) {
        String templateName = role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        return loadTemplate(templateName, language);
    }

    public static String formatTeamInfo(Map<String, Object> teamInfo, String language) {
        Map<String, String> labels = labels(language);

        StringBuilder sb = new StringBuilder();
        sb.append("\n## ").append(labels.get("team_info_heading"));

        Object teamName = teamInfo.get("team_name");
        if (teamName instanceof String value && !value.isEmpty()) {
            sb.append("\n- ").append(labels.get("team_name_label")).append(": ").append(value);
        }

        Object displayName = teamInfo.get("display_name");
        if (displayName instanceof String value && !value.isEmpty()) {
            sb.append("\n- ").append(labels.get("display_name_label")).append(": ").append(value);
        }

        Object desc = teamInfo.get("desc");
        if (desc instanceof String value && !value.isEmpty()) {
            sb.append("\n- ").append(labels.get("team_desc")).append(": ").append(value);
        }

        return sb.toString();
    }

    public static String formatTeamMembers(
            List<Map<String, String>> teamMembers,
            String language,
            String selfMemberName
    ) {
        Map<String, String> labels = labels(language);

        StringBuilder sb = new StringBuilder();
        sb.append("\n## ").append(labels.get("relationships_heading"));

        for (Map<String, String> member : teamMembers) {
            String memberName = member.getOrDefault("member_name", "");
            if (selfMemberName != null && selfMemberName.equals(memberName)) {
                continue;
            }
            String displayName = member.getOrDefault("display_name", "unknown");
            sb.append("\n- member_name=").append(memberName).append(" display_name=").append(displayName);

            String desc = member.getOrDefault("desc", "");
            if (!desc.isEmpty()) {
                sb.append(" :: ").append(desc);
            }
        }

        return sb.toString();
    }

    public static String getWorkflowTemplate(String teamMode) {
        return WORKFLOW_TEMPLATES.getOrDefault(teamMode, WORKFLOW_TEMPLATES.get("default"));
    }

    public static String buildSystemPrompt(
            TeamRole role,
            String persona,
            String basePrompt,
            Map<String, Object> teamInfo,
            List<Map<String, String>> teamMembers,
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
                lifecycle,
                language,
                teamMode
        );
    }

    private static String buildTeamPolicy(
            TeamRole role,
            String persona,
            String basePrompt,
            Map<String, Object> teamInfo,
            List<Map<String, String>> teamMembers,
            String memberName,
            String lifecycle,
            String language,
            String teamMode
    ) {
        String normalizedLanguage = normalizeLanguage(language);
        Map<String, String> labels = labels(normalizedLanguage);

        String policyName = role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        String rolePolicyText = loadTemplate(policyName, normalizedLanguage);

        String memberNameSection = memberName != null && !memberName.isBlank()
                ? labels.get("member_name_label") + ": " + memberName + "\n"
                : "";

        String workflowSection = "";
        if (role == TeamRole.LEADER) {
            workflowSection = loadTemplate(getWorkflowTemplate(teamMode), normalizedLanguage);
        }

        String lifecycleSection = "";
        if (role == TeamRole.LEADER) {
            String template = "persistent".equals(lifecycle) ? "lifecycle_persistent" : "lifecycle_temporary";
            lifecycleSection = loadTemplate(template, normalizedLanguage);
        }

        String teamInfoSection = teamInfo != null ? formatTeamInfo(teamInfo, normalizedLanguage) : "";
        String teamMembersSection = teamMembers != null
                ? formatTeamMembers(teamMembers, normalizedLanguage, memberName)
                : "";
        String basePromptSection = basePrompt != null && !basePrompt.isEmpty() ? "\n" + basePrompt : "";

        Map<String, String> values = new LinkedHashMap<>();
        values.put("member_name_section", memberNameSection);
        values.put("role_policy", rolePolicyText);
        values.put("workflow_section", workflowSection);
        values.put("lifecycle_section", lifecycleSection);
        values.put("persona_label", labels.get("persona"));
        values.put("persona", persona != null ? persona : "");
        values.put("team_info_section", teamInfoSection);
        values.put("team_members_section", teamMembersSection);
        values.put("base_prompt_section", basePromptSection);
        return renderTemplate(loadSharedTemplate("system_prompt"), values);
    }

    private static String loadTemplate(String name, String language) {
        String normalizedLanguage = normalizeLanguage(language);
        String resourcePath = "openjiuwen/agent_teams/agent/prompts/" + normalizedLanguage + "/" + name + ".md";
        String loaded = loadTemplateFile(resourcePath);
        if (loaded != null) {
            return loaded;
        }
        return fallbackTemplate(name, normalizedLanguage);
    }

    private static String loadSharedTemplate(String name) {
        String resourcePath = "openjiuwen/agent_teams/agent/prompts/" + name + ".md";
        String loaded = loadTemplateFile(resourcePath);
        if (loaded != null) {
            return loaded;
        }
        if ("system_prompt".equals(name)) {
            return "{{member_name_section}}{{role_policy}}{{workflow_section}}{{lifecycle_section}}\n"
                    + "{{persona_label}}: {{persona}}{{team_info_section}}{{team_members_section}}{{base_prompt_section}}";
        }
        return "";
    }

    private static String loadTemplateFile(String resourcePath) {
        ClassLoader loader = AgentPolicy.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(resourcePath)) {
            if (input != null) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (IOException e) {
            throw new CompletionException(e);
        }

        for (Path path : List.of(
                Path.of("..", "agent-core-0.1.12", resourcePath),
                Path.of("agent-core-0.1.12", resourcePath)
        )) {
            if (Files.isRegularFile(path)) {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8).strip();
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }
        }
        return null;
    }

    private static String renderTemplate(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return rendered;
    }

    private static String fallbackTemplate(String name, String language) {
        if ("leader_policy".equals(name)) {
            return "en".equals(language)
                    ? "You are TeamLeader. Use DAG planning, create_task, spawn_member, send_message, and approve_plan."
                    : "TeamLeader: use DAG planning, create_task, spawn_member, send_message, and approve_plan.";
        }
        if ("teammate_policy".equals(name)) {
            return "en".equals(language)
                    ? "You are Teammate. Use view_task, claim_task, collaborate, and report completion."
                    : "Teammate: use view_task, claim_task, collaborate, and report completion.";
        }
        if ("leader_workflow_predefined".equals(name)) {
            return "Predefined Team Mode: use build_team, create_task, view_task, and send_message. Do not spawn members.";
        }
        if ("leader_workflow_hybrid".equals(name)) {
            return "Hybrid Team Mode: combine predefined members with spawned specialists as needed.";
        }
        if ("leader_workflow".equals(name)) {
            return "Workflow (default): build the team, create_task, spawn members, and wait for notifications.";
        }
        if ("lifecycle_persistent".equals(name)) {
            return "Persistent lifecycle: preserve team members and shared memory for future tasks.";
        }
        if ("lifecycle_temporary".equals(name)) {
            return "Temporary lifecycle: clean up members and workspace after the task completes.";
        }
        return "";
    }

    private static Map<String, String> labels(String language) {
        return I18N_LABELS.getOrDefault(normalizeLanguage(language), I18N_LABELS.get("cn"));
    }

    private static String normalizeLanguage(String language) {
        return "en".equals(language) ? "en" : "cn";
    }

    public enum TeamRole {
        LEADER,
        MEMBER
    }
}
