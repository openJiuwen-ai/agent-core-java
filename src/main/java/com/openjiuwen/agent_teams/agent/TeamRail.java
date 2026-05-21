/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.rails.DeepAgentRail;

/**
 * TeamRail — decomposes team policy into ordered PromptSections.
 * <p>
 * Replaces the legacy monolithic build_system_prompt with one PromptSection per
 * content category. Each section is registered against the shared SystemPromptBuilder
 * before every model call so the team-specific slices line up with the harness sections.
 * <p>
 * Mirrors Python's {@code TeamRail} in
 * {@code openjiuwen.agent_teams.agent.team_rail}.
 */
public class TeamRail extends DeepAgentRail {

    private static final int PRIORITY = 12;
    private static final Set<String> DYNAMIC_SECTION_NAMES = Set.of(
            TeamSectionName.INFO,
            TeamSectionName.MEMBERS
    );

    private String language;
    private String memberName;
    private Object teamBackend;
    private String teamWorkspaceMount;
    private String teamWorkspacePath;
    private SystemPromptBuilder systemPromptBuilder;
    private List<PromptSection> staticSections;
    private MtimeSectionCache infoCache;
    private MtimeSectionCache membersCache;

    /**
     * Create a TeamRail with full configuration.
     */
    public TeamRail(
            TeamRole role,
            String persona,
            String memberName,
            String lifecycle,
            String teammateMode,
            String language,
            String teamMode,
            String basePrompt,
            String teamWorkspaceMount,
            String teamWorkspacePath,
            Object teamBackend
    ) {
        super();
        this.language = language != null ? language : "cn";
        this.memberName = memberName;
        this.teamBackend = teamBackend;
        this.teamWorkspaceMount = teamWorkspaceMount;
        this.teamWorkspacePath = teamWorkspacePath;
        this.systemPromptBuilder = null;

        // Build static sections
        List<String> humanNames = new ArrayList<>();
        this.staticSections = buildStaticSections(
                role, persona, memberName, lifecycle, teammateMode,
                teamMode, basePrompt, humanNames
        );

        // Initialize caches (null if no backend)
        this.infoCache = null;
        this.membersCache = null;
    }

    // -- Lifecycle hooks ------------------------------------------------------

    @Override
    public void init(Object agent) {
        super.init(agent);
        // Cache the agent's shared prompt builder
        this.systemPromptBuilder = null; // Stub: would get from agent
    }

    @Override
    public void uninit(Object agent) {
        if (systemPromptBuilder != null) {
            for (PromptSection section : staticSections) {
                systemPromptBuilder.removeSection(section.getName());
            }
            for (String name : DYNAMIC_SECTION_NAMES) {
                systemPromptBuilder.removeSection(name);
            }
        }
        systemPromptBuilder = null;
        super.uninit(agent);
    }

    /**
     * Inject static sections + refresh dynamic ones before each call.
     */
    public CompletableFuture<Void> beforeModelCall(Object ctx) {
        if (systemPromptBuilder == null) {
            return CompletableFuture.completedFuture(null);
        }

        for (PromptSection section : staticSections) {
            systemPromptBuilder.addSection(section);
        }

        // Dynamic section refresh would go here
        return CompletableFuture.completedFuture(null);
    }

    // -- Internal -------------------------------------------------------------

    private List<PromptSection> buildStaticSections(
            TeamRole role,
            String persona,
            String memberName,
            String lifecycle,
            String teammateMode,
            String teamMode,
            String basePrompt,
            List<String> humanAgentNames
    ) {
        List<PromptSection> sections = new ArrayList<>();

        PromptSection roleSection = buildTeamRoleSection(role, memberName, teammateMode, language);
        if (roleSection != null) sections.add(roleSection);

        PromptSection hittSection = buildTeamHittSection(role, humanAgentNames, language, memberName);
        if (hittSection != null) sections.add(hittSection);

        PromptSection workflowSection = buildTeamWorkflowSection(role, teamMode, language);
        if (workflowSection != null) sections.add(workflowSection);

        PromptSection lifecycleSection = buildTeamLifecycleSection(role, lifecycle, language);
        if (lifecycleSection != null) sections.add(lifecycleSection);

        PromptSection personaSection = buildTeamPersonaSection(persona, language);
        if (personaSection != null) sections.add(personaSection);

        PromptSection extraSection = buildTeamExtraSection(basePrompt, language);
        if (extraSection != null) sections.add(extraSection);

        return sections;
    }

    // -- Section Builders -----------------------------------------------------

