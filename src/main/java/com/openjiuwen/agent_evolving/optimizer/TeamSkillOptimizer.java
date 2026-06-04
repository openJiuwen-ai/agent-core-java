/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-based patch generation and rewrite support for team skills.
 *
 * <p>Mirrors Python's {@code TeamSkillOptimizer} in
 * {@code openjiuwen.agent_evolving.optimizer.team_skill_optimizer}.</p>
 */
public class TeamSkillOptimizer {

    private static final Logger LOGGER = Logger.getLogger(TeamSkillOptimizer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)```", Pattern.MULTILINE);

    public static final LlmResilience.LLMInvokePolicy TEAM_SKILL_PATCH_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(120, 420, 3, 1.0, true);

    public static final int PATCH_RETRY_SKILL_CONTENT_CHARS = 3000;
    public static final int PATCH_RETRY_TRAJECTORY_CHARS = 6000;
    public static final int TRAJECTORY_ISSUES_RETRY_CHARS = 2000;
    public static final int USER_INTENT_RETRY_CHARS = 500;
    public static final int SUMMARY_RETRY_CHARS = 200;

    public static final Map<String, String> PROPOSE_PROMPTS = new LinkedHashMap<>();
    public static final Map<String, String> PATCH_PROMPTS = new LinkedHashMap<>();
    public static final Map<String, String> USER_PATCH_PROMPTS = new LinkedHashMap<>();
    public static final Map<String, String> TRAJECTORY_PATCH_PROMPTS = new LinkedHashMap<>();

    private static final Set<String> KEY_TOOLS = new LinkedHashSet<>(
            List.of("spawn_member", "create_task", "build_team", "view_task", "send_message"));
    private static final int TOOL_BUDGET = 20_000;
    private static final int LLM_BUDGET = 10_000;

    static {
        PROPOSE_PROMPTS.put("cn", """
                你是多角色协作 Skill 设计专家。根据 AgentTeam 执行 trajectory，判断是否值得提炼一个可复用的 Team Skill。

                ## Trajectory 摘要
                {trajectory_summary}

                ## 已有 Team Skill 列表
                {existing_skill_names}

                如果值得提炼，输出 JSON：{"should_create": true, "name": "kebab-case-name",
                "description": "触发条件和适用场景", "body": "SKILL.md body", "reason": "原因",
                "roles": [{"id": "role-id", "skills": [], "tools": []}],
                "extra_files": {"roles/role-id.md": "角色说明", "workflow.md": "工作流", "bind.md": "约束"}}。
                如果不值得，输出 {"should_create": false, "reason": "..."}。只输出 JSON。
                """);
        PROPOSE_PROMPTS.put("en", """
                You are a multi-agent collaboration Skill designer. Decide whether the trajectory should be
                extracted into a reusable Team Skill.

                ## Trajectory Summary
                {trajectory_summary}

                ## Existing Team Skills
                {existing_skill_names}

                If useful, output JSON with should_create=true, name, description, body, reason, roles and
                extra_files. Otherwise output {"should_create": false, "reason": "..."}. Output JSON only.
                """);

        PATCH_PROMPTS.put("cn", """
                你是 Team Skill 演进专家。对比本次 AgentTeam trajectory 与当前 Team Skill 内容，判断是否有值得沉淀的经验。

                ## 当前 Team Skill 内容
                {skill_content}

                ## 本次 Trajectory 摘要
                {trajectory_summary}

                大多数情况应输出 need_patch=false。只有学到新的协作、流程、约束或排障经验时才输出 patch。
                输出 JSON：{"need_patch": true/false, "section": "章节名", "content": "Markdown 经验内容",
                "reason": "原因"}。只输出 JSON。
                """);
        PATCH_PROMPTS.put("en", """
                You are a Team Skill evolution expert. Compare this trajectory against the current Team Skill
                and decide whether new learnings should be captured.

                ## Current Team Skill Content
                {skill_content}

                ## Trajectory Summary
                {trajectory_summary}

                Most cases should return need_patch=false. Patch only when there is genuinely new insight.
                Output JSON with need_patch, section, content and reason. Output JSON only.
                """);

        USER_PATCH_PROMPTS.put("cn", """
                根据用户的改进意见，为团队技能生成演进 patch。

                当前团队技能：
                - 名称：{skill_name}
                - 描述：{description}
                - 角色：{roles_summary}
                - 工作流：{workflow_summary}

                用户意见：{user_intent}

                将用户意见归类到 Roles、Constraints、Collaboration、Instructions、Examples 或 Troubleshooting，
                并输出 JSON：{"section": "章节名", "action": "append", "content": "Markdown 经验内容"}。
                只输出 JSON。
                """);
        USER_PATCH_PROMPTS.put("en", """
                Based on the user's improvement suggestion, generate an evolution patch for the team skill.

                Current team skill:
                - Name: {skill_name}
                - Description: {description}
                - Roles: {roles_summary}
                - Workflow: {workflow_summary}

                User suggestion: {user_intent}

                Classify the feedback into Roles, Constraints, Collaboration, Instructions, Examples, or
                Troubleshooting. Output JSON with section, action=append and content. Output JSON only.
                """);

        TRAJECTORY_PATCH_PROMPTS.put("cn", """
                分析以下执行轨迹，判断团队技能是否需要演进。

                当前团队技能：{skill_content}
                执行轨迹：{trajectory_summary}
                轨迹分析发现的不足：{trajectory_issues}

                如果轨迹显示角色配合不当、约束被违反或流程低效，则生成 patch；多数情况 need_patch=false。
                输出 JSON：{"need_patch": true/false, "section": "章节名", "content": "Markdown 经验内容",
                "reason": "原因"}。只输出 JSON。
                """);
        TRAJECTORY_PATCH_PROMPTS.put("en", """
                Analyze the following execution trajectory and determine whether the team skill needs evolution.

                Current team skill: {skill_content}
                Trajectory summary: {trajectory_summary}
                Detected issues: {trajectory_issues}

                Generate a patch only when the trajectory shows skill deficiencies such as poor coordination,
                constraint violations or inefficient workflow. Most cases should return need_patch=false.
                Output JSON only.
                """);
    }

    private Model llm;
    private String model;
    private final String language;
    private final String debugDir;
    private final LlmResilience.LLMInvokePolicy patchLlmPolicy;

    public TeamSkillOptimizer(Model llm, String model) {
        this(llm, model, "cn", null, TEAM_SKILL_PATCH_LLM_POLICY);
    }

    public TeamSkillOptimizer(Model llm, String model, String language) {
        this(llm, model, language, null, TEAM_SKILL_PATCH_LLM_POLICY);
    }

    public TeamSkillOptimizer(Model llm, String model, String language, String debugDir,
                              LlmResilience.LLMInvokePolicy patchLlmPolicy) {
        this.llm = llm;
        this.model = model != null ? model : "";
        this.language = language != null ? language : "cn";
        this.debugDir = debugDir;
        this.patchLlmPolicy = patchLlmPolicy != null ? patchLlmPolicy : TEAM_SKILL_PATCH_LLM_POLICY;
    }

    public String getLanguage() {
        return language;
    }

    public Model getLlm() {
        return llm;
    }

    public String getModel() {
        return model;
    }

    public LlmResilience.LLMInvokePolicy getPatchLlmPolicy() {
        return patchLlmPolicy;
    }

    public String getDebugDir() {
        return debugDir;
    }

    public void updateLlm(Model llm, String model) {
        this.llm = llm;
        this.model = model != null ? model : "";
    }

    /**
     * Analyze trajectory against existing skill and generate a patch if warranted.
     */
    public CompletableFuture<Optional<EvolutionRecord>> generatePatch(
            Trajectory trajectory,
            String skillName,
            String currentSkillContent) {
        return CompletableFuture.supplyAsync(() -> runUnchecked(() ->
                generatePatchSync(trajectory, skillName, currentSkillContent)));
    }

    /**
     * Compatibility overload for older call sites that typed trajectories as {@code Object}.
     */
    public CompletableFuture<Optional<EvolutionRecord>> generatePatch(
            Object trajectory,
            String skillName,
            String currentSkillContent) {
        return generatePatch(asTrajectory(trajectory), skillName, currentSkillContent);
    }

    public CompletableFuture<Optional<EvolutionRecord>> generateUserPatch(
            Trajectory trajectory,
            String skillName,
            String userIntent) {
        return CompletableFuture.supplyAsync(() -> runUnchecked(() ->
                generateUserPatchSync(trajectory, skillName, userIntent)));
    }

    public CompletableFuture<Optional<EvolutionRecord>> generateTrajectoryPatch(
            Trajectory trajectory,
            String skillName,
            String currentSkillContent,
            List<Map<String, Object>> trajectoryIssues) {
        return CompletableFuture.supplyAsync(() -> runUnchecked(() ->
                generateTrajectoryPatchSync(trajectory, skillName, currentSkillContent, trajectoryIssues)));
    }

    /**
     * Propose a new Team Skill from a trajectory.
     */
    public CompletableFuture<Optional<Map<String, Object>>> proposeNewSkill(
            Trajectory trajectory,
            String existingSkillNames) {
        return CompletableFuture.supplyAsync(() -> runUnchecked(() -> {
            String prompt = promptFor(PROPOSE_PROMPTS)
                    .replace("{trajectory_summary}", buildTrajectorySummary(trajectory))
                    .replace("{existing_skill_names}", existingSkillNames != null ? existingSkillNames : "");
            String raw = callLlm(prompt, null, null, null);
            Map<String, Object> parsed = parseJsonFromLlm(raw);
            if (parsed == null || !asBoolean(parsed.getOrDefault("should_create", false))) {
                return Optional.empty();
            }
            return Optional.of(parsed);
        }));
    }

    public CompletableFuture<Optional<String>> regenerateBody(
            String skillName,
            String currentBody,
            List<?> evolutionRecords,
            String userIntent) {
        return CompletableFuture.supplyAsync(() -> runUnchecked(() -> {
            String evoSummary = buildEvolutionSummary(evolutionRecords);
            String intentSection = userIntent == null || userIntent.isBlank()
                    ? "" : "\n\n## User Intent\n" + userIntent;
            String prompt = """
                    You are a multi-role Team Skill documentation rewrite expert. Rewrite the body using
                    the current Team Skill body and accumulated evolution records.

                    ## Current Team Skill: {skill_name}

                    ```markdown
                    {current_body}
                    ```

                    ## Evolution Records
                    {evolution_summary}{intent_section}

                    Requirements:
                    1. Keep YAML frontmatter out of the output.
                    2. Integrate valuable evolution experiences into the body.
                    3. Keep role sub-file references.
                    4. Output Markdown body only.
                    """
                    .replace("{skill_name}", safe(skillName))
                    .replace("{current_body}", truncate(currentBody, 8000))
                    .replace("{evolution_summary}", evoSummary)
                    .replace("{intent_section}", intentSection);
            String raw = callLlm(prompt, null, null, null);
            String body = raw != null ? raw.strip() : "";
            return body.length() < 50 ? Optional.empty() : Optional.of(body);
        }));
    }

    public CompletableFuture<Optional<String>> regenerateBody(
            String skillName,
            String currentBody,
            List<?> evolutionRecords) {
        return regenerateBody(skillName, currentBody, evolutionRecords, null);
    }

    private Optional<EvolutionRecord> generatePatchSync(
            Trajectory trajectory,
            String skillName,
            String currentSkillContent) throws Exception {
        String summary = buildTrajectorySummary(trajectory);
        String skillContent = currentSkillContent != null ? currentSkillContent : "";
        LOGGER.info(() -> "[TeamSkillOptimizer] patch: skill='" + skillName + "', summary_len="
                + summary.length() + ", content_len=" + skillContent.length());

        String template = promptFor(PATCH_PROMPTS);
        String prompt = template
                .replace("{skill_content}", truncate(skillContent, 15_000))
                .replace("{trajectory_summary}", summary);
        String retryPrompt = template
                .replace("{skill_content}", truncate(skillContent, PATCH_RETRY_SKILL_CONTENT_CHARS))
                .replace("{trajectory_summary}", truncate(summary, PATCH_RETRY_TRAJECTORY_CHARS));

        String raw = callLlm(prompt, retryPrompt, patchLlmPolicy, text -> parseJsonFromLlm(text) != null);
        Map<String, Object> parsed = parseJsonFromLlm(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("TeamSkill patch response could not be parsed as JSON");
        }
        if (!asBoolean(parsed.getOrDefault("need_patch", false))) {
            LOGGER.info(() -> "[TeamSkillOptimizer] patch: no patch needed for '" + skillName + "'");
            return Optional.empty();
        }

        String section = stringValue(parsed.get("section"), "Instructions");
        String content = stringValue(parsed.get("content"), "");
        if (content.strip().isEmpty()) {
            throw new IllegalArgumentException("TeamSkill patch response contained empty content");
        }
        EvolutionPatch patch = EvolutionPatch.builder()
                .section(section)
                .action("append")
                .content(content)
                .target(EvolutionTarget.BODY)
                .build();
        EvolutionRecord record = EvolutionRecord.make(
                "team_skill_evolution",
                stringValue(parsed.get("reason"), "Auto-detected from trajectory"),
                patch,
                0.6,
                null);
        return Optional.of(record);
    }

    private Optional<EvolutionRecord> generateUserPatchSync(
            Trajectory trajectory,
            String skillName,
            String userIntent) throws Exception {
        String summary = buildTrajectorySummary(trajectory);
        String rolesSummary = deriveRolesSummary(summary);
        String workflowSummary = summary.toLowerCase().contains("workflow") || summary.toLowerCase().contains("mermaid")
                ? "Present in trajectory" : "N/A";
        String template = promptFor(USER_PATCH_PROMPTS);
        String prompt = template
                .replace("{skill_name}", safe(skillName))
                .replace("{description}", "team-skill")
                .replace("{roles_summary}", rolesSummary)
                .replace("{workflow_summary}", workflowSummary)
                .replace("{user_intent}", safe(userIntent));
        String retryPrompt = template
                .replace("{skill_name}", safe(skillName))
                .replace("{description}", "team-skill")
                .replace("{roles_summary}", truncate(rolesSummary, SUMMARY_RETRY_CHARS))
                .replace("{workflow_summary}", truncate(workflowSummary, SUMMARY_RETRY_CHARS))
                .replace("{user_intent}", truncate(userIntent, USER_INTENT_RETRY_CHARS));

        String raw = callLlm(prompt, retryPrompt, patchLlmPolicy, text -> parseJsonFromLlm(text) != null);
        Map<String, Object> parsed = parseJsonFromLlm(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("TeamSkill user patch response could not be parsed as JSON");
        }
        String section = stringValue(parsed.get("section"), "Instructions");
        String content = stringValue(parsed.get("content"), "");
        if (content.strip().isEmpty()) {
            throw new IllegalArgumentException("TeamSkill user patch response contained empty content");
        }

        EvolutionPatch patch = EvolutionPatch.builder()
                .section(section)
                .action("append")
                .content(content)
                .target(EvolutionTarget.BODY)
                .build();
        EvolutionRecord record = EvolutionRecord.make(
                "team_skill_user_patch",
                "User intent: " + truncate(userIntent, 200),
                patch,
                0.6,
                null);
        return Optional.of(record);
    }

    private Optional<EvolutionRecord> generateTrajectoryPatchSync(
            Trajectory trajectory,
            String skillName,
            String currentSkillContent,
            List<Map<String, Object>> trajectoryIssues) throws Exception {
        String summary = buildTrajectorySummary(trajectory);
        String issuesText = toJson(trajectoryIssues == null ? List.of() : trajectoryIssues);
        String template = promptFor(TRAJECTORY_PATCH_PROMPTS);
        String prompt = template
                .replace("{skill_content}", truncate(currentSkillContent, 15_000))
                .replace("{trajectory_summary}", summary)
                .replace("{trajectory_issues}", truncate(issuesText, 5000));
        String retryPrompt = template
                .replace("{skill_content}", truncate(currentSkillContent, PATCH_RETRY_SKILL_CONTENT_CHARS))
                .replace("{trajectory_summary}", truncate(summary, PATCH_RETRY_TRAJECTORY_CHARS))
                .replace("{trajectory_issues}", truncate(issuesText, TRAJECTORY_ISSUES_RETRY_CHARS));

        String raw = callLlm(prompt, retryPrompt, patchLlmPolicy, text -> parseJsonFromLlm(text) != null);
        Map<String, Object> parsed = parseJsonFromLlm(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("TeamSkill trajectory patch response could not be parsed as JSON");
        }
        if (!asBoolean(parsed.getOrDefault("need_patch", false))) {
            LOGGER.info(() -> "[TeamSkillOptimizer] trajectory_patch: no patch needed for '" + skillName + "'");
            return Optional.empty();
        }
        String section = stringValue(parsed.get("section"), "Workflow");
        String content = stringValue(parsed.get("content"), "");
        if (content.strip().isEmpty()) {
            throw new IllegalArgumentException("TeamSkill trajectory patch response contained empty content");
        }

        EvolutionPatch patch = EvolutionPatch.builder()
                .section(section)
                .action("append")
                .content(content)
                .target(EvolutionTarget.BODY)
                .build();
        EvolutionRecord record = EvolutionRecord.make(
                "team_skill_trajectory_patch",
                "Trajectory issues: " + truncate(issuesText, 200),
                patch,
                0.6,
                null);
        return Optional.of(record);
    }

    private String callLlm(String prompt, String retryPrompt, LlmResilience.LLMInvokePolicy policy,
                           Predicate<String> isResultUsable) throws Exception {
        if (llm == null) {
            throw new IllegalStateException("LLM client is required");
        }
        LOGGER.info(() -> "[TeamSkillOptimizer] LLM call start: model=" + model
                + ", prompt_len=" + (prompt != null ? prompt.length() : 0));
        long started = System.nanoTime();
        String result;
        if (policy == null) {
            Object response = llm.invoke(
                    Collections.singletonList(new UserMessage(prompt)),
                    null,
                    null,
                    null,
                    model,
                    null,
                    null,
                    null,
                    null,
                    null);
            result = responseToText(response);
        } else {
            result = LlmResilience.invokeTextWithRetry(
                    llm,
                    model,
                    prompt,
                    policy,
                    retryPrompt,
                    null,
                    isResultUsable);
        }
        double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
        LOGGER.info(() -> "[TeamSkillOptimizer] LLM call done: " + String.format("%.1f", elapsed)
                + "s, response_len=" + (result != null ? result.length() : 0));
        return result != null ? result : "";
    }

    /**
     * Build a concise text summary of the trajectory for LLM consumption.
     */
    public static String buildTrajectorySummary(Trajectory trajectory) {
        if (trajectory == null || trajectory.getSteps() == null) {
            return "";
        }
        List<String> toolLines = new ArrayList<>();
        List<String> llmLines = new ArrayList<>();
        int toolCount = 0;
        int llmCount = 0;

        for (TrajectoryStep step : trajectory.getSteps()) {
            if (step == null) {
                continue;
            }
            String kind = step.getKind();
            Object detail = step.getDetail();
            if ("tool".equals(kind) && hasAnyDetail(step)) {
                toolCount++;
                String toolName = firstNonBlank(
                        stringValue(readValue(detail, "tool_name"), null),
                        stringValue(readValue(detail, "toolName"), null),
                        stringValue(readMap(step.getMeta(), "tool_name"), null),
                        stringValue(readMap(step.getMeta(), "toolName"), null),
                        step.getOperatorId(),
                        "unknown");
                boolean keyTool = KEY_TOOLS.contains(toolName);
                String args = truncate(firstNonBlank(
                        stringValue(readValue(detail, "call_args"), null),
                        stringValue(readValue(detail, "callArgs"), null),
                        stringValue(readValue(detail, "args"), null),
                        stringValue(step.getInputs(), "")), keyTool ? 500 : 150);
                String result = truncate(firstNonBlank(
                        stringValue(readValue(detail, "call_result"), null),
                        stringValue(readValue(detail, "callResult"), null),
                        stringValue(readValue(detail, "result"), null),
                        stringValue(step.getOutputs(), "")), keyTool ? 500 : 200);
                toolLines.add("[Tool:" + toolName + "] args=" + args + " result=" + result);
            } else if ("llm".equals(kind) && hasAnyDetail(step)) {
                llmCount++;
                String response = firstNonBlank(
                        stringValue(readValue(detail, "response"), null),
                        stringValue(readValue(detail, "content"), null),
                        stringValue(step.getOutputs(), ""));
                if (!response.isBlank()) {
                    llmLines.add("[LLM] " + truncate(response, 300));
                }
            }
        }

        String toolSection = truncateSection(String.join("\n", toolLines), TOOL_BUDGET, "tool");
        String llmSection = truncateSection(String.join("\n", llmLines), LLM_BUDGET, "LLM");
        String full = "### Tool Calls (" + toolCount + ")\n" + toolSection
                + "\n\n### LLM Responses (" + llmCount + ")\n" + llmSection;
        LOGGER.info("[TeamSkillOptimizer] trajectory summary: " + llmCount + " LLM steps, "
                + toolCount + " tool steps, total_len=" + full.length());
        return full;
    }

    public static Map<String, Object> parseJson(String raw) {
        return parseJsonFromLlm(raw);
    }

    static Map<String, Object> parseJsonFromLlm(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw);
        if (matcher.find()) {
            candidates.add(matcher.group(1).strip());
        }

        candidates.add(raw.strip());
        candidates.add(fixJsonText(raw));

        String balanced = extractBalanced(raw, '{', '}');
        if (balanced != null) {
            candidates.add(balanced);
            candidates.add(fixJsonText(balanced));
        }

        Set<String> seen = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank() || !seen.add(candidate)) {
                continue;
            }
            Map<String, Object> parsed = tryParseMap(candidate);
            if (parsed != null) {
                return parsed;
            }
        }

        String head = raw.substring(0, Math.min(600, raw.length())).replace("\n", "\\n");
        LOGGER.warning("[TeamSkillOptimizer] JSON parse failed, raw_len=" + raw.length() + ", head=" + head);
        return null;
    }

    static String fixJsonText(String text) {
        String value = text != null ? text.strip() : "";
        value = value.replaceAll("(?m)^```(?:json)?\\s*", "");
        value = value.replaceAll("(?m)```\\s*$", "");
        value = value.replaceAll("//[^\\n]*", "");
        value = value.replaceAll(",\\s*([}\\]])", "$1");
        return value.strip();
    }

    static String extractBalanced(String text, char opener, char closer) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf(opener);
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == opener) {
                depth++;
            } else if (ch == closer) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static Map<String, Object> tryParseMap(String text) {
        try {
            return MAPPER.readValue(text, MAP_TYPE);
        } catch (JsonProcessingException exc) {
            return null;
        }
    }

    private static String buildEvolutionSummary(List<?> evolutionRecords) {
        if (evolutionRecords == null || evolutionRecords.isEmpty()) {
            return "(no evolutions)";
        }
        List<String> lines = new ArrayList<>();
        int count = Math.min(evolutionRecords.size(), 20);
        for (int i = 0; i < count; i++) {
            Object record = evolutionRecords.get(i);
            Object change = readValue(record, "change");
            String id = stringValue(readValue(record, "id"), "?");
            String section = stringValue(readValue(change, "section"), "?");
            String content = truncate(stringValue(readValue(change, "content"), ""), 200);
            lines.add("- [" + id + "] " + section + ": " + content);
        }
        return String.join("\n", lines);
    }

    private static String deriveRolesSummary(String summary) {
        if (summary == null || !summary.contains("spawn_member")) {
            return "N/A";
        }
        Matcher matcher = Pattern.compile("role[_-]?([a-zA-Z0-9_-]+)").matcher(summary);
        List<String> roles = new ArrayList<>();
        while (matcher.find() && roles.size() < 5) {
            String role = matcher.group(1);
            if (!roles.contains(role)) {
                roles.add(role);
            }
        }
        return roles.isEmpty() ? "N/A" : String.join(", ", roles);
    }

    private static Object readMap(Map<String, Object> map, String key) {
        return map != null ? map.get(key) : null;
    }

    private static Object readValue(Object source, String snakeOrCamelName) {
        if (source == null || snakeOrCamelName == null) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            Object value = map.get(snakeOrCamelName);
            if (value != null) {
                return value;
            }
            return map.get(toSnakeOrCamelPeer(snakeOrCamelName));
        }

        String camel = toCamelCase(snakeOrCamelName);
        for (String getter : List.of("get" + capitalize(camel), "is" + capitalize(camel), camel, snakeOrCamelName)) {
            try {
                Method method = source.getClass().getMethod(getter);
                method.setAccessible(true);
                return method.invoke(source);
            } catch (ReflectiveOperationException ignored) {
                // Try the next accessor form.
            }
        }
        for (String fieldName : List.of(camel, snakeOrCamelName)) {
            try {
                Field field = source.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (ReflectiveOperationException ignored) {
                // Try the next field form.
            }
        }
        return null;
    }

    private static String responseToText(Object response) {
        if (response == null) {
            return "";
        }
        if (response instanceof BaseMessage message) {
            Object content = message.getContent();
            return content != null ? String.valueOf(content) : "";
        }
        if (response instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null) {
                return String.valueOf(content);
            }
            Object text = map.get("text");
            return text != null ? String.valueOf(text) : "";
        }
        return String.valueOf(response);
    }

    private String promptFor(Map<String, String> prompts) {
        return prompts.getOrDefault(language, prompts.getOrDefault("en", prompts.values().iterator().next()));
    }

    private static boolean hasAnyDetail(TrajectoryStep step) {
        return step.getDetail() != null || step.getInputs() != null || step.getOutputs() != null
                || (step.getMeta() != null && !step.getMeta().isEmpty());
    }

    private static String truncateSection(String text, int budget, String label) {
        if (text == null || text.length() <= budget) {
            return text != null ? text : "";
        }
        return text.substring(0, budget) + "\n... (" + label + " section truncated)";
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exc) {
            return String.valueOf(value);
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static String stringValue(Object value, String defaultValue) {
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.strip());
        }
        return false;
    }

    private static String toCamelCase(String name) {
        if (name == null || !name.contains("_")) {
            return name;
        }
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char ch : name.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String toSnakeOrCamelPeer(String name) {
        if (name == null) {
            return null;
        }
        if (name.contains("_")) {
            return toCamelCase(name);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                builder.append('_').append(Character.toLowerCase(ch));
            } else {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Trajectory asTrajectory(Object trajectory) {
        if (trajectory == null || trajectory instanceof Trajectory) {
            return (Trajectory) trajectory;
        }
        throw new IllegalArgumentException("trajectory must be a Trajectory");
    }

    private static <T> T runUnchecked(CheckedSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new CompletionException(exc);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
