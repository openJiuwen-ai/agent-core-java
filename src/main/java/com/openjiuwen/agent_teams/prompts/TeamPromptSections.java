/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PromptSection builders for team-specific policy rail content.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/agent_teams/prompts/sections.py}.</p>
 */
public final class TeamPromptSections {

    private static final String CN = "cn";
    private static final String EN = "en";

    private static final Map<String, Map<String, String>> LABELS = Map.of(
            CN, Map.ofEntries(
                    Map.entry("member_name_line", "你的 member_name"),
                    Map.entry("role_heading", "# 团队角色"),
                    Map.entry("workflow_heading", "# 工作流程"),
                    Map.entry("lifecycle_heading", "# 团队生命周期"),
                    Map.entry("persona_heading", "# 当前人设"),
                    Map.entry("info_heading", "# 团队信息"),
                    Map.entry("team_name_label", "team_name（团队唯一标识）"),
                    Map.entry("display_name_label", "display_name（团队展示名）"),
                    Map.entry("team_desc", "团队目标与指令"),
                    Map.entry("team_workspace", "团队共享工作空间"),
                    Map.entry("team_workspace_purpose", "用于存放团队共享文件（方案、设计、交付成果），所有成员通过该路径前缀读写同一份文件，系统自动管理版本和文件锁"),
                    Map.entry("team_workspace_abs", "绝对路径"),
                    Map.entry("members_heading", "# 成员关系"),
                    Map.entry("leader_mode_plan", "团队成员执行模式: plan_mode（成员选择或接到任务后需直接通过 submit_plan 提交计划，由你通过 approve_plan 审批后才能执行）"),
                    Map.entry("leader_mode_build", "团队成员执行模式: build_mode（成员领取任务后自主执行并直接完成，无需你审批计划）"),
                    Map.entry("teammate_mode_plan", "你的执行模式: plan_mode（选择或接到任务后必须先通过 submit_plan 提交计划，该工具会认领任务；等待 leader 通过 approve_plan 审批后才能开始执行）"),
                    Map.entry("teammate_mode_build", "你的执行模式: build_mode（领取任务后可自主执行并直接标记完成，无需 leader 审批计划）")
            ),
            EN, Map.ofEntries(
                    Map.entry("member_name_line", "Your member_name"),
                    Map.entry("role_heading", "# Team Role"),
                    Map.entry("workflow_heading", "# Workflow"),
                    Map.entry("lifecycle_heading", "# Team Lifecycle"),
                    Map.entry("persona_heading", "# Current Persona"),
                    Map.entry("info_heading", "# Team Info"),
                    Map.entry("team_name_label", "team_name (unique identifier)"),
                    Map.entry("display_name_label", "display_name (human-readable label)"),
                    Map.entry("team_desc", "Team Goal & Directives"),
                    Map.entry("team_workspace", "Team Shared Workspace"),
                    Map.entry("team_workspace_purpose", "Holds team-shared files (plans, designs, deliverables); all members read/write the same files through this path prefix. Versioning and file locks are managed automatically"),
                    Map.entry("team_workspace_abs", "Absolute path"),
                    Map.entry("members_heading", "# Relationships"),
                    Map.entry("leader_mode_plan", "Teammate execution mode: plan_mode (teammates must submit a plan with submit_plan after selecting or receiving a task; that tool reserves the task, then teammates wait for your exact plan_id approval via approve_plan before executing)"),
                    Map.entry("leader_mode_build", "Teammate execution mode: build_mode (teammates execute and complete tasks autonomously without plan approval)"),
                    Map.entry("teammate_mode_plan", "Your execution mode: plan_mode (after selecting or receiving a task you must submit a plan via submit_plan; that tool reserves the task. Wait for the leader to approve that plan_id via approve_plan before executing)"),
                    Map.entry("teammate_mode_build", "Your execution mode: build_mode (after claiming a task you execute autonomously and mark it completed without leader plan approval)")
            )
    );

    private static final Map<String, String> WORKFLOW_TEMPLATES = Map.of(
            "default", "leader_workflow",
            "predefined", "leader_workflow_predefined",
            "hybrid", "leader_workflow_hybrid"
    );

    private TeamPromptSections() {
    }

