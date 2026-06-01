/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
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
        List<String> humanNames = resolveHumanAgentNames(teamBackend);
        this.staticSections = buildStaticSections(
                role, persona, memberName, lifecycle, teammateMode,
                teamMode, basePrompt, humanNames
        );

        // Initialize caches (null if no backend)
        if (teamBackend != null) {
            this.infoCache = new MtimeSectionCache(
                    () -> probeInteger("getTeamUpdatedAt"),
                    this::fetchAndBuildInfoSection
            );
            this.membersCache = new MtimeSectionCache(
                    () -> probeInteger("getMembersMaxUpdatedAt"),
                    this::fetchAndBuildMembersSection
            );
        } else {
            this.infoCache = null;
            this.membersCache = null;
        }
    }

    // -- Lifecycle hooks ------------------------------------------------------

    @Override
    public void init(Object agent) {
        super.init(agent);
        // Cache the agent's shared prompt builder
        this.systemPromptBuilder = resolveSystemPromptBuilder(agent);
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
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        refreshSections();
    }

    public CompletableFuture<Void> beforeModelCall(Object ctx) {
        refreshSections();
        return CompletableFuture.completedFuture(null);
    }

    private void refreshSections() {
        if (systemPromptBuilder == null) {
            return;
        }

        for (PromptSection section : staticSections) {
            systemPromptBuilder.addSection(section);
        }

        if (infoCache != null) {
            PromptSection section = infoCache.refresh().join();
            if (section != null) {
                systemPromptBuilder.addSection(section);
            }
        }
        if (membersCache != null) {
            PromptSection section = membersCache.refresh().join();
            if (section != null) {
                systemPromptBuilder.addSection(section);
            }
        }
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

        String policyName = role == TeamRole.LEADER ? "leader_policy" : "teammate_policy";
        String roleText = loadTemplate(policyName, language);
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
        String templateName = switch (teamMode) {
            case "predefined" -> "leader_workflow_predefined";
            case "hybrid" -> "leader_workflow_hybrid";
            default -> "leader_workflow";
        };
        String workflowText = loadTemplate(templateName, language);
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
        String templateName = "persistent".equals(lifecycle) ? "lifecycle_persistent" : "lifecycle_temporary";
        String lifecycleText = loadTemplate(templateName, language);
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
        List<String> names = new ArrayList<>(humanAgentNames);
        Collections.sort(names);
        String normalizedLanguage = language != null ? language : "cn";
        String body = "cn".equals(normalizedLanguage)
                ? buildCnHittBody(role, names, selfMemberName)
                : buildEnHittBody(role, names, selfMemberName);
        if (body == null) {
            return null;
        }
        return new PromptSection(
                TeamSectionName.HITT,
                Map.of(normalizedLanguage, body),
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
            lines.add("  - " + labels.getOrDefault("team_workspace_purpose", "Shared team files"));
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

    private CompletableFuture<PromptSection> fetchAndBuildInfoSection() {
        return invokeFuture(teamBackend, "getTeamInfo").thenApply(info -> {
            Map<String, Object> infoDict = null;
            if (info != null) {
                infoDict = new LinkedHashMap<>();
                infoDict.put("team_name", readString(info, "teamName", "team_name"));
                infoDict.put("display_name", readString(info, "displayName", "display_name"));
                infoDict.put("desc", firstNonNull(readString(info, "desc"), ""));
            }
            return buildTeamInfoSection(infoDict, teamWorkspaceMount, teamWorkspacePath, language);
        });
    }

    private CompletableFuture<PromptSection> fetchAndBuildMembersSection() {
        return invokeFuture(teamBackend, "listMembers").thenApply(members -> {
            List<Map<String, String>> membersList = null;
            if (members instanceof Iterable<?> iterable) {
                membersList = new ArrayList<>();
                for (Object member : iterable) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("member_name", firstNonNull(readString(member, "memberName", "member_name"), ""));
                    row.put("display_name", firstNonNull(readString(member, "displayName", "display_name"), "unknown"));
                    row.put("desc", firstNonNull(readString(member, "desc"), ""));
                    membersList.add(row);
                }
            }
            return buildTeamMembersSection(membersList, memberName, language);
        });
    }

    private CompletableFuture<Integer> probeInteger(String methodName) {
        return invokeFuture(teamBackend, methodName).thenApply(TeamRail::asInt);
    }

    private static SystemPromptBuilder resolveSystemPromptBuilder(Object agent) {
        if (agent == null) {
            return null;
        }
        Object value = readRawProperty(agent, "systemPromptBuilder", "system_prompt_builder");
        if (value instanceof SystemPromptBuilder builder) {
            return builder;
        }
        return null;
    }

    private static List<String> resolveHumanAgentNames(Object teamBackend) {
        if (teamBackend == null) {
            return new ArrayList<>();
        }
        try {
            Object names = invokeFuture(teamBackend, "humanAgentNames").join();
            if (names instanceof Iterable<?> iterable) {
                List<String> result = new ArrayList<>();
                for (Object name : iterable) {
                    if (name != null) {
                        result.add(String.valueOf(name));
                    }
                }
                Collections.sort(result);
                return result;
            }
        } catch (RuntimeException ignored) {
            // HITT is optional; missing backend support means no human-agent section.
        }
        return new ArrayList<>();
    }

    private static CompletableFuture<Object> invokeFuture(Object target, String methodName) {
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            Method method = findMethod(target.getClass(), methodName);
            method.setAccessible(true);
            Object value = method.invoke(target);
            if (value instanceof CompletableFuture<?> future) {
                return future.thenApply(result -> result);
            }
            return CompletableFuture.completedFuture(value);
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(new CompletionException(e));
        }
    }

    private static Method findMethod(Class<?> type, String methodName) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private static Object readRawProperty(Object target, String... aliases) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            for (String alias : aliases) {
                if (map.containsKey(alias)) {
                    return map.get(alias);
                }
            }
        }
        for (String alias : aliases) {
            Object getterValue = invokeGetter(target, alias);
            if (getterValue != null) {
                return getterValue;
            }
            Object fieldValue = readField(target, alias);
            if (fieldValue != null) {
                return fieldValue;
            }
        }
        return null;
    }

    private static Object invokeGetter(Object target, String alias) {
        for (String methodName : List.of("get" + toPascalCase(alias), alias)) {
            try {
                Method method = findMethod(target.getClass(), methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try the next Java/Python-style accessor.
            }
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new CompletionException(e);
            }
        }
        return null;
    }

    private static String readString(Object target, String... aliases) {
        Object value = readRawProperty(target, aliases);
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonNull(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private static String toPascalCase(String name) {
        StringBuilder result = new StringBuilder();
        boolean upper = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upper = true;
                continue;
            }
            result.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return result.toString();
    }

    private static String loadTemplate(String templateName, String language) {
        String loaded = loadTemplateFile(templateName, language);
        if (loaded != null) {
            return loaded;
        }
        if ("leader_policy".equals(templateName)) {
            return "Leader policy: create_task, spawn_member, send_message, approve_plan";
        }
        if ("teammate_policy".equals(templateName)) {
            return "Teammate policy: view_task, claim_task, send_message";
        }
        if ("leader_workflow_predefined".equals(templateName)) {
            return "\u9884\u5b9a\u4e49\u56e2\u961f\u6a21\u5f0f: create_task, send_message";
        }
        if ("leader_workflow_hybrid".equals(templateName)) {
            return "\u6df7\u5408\u56e2\u961f\u6a21\u5f0f: create_task, spawn_member, send_message";
        }
        if ("leader_workflow".equals(templateName)) {
            return "Default workflow: build_team, create_task, spawn_member, send_message";
        }
        if ("lifecycle_persistent".equals(templateName)) {
            return "Persistent team lifecycle: keep members alive for future tasks";
        }
        if ("lifecycle_temporary".equals(templateName)) {
            return "Temporary team lifecycle: shutdown_member, clean_team";
        }
        return "";
    }

    private static String loadTemplateFile(String templateName, String language) {
        String normalizedLanguage = "en".equals(language) ? "en" : "cn";
        String resourcePath = "openjiuwen/agent_teams/agent/prompts/"
                + normalizedLanguage + "/" + templateName + ".md";
        ClassLoader loader = TeamRail.class.getClassLoader();
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

    private static String buildCnHittBody(TeamRole role, List<String> names, String selfMemberName) {
        String roster = formatHumanAgentRoster(names, "cn");
        if (role == TeamRole.LEADER) {
            return "# HITT — 人类成员协作规则\n\n"
                    + roster + "。他们是真实人类操作者的代理，与你和其它 teammate 平等。"
                    + "所有 role=human_agent 的成员都适用下列规则：\n\n"
                    + "1. **禁止** 用 plain text 向任何人类成员发问或对话——所有定向"
                    + "沟通必须调用 `send_message(to=\"<human_member_name>\", ...)`，你的"
                    + "纯文本输出对方是看不到的。\n"
                    + "2. 可以通过 `update_task(task_id=..., assignee=\"<human_member_name>\")` "
                    + "把需要特定人类判断或操作的任务指派给对应成员。\n"
                    + "3. 一旦某个人类成员认领了任务（status=claimed），你 **不能** 取消"
                    + "（update_task status=cancelled）也 **不能** 改派（update_task "
                    + "assignee=<他人>），即使团队因人类没及时响应而停滞也必须保持停滞，"
                    + "只能用 `send_message` 催促对应人类成员。\n"
                    + "4. 每个人类成员始终是 ready 状态，不会进入 busy 或 shutdown，"
                    + "所以不要对它们调用 `shutdown_member` / `spawn_member`。\n"
                    + "5. 如果 user 表达了“我也要加入团队”之类的加入意图，且团队尚未"
                    + "创建，请在 `build_team` 时把 `enable_hitt=true`；若需要多个不同"
                    + "人类成员，通过 `predefined_members` 传入 role=human_agent 的 spec。\n";
        }
        if (role == TeamRole.TEAMMATE) {
            return "# HITT — 与人类成员协作\n\n"
                    + "团队里存在下列人类成员（真实人类）：" + roster + "。把他们视作普通 "
                    + "teammate：与他们交流一律通过 `send_message(to=<对应名字>, ...)`，"
                    + "不要假设他们会自动看到你的 plain text。他们可能拥有你无法完成的"
                    + "决策权或操作能力。\n";
        }
        if (role == TeamRole.HUMAN_AGENT) {
            String peers = selfMemberName != null && !selfMemberName.isEmpty()
                    ? "你的 member_name 是 `" + selfMemberName + "`。\n" : "";
            return "# HITT — 你是团队里的人类成员\n\n"
                    + roster + "。\n"
                    + peers
                    + "你是团队里真实人类操作者的代理，与 leader、teammate 平等。\n"
                    + "- 你只能通过 `send_message` 与团队交互；没有 `claim_task`、"
                    + "`update_task`、`spawn_member` 等工具。\n"
                    + "- Leader 通过 `update_task` 把任务指派给你后，你需要以对话方式"
                    + "与团队沟通进展；完成后通过 `send_message` 告知 leader。\n"
                    + "- 发送给你的消息一律自动标记已读，不会堆积未读。\n";
        }
        return null;
    }

    private static String buildEnHittBody(TeamRole role, List<String> names, String selfMemberName) {
        String roster = formatHumanAgentRoster(names, "en");
        if (role == TeamRole.LEADER) {
            return "# HITT — Collaborating with Human Members\n\n"
                    + roster + ". They represent real human operators and stand on "
                    + "equal footing with you and the other teammates. The following "
                    + "rules apply to every member whose role is `human_agent`:\n\n"
                    + "1. You **must not** address a human member via plain text — "
                    + "every direct exchange must go through "
                    + "`send_message(to=\"<human_member_name>\", ...)`. Your plain text "
                    + "output is not visible to human members.\n"
                    + "2. Use `update_task(task_id=..., "
                    + "assignee=\"<human_member_name>\")` to assign tasks that require a "
                    + "specific human's judgement or action.\n"
                    + "3. Once a human member claims a task (status=claimed) you "
                    + "**cannot** cancel it (`update_task status=cancelled`) and "
                    + "**cannot** reassign it (`update_task assignee=<someone>`). Even "
                    + "if the team stalls waiting for that human, it must stall — only "
                    + "`send_message` nudges to the specific human are allowed.\n"
                    + "4. Every human member stays READY forever; never call "
                    + "`shutdown_member` or `spawn_member` on them.\n"
                    + "5. If the user signals intent to join the team (e.g. \"I want "
                    + "to join\") and the team has not been created yet, call "
                    + "`build_team` with `enable_hitt=true`. If multiple distinct "
                    + "human members are needed, pass them via `predefined_members` "
                    + "as TeamMemberSpec entries with role=human_agent.\n";
        }
        if (role == TeamRole.TEAMMATE) {
            return "# HITT — Working with Human Members\n\n"
                    + "The team includes the following human members (real humans): "
                    + roster + ". Treat each of them as an ordinary teammate: every "
                    + "direct exchange must use `send_message(to=<their_name>, ...)`. "
                    + "Do not assume your plain text is visible to a human member; "
                    + "they may hold decisions or privileges you cannot execute.\n";
        }
        if (role == TeamRole.HUMAN_AGENT) {
            String peers = selfMemberName != null && !selfMemberName.isEmpty()
                    ? "Your member_name is `" + selfMemberName + "`.\n" : "";
            return "# HITT — You are a human member\n\n"
                    + roster + ".\n"
                    + peers
                    + "You represent the human operator on this team, equal in "
                    + "standing with the leader and teammates.\n"
                    + "- Your only tool is `send_message`; you do not have "
                    + "`claim_task`, `update_task`, `spawn_member`, etc.\n"
                    + "- When the leader assigns you a task via `update_task`, reply "
                    + "and coordinate through `send_message`. Announce completion "
                    + "through `send_message` too.\n"
                    + "- Every message addressed to you is auto-marked-read; there is "
                    + "no unread backlog on your side.\n";
        }
        return null;
    }

    private static String formatHumanAgentRoster(List<String> names, String language) {
        String quoted = String.join(", ", names.stream().map(name -> "`" + name + "`").toList());
        if ("cn".equals(language)) {
            return "注册的人类成员：" + quoted;
        }
        return "Registered human members: " + quoted;
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
        cnLabels.put("team_workspace_purpose", "用于存放团队共享文件（方案、设计、交付成果），所有成员通过该路径前缀读写同一份文件，系统自动管理版本和文件锁");
        cnLabels.put("team_workspace_abs", "绝对路径");
        cnLabels.put("members_heading", "# 成员关系");
        cnLabels.put("leader_mode_plan", "团队成员执行模式: plan_mode（成员领取任务后需先提交计划，由你通过 approve_plan 审批后才能执行）");
        cnLabels.put("leader_mode_build", "团队成员执行模式: build_mode（成员领取任务后自主执行并直接完成，无需你审批计划）");
        cnLabels.put("teammate_mode_plan", "你的执行模式: plan_mode（领取任务后必须先通过 write_plan 提交计划，等待 leader 通过 approve_plan 审批后才能开始执行）");
        cnLabels.put("teammate_mode_build", "你的执行模式: build_mode（领取任务后可自主执行并直接标记完成，无需 leader 审批计划）");
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
        enLabels.put("team_workspace_purpose", "Holds team-shared files (plans, designs, deliverables); all members read/write the same files through this path prefix. Versioning and file locks are managed automatically");
        enLabels.put("team_workspace_abs", "Absolute path");
        enLabels.put("members_heading", "# Relationships");
        enLabels.put("leader_mode_plan", "Teammate execution mode: plan_mode (teammates must submit a plan after claiming a task and wait for your approval via approve_plan before executing)");
        enLabels.put("leader_mode_build", "Teammate execution mode: build_mode (teammates execute and complete tasks autonomously without plan approval)");
        enLabels.put("teammate_mode_plan", "Your execution mode: plan_mode (after claiming a task you must submit a plan via write_plan and wait for the leader to approve it via approve_plan before executing)");
        enLabels.put("teammate_mode_build", "Your execution mode: build_mode (after claiming a task you execute autonomously and mark it completed without leader plan approval)");
        LABELS.put("en", enLabels);
    }

    private static Map<String, String> getLabels(String language) {
        return LABELS.getOrDefault(language, LABELS.get("cn"));
    }

    // -- Getters --------------------------------------------------------------

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }
}
