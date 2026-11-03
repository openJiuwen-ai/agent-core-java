/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.team.TeamRole;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AgentTeamPolicy.
 * 
 * @since 0.1.7
 */
public final class AgentTeamPolicy {
    private static final String PROMPTS_ROOT = "openjiuwen/agent_teams/agent/prompts/";
    private static final String DEFAULT_LANGUAGE = "cn";

    /**
     * Map.of.
     * 
     * @param "成员关系" "成员关系"
     * @since 0.1.7
     */
    private static final Map<String, Map<String, String>> I18N_LABELS = Map.of("cn",
            Map.of("persona", "当前人设", "member_name_label", "你的成员名（member_name）", "team_info_heading", "团队信息",
                    "team_name_label", "团队名（team_name）", "display_name_label", "显示名（display_name）", "team_desc",
                    "团队目标与指令", "relationships_heading", "成员关系"),
            "en",
            Map.of("persona", "Current Persona", "member_name_label", "Your member_name", "team_info_heading",
                    "Team Info", "team_name_label", "team_name", "display_name_label", "display_name", "team_desc",
                    "Team Goal & Directives", "relationships_heading", "Relationships"));

    /**
     * Map.of.
     * 
     * @since 0.1.7
     */
    private static final Map<String, String> WORKFLOW_TEMPLATES = Map.of("default", "leader_workflow", "predefined",
            "leader_workflow_predefined", "hybrid", "leader_workflow_hybrid");

    /**
     * AgentTeamPolicy.
     * 
     * @since 0.1.7
     */
    private AgentTeamPolicy() {
    }

    /**
     * rolePolicy.
     * 
     * @param role role
     * @return the result
     * @since 0.1.7
     */
    public static String rolePolicy(TeamRole role) {
        return rolePolicy(role, DEFAULT_LANGUAGE);
    }

    /**
     * rolePolicy.
     * 
     * @param role role
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static String rolePolicy(TeamRole role, String language) {
        return loadTemplate(role == TeamRole.LEADER ? "leader_policy" : "teammate_policy", normalizeLanguage(language));
    }

    /**
     * buildSystemPrompt.
     * 
     * @param role role
     * @param persona persona
     * @return the result
     * @since 0.1.7
     */
    public static String buildSystemPrompt(TeamRole role, String persona) {
        return builder(role, persona).build();
    }

    /**
     * builder.
     * 
     * @param role role
     * @param persona persona
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder(TeamRole role, String persona) {
        return new Builder(role, persona);
    }

    /**
     * buildTeamPolicy.
     * 
     * @param builder builder
     * @return the result
     * @since 0.1.7
     */
    private static String buildTeamPolicy(Builder builder) {
        String language = normalizeLanguage(builder.language);
        Map<String, String> labels = I18N_LABELS.getOrDefault(language, I18N_LABELS.get(DEFAULT_LANGUAGE));
        String policyName = builder.role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        String rolePolicyText = loadTemplate(policyName, language);

        String memberNameSection =
            isBlank(builder.memberName) ? "" : labels.get("member_name_label") + ": " + builder.memberName + "\n";

        String workflowSection = "";
        if (builder.role == TeamRole.LEADER) {
            String workflowName = WORKFLOW_TEMPLATES.getOrDefault(builder.teamMode, "leader_workflow");
            workflowSection = loadTemplate(workflowName, language);
        }

        String lifecycleSection = "";
        if (builder.role == TeamRole.LEADER) {
            String lifecycleName =
                Objects.equals(builder.lifecycle, "persistent") ? "lifecycle_persistent" : "lifecycle_temporary";
            lifecycleSection = loadTemplate(lifecycleName, language);
        }

        String template = loadSharedTemplate("system_prompt");
        return template.replace("{{member_name_section}}", memberNameSection).replace("{{role_policy}}", rolePolicyText)
                .replace("{{workflow_section}}", workflowSection).replace("{{lifecycle_section}}", lifecycleSection)
                .replace("{{persona_label}}", labels.get("persona")).replace("{{persona}}", builder.persona)
                .replace("{{team_info_section}}", formatTeamInfo(builder.teamInfo, labels))
                .replace("{{team_members_section}}", formatTeamMembers(builder.teamMembers, labels, builder.memberName))
                .replace("{{base_prompt_section}}", isBlank(builder.basePrompt) ? "" : "\n" + builder.basePrompt);
    }