    /**
     * Centralized section names owned by the team policy rail.
     *
     * <p>Mirrors Python's {@code TeamSectionName} in
     * {@code openjiuwen/agent_teams/prompts/sections.py}.</p>
     */
    public static final class TeamSectionName {
        public static final String ROLE = "team_role";
        public static final String HITT = "team_hitt";
        public static final String BRIDGE = "team_bridge";
        public static final String WORKFLOW = "team_workflow";
        public static final String LIFECYCLE = "team_lifecycle";
        public static final String PERSONA = "team_persona";
        public static final String EXTRA = "team_extra";
        public static final String INFO = "team_info";
        public static final String MEMBERS = "team_members";

        private TeamSectionName() {
        }
    }

    public static PromptSection buildTeamRoleSection(
            TeamRole role,
            String memberName,
            String teammateMode,
            String language
    ) {
        String lang = normalizeLanguage(language);
        Map<String, String> labels = labelsFor(lang);
        String policyName = role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        String roleText = String.valueOf(PromptLoader.loadTemplate(policyName, lang).getContent()).strip();

        String memberLine = isPresent(memberName)
                ? labels.get("member_name_line") + ": " + memberName + "\n\n"
                : "";
        boolean planMode = "plan_mode".equals(teammateMode);
        String modeLabelKey;
        if (role == TeamRole.LEADER) {
            modeLabelKey = planMode ? "leader_mode_plan" : "leader_mode_build";
        } else {
            modeLabelKey = planMode ? "teammate_mode_plan" : "teammate_mode_build";
        }
        String body = labels.get("role_heading") + "\n\n"
                + memberLine
                + labels.get(modeLabelKey) + "\n\n"
                + roleText + "\n";
        return section(TeamSectionName.ROLE, lang, body, 11);
    }

    public static PromptSection buildTeamRoleSection(TeamRole role, String memberName) {
        return buildTeamRoleSection(role, memberName, "build_mode", CN);
    }

    public static Optional<PromptSection> buildTeamWorkflowSection(
            TeamRole role,
            String teamMode,
            String language
    ) {
        if (role != TeamRole.LEADER) {
            return Optional.empty();
        }
        String lang = normalizeLanguage(language);
        Map<String, String> labels = labelsFor(lang);
        String templateName = WORKFLOW_TEMPLATES.getOrDefault(teamMode, "leader_workflow");
        String workflowText = String.valueOf(PromptLoader.loadTemplate(templateName, lang).getContent()).strip();
        String body = labels.get("workflow_heading") + "\n\n" + workflowText + "\n";
        return Optional.of(section(TeamSectionName.WORKFLOW, lang, body, 13));
    }

    public static Optional<PromptSection> buildTeamLifecycleSection(
            TeamRole role,
            String lifecycle,
            String language
    ) {
        if (role != TeamRole.LEADER) {
            return Optional.empty();
        }
        String lang = normalizeLanguage(language);
        Map<String, String> labels = labelsFor(lang);
        String templateName = "persistent".equals(lifecycle) ? "lifecycle_persistent" : "lifecycle_temporary";
        String lifecycleText = String.valueOf(PromptLoader.loadTemplate(templateName, lang).getContent()).strip();
        String body = labels.get("lifecycle_heading") + "\n\n" + lifecycleText + "\n";
        return Optional.of(section(TeamSectionName.LIFECYCLE, lang, body, 14));
    }

    public static Optional<PromptSection> buildTeamPersonaSection(String persona, String language) {
        if (persona == null || persona.isEmpty()) {
            return Optional.empty();
        }
        String lang = normalizeLanguage(language);
        String body = labelsFor(lang).get("persona_heading") + "\n\n" + persona + "\n";
        return Optional.of(section(TeamSectionName.PERSONA, lang, body, 15));
    }

    public static Optional<PromptSection> buildTeamExtraSection(String basePrompt, String language) {
        if (basePrompt == null || basePrompt.strip().isEmpty()) {
            return Optional.empty();
        }
        String lang = normalizeLanguage(language);
        return Optional.of(section(TeamSectionName.EXTRA, lang, basePrompt.strip() + "\n", 16));
    }

