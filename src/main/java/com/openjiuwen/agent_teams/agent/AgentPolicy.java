/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.*;

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
        Map<String, String> labels = I18N_LABELS.getOrDefault(language, I18N_LABELS.get("en"));
        
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
        Map<String, String> labels = I18N_LABELS.getOrDefault(language, I18N_LABELS.get("en"));
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n## ").append(labels.get("relationships_heading"));
        
        for (Map<String, String> member : teamMembers) {
            String memberName = member.get("member_name");
            String displayName = member.getOrDefault("display_name", memberName);
            
            if (selfMemberName != null && selfMemberName.equals(memberName)) {
                sb.append("\n- **").append(displayName).append("** (you)");
            } else {
                sb.append("\n- ").append(displayName);
            }
            
            String desc = member.get("desc");
            if (desc != null && !desc.isEmpty()) {
                sb.append(": ").append(desc);
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
    
    /**
     * Load template content by name.
     * Placeholder implementation - should load from prompts/{language}/{name}.md
     *
     * @param name Template name
     * @param language Language code
     * @return Template content
     */
    private static String loadTemplate(String name, String language) {
        // Placeholder: should load from external template files
        return "# " + name + " template\n\n[Placeholder - load from prompts/" + language + "/" + name + ".md]";
    }
    
    /**
     * TeamRole enum.
     */
    public enum TeamRole {
        LEADER,
        MEMBER
    }
}