    /**
     * Build the role + member name section.
     */
    public static PromptSection buildTeamRoleSection(
            TeamRole role,
            String memberName,
            String teammateMode,
            String language
    ) {
        Map<String, String> labels = getLabels(language);
        String roleHeading = labels.getOrDefault("role_heading", "# Team Role");
        String memberLine = memberName != null
                ? labels.getOrDefault("member_name_line", "Your member_name") + ": " + memberName + "\n\n"
                : "";

        boolean isPlanMode = "plan_mode".equals(teammateMode);
        String modeLabelKey;
        if (role == TeamRole.LEADER) {
            modeLabelKey = isPlanMode ? "leader_mode_plan" : "leader_mode_build";
        } else {
            modeLabelKey = isPlanMode ? "teammate_mode_plan" : "teammate_mode_build";
        }
        String modeLine = labels.getOrDefault(modeLabelKey, "") + "\n\n";

        // Stub: would load actual policy template
        String roleText = "Role policy placeholder";
        String body = roleHeading + "\n\n" + memberLine + modeLine + roleText + "\n";

        return new PromptSection(
                TeamSectionName.ROLE,
                Map.of(language, body),
                11
        );
    }

    /**
     * Build the workflow section (LEADER only).
     */
    public static PromptSection buildTeamWorkflowSection(
            TeamRole role,
            String teamMode,
            String language
    ) {
        if (role != TeamRole.LEADER) {
            return null;
        }
        Map<String, String> labels = getLabels(language);
        String workflowHeading = labels.getOrDefault("workflow_heading", "# Workflow");
        String workflowText = "Workflow placeholder";
        String body = workflowHeading + "\n\n" + workflowText + "\n";

        return new PromptSection(
                TeamSectionName.WORKFLOW,
                Map.of(language, body),
                13
        );
    }

    /**
     * Build the team lifecycle section (LEADER only).
     */
    public static PromptSection buildTeamLifecycleSection(
            TeamRole role,
            String lifecycle,
            String language
    ) {
        if (role != TeamRole.LEADER) {
            return null;
        }
        Map<String, String> labels = getLabels(language);
        String lifecycleHeading = labels.getOrDefault("lifecycle_heading", "# Team Lifecycle");
        String lifecycleText = "Lifecycle placeholder";
        String body = lifecycleHeading + "\n\n" + lifecycleText + "\n";

        return new PromptSection(
                TeamSectionName.LIFECYCLE,
                Map.of(language, body),
                14
        );
    }

    /**
     * Build the persona section.
     */
    public static PromptSection buildTeamPersonaSection(String persona, String language) {
        if (persona == null || persona.isEmpty()) {
            return null;
        }
        Map<String, String> labels = getLabels(language);
        String personaHeading = labels.getOrDefault("persona_heading", "# Current Persona");
        String body = personaHeading + "\n\n" + persona + "\n";

        return new PromptSection(
                TeamSectionName.PERSONA,
                Map.of(language, body),
                15
        );
    }

    /**
     * Build the user-supplied extra instructions section.
     */
    public static PromptSection buildTeamExtraSection(String basePrompt, String language) {
        if (basePrompt == null || basePrompt.trim().isEmpty()) {
            return null;
        }
        String body = basePrompt.trim() + "\n";

        return new PromptSection(
                TeamSectionName.EXTRA,
                Map.of(language, body),
                16
        );
    }

    /**
     * Build the HITT collaboration-rules section.
     */
    public static PromptSection buildTeamHittSection(
            TeamRole role,
            List<String> humanAgentNames,
            String language,
            String selfMemberName
    ) {
        if (humanAgentNames == null || humanAgentNames.isEmpty()) {
            return null;
        }
        String body = "HITT section placeholder";
        return new PromptSection(
                TeamSectionName.HITT,
                Map.of(language, body),
                12
        );
    }

