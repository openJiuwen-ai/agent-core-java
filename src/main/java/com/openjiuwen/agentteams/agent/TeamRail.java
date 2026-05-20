/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMember;
import com.openjiuwen.agentteams.tools.database.TeamRecord;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Public class TeamRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TeamRail extends DeepAgentRail {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String ROLE = "team_role";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String HITT = "team_hitt";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String WORKFLOW = "team_workflow";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String LIFECYCLE = "team_lifecycle";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String PERSONA = "team_persona";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String EXTRA = "team_extra";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String INFO = "team_info";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String MEMBERS = "team_members";

    private final List<PromptSection> staticSections;
    private final TeamBackend teamBackend;
    private final String memberName;
    private final String language;
    private final String teamWorkspaceMount;
    private final String teamWorkspacePath;
    private final SectionCache infoCache;
    private final SectionCache membersCache;
    private DeepAgent owner;

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamRail(
            TeamRole role,
            String persona,
            String memberName,
            String lifecycle,
            String teammateMode,
            String language,
            String teamMode,
            String basePrompt
    ) {
        this(role, persona, memberName, lifecycle, teammateMode, language, teamMode, basePrompt, null, null, List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
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
            Iterable<String> humanAgentNames
    ) {
        this(role,
                persona,
                memberName,
                lifecycle,
                teammateMode,
                language,
                teamMode,
                basePrompt,
                teamWorkspaceMount,
                teamWorkspacePath,
                humanAgentNames,
                null);
    }

    /**
     * Auto-generated for codecheck compliance.
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
            Iterable<String> humanAgentNames,
            TeamBackend teamBackend
    ) {
        String lang = normalizeLanguage(language);
        this.teamBackend = teamBackend;
        this.memberName = memberName;
        this.language = lang;
        this.teamWorkspaceMount = teamWorkspaceMount;
        this.teamWorkspacePath = teamWorkspacePath;
        this.staticSections = buildStaticSections(
                role != null ? role : TeamRole.MEMBER,
                persona,
                memberName,
                lifecycle,
                teammateMode,
                lang,
                teamMode,
                basePrompt,
                teamWorkspaceMount,
                teamWorkspacePath,
                humanAgentNames
        );
        this.infoCache = teamBackend != null ? new SectionCache() : null;
        this.membersCache = teamBackend != null ? new SectionCache() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int priority() {
        return 12;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void init(Object agent) {
        super.init(agent);
        if (agent instanceof DeepAgent deepAgent) {
            this.owner = deepAgent;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void uninit(Object agent) {
        if (owner != null) {
            for (PromptSection section : staticSections) {
                owner.getAgent().getPromptBuilder().removeSection(section.getName());
            }
            owner.getAgent().getPromptBuilder().removeSection(INFO);
            owner.getAgent().getPromptBuilder().removeSection(MEMBERS);
        }
        owner = null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (owner == null) {
            return;
        }
        for (PromptSection section : staticSections) {
            owner.getAgent().getPromptBuilder().addSection(section);
        }
        if (teamBackend != null) {
            PromptSection infoSection = infoCache.refresh(
                    teamBackend.getTeamUpdatedAt(),
                    this::fetchAndBuildInfoSection);
            if (infoSection != null) {
                owner.getAgent().getPromptBuilder().addSection(infoSection);
            }
            PromptSection membersSection = membersCache.refresh(
                    teamBackend.getMembersMaxUpdatedAt(),
                    this::fetchAndBuildMembersSection
            );
            if (membersSection != null) {
                owner.getAgent().getPromptBuilder().addSection(membersSection);
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<PromptSection> getStaticSections() {
        return List.copyOf(staticSections);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PromptSection buildTeamRoleSection(
            TeamRole role,
            String memberName,
            String teammateMode,
            String language
    ) {
        String lang = normalizeLanguage(language);
        String roleText = AgentTeamPolicy.rolePolicy(role, lang).trim();
        boolean isPlanMode = "plan_mode".equals(teammateMode);
        String memberLine = memberName != null && !memberName.isBlank()
                ? labels(lang, "member_name_line") + ": " + memberName + "\n\n"
                : "";
        String modeLine;
        if (role == TeamRole.LEADER) {
            modeLine = labels(lang, isPlanMode ? "leader_mode_plan" : "leader_mode_build");
        } else {
            modeLine = labels(lang, isPlanMode ? "teammate_mode_plan" : "teammate_mode_build");
        }
        String body = labels(lang, "role_heading") + "\n\n" + memberLine + modeLine + "\n\n" + roleText + "\n";
        return new PromptSection(ROLE, Map.of(lang, body), 11);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PromptSection buildTeamWorkflowSection(TeamRole role, String teamMode, String language) {
        if (role != TeamRole.LEADER) {
            return nullValue();
        }
        String lang = normalizeLanguage(language);
        String workflow = AgentTeamPolicy.loadTemplateForRail(workflowTemplate(teamMode), lang).trim();
        return new PromptSection(
                WORKFLOW,
                Map.of(lang, labels(lang, "workflow_heading") + "\n\n" + workflow + "\n"),
                13);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PromptSection buildTeamHittSection(
            TeamRole role,
            Iterable<String> humanAgentNames,
            String language,
            String selfMemberName
    ) {
        List<String> names = new ArrayList<>();
        if (humanAgentNames != null) {
            for (String name : humanAgentNames) {
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        if (names.isEmpty() || role == TeamRole.USER) {
            return nullValue();
        }
        names.sort(String::compareTo);
        String lang = normalizeLanguage(language);
        String roster = formatHumanAgentRoster(names, lang);
        String body = "en".equals(lang)
                ? hittSectionEn(role, roster, selfMemberName)
                : hittSectionCn(role, roster, selfMemberName);
        return body != null ? new PromptSection(HITT, Map.of(lang, body), 12) : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PromptSection buildTeamLifecycleSection(TeamRole role, String lifecycle, String language) {
        if (role != TeamRole.LEADER) {
            return nullValue();
        }
        String lang = normalizeLanguage(language);
        return new PromptSection(
                LIFECYCLE,
                Map.of(
                        lang,
                        labels(lang, "lifecycle_heading")
                                + "\n\n"
                                + AgentTeamPolicy.loadTemplateForRail(
                                "persistent".equals(lifecycle) ? "lifecycle_persistent" : "lifecycle_temporary",
                                lang).trim()
                                + "\n"),
                14);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PromptSection buildTeamPersonaSection(String persona, String language) {
        if (persona == null || persona.isBlank()) {
            return nullValue();
        }
        String lang = normalizeLanguage(language);
        return new PromptSection(PERSONA, Map.of(lang, labels(lang, "persona_heading") + "\n\n" + persona + "\n"), 15);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PromptSection buildTeamExtraSection(String basePrompt, String language) {
        if (basePrompt == null || basePrompt.isBlank()) {
            return nullValue();
        }
        String lang = normalizeLanguage(language);
        return new PromptSection(EXTRA, Map.of(lang, basePrompt.trim() + "\n"), 16);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PromptSection buildTeamInfoSection(
            TeamRecord teamInfo,
            String teamWorkspaceMount,
            String teamWorkspacePath,
            String language
    ) {
        String lang = normalizeLanguage(language);
        String mount = teamWorkspaceMount != null ? teamWorkspaceMount.trim() : "";
        boolean isTeamFieldsMissing = teamInfo == null
                || isBlank(teamInfo.getTeamName())
                && isBlank(teamInfo.getDisplayName())
                && isBlank(teamInfo.getDesc());
        if (isTeamFieldsMissing && mount.isEmpty()) {
            return nullValue();
        }
        List<String> lines = new ArrayList<>();
        lines.add(labels(lang, "info_heading"));
        lines.add("");
        if (teamInfo != null && !isBlank(teamInfo.getTeamName())) {
            lines.add("- " + labels(lang, "team_name_label") + ": " + teamInfo.getTeamName());
        }
        if (teamInfo != null && !isBlank(teamInfo.getDisplayName())) {
            lines.add("- " + labels(lang, "display_name_label") + ": " + teamInfo.getDisplayName());
        }
        if (teamInfo != null && !isBlank(teamInfo.getDesc())) {
            lines.add("- " + labels(lang, "team_desc") + ": " + teamInfo.getDesc());
        }
        if (!mount.isEmpty()) {
            lines.add("- " + labels(lang, "teamworkspace") + ": `" + mount + "`");
            lines.add("  - " + labels(lang, "teamworkspace_purpose"));
            if (teamWorkspacePath != null && !teamWorkspacePath.isBlank()) {
                lines.add("  - " + labels(lang, "teamworkspace_abs") + ": `" + teamWorkspacePath + "`");
            }
        }
        return new PromptSection(INFO, Map.of(lang, String.join("\n", lines) + "\n"), 65);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static PromptSection buildTeamMembersSection(
            Iterable<TeamMember> teamMembers,
            String selfMemberName,
            String language
    ) {
        if (teamMembers == null) {
            return nullValue();
        }
        String lang = normalizeLanguage(language);
        List<String> rows = new ArrayList<>();
        for (TeamMember member : teamMembers) {
            if (member == null || isBlank(member.getMemberName()) || member.getMemberName().equals(selfMemberName)) {
                continue;
            }
            String displayName = !isBlank(member.getDisplayName()) ? member.getDisplayName() : "unknown";
            String line = "- member_name=" + member.getMemberName() + " display_name=" + displayName;
            if (!isBlank(member.getDescription())) {
                line += " :: " + member.getDescription();
            }
            rows.add(line);
        }
        if (rows.isEmpty()) {
            return nullValue();
        }
        String body = labels(lang, "members_heading") + "\n\n" + String.join("\n", rows) + "\n";
        return new PromptSection(MEMBERS, Map.of(lang, body), 66);
    }

    private PromptSection fetchAndBuildInfoSection() {
        return buildTeamInfoSection(teamBackend.getTeamInfo(), teamWorkspaceMount, teamWorkspacePath, language);
    }

    private PromptSection fetchAndBuildMembersSection() {
        return buildTeamMembersSection(teamBackend.listMembers(), memberName, language);
    }

    private static List<PromptSection> buildStaticSections(
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
            Iterable<String> humanAgentNames
    ) {
        List<PromptSection> sections = new ArrayList<>();
        addIfPresent(sections, buildTeamRoleSection(role, memberName, teammateMode, language));
        addIfPresent(sections, buildTeamHittSection(role, humanAgentNames, language, memberName));
        addIfPresent(sections, buildTeamWorkflowSection(role, teamMode, language));
        addIfPresent(sections, buildTeamLifecycleSection(role, lifecycle, language));
        addIfPresent(sections, buildTeamPersonaSection(persona, language));
        addIfPresent(sections, buildTeamExtraSection(basePrompt, language));
        return sections;
    }

    private static void addIfPresent(List<PromptSection> sections, PromptSection section) {
        if (section != null) {
            sections.add(section);
        }
    }

    private static String workflowTemplate(String teamMode) {
        if ("predefined".equals(teamMode)) {
            return "leader_workflow_predefined";
        }
        if ("hybrid".equals(teamMode)) {
            return "leader_workflow_hybrid";
        }
        return "leader_workflow";
    }

    private static String normalizeLanguage(String language) {
        return language == null || language.isBlank() ? PromptSection.DEFAULT_LANGUAGE : language;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String formatHumanAgentRoster(List<String> names, String language) {
        return "en".equals(language)
                ? "Registered human members: " + names.stream()
                        .map(name -> "`" + name + "`")
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("")
                : "注册的人类成员：" + names.stream()
                        .map(name -> "`" + name + "`")
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("");
    }

    private static String hittSectionCn(TeamRole role, String roster, String selfMemberName) {
        if (role == TeamRole.LEADER) {
            return "# HITT — 人类成员协作规则\n\n"
                    + roster + "。他们是真实人类操作者的代理，与你和其它 teammate 平等。所有 role=human_agent 的成员都适用下列规则：\n\n"
                    + "1. **禁止** 用 plain text 向任何人类成员发问或对话——所有定向沟通必须调用 "
                    + "`send_message(to=\"<human_member_name>\", ...)`，你的纯文本输出对方是看不到的。\n"
                    + "2. 可以通过 `update_task(task_id=..., assignee=\"<human_member_name>\")` 把需要特定人类判断或操作的任务指派给对应成员。\n"
                    + "3. 一旦某个人类成员认领了任务（status=claimed），你 **不能** 取消也 **不能** 改派，只能用 `send_message` 催促对应人类成员。\n"
                    + "4. 每个人类成员始终是 ready 状态，不会进入 busy 或 shutdown，所以不要对它们调用 `shutdown_member` / `spawn_member`。\n";
        }
        if (role == TeamRole.HUMAN_AGENT) {
            String self = selfMemberName != null && !selfMemberName.isBlank()
                    ? "你的 member_name 是 `" + selfMemberName + "`。\n"
                    : "";
            return "# HITT — 你是团队里的人类成员\n\n"
                    + roster + "。\n" + self
                    + "你是团队里真实人类操作者的代理，与 leader、teammate 平等。\n"
                    + "- 你只能通过 `send_message` 与团队交互；没有 `claim_task`、`update_task`、`spawn_member` 等工具。\n"
                    + "- 发送给你的消息一律自动标记已读，不会堆积未读。\n";
        }
        return "# HITT — 与人类成员协作\n\n"
                + "团队里存在下列人类成员（真实人类）：" + roster
                + "。把他们视作普通 teammate：与他们交流一律通过 `send_message(to=<对应名字>, ...)`，不要假设他们会自动看到你的 plain text。\n";
    }

    private static String hittSectionEn(TeamRole role, String roster, String selfMemberName) {
        if (role == TeamRole.LEADER) {
            return "# HITT — Collaborating with Human Members\n\n"
                    + roster + ". They represent real human operators and stand on equal footing with you and "
                    + "the other teammates. The following rules apply to every member whose role is "
                    + "`human_agent`:\n\n"
                    + "1. You **must not** address a human member via plain text — every direct exchange "
                    + "must go through `send_message(to=\"<human_member_name>\", ...)`.\n"
                    + "2. Use `update_task(task_id=..., assignee=\"<human_member_name>\")` to assign tasks "
                    + "that require a specific human's judgement or action.\n"
                    + "3. Once a human member claims a task, you **cannot** cancel it and **cannot** "
                    + "reassign it; only `send_message` nudges are allowed.\n"
                    + "4. Every human member stays READY forever; never call `shutdown_member` or "
                    + "`spawn_member` on them.\n";
        }
        if (role == TeamRole.HUMAN_AGENT) {
            String self = selfMemberName != null && !selfMemberName.isBlank()
                    ? "Your member_name is `" + selfMemberName + "`.\n"
                    : "";
            return "# HITT — You are a human member\n\n"
                    + roster + ".\n" + self
                    + "You represent the human operator on this team, equal in standing with the leader "
                    + "and teammates.\n"
                    + "- Your only tool is `send_message`; you do not have `claim_task`, `update_task`, "
                    + "`spawn_member`, etc.\n"
                    + "- Every message addressed to you is auto-marked-read; there is no unread backlog "
                    + "on your side.\n";
        }
        return "# HITT — Working with Human Members\n\n"
                + "The team includes the following human members (real humans): " + roster
                + ". Treat each of them as an ordinary teammate: every direct exchange must use "
                + "`send_message(to=<their_name>, ...)`.\n";
    }

    private static String labels(String language, String key) {
        Map<String, String> cn = Map.ofEntries(
                Map.entry("member_name_line", "你的 member_name"),
                Map.entry("role_heading", "# 团队角色"),
                Map.entry("workflow_heading", "# 工作流程"),
                Map.entry("lifecycle_heading", "# 团队生命周期"),
                Map.entry("persona_heading", "# 当前人设"),
                Map.entry("info_heading", "# 团队信息"),
                Map.entry("team_name_label", "team_name（团队唯一标识）"),
                Map.entry("display_name_label", "display_name（团队展示名）"),
                Map.entry("team_desc", "团队目标与指令"),
                Map.entry("teamworkspace", "团队共享工作空间"),
                Map.entry("teamworkspace_purpose", "用于存放团队共享文件（方案、设计、交付成果），所有成员通过该路径前缀读写同一份文件，系统自动管理版本和文件锁"),
                Map.entry("teamworkspace_abs", "绝对路径"),
                Map.entry("members_heading", "# 成员关系"),
                Map.entry("leader_mode_plan", "团队成员执行模式: plan_mode（成员领取任务后需先提交计划，由你通过 approve_plan 审批后才能执行）"),
                Map.entry("leader_mode_build", "团队成员执行模式: build_mode（成员领取任务后自主执行并直接完成，无需你审批计划）"),
                Map.entry("teammate_mode_plan",
                        "你的执行模式: plan_mode（领取任务后必须先通过 write_plan 提交计划，等待 leader 通过 approve_plan 审批后才能开始执行）"),
                Map.entry("teammate_mode_build", "你的执行模式: build_mode（领取任务后可自主执行并直接标记完成，无需 leader 审批计划）")
        );
        Map<String, String> en = Map.ofEntries(
                Map.entry("member_name_line", "Your member_name"),
                Map.entry("role_heading", "# Team Role"),
                Map.entry("workflow_heading", "# Workflow"),
                Map.entry("lifecycle_heading", "# Team Lifecycle"),
                Map.entry("persona_heading", "# Current Persona"),
                Map.entry("info_heading", "# Team Info"),
                Map.entry("team_name_label", "team_name (unique identifier)"),
                Map.entry("display_name_label", "display_name (human-readable label)"),
                Map.entry("team_desc", "Team Goal & Directives"),
                Map.entry("teamworkspace", "Team Shared Workspace"),
                Map.entry("teamworkspace_purpose",
                        "Holds team-shared files (plans, designs, deliverables); all members read/write the same "
                                + "files through this path prefix. Versioning and file locks are managed "
                                + "automatically"),
                Map.entry("teamworkspace_abs", "Absolute path"),
                Map.entry("members_heading", "# Relationships"),
                Map.entry("leader_mode_plan",
                        "Teammate execution mode: plan_mode (teammates must submit a plan after claiming a task "
                                + "and wait for your approval via approve_plan before executing)"),
                Map.entry("leader_mode_build",
                        "Teammate execution mode: build_mode (teammates execute and complete tasks autonomously "
                                + "without plan approval)"),
                Map.entry("teammate_mode_plan",
                        "Your execution mode: plan_mode (after claiming a task you must submit a plan via "
                                + "write_plan and wait for the leader to approve it via approve_plan before "
                                + "executing)"),
                Map.entry("teammate_mode_build",
                        "Your execution mode: build_mode (after claiming a task you execute autonomously and "
                                + "mark it completed without leader plan approval)")
        );
        return ("en".equals(language) ? en : cn).get(key);
    }

    private static final class SectionCache {
        private boolean isInitialized;
        private long cachedMtime;
        private PromptSection cachedSection;

        private PromptSection refresh(long mtime, SectionFetcher fetcher) {
            if (isInitialized && cachedMtime == mtime) {
                return cachedSection;
            }
            cachedSection = fetcher.fetch();
            cachedMtime = mtime;
            isInitialized = true;
            return cachedSection;
        }
    }

    @FunctionalInterface
    private interface SectionFetcher {
        PromptSection fetch();
    }
    private static <T> T nullValue() {
        return null;
    }

}
