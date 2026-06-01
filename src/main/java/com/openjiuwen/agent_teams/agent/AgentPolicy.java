/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionException;

/**
 * Role-aware prompt and policy helpers.
 * <p>
 * The system prompt is the primary driver of team behavior —
 * the CoordinatorLoop only wakes the DeepAgent and injects
 * unread messages; all decision logic comes from these prompts.
 * <p>
 * Mirrors Python's {@code policy.py} in
 * {@code openjiuwen.agent_teams.agent.policy}.
 */
public class AgentPolicy {

    /** I18N labels for formatting */
    private static final Map<String, Map<String, String>> I18N_LABELS = new HashMap<>();
    
    static {
        Map<String, String> cnLabels = new HashMap<>();
        cnLabels.put("persona", "当前人设");
        cnLabels.put("member_name_label", "你的成员名（member_name）");
        cnLabels.put("team_info_heading", "团队信息");
        cnLabels.put("team_name_label", "团队名（team_name）");
        cnLabels.put("display_name_label", "显示名（display_name）");
        cnLabels.put("team_desc", "团队目标与指令");
        cnLabels.put("relationships_heading", "成员关系");
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
    
    /** Workflow templates */
    private static final Map<String, String> WORKFLOW_TEMPLATES = new HashMap<>();
    
    static {
        WORKFLOW_TEMPLATES.put("default", "leader_workflow");
        WORKFLOW_TEMPLATES.put("predefined", "leader_workflow_predefined");
        WORKFLOW_TEMPLATES.put("hybrid", "leader_workflow_hybrid");
    }
    
    /**
     * Return the base policy string for a role.
     * <p>
     * Mirrors Python: role_policy(role, language)
     *
     * @param role TeamRole enum (LEADER or MEMBER)
     * @param language Language code ("cn" or "en")
     * @return Policy template content
     */
    public static String rolePolicy(TeamRole role, String language) {
        String templateName = role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        return loadTemplate(templateName, language);
    }
    
    /**
     * Format team information from database TeamInfo into a prompt section.
     * <p>
     * Mirrors Python: _format_team_info(team_info, labels)
     *
     * @param teamInfo Team info map
     * @param language Language code
     * @return Formatted team info string
     */
    public static String formatTeamInfo(Map<String, Object> teamInfo, String language) {
        Map<String, String> labels = labels(language);
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n## ").append(labels.get("team_info_heading"));
        
        String teamName = (String) teamInfo.get("team_name");
        if (teamName != null && !teamName.isEmpty()) {
            sb.append("\n- ").append(labels.get("team_name_label")).append(": ").append(teamName);
        }
        
        String displayName = (String) teamInfo.get("display_name");
        if (displayName != null && !displayName.isEmpty()) {
            sb.append("\n- ").append(labels.get("display_name_label")).append(": ").append(displayName);
        }
        
        String desc = (String) teamInfo.get("desc");
        if (desc != null && !desc.isEmpty()) {
            sb.append("\n- ").append(labels.get("team_desc")).append(": ").append(desc);
        }
        
        return sb.toString();
    }
    
    /**
     * Format team member list into a Relationships prompt section.
     * <p>
     * Mirrors Python: _format_team_members(team_members, labels, self_member_name)
     *
     * @param teamMembers List of member maps
     * @param language Language code
     * @param selfMemberName Current member's name (optional)
     * @return Formatted relationships string
     */
    public static String formatTeamMembers(List<Map<String, String>> teamMembers, 
                                            String language, 
                                            String selfMemberName) {
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
    
    /**
     * Get workflow template name by team mode.
     *
     * @param teamMode Team mode ("default", "predefined", or "hybrid")
     * @return Workflow template name
     */
    public static String getWorkflowTemplate(String teamMode) {
        return WORKFLOW_TEMPLATES.getOrDefault(teamMode, WORKFLOW_TEMPLATES.get("default"));
    }

    public static String buildSystemPrompt(TeamRole role,
                                           String persona,
                                           String basePrompt,
                                           Map<String, Object> teamInfo,
                                           List<Map<String, String>> teamMembers,
                                           String memberName,
                                           String lifecycle,
                                           String language,
                                           String teamMode) {
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

    private static String buildTeamPolicy(TeamRole role,
                                          String persona,
                                          String basePrompt,
                                          Map<String, Object> teamInfo,
                                          List<Map<String, String>> teamMembers,
                                          String memberName,
                                          String lifecycle,
                                          String language,
                                          String teamMode) {
        String normalizedLanguage = normalizeLanguage(language);
        Map<String, String> labels = labels(normalizedLanguage);

        String policyName = role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        String rolePolicyText = loadTemplate(policyName, normalizedLanguage);

        String memberNameSection = memberName != null && !memberName.isBlank()
                ? labels.get("member_name_label") + ": " + memberName + "\n" : "";

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
                ? formatTeamMembers(teamMembers, normalizedLanguage, memberName) : "";
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

    /**
     * Load template content by name.
     *
     * @param name Template name
     * @param language Language code
     * @return Template content
     */
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
                    ? "You are TeamLeader. Use create_task, spawn_member, send_message, and approve_plan to coordinate experts."
                    : "你是 TeamLeader。使用 create_task、spawn_member、send_message 和 approve_plan 协调专家团队。";
        }
        if ("teammate_policy".equals(name)) {
            return "en".equals(language)
                    ? "You are Teammate. Claim suitable tasks, plan independently, collaborate, and report completion."
                    : "你是 Teammate。主动领取匹配任务，独立规划，协作执行，并汇报完成结果。";
        }
        if ("leader_workflow_predefined".equals(name)) {
            return "en".equals(language)
                    ? "Predefined team mode: use build_team, create_task, view_task, and send_message. Do not spawn members."
                    : "预定义团队模式：使用 build_team、create_task、view_task 和 send_message，不要创建新成员。";
        }
        if ("leader_workflow_hybrid".equals(name)) {
            return "en".equals(language)
                    ? "Hybrid team mode: combine predefined members with spawned specialists as needed."
                    : "混合团队模式：结合预定义成员，并按需创建专业成员。";
        }
        if ("leader_workflow".equals(name)) {
            return "en".equals(language)
                    ? "Default workflow: build the team, create tasks, spawn members, and wait for notifications."
                    : "默认流程：组建团队、创建任务、创建成员，并等待通知。";
        }
        if ("lifecycle_persistent".equals(name)) {
            return "en".equals(language)
                    ? "Persistent lifecycle: preserve team members and shared memory for future tasks."
                    : "持久生命周期：保留团队成员和共享记忆以支持后续任务。";
        }
        if ("lifecycle_temporary".equals(name)) {
            return "en".equals(language)
                    ? "Temporary lifecycle: clean up members and workspace after the task completes."
                    : "临时生命周期：任务完成后清理成员和工作空间。";
        }
        return "";
    }

    private static Map<String, String> labels(String language) {
        return I18N_LABELS.getOrDefault(normalizeLanguage(language), I18N_LABELS.get("cn"));
    }

    private static String normalizeLanguage(String language) {
        return "en".equals(language) ? "en" : "cn";
    }
    
    /**
     * TeamRole enum.
     */
    public enum TeamRole {
        LEADER,
        MEMBER
    }
}
