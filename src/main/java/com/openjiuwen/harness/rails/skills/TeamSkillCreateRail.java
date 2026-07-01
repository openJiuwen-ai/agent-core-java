/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent rail for team-skill creation proposals.
 *
 * <p>Mirrors Python's {@code TeamSkillCreateRail} in
 * {@code openjiuwen/harness/rails/skills/team_skill_create_rail.py}.</p>
 */
public class TeamSkillCreateRail extends EvolutionRail {

    private static final String DEFAULT_SESSION_ID = "__default__";
    private static final Set<String> TEAM_SKILL_KINDS = Set.of("team-skill", "swarm-skill");
    private static final String FOLLOW_UP_PROMPT_CN = """
            **重要：你必须先向用户确认，不可跳过此步骤。**
            系统检测到对话中 spawn 了多个团队成员，可能值得创建团队技能。请按以下步骤执行：
            1. 直接询问或调用 ask_user 工具向用户确认：
               - 问题："我检测到多 Agent 协作模式可能值得创建为团队技能。是否创建？"
               - 选项：["创建"，"跳过"，"自定义指令：（请描述需求）"]
            2. 如果用户选择"创建"或提供了自定义指令，请调用 **team-skill-creator** 技能，根据用户的要求和当前对话上下文执行团队技能创建。
               新技能应保存到技能目录：%s""";
    private static final String FOLLOW_UP_PROMPT_EN = """
            **Important: You MUST confirm with the user first. Do not skip this step.**
            The system detected multiple team member spawns that may be worth creating as a Team Skill. Please follow these steps:
            1. Directly inquire or invoke the `ask_user` tool to confirm with the user:
               - Question: "I detected a multi-agent collaboration pattern that may be worth creating as a Team Skill. Create it?"
               - Options: ["Create", "Skip", "Custom instruction: (describe your needs)"]
            2. If user chooses "Create" or provides a custom instruction, invoke the **team-skill-creator** skill to execute the team skill creation.
               Save the new skill to: %s""";

    private final Path skillsDir;
    private final String language;
    private final boolean autoTrigger;
    private final int minTeamMembersForCreate;
    private String completedSessionId;
    private final Map<String, Integer> proposedSpawnCounts = new LinkedHashMap<>();

    public TeamSkillCreateRail(Path skillsDir) {
        this(skillsDir, "cn", true, 2);
    }

    public TeamSkillCreateRail(Path skillsDir, String language, boolean autoTrigger, int minTeamMembersForCreate) {
        super(100, EvolutionTriggerPoint.NONE, true, Set.of());
        setPriority(85);
        this.skillsDir = skillsDir;
        this.language = "en".equals(language) ? "en" : "cn";
        this.autoTrigger = autoTrigger;
        this.minTeamMembersForCreate = minTeamMembersForCreate;
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        super.afterTaskIteration(ctx);
        maybeEnqueueCreationFollowUp(ctx);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        super.afterInvoke(ctx);
        maybeEnqueueCreationFollowUp(ctx);
    }

    public boolean notifyTeamCompleted(CallbackContext ctx) {
        if (!autoTrigger) {
            return false;
        }
        String sessionId = currentSessionId(ctx);
        if (sessionId == null) {
            return false;
        }
        completedSessionId = sessionId;
        return true;
    }

    public boolean shouldProposeNewTeamSkill() {
        return countSpawnMemberCalls() >= minTeamMembersForCreate;
    }

    public int countSpawnMemberCalls() {
        int count = 0;
        for (Map<String, Object> step : buildTrajectory()) {
            Object values = step.get("values");
            if (!(values instanceof Map<?, ?> map)) {
                continue;
            }
            Object name = map.get("tool_name");
            if (name != null && String.valueOf(name).contains("spawn_member")) {
                count += 1;
            }
        }
        return count;
    }

    public Path getSkillsDir() {
        return skillsDir;
    }

    public boolean isAutoTrigger() {
        return autoTrigger;
    }

    public int getMinTeamMembersForCreate() {
        return minTeamMembersForCreate;
    }

    public EvolutionTriggerPoint getEvolutionTriggerPoint() {
        return EvolutionTriggerPoint.NONE;
    }

    private boolean maybeEnqueueCreationFollowUp(CallbackContext ctx) {
        String sessionId = currentSessionId(ctx);
        int spawnCount = countSpawnMemberCalls();
        if (!canEnqueueCreationFollowUp(sessionId, spawnCount)) {
            return false;
        }
        String prompt = buildFollowUpPrompt();
        ctx.put("team_skill_create_follow_up", prompt);
        if (ctx.getAgent() != null) {
            ctx.getAgent().loopController().enqueueFollowUp(prompt);
        }
        proposedSpawnCounts.put(sessionId, spawnCount);
        if (sessionId.equals(completedSessionId)) {
            completedSessionId = null;
        }
        return true;
    }