    /**
     * formatTeamInfo.
     * 
     * @param teamInfo teamInfo
     * @param labels labels
     * @return the result
     * @since 0.1.7
     */
    private static String formatTeamInfo(Map<String, ?> teamInfo, Map<String, String> labels) {
        if (teamInfo == null || teamInfo.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("\n## " + labels.get("team_info_heading"));
        appendIfPresent(lines, labels.get("team_name_label"), teamInfo.get("team_name"));
        appendIfPresent(lines, labels.get("display_name_label"), teamInfo.get("display_name"));
        appendIfPresent(lines, labels.get("team_desc"), teamInfo.get("desc"));
        return String.join("\n", lines);
    }

    /**
     * formatTeamMembers.
     * 
     * @param teamMembers teamMembers
     * @param labels labels
     * @param selfMemberName selfMemberName
     * @return the result
     * @since 0.1.7
     */
    private static String formatTeamMembers(List<Map<String, String>> teamMembers, Map<String, String> labels,
            String selfMemberName) {
        if (teamMembers == null || teamMembers.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add("\n## " + labels.get("relationships_heading"));
        for (Map<String, String> member : teamMembers) {
            String memberName = member.getOrDefault("member_name", "");
            if (Objects.equals(memberName, selfMemberName)) {
                continue;
            }
            String displayName = member.getOrDefault("display_name", "unknown");
            String desc = member.getOrDefault("desc", "");
            String line = "- member_name=" + memberName + " display_name=" + displayName;
            if (!desc.isBlank()) {
                line += " :: " + desc;
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    /**
     * appendIfPresent.
     * 
     * @param lines lines
     * @param label label
     * @param value value
     * @since 0.1.7
     */
    private static void appendIfPresent(List<String> lines, String label, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        lines.add("- " + label + ": " + value);
    }

    /**
     * loadTemplate.
     * 
     * @param name name
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static String loadTemplate(String name, String language) {
        return loadResource(PROMPTS_ROOT + language + "/" + name + ".md");
    }

    static String loadTemplateForRail(String name, String language) {
        return loadTemplate(name, normalizeLanguage(language));
    }

    /**
     * loadSharedTemplate.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    private static String loadSharedTemplate(String name) {
        return loadResource(PROMPTS_ROOT + name + ".md");
    }

    /**
     * loadResource.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static String loadResource(String path) {
        ClassLoader classLoader = AgentTeamPolicy.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing agent team prompt resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load agent team prompt resource: " + path, e);
        }
    }

    /**
     * normalizeLanguage.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeLanguage(String language) {
        return isBlank(language) ? DEFAULT_LANGUAGE : language;
    }

    /**
     * isBlank.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private final TeamRole role;
        private final String persona;
        private String basePrompt;
        private Map<String, ?> teamInfo;
        private List<Map<String, String>> teamMembers;
        private String memberName;
        private String lifecycle = "temporary";
        private String language = DEFAULT_LANGUAGE;
        private String teamMode = "default";

        /**
         * Builder.
         * 
         * @param role role
         * @param persona persona
         * @since 0.1.7
         */
        private Builder(TeamRole role, String persona) {
            this.role = Objects.requireNonNull(role, "role is required");
            this.persona = Objects.requireNonNullElse(persona, "");
        }

        /**
         * basePrompt.
         * 
         * @param basePrompt basePrompt
         * @return the result
         * @since 0.1.7
         */
        public Builder basePrompt(String basePrompt) {
            this.basePrompt = basePrompt;
            return this;
        }

        /**
         * teamInfo.
         * 
         * @param teamInfo teamInfo
         * @return the result
         * @since 0.1.7
         */
        public Builder teamInfo(Map<String, ?> teamInfo) {
            this.teamInfo = teamInfo != null ? new LinkedHashMap<>(teamInfo) : null;
            return this;
        }

        /**
         * teamMembers.
         * 
         * @param teamMembers teamMembers
         * @return the result
         * @since 0.1.7
         */
        public Builder teamMembers(List<Map<String, String>> teamMembers) {
            this.teamMembers = teamMembers != null ? List.copyOf(teamMembers) : null;
            return this;
        }

        /**
         * memberName.
         * 
         * @param memberName memberName
         * @return the result
         * @since 0.1.7
         */
        public Builder memberName(String memberName) {
            this.memberName = memberName;
            return this;
        }

        /**
         * lifecycle.
         * 
         * @param lifecycle lifecycle
         * @return the result
         * @since 0.1.7
         */
        public Builder lifecycle(String lifecycle) {
            this.lifecycle = lifecycle;
            return this;
        }

        /**
         * language.
         * 
         * @param language language
         * @return the result
         * @since 0.1.7
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * teamMode.
         * 
         * @param teamMode teamMode
         * @return the result
         * @since 0.1.7
         */
        public Builder teamMode(String teamMode) {
            this.teamMode = teamMode;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public String build() {
            return buildTeamPolicy(this);
        }
    }
}