    /**
     * Build the team metadata section.
     */
    public static PromptSection buildTeamInfoSection(
            Map<String, Object> teamInfo,
            String teamWorkspaceMount,
            String teamWorkspacePath,
            String language
    ) {
        Map<String, String> labels = getLabels(language);
        String infoHeading = labels.getOrDefault("info_heading", "# Team Info");

        String teamName = teamInfo != null ? (String) teamInfo.get("team_name") : null;
        String displayName = teamInfo != null ? (String) teamInfo.get("display_name") : null;
        String desc = teamInfo != null ? (String) teamInfo.get("desc") : null;
        String mount = teamWorkspaceMount != null ? teamWorkspaceMount.trim() : "";

        if (teamName == null && displayName == null && desc == null && mount.isEmpty()) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        lines.add(infoHeading);
        lines.add("");
        if (teamName != null) {
            lines.add("- " + labels.getOrDefault("team_name_label", "team_name") + ": " + teamName);
        }
        if (displayName != null) {
            lines.add("- " + labels.getOrDefault("display_name_label", "display_name") + ": " + displayName);
        }
        if (desc != null) {
            lines.add("- " + labels.getOrDefault("team_desc", "Team Goal") + ": " + desc);
        }
        if (!mount.isEmpty()) {
            lines.add("- " + labels.getOrDefault("team_workspace", "Team Shared Workspace") + ": `" + mount + "`");
            if (teamWorkspacePath != null) {
                lines.add("  - " + labels.getOrDefault("team_workspace_abs", "Absolute path") + ": `" + teamWorkspacePath + "`");
            }
        }
        String body = String.join("\n", lines) + "\n";

        return new PromptSection(
                TeamSectionName.INFO,
                Map.of(language, body),
                65
        );
    }

    /**
     * Build the team relationships section.
     */
    public static PromptSection buildTeamMembersSection(
            List<Map<String, String>> teamMembers,
            String selfMemberName,
            String language
    ) {
        if (teamMembers == null || teamMembers.isEmpty()) {
            return null;
        }
        Map<String, String> labels = getLabels(language);
        String membersHeading = labels.getOrDefault("members_heading", "# Relationships");

        List<String> rows = new ArrayList<>();
        for (Map<String, String> member : teamMembers) {
            String memberName = member.getOrDefault("member_name", "");
            if (memberName.equals(selfMemberName)) {
                continue;
            }
            String displayName = member.getOrDefault("display_name", "unknown");
            String desc = member.getOrDefault("desc", "");
            String line = "- member_name=" + memberName + " display_name=" + displayName;
            if (!desc.isEmpty()) {
                line += " :: " + desc;
            }
            rows.add(line);
        }
        if (rows.isEmpty()) {
            return null;
        }
        String body = membersHeading + "\n\n" + String.join("\n", rows) + "\n";

        return new PromptSection(
                TeamSectionName.MEMBERS,
                Map.of(language, body),
                66
        );
    }

    // -- Labels ---------------------------------------------------------------

    private static final Map<String, Map<String, String>> LABELS = new HashMap<>();

    static {
        Map<String, String> cnLabels = new HashMap<>();
        cnLabels.put("member_name_line", "你的 member_name");
        cnLabels.put("role_heading", "# 团队角色");
        cnLabels.put("workflow_heading", "# 工作流程");
        cnLabels.put("lifecycle_heading", "# 团队生命周期");
        cnLabels.put("persona_heading", "# 当前人设");
        cnLabels.put("info_heading", "# 团队信息");
        cnLabels.put("team_name_label", "team_name（团队唯一标识）");
        cnLabels.put("display_name_label", "display_name（团队展示名）");
        cnLabels.put("team_desc", "团队目标与指令");
        cnLabels.put("team_workspace", "团队共享工作空间");
        cnLabels.put("team_workspace_abs", "绝对路径");
        cnLabels.put("members_heading", "# 成员关系");
        LABELS.put("cn", cnLabels);

        Map<String, String> enLabels = new HashMap<>();
        enLabels.put("member_name_line", "Your member_name");
        enLabels.put("role_heading", "# Team Role");
        enLabels.put("workflow_heading", "# Workflow");
        enLabels.put("lifecycle_heading", "# Team Lifecycle");
        enLabels.put("persona_heading", "# Current Persona");
        enLabels.put("info_heading", "# Team Info");
        enLabels.put("team_name_label", "team_name (unique identifier)");
        enLabels.put("display_name_label", "display_name (human-readable label)");
        enLabels.put("team_desc", "Team Goal & Directives");
        enLabels.put("team_workspace", "Team Shared Workspace");
        enLabels.put("team_workspace_abs", "Absolute path");
        enLabels.put("members_heading", "# Relationships");
        LABELS.put("en", enLabels);
    }

    private static Map<String, String> getLabels(String language) {
        return LABELS.getOrDefault(language, LABELS.get("cn"));
    }

    // -- Inner class for cache ------------------------------------------------

    /**
     * Stub for MtimeSectionCache.
     */
    private static class MtimeSectionCache {
        public CompletableFuture<PromptSection> refresh() {
            return CompletableFuture.completedFuture(null);
        }
    }

    // -- Getters --------------------------------------------------------------

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }
}