    private boolean canEnqueueCreationFollowUp(String sessionId, int spawnCount) {
        if (!autoTrigger || sessionId == null || !sessionId.equals(completedSessionId)) {
            return false;
        }
        if (spawnCount < minTeamMembersForCreate) {
            return false;
        }
        if (spawnCount <= proposedSpawnCounts.getOrDefault(sessionId, 0)) {
            return false;
        }
        return detectUsedTeamSkill() == null;
    }

    private String buildFollowUpPrompt() {
        String dir = skillsDir == null ? "" : skillsDir.toString();
        if ("en".equals(language)) {
            return FOLLOW_UP_PROMPT_EN.formatted(dir);
        }
        return FOLLOW_UP_PROMPT_CN.formatted(dir);
    }

    private String currentSessionId(CallbackContext ctx) {
        String fromContext = sessionIdFromValues(ctx == null ? null : ctx.getValues());
        if (fromContext != null) {
            return fromContext;
        }
        List<Map<String, Object>> trajectory = buildTrajectory();
        for (int i = trajectory.size() - 1; i >= 0; i--) {
            Map<String, Object> step = trajectory.get(i);
            Object values = step.get("values");
            if (values instanceof Map<?, ?> map) {
                String sessionId = sessionIdFromValues(map);
                if (sessionId != null) {
                    return sessionId;
                }
            }
        }
        return buildTrajectory().isEmpty() ? null : DEFAULT_SESSION_ID;
    }

    private static String sessionIdFromValues(Map<?, ?> values) {
        if (values == null) {
            return null;
        }
        Object conversationId = values.get("conversation_id");
        if (conversationId != null && !String.valueOf(conversationId).isBlank()) {
            return String.valueOf(conversationId);
        }
        Object sessionId = values.get("session_id");
        if (sessionId != null && !String.valueOf(sessionId).isBlank()) {
            return String.valueOf(sessionId);
        }
        return null;
    }

    private String detectUsedTeamSkill() {
        Set<String> knownTeamSkills = knownTeamSkillNames();
        if (knownTeamSkills.isEmpty()) {
            return null;
        }
        for (Map<String, Object> step : buildTrajectory()) {
            Object values = step.get("values");
            if (!(values instanceof Map<?, ?> map)) {
                continue;
            }
            Object callArgs = map.get("call_args");
            String fromArgs = skillNameFromPayload(callArgs, knownTeamSkills);
            if (fromArgs != null) {
                return fromArgs;
            }
            String fromText = skillNameFromText(callArgs, knownTeamSkills);
            if (fromText != null) {
                return fromText;
            }
            String fromResult = skillNameFromText(map.get("call_result"), knownTeamSkills);
            if (fromResult != null) {
                return fromResult;
            }
        }
        return null;
    }

    private Set<String> knownTeamSkillNames() {
        Set<String> names = new java.util.LinkedHashSet<>();
        if (skillsDir == null || !Files.isDirectory(skillsDir)) {
            return names;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(skillsDir)) {
            for (Path child : children) {
                Path skillMd = child.resolve("SKILL.md");
                if (!Files.isRegularFile(skillMd)) {
                    continue;
                }
                String kind = parseTopLevelKind(skillMd);
                if (TEAM_SKILL_KINDS.contains(kind)) {
                    names.add(child.getFileName().toString());
                }
            }
        } catch (IOException exception) {
            return Set.of();
        }
        return names;
    }

    private static String parseTopLevelKind(Path skillMd) {
        try {
            String content = Files.readString(skillMd, StandardCharsets.UTF_8);
            if (!content.startsWith("---")) {
                return null;
            }
            String[] lines = content.split("\\R");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].strip();
                if ("---".equals(line)) {
                    break;
                }
                if (line.startsWith("kind:")) {
                    return line.substring("kind:".length()).strip().replace("\"", "").replace("'", "");
                }
            }
        } catch (IOException exception) {
            return null;
        }
        return null;
    }

    private static String skillNameFromPayload(Object payload, Set<String> knownTeamSkills) {
        if (!(payload instanceof Map<?, ?> map)) {
            return null;
        }
        Object skillName = map.get("skill_name");
        if (skillName != null && knownTeamSkills.contains(String.valueOf(skillName))) {
            return String.valueOf(skillName);
        }
        Object path = map.get("path");
        if (path != null) {
            String detected = skillNameFromText(path, knownTeamSkills);
            if (detected != null) {
                return detected;
            }
        }
        return skillNameFromText(map.get("relative_file_path"), knownTeamSkills);
    }

    private static String skillNameFromText(Object text, Set<String> knownTeamSkills) {
        if (text == null) {
            return null;
        }
        String value = String.valueOf(text);
        for (String name : knownTeamSkills) {
            if (value.contains(name)) {
                return name;
            }
        }
        return null;
    }
}