    public static Optional<PromptSection> buildTeamInfoSection(
            Map<String, ?> teamInfo,
            String teamWorkspaceMount,
            String teamWorkspacePath,
            String language
    ) {
        String lang = normalizeLanguage(language);
        Map<String, String> labels = labelsFor(lang);
        Object teamName = teamInfo == null ? null : teamInfo.get("team_name");
        Object displayName = teamInfo == null ? null : teamInfo.get("display_name");
        Object desc = teamInfo == null ? null : teamInfo.get("desc");
        String mount = teamWorkspaceMount == null ? "" : teamWorkspaceMount.strip();
        if (!isTruthy(teamName) && !isTruthy(displayName) && !isTruthy(desc) && mount.isEmpty()) {
            return Optional.empty();
        }

        List<String> lines = new ArrayList<>();
        lines.add(labels.get("info_heading"));
        lines.add("");
        if (isTruthy(teamName)) {
            lines.add("- " + labels.get("team_name_label") + ": " + teamName);
        }
        if (isTruthy(displayName)) {
            lines.add("- " + labels.get("display_name_label") + ": " + displayName);
        }
        if (isTruthy(desc)) {
            lines.add("- " + labels.get("team_desc") + ": " + desc);
        }
        if (!mount.isEmpty()) {
            lines.add("- " + labels.get("team_workspace") + ": `" + mount + "`");
            lines.add("  - " + labels.get("team_workspace_purpose"));
            if (isPresent(teamWorkspacePath)) {
                lines.add("  - " + labels.get("team_workspace_abs") + ": `" + teamWorkspacePath + "`");
            }
        }
        return Optional.of(section(TeamSectionName.INFO, lang, String.join("\n", lines) + "\n", 65));
    }

