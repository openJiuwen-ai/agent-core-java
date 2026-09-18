/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.prompts.MtimeSectionCache;
import com.openjiuwen.agent_teams.prompts.TeamPromptSections;
import com.openjiuwen.agent_teams.prompts.TeamPromptSections.TeamSectionName;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Injects team-specific prompt sections into the system prompt builder.
 *
 * <p>Mirrors Python's {@code TeamPolicyRail} in
 * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
 */
public class TeamPolicyRail {

    public static final int PRIORITY = 12;

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final List<String> DYNAMIC_SECTION_NAMES = List.of(
            TeamSectionName.HITT,
            TeamSectionName.INFO,
            TeamSectionName.MEMBERS
    );

    private final TeamRole role;
    private final String language;
    private final String memberName;
    private final TeamBackend teamBackend;
    private final String teamWorkspaceMount;
    private final String teamWorkspacePath;
    private final boolean exposeHumanAgentsToTeammates;
    private final List<PromptSection> staticSections;
    private final MtimeSectionCache infoCache;

    private SystemPromptBuilder systemPromptBuilder;
    private long membersCachedMtime;
    private boolean membersCacheInitialized;
    private PromptSection cachedHittSection;
    private PromptSection cachedMembersSection;

    public TeamPolicyRail(Config config) {
        Config effectiveConfig = Objects.requireNonNull(config, "config");
        this.role = Objects.requireNonNull(effectiveConfig.role(), "role");
        this.language = defaultString(effectiveConfig.language(), "cn");
        this.memberName = effectiveConfig.memberName();
        this.teamBackend = effectiveConfig.teamBackend();
        this.teamWorkspaceMount = effectiveConfig.teamWorkspaceMount();
        this.teamWorkspacePath = effectiveConfig.teamWorkspacePath();
        this.exposeHumanAgentsToTeammates = effectiveConfig.exposeHumanAgentsToTeammates();
        Collection<String> bridgeNames = teamBackend == null ? List.of() : sortedStrings(teamBackend.bridgeAgentNames());
        this.staticSections = buildStaticSections(
                role,
                defaultString(effectiveConfig.persona(), ""),
                memberName,
                defaultString(effectiveConfig.lifecycle(), "temporary"),
                defaultString(effectiveConfig.teammateMode(), "build_mode"),
                defaultString(effectiveConfig.teamMode(), "default"),
                effectiveConfig.basePrompt(),
                bridgeNames
        );
        this.infoCache = teamBackend == null
                ? null
                : new MtimeSectionCache(teamBackend::getTeamUpdatedAt, this::fetchAndBuildInfoSection);
    }