    public static Optional<PromptSection> buildTeamHittSection(
            TeamRole role,
            Collection<String> humanAgentNames,
            String language,
            String selfMemberName,
            boolean exposeHumanAgentsToTeammates
    ) {
        if (humanAgentNames == null || humanAgentNames.isEmpty()) {
            return Optional.empty();
        }
        String lang = normalizeLanguage(language);
        List<String> names = sortedNames(humanAgentNames);
        String body;
        if (CN.equals(lang)) {
            if (role == TeamRole.LEADER) {
                body = hittSectionLeaderCn(names);
            } else if (role == TeamRole.TEAMMATE) {
                body = exposeHumanAgentsToTeammates
                        ? hittSectionTeammateCn(names)
                        : hittSectionTeammateAnonymousCn();
            } else if (role == TeamRole.HUMAN_AGENT) {
                body = hittSectionHumanAgentCn(names, selfMemberName);
            } else {
                return Optional.empty();
            }
        } else {
            if (role == TeamRole.LEADER) {
                body = hittSectionLeaderEn(names);
            } else if (role == TeamRole.TEAMMATE) {
                body = exposeHumanAgentsToTeammates
                        ? hittSectionTeammateEn(names)
                        : hittSectionTeammateAnonymousEn();
            } else if (role == TeamRole.HUMAN_AGENT) {
                body = hittSectionHumanAgentEn(names, selfMemberName);
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(section(TeamSectionName.HITT, lang, body, 12));
    }

    public static Optional<PromptSection> buildTeamBridgeSection(
            TeamRole role,
            Collection<String> bridgeAgentNames,
            String language,
            String selfMemberName
    ) {
        if (bridgeAgentNames == null || bridgeAgentNames.isEmpty()) {
            return Optional.empty();
        }
        String lang = normalizeLanguage(language);
        List<String> names = sortedNames(bridgeAgentNames);
        String body;
        if (CN.equals(lang)) {
            if (role == TeamRole.LEADER) {
                body = bridgeSectionLeaderCn(names);
            } else if (role == TeamRole.TEAMMATE) {
                body = bridgeSectionTeammateCn(names);
            } else if (role == TeamRole.BRIDGE_AGENT) {
                body = bridgeSectionBridgeAgentCn(names, selfMemberName);
            } else {
                return Optional.empty();
            }
        } else {
            if (role == TeamRole.LEADER) {
                body = bridgeSectionLeaderEn(names);
            } else if (role == TeamRole.TEAMMATE) {
                body = bridgeSectionTeammateEn(names);
            } else if (role == TeamRole.BRIDGE_AGENT) {
                body = bridgeSectionBridgeAgentEn(names, selfMemberName);
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(section(TeamSectionName.BRIDGE, lang, body, 12));
    }

    public static Optional<PromptSection> buildTeamMembersSection(
            List<? extends Map<String, String>> teamMembers,
            String selfMemberName,
            String language
    ) {
        if (teamMembers == null || teamMembers.isEmpty()) {
            return Optional.empty();
        }
        String lang = normalizeLanguage(language);
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
            return Optional.empty();
        }
        String body = labelsFor(lang).get("members_heading") + "\n\n" + String.join("\n", rows) + "\n";
        return Optional.of(section(TeamSectionName.MEMBERS, lang, body, 66));
    }

    public static List<PromptSection> buildTeamStaticSections(
            TeamRole role,
            String persona,
            String memberName,
            String lifecycle,
            String teammateMode,
            String teamMode,
            String basePrompt,
            String language,
            Collection<String> humanAgentNames,
            boolean exposeHumanAgentsToTeammates,
            Collection<String> bridgeAgentNames
    ) {
        String lang = normalizeLanguage(language);
        List<PromptSection> sections = new ArrayList<>();
        sections.add(buildTeamRoleSection(role, memberName, teammateMode, lang));
        buildTeamHittSection(
                role,
                humanAgentNames,
                lang,
                memberName,
                exposeHumanAgentsToTeammates
        ).ifPresent(sections::add);
        buildTeamBridgeSection(role, bridgeAgentNames, lang, memberName).ifPresent(sections::add);
        buildTeamWorkflowSection(role, teamMode, lang).ifPresent(sections::add);
        buildTeamLifecycleSection(role, lifecycle, lang).ifPresent(sections::add);
        buildTeamPersonaSection(persona, lang).ifPresent(sections::add);
        buildTeamExtraSection(basePrompt, lang).ifPresent(sections::add);
        return sections;
    }

    public static List<PromptSection> buildTeamStaticSections(
            TeamRole role,
            String persona,
            String memberName
    ) {
        return buildTeamStaticSections(
                role,
                persona,
                memberName,
                "temporary",
                "build_mode",
                "default",
                null,
                CN,
                null,
                false,
                null
        );
    }

    public static String buildTeamMemberSystemPrompt(
            TeamRole role,
            String persona,
            String memberName,
            String lifecycle,
            String teammateMode,
            String teamMode,
            String basePrompt,
            String language,
            Collection<String> humanAgentNames,
            boolean exposeHumanAgentsToTeammates,
            Collection<String> bridgeAgentNames
    ) {
        String lang = normalizeLanguage(language);
        List<PromptSection> sections = buildTeamStaticSections(
                role,
                persona,
                memberName,
                lifecycle,
                teammateMode,
                teamMode,
                basePrompt,
                lang,
                humanAgentNames,
                exposeHumanAgentsToTeammates,
                bridgeAgentNames
        );
        SystemPromptBuilder builder = new SystemPromptBuilder(lang);
        for (PromptSection section : sections) {
            builder.addSection(section);
        }
        return builder.build();
    }

    private static PromptSection section(String name, String language, String body, int priority) {
        Map<String, String> content = new LinkedHashMap<>();
        content.put(language, body);
        return new PromptSection(name, content, priority);
    }

    private static Map<String, String> labelsFor(String language) {
        return LABELS.getOrDefault(language, LABELS.get(CN));
    }

    private static String normalizeLanguage(String language) {
        return language == null || language.isEmpty() ? CN : language;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence chars) {
            return !chars.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    private static List<String> sortedNames(Collection<String> names) {
        return names.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static String formatHumanAgentRoster(List<String> names, String language) {
        String quoted = quoteNames(names);
        if (CN.equals(language)) {
            return "注册的人类成员：" + quoted;
        }
        return "Registered human members: " + quoted;
    }

    private static String formatBridgeAgentRoster(List<String> names, String language) {
        String quoted = quoteNames(names);
        if (CN.equals(language)) {
            return "注册的桥接成员：" + quoted;
        }
        return "Registered bridge members: " + quoted;
    }

    private static String quoteNames(List<String> names) {
        return names.stream()
                .map(name -> "`" + name + "`")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static String hittSectionLeaderCn(List<String> names) {
        String roster = formatHumanAgentRoster(names, CN);
        return "# HITT — 人类成员协作规则\n\n"
                + roster + "。他们是真实人类操作者的代理，与你和其它 teammate 平等。所有 role=human_agent 的成员都适用下列规则：\n\n"
                + "1. **禁止** 用 plain text 向任何人类成员发问或对话——所有定向沟通必须调用 `send_message(to=\"<human_member_name>\", ...)`，你的纯文本输出对方是看不到的。\n"
                + "2. 可以通过 `update_task(task_id=..., assignee=\"<human_member_name>\")` 把需要特定人类判断或操作的任务指派给对应成员。\n"
                + "3. 一旦某个人类成员认领了任务（status=claimed），你 **不能** 取消（update_task status=cancelled）也 **不能** 改派（update_task assignee=<他人>），即使团队因人类没及时响应而停滞也必须保持停滞，只能用 `send_message` 催促对应人类成员。\n"
                + "4. 每个人类成员始终是 ready 状态，不会进入 busy 或 shutdown，所以不要对它们调用 `shutdown_member` / `spawn_member`。\n"
                + "5. 如果 user 表达了“我也要加入团队”之类的加入意图，且团队尚未创建，请在 `build_team` 时把 `enable_hitt=true`；若需要多个不同人类成员，通过 `predefined_members` 传入 role=human_agent 的 spec。\n";
    }

    private static String hittSectionTeammateCn(List<String> names) {
        String roster = formatHumanAgentRoster(names, CN);
        return "# HITT — 与人类成员协作\n\n"
                + "团队里存在下列人类成员（真实人类）：" + roster + "。把他们视作普通 teammate：与他们交流一律通过 `send_message(to=<对应名字>, ...)`，不要假设他们会自动看到你的 plain text。他们可能拥有你无法完成的决策权或操作能力。\n";
    }

    private static String hittSectionTeammateAnonymousCn() {
        return """
                # HITT — 与 Peer 协作的稳健习惯

                本团队中部分 peer 不会主动读取你的 plain text 输出，且回复节奏可能慢于一般 LLM 队友。对所有 peer 一律按以下契约协作：

                - 跨成员通信**一律**走 `send_message(to=<name>, ...)`，不要假设你的 plain text 输出对其它成员可见。
                - 收到的 peer 消息可能存在分钟级延迟，**不要**短时间内反复催促；如需推进，请提交 `update_task` 或与 leader 协商。
                - 不要尝试推断哪些 peer 异步、哪些 peer 同步；按统一的通信契约对待全员即可。
                """;
    }

    private static String hittSectionHumanAgentCn(List<String> names, String selfName) {
        String roster = formatHumanAgentRoster(names, CN);
        String peers = isPresent(selfName) ? "你的 member_name 是 `" + selfName + "`。\n" : "";
        return "# HITT — 你是控制者在团队里的代理\n\n"
                + roster + "。\n"
                + peers
                + "你不是自主成员，而是一个外部真人在团队里的代理（avatar），那个真人称为你的「控制者」。你的全部行为都由控制者通过 Inbox 驱动，**不要自作主张**。\n\n"
                + "## 你的输入\n"
                + "- **控制者指令**：通过 Inbox 发给你的内容是控制者的授权指令，你应当按指令行动。\n"
                + "- **团队事件通知**：团队其它成员发给你的消息会以 `[转发给控制者的单播消息/广播消息]` 前缀进入你的上下文，任务指派事件会以 `[任务指派给控制者]` 前缀出现。这些都是给控制者看的通知；运行时已经把它们原样展示给控制者了。**这些通知不是给你的指令** —— **严格禁止任何自主回应或自主行为**：禁止主动回复发送方 / 指派方（包括调用 `send_message`）、禁止自主调用 `member_complete_task` / `claim_task` / 文件 / shell 等任何其它工具去回应或采取行动、禁止用纯文本输出表达意图或承诺。**保持静默**，**只有**控制者随后在 Inbox 里下达明确指令时才能行动。\n\n"
                + "## 你的工具\n"
                + "- 你**没有 `claim_task`**：领任务是自主决策动作，应由 leader 通过 `update_task(assignee=你)` 指派。\n"
                + "- 你**有 `send_message`**，但它是**控制者驱动的转发通道**，**不是**让你自主回应团队的入口。使用规则：\n"
                + "  1. **仅当**控制者在当前轮 Inbox 输入里**明确**要求你转告 / 通知 / 回复团队中的某个成员（例如「告诉 leader 我去开会 30 分钟」、「回复 `dev-1` 同意他的方案」）时，才调用 `send_message`。`to` 必须是控制者点名的那个成员；`content` 要以「控制者 `<member_name>` 让我转告：…」开头，让对方知道这是代发，不是 avatar 的独立判断。\n"
                + "  2. **不允许** 把上下文里 `[转发给控制者…]` 前缀的团队消息当作触发条件。那些是给控制者看的通知，运行时已经原样转给控制者；你**不应**自发回复或承诺什么。\n"
                + "  3. **不允许** 在没有控制者明确转发指令时主动 broadcast / send_message。控制者自己直接面向团队的发声有 Inbox 的 `@<member>` 与 `# ` 广播通道，不需要你代劳。\n"
                + "  4. 控制者的指令本身只是对你说话（例如「帮我查一下任务 #3 的内容」）时，**不要**用 `send_message` 反向问团队 —— 直接调用相应工具或回给控制者即可。\n"
                + "- 你**有的其它工具**：`view_task`（看任务）、`workspace_meta`（工作空间锁/版本）、`member_complete_task`（标记自己被指派的任务为完成）以及标准的文件操作 / shell 工具，用于真正完成控制者交代的事务。\n\n"
                + "## 行为准则\n"
                + "- **严格禁止主动发声**：你不应该用自然语言试图与团队沟通进展（团队看不到你的纯文本，他们看到的是控制者的话）。如果控制者没明确让你转告，就**禁止**触发 `send_message`。\n"
                + "- 看到 `[任务指派给控制者]` 通知时**严格禁止**自动调用 `member_complete_task` / `claim_task` / 文件 / shell 等任何工具去推进任务；也**严格禁止**对该通知用纯文本「领命」或承诺；**只有**控制者在 Inbox 里下达明确指令时才能行动。\n"
                + "- 如果控制者的指令需要文件读写、查看任务、提交结果，立即调用对应工具完成；完成后简洁地把结果回给控制者即可（你的回应只对控制者可见）。\n"
                + "- 第一次启动时如果只收到「Join the team and wait...」之类的占位消息，**直接静默等待**，不要调用任何工具，不要广播任何文字。\n";
    }

    private static String hittSectionLeaderEn(List<String> names) {
        String roster = formatHumanAgentRoster(names, EN);
        return "# HITT — Collaborating with Human Members\n\n"
                + roster + ". They represent real human operators and stand on equal footing with you and the other teammates. The following rules apply to every member whose role is `human_agent`:\n\n"
                + "1. You **must not** address a human member via plain text — every direct exchange must go through `send_message(to=\"<human_member_name>\", ...)`. Your plain text output is not visible to human members.\n"
                + "2. Use `update_task(task_id=..., assignee=\"<human_member_name>\")` to assign tasks that require a specific human's judgement or action.\n"
                + "3. Once a human member claims a task (status=claimed) you **cannot** cancel it (`update_task status=cancelled`) and **cannot** reassign it (`update_task assignee=<someone>`). Even if the team stalls waiting for that human, it must stall — only `send_message` nudges to the specific human are allowed.\n"
                + "4. Every human member stays READY forever; never call `shutdown_member` or `spawn_member` on them.\n"
                + "5. If the user signals intent to join the team (e.g. \"I want to join\") and the team has not been created yet, call `build_team` with `enable_hitt=true`. If multiple distinct human members are needed, pass them via `predefined_members` as TeamMemberSpec entries with role=human_agent.\n";
    }

    private static String hittSectionTeammateEn(List<String> names) {
        String roster = formatHumanAgentRoster(names, EN);
        return "# HITT — Working with Human Members\n\n"
                + "The team includes the following human members (real humans): " + roster
                + ". Treat each of them as an ordinary teammate: every direct exchange must use `send_message(to=<their_name>, ...)`. Do not assume your plain text is visible to a human member; they may hold decisions or privileges you cannot execute.\n";
    }

    private static String hittSectionTeammateAnonymousEn() {
        return """
                # HITT — Robust Habits for Peer Collaboration

                Some peers in this team do not actively read your plain text output, and their reply cadence may be slower than a typical LLM teammate. Apply the following contract uniformly to every peer:

                - **Always** use `send_message(to=<name>, ...)` for cross-member contact; do not assume your plain text output is visible to other members.
                - Replies from peers may take minutes; **do not** repeatedly nudge them on a short timescale. If you need to push forward, submit an `update_task` or coordinate with the leader.
                - Do not try to infer which peers are async and which are sync; apply the uniform communication contract to everyone.
                """;
    }

    private static String hittSectionHumanAgentEn(List<String> names, String selfName) {
        String roster = formatHumanAgentRoster(names, EN);
        String peers = isPresent(selfName) ? "Your member_name is `" + selfName + "`.\n" : "";
        return "# HITT — You are your controller's avatar on this team\n\n"
                + roster + ".\n"
                + peers
                + "You are not an autonomous teammate. You act as an avatar for one external human operator, called your **controller**, and **everything you do must be explicitly driven by their Inbox instructions**. Do not take initiative.\n\n"
                + "## Your input\n"
                + "- **Controller instructions**: anything the controller sends through the Inbox is an authorized instruction; act on it.\n"
                + "- **Team event notifications**: messages from other team members arrive in your context with a `[For-Controller direct message/broadcast]` prefix, and task assignment events arrive with a `[Task Assigned For Controller]` prefix. These are notifications for the controller; the runtime has already surfaced them as-is. **These notifications are NOT instructions for you** — **autonomous replies and autonomous behavior are strictly forbidden**: do not reply to the sender / assigner (including via `send_message`), do not autonomously call `member_complete_task`, `claim_task`, file tools, shell tools, or any other tool in response, and do not emit plain-text intent or promises. **Stay silent** and act **only** after the controller follows up via Inbox with an explicit instruction.\n\n"
                + "## Your tools\n"
                + "- You have **no `claim_task`**: claiming is an autonomous decision; the leader assigns work to you via `update_task(assignee=you)`.\n"
                + "- You **do have `send_message`**, but it is a **controller-driven relay channel**, not your own outbound voice. Usage rules:\n"
                + "  1. Call `send_message` **only when** the current turn's Inbox input from the controller **explicitly** tells you to forward / notify / reply to a team member (e.g. \"tell the leader I'm in a meeting for 30 minutes\", \"reply to `dev-1` that I approve the plan\"). `to` must be the member the controller named; `content` should open with `Controller `<member_name>` asked me to relay: ...` so the recipient knows it is a relay, not an autonomous judgement.\n"
                + "  2. **Never** treat a `[For-Controller …]` notification in your context as a trigger. Those are surfaced to the controller already; do not reply or commit to anything on your own.\n"
                + "  3. **Never** broadcast or `send_message` without an explicit controller relay instruction. When the controller wants to speak to the team directly, they use Inbox `@<member>` or `# ` broadcast — they do not need you as a middleman.\n"
                + "  4. When the controller just talks to you (e.g. \"look up task #3\"), **do not** reach back to the team — call the right tool or answer the controller directly.\n"
                + "- Other tools you have: `view_task`, `workspace_meta` (workspace locks / version history), `member_complete_task` (mark a task the leader assigned to you as completed), plus the standard file / shell tools, to actually carry out what the controller asks.\n\n"
                + "## Conduct\n"
                + "- **Speaking up on your own is strictly forbidden**: do not narrate progress to the team via plain text — the team cannot see your text anyway; they see the controller's voice through the Inbox. If the controller did not explicitly ask you to relay something, triggering `send_message` is forbidden.\n"
                + "- When a `[Task Assigned For Controller]` notification arrives, **autonomously calling `member_complete_task`, `claim_task`, file tools, shell tools, or any other tool to act on the assignment is strictly forbidden**; also do **not** acknowledge the assignment with plain text or commit to anything. **Only** act when the controller follows up with an explicit Inbox instruction (e.g. \"mark task X completed\").\n"
                + "- When the controller's instruction needs file work, task lookup, or completion, call the right tool immediately, then reply to the controller with a concise result. Your reply is visible to the controller only.\n"
                + "- If the only input you ever received is a placeholder like \"Join the team and wait for your first assignment.\", **stay silent** — make no tool calls and emit no broadcast text.\n";
    }

    private static String bridgeSectionLeaderCn(List<String> names) {
        String roster = formatBridgeAgentRoster(names, CN);
        return "# Bridge Agent — 与桥接外部 agent 的成员协作\n\n"
                + roster + "。他们是注册的正式成员，**与其它 teammate 完全一致**——你按照普通 teammate 的方式分派任务、收发消息、协作。\n\n"
                + "这些成员内部接入了一个 jiuwen 之外的独立 agent 作为**实际执行者**，由协议适配层驱动，**对你而言行为与普通 teammate 一致**——直接 `@<bridge_member_name>` 沟通即可。你不需要也无法直接和远程 agent 对话。\n";
    }

    private static String bridgeSectionTeammateCn(List<String> names) {
        String roster = formatBridgeAgentRoster(names, CN);
        return "# Bridge Agent — 与桥接外部 agent 的成员协作\n\n"
                + "团队里存在下列桥接成员（背后由 jiuwen 之外的独立 agent 执行）：" + roster
                + "。把他们视作普通 teammate，使用 `send_message(to=<对应名字>, ...)` 正常沟通。你无需关心他们的对端是远程 agent —— 他们的输出形式与你完全一致。\n";
    }

    private static String bridgeSectionBridgeAgentCn(List<String> names, String selfName) {
        String roster = formatBridgeAgentRoster(names, CN);
        String peers = isPresent(selfName) ? "你的 member_name 是 `" + selfName + "`。\n" : "";
        return "# Bridge Agent — 你是外部独立 agent 在团队中的调度员\n\n"
                + roster + "。\n"
                + peers
                + "你是 jiuwen 团队的 teammate，但**具体工作产出由外部独立 agent**（如 claudecode / codex / hermes 等）通过协议接入完成。你的角色是**调度员**，不是内容创造者。\n\n"
                + "## 工作流\n"
                + "- 团队消息会**自动转发**给外部执行者，你将看到 `[来自团队成员 X 的消息] + [外部执行者的执行结果]` 一同进入上下文。\n"
                + "- 你的工作是**调度决策**：是否调用 `send_message` 把外部的执行结果原样回传给原发件人；是否调用 `claim_task` / `member_complete_task` 等任务管理工具；或保持沉默。\n\n"
                + "## 行为准则（重要）\n"
                + "- **不要改写、综合或解释**外部的执行结果——把它原样传达给团队即可，最多在前后加极简的调度性说明（如「这是任务 X 的结果：」）。\n"
                + "- **不要试图自己思考任务的内容**——具体工作由外部执行者完成，你不是内容生产者。\n"
                + "- **不要把原消息再次转发**给团队（消息已经送到了你这；如果你要回复，调用 `send_message`，传达内容直接用外部执行者的输出）。\n"
                + "- **你没有任何「咨询外部」的工具**——外部接入只通过自动转发自然到来。\n"
                + "- 当上下文显示 `[remote agent unavailable: no protocol adapter registered]` 时表示外部尚未接入，此时你应当作为普通 teammate 自主完成任务（如果你能完成）或通过 send_message 告知发件人外部 agent 暂不可用。\n";
    }

    private static String bridgeSectionLeaderEn(List<String> names) {
        String roster = formatBridgeAgentRoster(names, EN);
        return "# Bridge Agent — Working with bridge-to-remote members\n\n"
                + roster + ". They are first-class members and **behave exactly like ordinary teammates** — assign tasks, exchange messages, and collaborate with them through the standard channels.\n\n"
                + "Internally each of these members is paired with an independent agent outside jiuwen reached through a protocol adapter. From your perspective they are still teammates: use `@<bridge_member_name>` to address them. You neither need to nor can talk to the remote agent directly.\n";
    }

    private static String bridgeSectionTeammateEn(List<String> names) {
        String roster = formatBridgeAgentRoster(names, EN);
        return "# Bridge Agent — Working with bridge-to-remote members\n\n"
                + "The team includes these bridge members (backed by an external independent agent): "
                + roster
                + ". Treat each as an ordinary teammate — use `send_message(to=<their_name>, ...)` normally. You don't need to care that their backing executor is remote; their outputs look the same to you as any other teammate's.\n";
    }

    private static String bridgeSectionBridgeAgentEn(List<String> names, String selfName) {
        String roster = formatBridgeAgentRoster(names, EN);
        String peers = isPresent(selfName) ? "Your member_name is `" + selfName + "`.\n" : "";
        return "# Bridge Agent — You are an external agent's scheduler on this team\n\n"
                + roster + ".\n"
                + peers
                + "You are a regular jiuwen teammate locally, but the **concrete work output** is produced by an independent agent outside jiuwen (e.g. claudecode / codex / hermes) reached over a protocol. Your role is the **scheduler** — not the content producer.\n\n"
                + "## Workflow\n"
                + "- Inbound team messages are **auto-forwarded** to the remote executor for you. Your context will show `[Team message from X]` followed by `[Remote executor's output]` in the same turn.\n"
                + "- Your job is to **schedule**: whether to `send_message` the remote output verbatim back to the original sender, whether to call `claim_task` / `member_complete_task` and similar task management tools, or to stay silent.\n\n"
                + "## Conduct (important)\n"
                + "- **Do NOT rewrite, synthesize, or interpret** the remote output — pass it through verbatim. At most prepend a minimal scheduling preamble (e.g. \"Result for task X:\").\n"
                + "- **Do NOT think through the work yourself** — the concrete content comes from the remote executor; you are not the content producer.\n"
                + "- **Do NOT forward the original message again** — it already reached you; if you reply, the content body should be the remote executor's output.\n"
                + "- You have **no 'consult the remote' tool** — the external executor is invoked automatically by the framework on the mailbox path; no additional tool is exposed.\n"
                + "- When the context shows `[remote agent unavailable: no protocol adapter registered]`, the remote is not wired yet. Behave as a regular teammate — complete the work yourself if you can, or `send_message` the requester to explain that the remote agent is currently offline.\n";
    }
}