    public int getPriority() {
        return PRIORITY;
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public List<PromptSection> getStaticSections() {
        return List.copyOf(staticSections);
    }

    public void init(PolicyAgent agent) {
        systemPromptBuilder = agent == null ? null : agent.getSystemPromptBuilder();
    }

    public void uninit(PolicyAgent ignoredAgent) {
        if (systemPromptBuilder != null) {
            for (PromptSection section : staticSections) {
                systemPromptBuilder.removeSection(section.getName());
            }
            for (String name : DYNAMIC_SECTION_NAMES) {
                systemPromptBuilder.removeSection(name);
            }
        }
        systemPromptBuilder = null;
    }

    public CompletionStage<Void> beforeModelCall() {
        return beforeModelCall(new PolicyCallbackContext());
    }

    public CompletionStage<Void> beforeModelCall(PolicyCallbackContext ignoredContext) {
        if (systemPromptBuilder == null) {
            return CompletableFuture.completedFuture(null);
        }

        for (PromptSection section : staticSections) {
            systemPromptBuilder.addSection(section);
        }

        if (teamBackend == null) {
            return CompletableFuture.completedFuture(null);
        }

        return refreshMemberSections()
                .thenCompose(memberSections -> {
                    addIfPresent(memberSections.hittSection());
                    addIfPresent(memberSections.membersSection());
                    if (infoCache == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return infoCache.refresh().thenAccept(this::addIfPresent);
                });
    }

    private List<PromptSection> buildStaticSections(
            TeamRole role,
            String persona,
            String memberName,
            String lifecycle,
            String teammateMode,
            String teamMode,
            String basePrompt,
            Collection<String> bridgeAgentNames
    ) {
        List<PromptSection> sections = TeamPromptSections.buildTeamStaticSections(
                role,
                persona,
                memberName,
                lifecycle,
                teammateMode,
                teamMode,
                basePrompt,
                language,
                null,
                false,
                bridgeAgentNames
        );
        TEAM_LOGGER.info(
                "[{}] TeamPolicyRail static sections: section_names={}",
                defaultString(memberName, "?"),
                sections.stream().map(PromptSection::getName).toList()
        );
        return List.copyOf(sections);
    }

    private CompletionStage<MemberSections> refreshMemberSections() {
        return teamBackend.getMembersMaxUpdatedAt().thenCompose(mtime -> {
            long normalizedMtime = mtime == null ? 0L : mtime;
            if (membersCacheInitialized && normalizedMtime == membersCachedMtime) {
                return CompletableFuture.completedFuture(new MemberSections(cachedHittSection, cachedMembersSection));
            }

            return fetchAndBuildHittSection()
                    .thenCompose(hittSection -> fetchAndBuildMembersSection()
                            .thenApply(membersSection -> {
                                cachedHittSection = hittSection;
                                cachedMembersSection = membersSection;
                                membersCachedMtime = normalizedMtime;
                                membersCacheInitialized = true;
                                return new MemberSections(cachedHittSection, cachedMembersSection);
                            }));
        });
    }

    private CompletionStage<PromptSection> fetchAndBuildHittSection() {
        return teamBackend.humanAgentNames().thenApply(humanNames -> {
            List<String> safeHumanNames = safeStrings(humanNames);
            TEAM_LOGGER.info(
                    "[{}] HITT section refresh: human_agent_names={}",
                    defaultString(memberName, "?"),
                    safeHumanNames
            );
            return TeamPromptSections.buildTeamHittSection(
                        role,
                        safeHumanNames,
                        language,
                        memberName,
                        exposeHumanAgentsToTeammates
                ).orElse(null);
        });
    }

    private CompletionStage<PromptSection> fetchAndBuildInfoSection() {
        return teamBackend.getTeamInfo().thenApply(info -> {
            Map<String, String> infoMap = null;
            if (info != null) {
                infoMap = new LinkedHashMap<>();
                infoMap.put("team_name", info.teamName());
                infoMap.put("display_name", info.displayName());
                infoMap.put("desc", defaultString(info.desc(), ""));
            }
            return TeamPromptSections.buildTeamInfoSection(
                    infoMap,
                    teamWorkspaceMount,
                    teamWorkspacePath,
                    language
            ).orElse(null);
        });
    }

    private CompletionStage<PromptSection> fetchAndBuildMembersSection() {
        return teamBackend.listMembers().thenApply(members -> {
            List<Map<String, String>> memberRows = null;
            if (members != null && !members.isEmpty()) {
                memberRows = new ArrayList<>();
                for (TeamMemberSnapshot member : members) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("member_name", defaultString(member.memberName(), ""));
                    row.put("display_name", defaultString(member.displayName(), "unknown"));
                    row.put("desc", defaultString(member.desc(), ""));
                    memberRows.add(row);
                }
            }
            return TeamPromptSections.buildTeamMembersSection(memberRows, memberName, language).orElse(null);
        });
    }

    private void addIfPresent(PromptSection section) {
        if (section != null && systemPromptBuilder != null) {
            systemPromptBuilder.addSection(section);
        }
    }

    private static List<String> sortedStrings(Collection<String> values) {
        return safeStrings(values).stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static List<String> safeStrings(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    /**
     * Constructor options for {@link TeamPolicyRail}.
     *
     * <p>Mirrors Python's keyword-only {@code TeamPolicyRail.__init__} parameters in
     * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
     */
    public record Config(
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
            TeamBackend teamBackend,
            boolean exposeHumanAgentsToTeammates
    ) {
        public Config {
            role = role == null ? TeamRole.TEAMMATE : role;
            persona = persona == null ? "" : persona;
            lifecycle = lifecycle == null ? "temporary" : lifecycle;
            teammateMode = teammateMode == null ? "build_mode" : teammateMode;
            language = language == null ? "cn" : language;
            teamMode = teamMode == null ? "default" : teamMode;
        }

        public static Config of(TeamRole role, String persona) {
            return new Config(role, persona, null, null, null, null, null, null, null, null, null, false);
        }
    }

    /**
     * Minimal agent view needed to cache the shared prompt builder.
     *
     * <p>Mirrors Python's {@code getattr(agent, "system_prompt_builder", None)} access in
     * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
     */
    public interface PolicyAgent {
        SystemPromptBuilder getSystemPromptBuilder();
    }

    /**
     * Minimal team backend behavior used by the policy rail.
     *
     * <p>Mirrors Python's {@code TeamBackend} calls in
     * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
     */
    public interface TeamBackend {
        Collection<String> bridgeAgentNames();

        CompletionStage<Long> getTeamUpdatedAt();

        CompletionStage<TeamInfoSnapshot> getTeamInfo();

        CompletionStage<Long> getMembersMaxUpdatedAt();

        CompletionStage<List<String>> humanAgentNames();

        CompletionStage<List<TeamMemberSnapshot>> listMembers();
    }

    /**
     * Team metadata snapshot used to build the dynamic info section.
     *
     * <p>Mirrors Python's team info object fields in
     * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
     */
    public record TeamInfoSnapshot(String teamName, String displayName, String desc) {
    }

    /**
     * Team member snapshot used to build the dynamic members section.
     *
     * <p>Mirrors Python's member object fields in
     * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
     */
    public record TeamMemberSnapshot(String memberName, String displayName, String desc) {
    }

    /**
     * Minimal callback context marker for the before-model-call lifecycle.
     *
     * <p>Mirrors Python's {@code AgentCallbackContext} parameter in
     * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
     */
    public static final class PolicyCallbackContext {
    }

    /**
     * Pair of dynamic member-derived sections refreshed from one members mtime probe.
     *
     * <p>Mirrors Python's {@code _refresh_member_sections} tuple return in
     * {@code openjiuwen/agent_teams/rails/team_policy_rail.py}.</p>
     */
    private record MemberSections(PromptSection hittSection, PromptSection membersSection) {
    }
}
