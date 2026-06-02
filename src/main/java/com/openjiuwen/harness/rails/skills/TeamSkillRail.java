/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.checkpointing.PendingChange;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.optimizer.TeamSkillOptimizer;
import com.openjiuwen.agent_evolving.optimizer.skill_call.ExperienceScorer;
import com.openjiuwen.agent_evolving.trajectory.TeamTrajectoryAggregator;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStore;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.memory.lite.Frontmatter;
import com.openjiuwen.core.operator.skill_call.SkillCallOperator;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Team skill evolution rail - counterpart of SkillEvolutionRail for team skills.
 *
 * <p>Mirrors Python's {@code TeamSkillRail} in
 * {@code openjiuwen.harness.rails.skills.team_skill_rail}.</p>
 */
public class TeamSkillRail extends EvolutionRail {

    private static final Logger LOG = LoggerFactory.getLogger(TeamSkillRail.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS =
            new TypeReference<>() {
            };
    private static final TypeReference<Map<String, Object>> MAP_OF_OBJECTS =
            new TypeReference<>() {
            };
    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)```", Pattern.MULTILINE);
    private static final Pattern LEGACY_SKILL_MD_PATTERN =
            Pattern.compile("[/\\\\]([^/\\\\]+)[/\\\\]SKILL\\.md", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILLS_PATH_PATTERN =
            Pattern.compile("[/\\\\]skills[/\\\\]([^/\\\\]+)(?=[/\\\\])", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_SKILL_TOOL_PATTERN =
            Pattern.compile("\\bskill_tool\\s*\\(\\s*skill_name\\s*=\\s*['\"]?([A-Za-z0-9._-]+)['\"]?",
                    Pattern.CASE_INSENSITIVE);

    public static final double DEFAULT_TEAM_EVOLUTION_TOTAL_TIMEOUT_SECS = 600.0;
    public static final LlmResilience.LLMInvokePolicy TEAM_USER_REQUEST_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(30, 90, 3, 1.0, true);
    public static final LlmResilience.LLMInvokePolicy TEAM_TRAJECTORY_ISSUE_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(60, 180, 3, 1.0, true);
    public static final LlmResilience.LLMInvokePolicy TEAM_PATCH_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(120, 420, 3, 1.0, true);

    private static final String USER_REQUEST_PROMPT_CN = """
            判断以下用户输入是否包含对当前团队任务或团队协作方式的改进意见。
            如果是，提取改进意图的摘要。

            团队技能描述：{team_skill_description}
            当前角色：{roles}
            用户输入：{user_messages}

            输出 JSON: {"is_improvement": true/false, "intent": "str"}
            """;
    private static final String USER_REQUEST_PROMPT_EN = """
            Determine if the following user input contains improvement suggestions for the current team task
            or collaboration approach. If yes, extract a summary of the improvement intent.

            Team skill description: {team_skill_description}
            Current roles: {roles}
            User input: {user_messages}

            Output JSON: {"is_improvement": true/false, "intent": "str"}
            """;
    private static final String TRAJECTORY_ISSUE_PROMPT_CN = """
            分析以下执行轨迹，判断团队技能是否存在不足需要演进。

            当前团队技能：
            {skill_content}

            执行轨迹摘要：
            {trajectory_summary}

            请从角色配合、约束遵循、流程效率、角色能力几个维度分析。
            如果存在不足，输出 JSON 数组：
            [{"issue_type": str, "description": str, "affected_role": str, "severity": "low"|"medium"|"high"}]
            如果没有问题，输出空数组 []。
            """;
    private static final String TRAJECTORY_ISSUE_PROMPT_EN = """
            Analyze the following execution trajectory and determine whether the team skill has deficiencies.

            Current team skill:
            {skill_content}

            Trajectory summary:
            {trajectory_summary}

            Analyze role coordination, constraint violations, workflow inefficiency, and role capability gaps.
            If issues exist, output a JSON array:
            [{"issue_type": str, "description": str, "affected_role": str, "severity": "low"|"medium"|"high"}]
            If no issues, output empty array [].
            """;

    private static final String REBUILD_PROMPT_TEMPLATE_CN = """
            你收到了一个团队技能的重建请求。旧版本已归档，请执行以下步骤：

            ## 已筛选的历史演进经验（score >= {min_score}）

            {evolution_records}

            ## 用户意图

            {user_intent}

            ## 执行要求

            请调用 teamskill-creator 技能：
            1. 基于以上历史经验和用户意图，生成新的 SKILL.md
            2. 重置 evolutions.json 为空列表

            旧版本已归档至 archive/ 目录，可直接创建新版本。""";
    private static final String REBUILD_PROMPT_TEMPLATE_EN = """
            You received a team skill rebuild request. Old version has been archived. Please follow these steps:

            ## Filtered Historical Evolution Records (score >= {min_score})

            {evolution_records}

            ## User Intent

            {user_intent}

            ## Execution Requirements

            Please invoke the teamskill-creator skill:
            1. Generate new SKILL.md based on the historical records and user intent above
            2. Reset evolutions.json to empty list

            Old version has been archived to archive/ directory, you can directly create the new version.""";

    private final TeamSkillStore store;
    private TeamSkillOptimizer optimizer;
    private ExperienceScorer scorer;
    private final boolean autoSave;
    private final boolean asyncEvolution;
    private final LlmResilience.LLMInvokePolicy userRequestLlmPolicy;
    private final LlmResilience.LLMInvokePolicy trajectoryIssueLlmPolicy;
    private final double evolutionTotalTimeoutSecs;
    private final Map<String, PendingChange> pendingPatchSnapshots = new ConcurrentHashMap<>();
    private final Map<String, SkillCallOperator> patchSkillOps = new ConcurrentHashMap<>();
    private final List<OutputSchema> pendingApprovalEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> pendingEvolutionOutcomes =
            Collections.synchronizedList(new ArrayList<>());
    private final TrajectoryStore teamTrajectoryStore;
    private volatile boolean evolutionInProgress;
    private String teamId;
    private Path trajectoriesDir;
    private TrajectoryBuilder builder;

    public TeamSkillRail() {
        this(new FileEvolutionStore(defaultSkillsDir()), null, "", "cn", false, true,
                TEAM_USER_REQUEST_LLM_POLICY, TEAM_TRAJECTORY_ISSUE_LLM_POLICY, TEAM_PATCH_LLM_POLICY,
                ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY,
                DEFAULT_TEAM_EVOLUTION_TOTAL_TIMEOUT_SECS);
    }

    public TeamSkillRail(String skillsDir, Model llm, String model) {
        this(skillsDir, llm, model, "cn", false, true);
    }

    public TeamSkillRail(String skillsDir, Model llm, String model, boolean autoSave, boolean asyncEvolution) {
        this(skillsDir, llm, model, "cn", autoSave, asyncEvolution);
    }

    public TeamSkillRail(
            String skillsDir,
            Model llm,
            String model,
            String language,
            boolean autoSave,
            boolean asyncEvolution
    ) {
        this(new FileEvolutionStore(skillsDir), llm, model, language, autoSave, asyncEvolution,
                TEAM_USER_REQUEST_LLM_POLICY, TEAM_TRAJECTORY_ISSUE_LLM_POLICY, TEAM_PATCH_LLM_POLICY,
                ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY,
                DEFAULT_TEAM_EVOLUTION_TOTAL_TIMEOUT_SECS);
    }

    public TeamSkillRail(
            String skillsDir,
            Model llm,
            String model,
            String language,
            boolean autoSave,
            boolean asyncEvolution,
            LlmResilience.LLMInvokePolicy userRequestLlmPolicy,
            LlmResilience.LLMInvokePolicy trajectoryIssueLlmPolicy,
            LlmResilience.LLMInvokePolicy patchLlmPolicy,
            LlmResilience.LLMInvokePolicy evaluateLlmPolicy,
            LlmResilience.LLMInvokePolicy simplifyLlmPolicy,
            double evolutionTotalTimeoutSecs
    ) {
        this(new FileEvolutionStore(skillsDir), llm, model, language, autoSave, asyncEvolution,
                userRequestLlmPolicy, trajectoryIssueLlmPolicy, patchLlmPolicy, evaluateLlmPolicy,
                simplifyLlmPolicy, evolutionTotalTimeoutSecs);
    }

    public TeamSkillRail(
            String skillsDir,
            Model llm,
            String model,
            String language,
            boolean autoSave,
            boolean asyncEvolution,
            TrajectoryStore teamTrajectoryStore,
            LlmResilience.LLMInvokePolicy userRequestLlmPolicy,
            LlmResilience.LLMInvokePolicy trajectoryIssueLlmPolicy,
            LlmResilience.LLMInvokePolicy patchLlmPolicy,
            LlmResilience.LLMInvokePolicy evaluateLlmPolicy,
            LlmResilience.LLMInvokePolicy simplifyLlmPolicy,
            double evolutionTotalTimeoutSecs
    ) {
        this(new FileEvolutionStore(skillsDir), llm, model, language, autoSave, asyncEvolution,
                teamTrajectoryStore, userRequestLlmPolicy, trajectoryIssueLlmPolicy, patchLlmPolicy,
                evaluateLlmPolicy, simplifyLlmPolicy, evolutionTotalTimeoutSecs);
    }

    public TeamSkillRail(
            TeamSkillStore store,
            Model llm,
            String model,
            String language,
            boolean autoSave,
            boolean asyncEvolution,
            LlmResilience.LLMInvokePolicy userRequestLlmPolicy,
            LlmResilience.LLMInvokePolicy trajectoryIssueLlmPolicy,
            LlmResilience.LLMInvokePolicy patchLlmPolicy,
            LlmResilience.LLMInvokePolicy evaluateLlmPolicy,
            LlmResilience.LLMInvokePolicy simplifyLlmPolicy,
            double evolutionTotalTimeoutSecs
    ) {
        this(store, llm, model, language, autoSave, asyncEvolution, null, userRequestLlmPolicy,
                trajectoryIssueLlmPolicy, patchLlmPolicy, evaluateLlmPolicy, simplifyLlmPolicy,
                evolutionTotalTimeoutSecs);
    }

    public TeamSkillRail(
            TeamSkillStore store,
            Model llm,
            String model,
            String language,
            boolean autoSave,
            boolean asyncEvolution,
            TrajectoryStore teamTrajectoryStore,
            LlmResilience.LLMInvokePolicy userRequestLlmPolicy,
            LlmResilience.LLMInvokePolicy trajectoryIssueLlmPolicy,
            LlmResilience.LLMInvokePolicy patchLlmPolicy,
            LlmResilience.LLMInvokePolicy evaluateLlmPolicy,
            LlmResilience.LLMInvokePolicy simplifyLlmPolicy,
            double evolutionTotalTimeoutSecs
    ) {
        super(EvolutionTrigger.MANUAL);
        this.store = store != null ? store : new FileEvolutionStore(defaultSkillsDir());
        String debugDir = this.store.primaryBaseDir().resolveSibling("_debug").toString();
        this.optimizer = new TeamSkillOptimizer(
                llm,
                model,
                language != null ? language : "cn",
                debugDir,
                patchLlmPolicy != null ? patchLlmPolicy : TEAM_PATCH_LLM_POLICY);
        this.scorer = new ExperienceScorer(
                llm,
                model,
                language != null ? language : "cn",
                evaluateLlmPolicy,
                simplifyLlmPolicy);
        this.autoSave = autoSave;
        this.asyncEvolution = asyncEvolution;
        this.userRequestLlmPolicy = userRequestLlmPolicy != null ? userRequestLlmPolicy : TEAM_USER_REQUEST_LLM_POLICY;
        this.trajectoryIssueLlmPolicy =
                trajectoryIssueLlmPolicy != null ? trajectoryIssueLlmPolicy : TEAM_TRAJECTORY_ISSUE_LLM_POLICY;
        this.evolutionTotalTimeoutSecs = evolutionTotalTimeoutSecs;
        this.teamTrajectoryStore = teamTrajectoryStore;
        LOG.info("[TeamSkillRail] initialized: skillsDir={}, model={}, autoSave={}",
                this.store.primaryBaseDir(), model, autoSave);
    }

    public TeamSkillRail(TeamSkillStore store, TeamSkillOptimizer optimizer, ExperienceScorer scorer) {
        this(store, optimizer, scorer, false, true, null);
    }

    public TeamSkillRail(
            TeamSkillStore store,
            TeamSkillOptimizer optimizer,
            ExperienceScorer scorer,
            boolean autoSave,
            boolean asyncEvolution,
            TrajectoryStore teamTrajectoryStore
    ) {
        super(EvolutionTrigger.MANUAL);
        this.store = store != null ? store : new FileEvolutionStore(defaultSkillsDir());
        this.optimizer = optimizer;
        this.scorer = scorer;
        this.autoSave = autoSave;
        this.asyncEvolution = asyncEvolution;
        this.userRequestLlmPolicy = TEAM_USER_REQUEST_LLM_POLICY;
        this.trajectoryIssueLlmPolicy = TEAM_TRAJECTORY_ISSUE_LLM_POLICY;
        this.evolutionTotalTimeoutSecs = DEFAULT_TEAM_EVOLUTION_TOTAL_TIMEOUT_SECS;
        this.teamTrajectoryStore = teamTrajectoryStore;
    }

    public TeamSkillStore store() {
        return store;
    }

    public TeamSkillStore getStore() {
        return store;
    }

    public ExperienceScorer scorer() {
        return scorer;
    }

    public ExperienceScorer getScorer() {
        return scorer;
    }

    public TeamSkillOptimizer optimizer() {
        return optimizer;
    }

    public TeamSkillOptimizer getOptimizer() {
        return optimizer;
    }

    public LlmResilience.LLMInvokePolicy userRequestLlmPolicy() {
        return userRequestLlmPolicy;
    }

    public LlmResilience.LLMInvokePolicy getUserRequestLlmPolicy() {
        return userRequestLlmPolicy;
    }

    public LlmResilience.LLMInvokePolicy trajectoryIssueLlmPolicy() {
        return trajectoryIssueLlmPolicy;
    }

    public LlmResilience.LLMInvokePolicy getTrajectoryIssueLlmPolicy() {
        return trajectoryIssueLlmPolicy;
    }

    public LlmResilience.LLMInvokePolicy patchLlmPolicy() {
        return optimizer != null ? optimizer.getPatchLlmPolicy() : TEAM_PATCH_LLM_POLICY;
    }

    public LlmResilience.LLMInvokePolicy getPatchLlmPolicy() {
        return patchLlmPolicy();
    }

    public LlmResilience.LLMInvokePolicy evaluateLlmPolicy() {
        return scorer != null ? scorer.getEvaluateLlmPolicy() : ExperienceScorer.EVALUATE_LLM_POLICY;
    }

    public LlmResilience.LLMInvokePolicy getEvaluateLlmPolicy() {
        return evaluateLlmPolicy();
    }

    public LlmResilience.LLMInvokePolicy simplifyLlmPolicy() {
        return scorer != null ? scorer.getSimplifyLlmPolicy() : ExperienceScorer.SIMPLIFY_LLM_POLICY;
    }

    public LlmResilience.LLMInvokePolicy getSimplifyLlmPolicy() {
        return simplifyLlmPolicy();
    }

    public double evolutionTotalTimeoutSecs() {
        return evolutionTotalTimeoutSecs;
    }

    public double getEvolutionTotalTimeoutSecs() {
        return evolutionTotalTimeoutSecs;
    }

    public Map<String, Object> evolutionConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("user_request_llm_policy", userRequestLlmPolicy());
        config.put("trajectory_issue_llm_policy", trajectoryIssueLlmPolicy());
        config.put("patch_llm_policy", patchLlmPolicy());
        config.put("evaluate_llm_policy", evaluateLlmPolicy());
        config.put("simplify_llm_policy", simplifyLlmPolicy());
        config.put("evolution_total_timeout_secs", evolutionTotalTimeoutSecs);
        return config;
    }

    public Map<String, Object> getEvolutionConfig() {
        return evolutionConfig();
    }

    public void setBuilder(TrajectoryBuilder builder) {
        this.builder = builder;
    }

    public TrajectoryBuilder getBuilder() {
        return builder;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public void setTrajectoriesDir(Path trajectoriesDir) {
        this.trajectoriesDir = trajectoriesDir;
    }

    public TrajectoryStore getTeamTrajectoryStore() {
        return teamTrajectoryStore;
    }

    public boolean isEvolutionInProgress() {
        return evolutionInProgress;
    }

    public Map<String, PendingChange> getPendingPatchSnapshots() {
        return pendingPatchSnapshots;
    }

    public List<Map<String, Object>> drainEvolutionOutcomes() {
        synchronized (pendingEvolutionOutcomes) {
            List<Map<String, Object>> outcomes = new ArrayList<>(pendingEvolutionOutcomes);
            pendingEvolutionOutcomes.clear();
            return outcomes;
        }
    }

    public List<OutputSchema> getPendingApprovalEventsSnapshot() {
        synchronized (pendingApprovalEvents) {
            return new ArrayList<>(pendingApprovalEvents);
        }
    }

    public List<OutputSchema> drainPendingApprovalEvents(boolean wait, Double timeout) {
        double effectiveTimeout = wait && timeout == null ? evolutionTotalTimeoutSecs : (timeout != null ? timeout : 0.0);
        long deadline = System.nanoTime() + (long) (Math.max(effectiveTimeout, 0.0) * 1_000_000_000L);
        while (wait && pendingApprovalEvents.isEmpty() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return collectPendingApprovalEvents();
    }

    public List<OutputSchema> drainPendingApprovalEvents(boolean wait) {
        return drainPendingApprovalEvents(wait, null);
    }

    public CompletableFuture<List<OutputSchema>> drainPendingApprovalEventsAsync(boolean wait, Double timeout) {
        return CompletableFuture.supplyAsync(() -> drainPendingApprovalEvents(wait, timeout));
    }

    @Override
    public void init(Object agent) {
        super.init(agent);
        LOG.info("[TeamSkillRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[TeamSkillRail] Uninitialized");
        super.uninit(agent);
    }

    public boolean notifyTeamCompleted() {
        if (evolutionInProgress) {
            return false;
        }
        if (builder == null) {
            LOG.warn("[TeamSkillRail] notifyTeamCompleted: no trajectory available");
            return false;
        }
        Trajectory trajectory = builder.buildTrajectory();
        evolutionInProgress = true;
        emitProgress("all tasks completed, starting evolution analysis...");
        if (asyncEvolution) {
            CompletableFuture.runAsync(() -> runEvolutionWithReset(trajectory));
        } else {
            runEvolutionWithReset(trajectory);
        }
        dumpTrajectoryDebug(trajectory);
        return true;
    }

    public boolean notifyTeamCompleted(Trajectory trajectory) {
        if (evolutionInProgress || trajectory == null) {
            return false;
        }
        evolutionInProgress = true;
        emitProgress("all tasks completed, starting evolution analysis...");
        if (asyncEvolution) {
            CompletableFuture.runAsync(() -> runEvolutionWithReset(trajectory));
        } else {
            runEvolutionWithReset(trajectory);
        }
        dumpTrajectoryDebug(trajectory);
        return true;
    }

    public static boolean allTasksCompleted(Object result) {
        String text = String.valueOf(result).toLowerCase(Locale.ROOT);
        if (!text.contains("completed")) {
            return false;
        }
        return !text.contains("pending")
                && !text.contains("claimed")
                && !text.contains("in_progress")
                && !text.contains("blocked");
    }

    public void runEvolution(Trajectory trajectory) {
        long start = System.nanoTime();
        try {
            trajectory = aggregateTeamTrajectoryIfAvailable(trajectory);
            String usedSkill = detectUsedTeamSkill(trajectory);
            if (usedSkill == null || usedSkill.isBlank()) {
                LOG.info("[TeamSkillRail] no existing skill detected, skipping");
                emitProgress("no existing skill found, skipping");
                return;
            }
            String currentContent = store.readSkillContent(usedSkill);
            List<TrajectoryIssue> trajectoryIssues = detectTrajectoryIssues(trajectory, currentContent);
            if (trajectoryIssues.isEmpty()) {
                LOG.info("[TeamSkillRail] no signals detected for '{}'", usedSkill);
                emitProgress("no evolution signals detected");
                return;
            }
            emitProgress("trajectory issues detected: " + trajectoryIssues.size() + " issues");
            List<Map<String, Object>> issueMaps = trajectoryIssues.stream().map(TrajectoryIssue::toMap).toList();
            Optional<EvolutionRecord> record = joinFuture(optimizer.generateTrajectoryPatch(
                    trajectory,
                    usedSkill,
                    currentContent != null ? currentContent : "",
                    issueMaps));
            if (record.isPresent()) {
                handlePatchRecord(record.get(), usedSkill);
            } else {
                emitProgress("no patch generated");
            }
            double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
            LOG.info("[TeamSkillRail] runEvolution completed in {}s", String.format(Locale.ROOT, "%.1f", elapsed));
        } catch (Exception exc) {
            LOG.warn("[TeamSkillRail] runEvolution failed: {}", exc.getMessage(), exc);
            pendingEvolutionOutcomes.add(Map.of(
                    "status", "failed",
                    "message", "team skill evolution failed: " + exc.getMessage()));
            emitProgress("evolution analysis failed: " + exc.getMessage());
        } finally {
            evolutionInProgress = false;
        }
    }

    public Map<String, Object> snapshotForEvolution(
            Trajectory trajectory,
            List<Map<String, Object>> parsedMessages
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("trajectory", trajectory);
        snapshot.put("parsed_messages", parsedMessages != null ? new ArrayList<>(parsedMessages) : new ArrayList<>());
        snapshot.put("skill_name", "team-skill");
        return snapshot;
    }

    public void runEvolution(Trajectory trajectory, Object ctx, Map<String, Object> snapshot) {
        Object snapTrajectory = snapshot != null ? snapshot.get("trajectory") : null;
        if (snapTrajectory instanceof Trajectory trajectoryFromSnapshot) {
            runEvolution(trajectoryFromSnapshot);
        } else {
            runEvolution(trajectory);
        }
    }

    @Override
    protected void runEvolution() {
        if (builder != null) {
            runEvolution(builder.buildTrajectory());
        }
    }

    public void onApprovePatch(String requestId) {
        PendingChange pending = pendingPatchSnapshots.get(requestId);
        if (pending == null) {
            LOG.warn("[TeamSkillRail] onApprovePatch: unknown requestId={}", requestId);
            return;
        }

        SkillCallOperator skillOp = patchSkillOps.computeIfAbsent(
                pending.getSkillName(),
                SkillCallOperator::new);
        List<Object> payload = new ArrayList<>(pending.getPayload());
        SkillCallOperator.FlushResult result = joinFuture(skillOp.flushRecordsToStore(store, payload));
        if (!result.getRemainingRecords().isEmpty()) {
            List<EvolutionRecord> remaining = new ArrayList<>();
            for (Object item : result.getRemainingRecords()) {
                if (item instanceof EvolutionRecord record) {
                    remaining.add(record);
                }
            }
            pending.replacePayload(remaining);
            LOG.warn("[TeamSkillRail] onApprovePatch partial failure: {}/{} patch(es) written for '{}'",
                    result.getFlushedCount(), result.getFlushedCount() + remaining.size(), pending.getSkillName());
            return;
        }

        pendingPatchSnapshots.remove(requestId);
        LOG.info("[TeamSkillRail] user approved {} patch(es) for '{}'",
                result.getFlushedCount(), pending.getSkillName());
    }

    public void onRejectPatch(String requestId) {
        PendingChange pending = pendingPatchSnapshots.remove(requestId);
        if (pending != null) {
            LOG.info("[TeamSkillRail] user rejected {} patch(es) for '{}'",
                    pending.getPayload().size(), pending.getSkillName());
        }
    }

    public Optional<Map<String, Integer>> requestSimplify(String skillName) {
        return requestSimplify(skillName, null);
    }

    public Optional<Map<String, Integer>> requestSimplify(String skillName, String userIntent) {
        if (!store.skillExists(skillName)) {
            return Optional.empty();
        }
        EvolutionLog log = store.loadFullEvolutionLog(skillName);
        List<EvolutionRecord> records = log != null ? log.getEntries() : Collections.emptyList();
        if (records.isEmpty()) {
            return Optional.empty();
        }
        String content = store.readSkillContent(skillName);
        String summary = store.extractDescriptionFromSkillMd(content);
        List<Map<String, Object>> actions;
        try {
            actions = scorer.simplify(skillName, summary, records, userIntent);
        } catch (Exception exc) {
            throw new CompletionException(exc);
        }
        if (actions == null || actions.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Integer> result = scorer.executeSimplifyActions(store, skillName, actions);
        emitProgress("simplify completed for '" + skillName + "': " + result);
        return Optional.of(result);
    }

    public Optional<String> requestRebuild(String skillName) {
        return requestRebuild(skillName, null, 0.5);
    }

    public Optional<String> requestRebuild(String skillName, String userIntent) {
        return requestRebuild(skillName, userIntent, 0.5);
    }

    public Optional<String> requestRebuild(String skillName, String userIntent, double minScore) {
        if (!store.skillExists(skillName)) {
            return Optional.empty();
        }

        String evoArchive = null;
        try {
            store.archiveSkillBody(skillName);
        } catch (RuntimeException exc) {
            LOG.warn("[TeamSkillRail] skill body archive failed for '{}': {}", skillName, exc.getMessage());
        }

        try {
            evoArchive = store.archiveEvolutions(skillName);
        } catch (RuntimeException exc) {
            LOG.warn("[TeamSkillRail] evolutions archive failed for '{}': {}", skillName, exc.getMessage());
            emitProgress("archive failed for '" + skillName + "': " + exc.getMessage());
        }

        if (evoArchive != null && !evoArchive.isBlank()) {
            emitProgress("archived old version for '" + skillName + "'");
        }

        String followupText = buildRebuildPrompt(skillName, userIntent, minScore);
        if (evoArchive != null && !evoArchive.isBlank()) {
            store.clearEvolutions(skillName);
            emitProgress("cleared evolutions for '" + skillName + "' after archiving");
        }
        emitProgress("rebuild prompt generated for '" + skillName + "'");
        return Optional.of(followupText);
    }

    public Optional<String> requestUserEvolution(String skillName, String userIntent) {
        return requestUserEvolution(skillName, userIntent, false);
    }

    public Optional<String> requestUserEvolution(String skillName, String userIntent, boolean autoApprove) {
        if (!store.skillExists(skillName)) {
            LOG.warn("[TeamSkillRail] requestUserEvolution: skill '{}' not found", skillName);
            return Optional.empty();
        }
        Trajectory trajectory = builder != null ? builder.buildTrajectory() : null;
        if (trajectory == null) {
            trajectory = Trajectory.builder()
                    .executionId("user_triggered")
                    .sessionId("user_triggered")
                    .source("user_triggered")
                    .steps(Collections.emptyList())
                    .build();
        }

        Optional<EvolutionRecord> record = joinFuture(optimizer.generateUserPatch(trajectory, skillName, userIntent));
        if (record.isEmpty()) {
            LOG.info("[TeamSkillRail] requestUserEvolution: no patch generated for '{}'", skillName);
            return Optional.empty();
        }

        EvolutionRecord evolutionRecord = record.get();
        if (autoApprove) {
            store.saveRecord(skillName, evolutionRecord);
            emitProgress("evolution patch auto-approved for '" + skillName + "'");
            return Optional.of(evolutionRecord.getId());
        }

        String changeId = "team_skill_evolve_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        PendingChange pending = PendingChange.make(skillName, List.of(evolutionRecord), changeId);
        pendingPatchSnapshots.put(changeId, pending);
        emitPatchApprovalEvent(skillName, pending);
        emitProgress("patch for '" + skillName + "' ready, awaiting approval");
        return Optional.of(changeId);
    }

    public List<OutputSchema> collectPendingApprovalEvents() {
        synchronized (pendingApprovalEvents) {
            List<OutputSchema> events = new ArrayList<>(pendingApprovalEvents);
            pendingApprovalEvents.clear();
            return events;
        }
    }

    public String detectUsedTeamSkill(Trajectory trajectory) {
        Set<String> allSkillNames = new LinkedHashSet<>(store.listSkillNames());
        if (allSkillNames.isEmpty()) {
            return null;
        }
        Set<String> knownSkills = new LinkedHashSet<>();
        for (String name : allSkillNames) {
            if (isTeamSkill(name)) {
                knownSkills.add(name);
            }
        }
        if (knownSkills.isEmpty() || trajectory == null || trajectory.getSteps() == null) {
            return null;
        }

        List<Object> skillToolPayloads = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (TrajectoryStep step : trajectory.getSteps()) {
            if (step == null || !"tool".equals(step.getKind())) {
                continue;
            }
            Object detail = step.getDetail();
            Object toolName = readValue(detail, "tool_name");
            if ("skill_tool".equals(String.valueOf(toolName))) {
                skillToolPayloads.add(readValue(detail, "call_args"));
            }
            texts.add(String.valueOf(readValue(detail, "call_args")));
            texts.add(String.valueOf(readValue(detail, "call_result")));
        }
        return inferSkillFromTexts(knownSkills, skillToolPayloads, texts);
    }

    public Optional<UserIntent> detectUserRequest(List<Map<String, Object>> messages, String teamSkillContent) {
        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }
        List<String> userMessages = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            if ("user".equals(message.get("role"))) {
                userMessages.add(String.valueOf(message.getOrDefault("content", "")));
            }
        }
        if (userMessages.isEmpty()) {
            return Optional.empty();
        }
        int start = Math.max(0, userMessages.size() - 10);
        String userText = String.join("\n", userMessages.subList(start, userMessages.size()));
        String skillSummary = truncate(teamSkillContent != null ? teamSkillContent : "", 1000);
        String language = optimizer != null ? optimizer.getLanguage() : "cn";
        String template = "cn".equals(language) ? USER_REQUEST_PROMPT_CN : USER_REQUEST_PROMPT_EN;
        String prompt = template
                .replace("{team_skill_description}", skillSummary)
                .replace("{roles}", "")
                .replace("{user_messages}", truncate(userText, 2000));
        try {
            String raw = LlmResilience.invokeTextWithRetry(
                    optimizer.getLlm(),
                    optimizer.getModel(),
                    prompt,
                    userRequestLlmPolicy,
                    null,
                    null,
                    text -> TeamSkillOptimizer.parseJson(text) != null);
            Map<String, Object> parsed = TeamSkillOptimizer.parseJson(raw);
            if (parsed != null && asBoolean(parsed.get("is_improvement"))) {
                return Optional.of(new UserIntent(true, String.valueOf(parsed.getOrDefault("intent", ""))));
            }
            return Optional.empty();
        } catch (Exception exc) {
            throw asRuntime(exc);
        }
    }

    public List<TrajectoryIssue> detectTrajectoryIssues(Trajectory trajectory, String teamSkillContent) {
        String summary = TeamSkillOptimizer.buildTrajectorySummary(trajectory);
        String language = optimizer != null ? optimizer.getLanguage() : "cn";
        String template = "cn".equals(language) ? TRAJECTORY_ISSUE_PROMPT_CN : TRAJECTORY_ISSUE_PROMPT_EN;
        String prompt = template
                .replace("{skill_content}", truncate(teamSkillContent != null ? teamSkillContent : "", 10_000))
                .replace("{trajectory_summary}", summary);
        try {
            String raw = LlmResilience.invokeTextWithRetry(
                    optimizer.getLlm(),
                    optimizer.getModel(),
                    prompt,
                    trajectoryIssueLlmPolicy,
                    null,
                    null,
                    text -> parseIssueList(text) != null);
            List<Map<String, Object>> parsed = parseIssueList(raw);
            if (parsed == null || parsed.isEmpty()) {
                return Collections.emptyList();
            }
            List<TrajectoryIssue> issues = new ArrayList<>();
            for (Map<String, Object> item : parsed) {
                String severity = String.valueOf(item.getOrDefault("severity", "medium"));
                if (!Set.of("low", "medium", "high").contains(severity)) {
                    severity = "medium";
                }
                if ("low".equals(severity)) {
                    continue;
                }
                issues.add(new TrajectoryIssue(
                        String.valueOf(item.getOrDefault("issue_type", "unknown")),
                        String.valueOf(item.getOrDefault("description", "")),
                        String.valueOf(item.getOrDefault("affected_role", "")),
                        severity));
            }
            return issues;
        } catch (Exception exc) {
            throw asRuntime(exc);
        }
    }

    public Optional<Map<String, Object>> collectMessages(Object ctx) {
        return Optional.empty();
    }

    public boolean isTeamSkill(String name) {
        Path skillDir = store.resolveSkillDir(name);
        if (skillDir == null) {
            return false;
        }
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMd)) {
            return false;
        }
        try {
            Map<String, String> frontmatter = Frontmatter.parseFrontmatter(Files.readString(skillMd));
            return frontmatter != null && "team-skill".equals(frontmatter.get("kind"));
        } catch (IOException exc) {
            return false;
        }
    }

    public static String formatEvolutionRecords(List<EvolutionRecord> records) {
        return formatEvolutionRecords(records, "cn");
    }

    public static String formatEvolutionRecords(List<EvolutionRecord> records, String language) {
        boolean cn = "cn".equals(language);
        String header = cn ? "经验" : "Experience";
        String contentLabel = cn ? "内容" : "Content";
        String empty = cn ? "（无演进经验）" : "(no evolution records)";
        if (records == null || records.isEmpty()) {
            return empty;
        }
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (EvolutionRecord record : records) {
            EvolutionPatch change = record.getChange();
            String section = change != null ? change.getSection() : "?";
            String content = change != null ? change.getContent() : "";
            lines.add("### " + header + " #" + index + " [" + safe(record.getTimestamp()) + "] - source: "
                    + safe(record.getSource()) + "\n- Section: " + safe(section) + "\n- "
                    + contentLabel + ": " + safe(content));
            index++;
        }
        return String.join("\n\n", lines);
    }

    public String buildRebuildPrompt(String skillName, String userIntent, double minScore) {
        EvolutionLog log = store.loadFullEvolutionLog(skillName);
        List<EvolutionRecord> filtered = new ArrayList<>();
        if (log != null && log.getEntries() != null) {
            for (EvolutionRecord record : log.getEntries()) {
                if (record == null || record.getScore() < minScore) {
                    continue;
                }
                EvolutionPatch change = record.getChange();
                if (change != null && change.getSkipReason() != null && !change.getSkipReason().isBlank()) {
                    continue;
                }
                filtered.add(record);
            }
        }
        String language = optimizer != null ? optimizer.getLanguage() : "cn";
        String evolutionText = formatEvolutionRecords(filtered, language);
        String intent;
        if ("cn".equals(language)) {
            intent = userIntent != null ? userIntent : "根据以上演进经验，对团队技能进行全面优化和重建。";
        } else {
            intent = userIntent != null ? userIntent
                    : "Based on the evolution records above, perform a comprehensive rebuild of the team skill.";
        }
        String template = "cn".equals(language) ? REBUILD_PROMPT_TEMPLATE_CN : REBUILD_PROMPT_TEMPLATE_EN;
        return template
                .replace("{evolution_records}", evolutionText)
                .replace("{user_intent}", intent)
                .replace("{min_score}", String.format(Locale.ROOT, "%.2f", minScore));
    }

    private void runEvolutionWithReset(Trajectory trajectory) {
        try {
            runEvolution(trajectory);
        } finally {
            evolutionInProgress = false;
        }
    }

    private Trajectory aggregateTeamTrajectoryIfAvailable(Trajectory trajectory) {
        if (teamTrajectoryStore == null || trajectory == null || trajectory.getSessionId() == null
                || trajectory.getSessionId().isBlank()) {
            return trajectory;
        }
        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(
                teamTrajectoryStore,
                teamId != null && !teamId.isBlank() ? teamId : "unknown");
        TeamTrajectoryAggregator.TeamTrajectory teamTrajectory =
                aggregator.aggregate(trajectory.getSessionId(), true);
        if (teamTrajectory == null || teamTrajectory.getMembers() == null || teamTrajectory.getMembers().isEmpty()) {
            return trajectory;
        }
        Trajectory combined = teamTrajectory.getCombined();
        int memberCount = combined.getMeta() != null && combined.getMeta().get("member_count") instanceof Number number
                ? number.intValue() : teamTrajectory.getMembers().size();
        int stepCount = combined.getSteps() != null ? combined.getSteps().size() : 0;
        emitProgress("aggregated " + memberCount + " members, " + stepCount + " collaborative steps");
        return combined;
    }

    private void handlePatchRecord(EvolutionRecord record, String skillName) {
        EvolutionPatch change = record.getChange();
        LOG.info("[TeamSkillRail] PATCH generated: section='{}', contentLen={}",
                change != null ? change.getSection() : "", change != null && change.getContent() != null
                        ? change.getContent().length() : 0);
        if (autoSave) {
            store.saveRecord(skillName, record);
            emitProgress("patch auto-saved to '" + skillName + "'");
            return;
        }
        String changeId = "team_skill_evolve_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        PendingChange pending = PendingChange.make(skillName, List.of(record), changeId);
        pendingPatchSnapshots.put(changeId, pending);
        emitPatchApprovalEvent(skillName, pending);
        emitProgress("patch for '" + skillName + "' ready, awaiting approval");
    }

    private void emitPatchApprovalEvent(String skillName, PendingChange pending) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (EvolutionRecord record : pending.getPayload()) {
            EvolutionPatch change = record.getChange();
            String preview = truncate(change != null ? change.getContent() : "", 1000);
            questions.add(Map.of(
                    "question", "**Team Skill '" + skillName + "' evolution:**\n\n- **Section**: "
                            + (change != null ? safe(change.getSection()) : "") + "\n\n" + preview,
                    "header", "Team Skill Patch Approval",
                    "options", List.of(
                            Map.of("label", "Accept", "description", "Keep this evolution"),
                            Map.of("label", "Reject", "description", "Discard this evolution")),
                    "multi_select", false));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", pending.getChangeId());
        payload.put("_evolution_meta", Map.of("skill_name", skillName, "request_id", pending.getChangeId()));
        payload.put("questions", questions);
        pendingApprovalEvents.add(new OutputSchema("chat.ask_user_question", 0, payload));

        String sections = pending.getPayload().stream()
                .map(EvolutionRecord::getChange)
                .filter(Objects::nonNull)
                .map(EvolutionPatch::getSection)
                .toList()
                .toString();
        emitProgress("TEAM SKILL PATCH PROPOSED: '" + skillName + "'\n  sections: " + sections
                + "\n  patch_count: " + pending.getPayload().size()
                + "\n  change_id: " + pending.getChangeId()
                + "\n  ACTION: an approval dialog should pop up; if not visible, check approval panel or rerun task");
    }

    private void emitProgress(String message) {
        LOG.info("[TeamSkillRail] {}", message);
        pendingApprovalEvents.add(new OutputSchema(
                "llm_reasoning",
                0,
                Map.of("content", "[Team Skill Evolution] " + message + "\n")));
    }

    private void dumpTrajectoryDebug(Trajectory trajectory) {
        if (trajectory == null) {
            return;
        }
        try {
            Path debugDir = trajectoriesDir != null ? trajectoriesDir : store.primaryBaseDir().resolveSibling("_debug");
            Files.createDirectories(debugDir);
            String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());
            String execution = trajectory.getExecutionId() != null ? trajectory.getExecutionId() : "unknown";
            Path path = debugDir.resolve("trajectory_" + ts + "_" + truncate(execution, 8) + ".json");
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("execution_id", trajectory.getExecutionId());
            dump.put("session_id", trajectory.getSessionId());
            dump.put("source", trajectory.getSource());
            dump.put("step_count", trajectory.getSteps() != null ? trajectory.getSteps().size() : 0);
            Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(dump),
                    StandardCharsets.UTF_8);
        } catch (Exception exc) {
            LOG.warn("[TeamSkillRail] trajectory dump failed: {}", exc.getMessage());
        }
    }

    private List<Map<String, Object>> parseIssueList(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(raw);
        if (matcher.find()) {
            candidates.add(matcher.group(1).strip());
        }
        candidates.add(raw.strip());
        String balanced = extractBalanced(raw, '[', ']');
        if (balanced != null) {
            candidates.add(balanced);
        }
        for (String candidate : candidates) {
            try {
                if (candidate.startsWith("[")) {
                    return MAPPER.readValue(fixJsonText(candidate), LIST_OF_MAPS);
                }
            } catch (JsonProcessingException ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static String inferSkillFromTexts(
            Set<String> knownSkills,
            List<Object> skillToolPayloads,
            List<String> texts
    ) {
        if (knownSkills == null || knownSkills.isEmpty()) {
            return null;
        }
        Map<String, SkillReferenceScore> hits = new LinkedHashMap<>();
        for (Object payload : skillToolPayloads) {
            String skillName = extractSkillToolName(payload);
            if (knownSkills.contains(skillName)) {
                hits.computeIfAbsent(skillName, ignored -> new SkillReferenceScore()).skillToolHits++;
            }
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            collectPatternHits(text, INLINE_SKILL_TOOL_PATTERN, knownSkills, hits, "skill_tool");
            collectPatternHits(text, SKILLS_PATH_PATTERN, knownSkills, hits, "skills_path");
            collectPatternHits(text, LEGACY_SKILL_MD_PATTERN, knownSkills, hits, "legacy_skill_md");
        }
        if (hits.isEmpty()) {
            String combined = String.join("\n", texts).toLowerCase(Locale.ROOT);
            for (String skill : knownSkills) {
                String lower = skill.toLowerCase(Locale.ROOT);
                if (combined.contains(lower) || combined.contains(lower + "/skill.md")) {
                    return skill;
                }
            }
        }
        return hits.entrySet().stream()
                .max((left, right) -> left.getValue().compareTo(right.getValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static void collectPatternHits(
            String text,
            Pattern pattern,
            Set<String> knownSkills,
            Map<String, SkillReferenceScore> hits,
            String kind
    ) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String skillName = matcher.group(1);
            if (!knownSkills.contains(skillName)) {
                continue;
            }
            SkillReferenceScore score = hits.computeIfAbsent(skillName, ignored -> new SkillReferenceScore());
            switch (kind) {
                case "skill_tool" -> score.skillToolHits++;
                case "skills_path" -> score.skillsPathHits++;
                case "legacy_skill_md" -> score.legacySkillMdHits++;
                default -> {
                    // No-op for unknown score kinds.
                }
            }
        }
    }

    private static String extractSkillToolName(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object value = map.get("skill_name");
            if (value == null) {
                value = map.get("skillName");
            }
            return value != null ? String.valueOf(value) : "";
        }
        if (payload instanceof String text && !text.isBlank()) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(text, MAP_OF_OBJECTS);
                Object value = parsed.get("skill_name");
                if (value == null) {
                    value = parsed.get("skillName");
                }
                return value != null ? String.valueOf(value) : "";
            } catch (JsonProcessingException ignored) {
                return "";
            }
        }
        return "";
    }

    private static final class SkillReferenceScore implements Comparable<SkillReferenceScore> {
        private int skillToolHits;
        private int skillsPathHits;
        private int legacySkillMdHits;

        @Override
        public int compareTo(SkillReferenceScore other) {
            int bySkillTool = Integer.compare(skillToolHits, other.skillToolHits);
            if (bySkillTool != 0) {
                return bySkillTool;
            }
            int bySkillsPath = Integer.compare(skillsPathHits, other.skillsPathHits);
            if (bySkillsPath != 0) {
                return bySkillsPath;
            }
            return Integer.compare(legacySkillMdHits, other.legacySkillMdHits);
        }
    }

    private static String fixJsonText(String text) {
        return text.strip()
                .replaceAll("(?m)^```(?:json)?\\s*", "")
                .replaceAll("(?m)```\\s*$", "")
                .replaceAll("//[^\\n]*", "")
                .replaceAll(",\\s*([}\\]])", "$1")
                .strip();
    }

    private static String extractBalanced(String text, char opener, char closer) {
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

    private static Object readValue(Object source, String snakeOrCamelName) {
        if (source == null || snakeOrCamelName == null) {
            return "";
        }
        if (source instanceof Map<?, ?> map) {
            Object value = map.get(snakeOrCamelName);
            if (value != null) {
                return value;
            }
            value = map.get(toSnakeOrCamelPeer(snakeOrCamelName));
            return value != null ? value : "";
        }
        String camel = toCamelCase(snakeOrCamelName);
        for (String getter : List.of("get" + capitalize(camel), "is" + capitalize(camel), camel, snakeOrCamelName)) {
            try {
                Method method = source.getClass().getMethod(getter);
                method.setAccessible(true);
                return method.invoke(source);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
                // Try the next accessor form.
            }
        }
        for (String fieldName : List.of(camel, snakeOrCamelName)) {
            try {
                Field field = source.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (IllegalAccessException | NoSuchFieldException ignored) {
                // Try the next field form.
            }
        }
        return "";
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).strip());
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

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static RuntimeException asRuntime(Exception exc) {
        if (exc instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new CompletionException(exc);
    }

    private static <T> T joinFuture(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exc) {
            if (exc.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exc;
        }
    }

    private static Path defaultSkillsDir() {
        return Path.of(System.getProperty("java.io.tmpdir"), "openjiuwen-team-skill-rail", "skills");
    }

    public enum TeamSignalType {
        USER_REQUEST("user_request"),
        TRAJECTORY_ISSUE("trajectory_issue");

        private final String value;

        TeamSignalType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class UserIntent {
        private final boolean improvement;
        private final String intent;

        public UserIntent(boolean improvement, String intent) {
            this.improvement = improvement;
            this.intent = intent != null ? intent : "";
        }

        public boolean isImprovement() {
            return improvement;
        }

        public String getIntent() {
            return intent;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UserIntent that)) {
                return false;
            }
            return improvement == that.improvement && Objects.equals(intent, that.intent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(improvement, intent);
        }
    }

    public static final class TrajectoryIssue {
        private final String issueType;
        private final String description;
        private final String affectedRole;
        private final String severity;

        public TrajectoryIssue(String issueType, String description) {
            this(issueType, description, "", "medium");
        }

        public TrajectoryIssue(String issueType, String description, String affectedRole, String severity) {
            this.issueType = issueType != null ? issueType : "unknown";
            this.description = description != null ? description : "";
            this.affectedRole = affectedRole != null ? affectedRole : "";
            this.severity = severity != null ? severity : "medium";
        }

        public String getIssueType() {
            return issueType;
        }

        public String getDescription() {
            return description;
        }

        public String getAffectedRole() {
            return affectedRole;
        }

        public String getSeverity() {
            return severity;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("issue_type", issueType);
            map.put("description", description);
            map.put("affected_role", affectedRole);
            map.put("severity", severity);
            return map;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TrajectoryIssue that)) {
                return false;
            }
            return Objects.equals(issueType, that.issueType)
                    && Objects.equals(description, that.description)
                    && Objects.equals(affectedRole, that.affectedRole)
                    && Objects.equals(severity, that.severity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(issueType, description, affectedRole, severity);
        }
    }

    public interface TeamSkillStore extends EvolutionStore {
        Path primaryBaseDir();

        boolean skillExists(String skillName);

        List<String> listSkillNames();

        Path resolveSkillDir(String skillName);

        EvolutionLog loadFullEvolutionLog(String skillName);

        String appendRecord(String skillName, EvolutionRecord record);

        String archiveSkillBody(String skillName);

        String archiveEvolutions(String skillName);

        void clearEvolutions(String skillName);

        String extractDescriptionFromSkillMd(String content);
    }

    public static class FileEvolutionStore implements TeamSkillStore {
        private final List<Path> baseDirs;

        public FileEvolutionStore(String skillsDir) {
            this(List.of(Path.of(skillsDir != null ? skillsDir : defaultSkillsDir().toString())));
        }

        public FileEvolutionStore(Path skillsDir) {
            this(List.of(skillsDir));
        }

        public FileEvolutionStore(List<Path> baseDirs) {
            this.baseDirs = baseDirs == null || baseDirs.isEmpty()
                    ? List.of(defaultSkillsDir())
                    : baseDirs.stream().filter(Objects::nonNull).map(Path::toAbsolutePath).map(Path::normalize).toList();
        }

        @Override
        public Path primaryBaseDir() {
            return baseDirs.get(0);
        }

        @Override
        public boolean skillExists(String skillName) {
            Path dir = resolveSkillDir(skillName);
            return dir != null && Files.exists(dir.resolve("SKILL.md"));
        }

        @Override
        public List<String> listSkillNames() {
            Set<String> names = new LinkedHashSet<>();
            for (Path baseDir : baseDirs) {
                if (!Files.isDirectory(baseDir)) {
                    continue;
                }
                try (var stream = Files.list(baseDir)) {
                    stream.filter(Files::isDirectory)
                            .filter(path -> Files.exists(path.resolve("SKILL.md")))
                            .map(path -> path.getFileName().toString())
                            .sorted()
                            .forEach(names::add);
                } catch (IOException exc) {
                    throw new UncheckedIOException(exc);
                }
            }
            return new ArrayList<>(names);
        }

        @Override
        public Path resolveSkillDir(String skillName) {
            if (skillName == null || skillName.isBlank()) {
                return null;
            }
            for (Path baseDir : baseDirs) {
                Path candidate = baseDir.resolve(skillName);
                if (Files.exists(candidate.resolve("SKILL.md"))) {
                    return candidate;
                }
            }
            return primaryBaseDir().resolve(skillName);
        }

        @Override
        public String readSkillContent(String skillName) {
            Path skillMd = resolveSkillDir(skillName).resolve("SKILL.md");
            if (!Files.exists(skillMd)) {
                return null;
            }
            try {
                return Files.readString(skillMd, StandardCharsets.UTF_8);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        @Override
        public boolean writeSkillContent(String skillName, String content) {
            Path dir = primaryBaseDir().resolve(skillName);
            try {
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("SKILL.md"), content != null ? content : "", StandardCharsets.UTF_8);
                return true;
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        @Override
        public EvolutionLog loadEvolutionLog(String skillName) {
            Path path = evolutionsPath(skillName);
            if (!Files.exists(path)) {
                return EvolutionLog.empty(skillName);
            }
            try {
                String raw = Files.readString(path, StandardCharsets.UTF_8);
                if (raw.isBlank()) {
                    return EvolutionLog.empty(skillName);
                }
                if (raw.strip().startsWith("[")) {
                    List<Map<String, Object>> entries = MAPPER.readValue(raw, LIST_OF_MAPS);
                    EvolutionLog log = EvolutionLog.empty(skillName);
                    log.setEntries(entries.stream().map(EvolutionRecord::fromDict).toList());
                    return log;
                }
                Map<String, Object> data = MAPPER.readValue(raw, new TypeReference<>() {
                });
                return EvolutionLog.fromDict(data);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        @Override
        public boolean saveEvolutionLog(String skillName, EvolutionLog log) {
            Path path = evolutionsPath(skillName);
            try {
                Files.createDirectories(path.getParent());
                EvolutionLog effective = log != null ? log : EvolutionLog.empty(skillName);
                effective.setSkillId(skillName);
                effective.setUpdatedAt(Instant.now().toString());
                Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(effective.toDict()),
                        StandardCharsets.UTF_8);
                return true;
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        @Override
        public int deleteRecords(String skillName, List<String> recordIds) {
            if (recordIds == null || recordIds.isEmpty()) {
                return 0;
            }
            EvolutionLog log = loadEvolutionLog(skillName);
            int before = log.getEntries().size();
            log.setEntries(log.getEntries().stream()
                    .filter(record -> !recordIds.contains(record.getId()))
                    .toList());
            saveEvolutionLog(skillName, log);
            return before - log.getEntries().size();
        }

        @Override
        public List<EvolutionRecord> loadRecords(String skillName) {
            return new ArrayList<>(loadEvolutionLog(skillName).getEntries());
        }

        @Override
        public boolean saveRecord(String skillName, EvolutionRecord record) {
            EvolutionLog log = loadEvolutionLog(skillName);
            log.getEntries().add(record);
            return saveEvolutionLog(skillName, log);
        }

        @Override
        public boolean mergeRecords(String skillName, String primaryId, List<String> removeIds, String newContent) {
            EvolutionLog log = loadEvolutionLog(skillName);
            boolean updated = false;
            List<EvolutionRecord> entries = new ArrayList<>();
            for (EvolutionRecord record : log.getEntries()) {
                if (record.getId().equals(primaryId)) {
                    if (record.getChange() != null) {
                        record.getChange().setContent(newContent);
                    }
                    updated = true;
                    entries.add(record);
                } else if (removeIds == null || !removeIds.contains(record.getId())) {
                    entries.add(record);
                }
            }
            if (!updated) {
                return false;
            }
            log.setEntries(entries);
            return saveEvolutionLog(skillName, log);
        }

        @Override
        public boolean updateRecordContent(String skillName, String recordId, String newContent) {
            EvolutionLog log = loadEvolutionLog(skillName);
            boolean updated = false;
            for (EvolutionRecord record : log.getEntries()) {
                if (record.getId().equals(recordId) && record.getChange() != null) {
                    record.getChange().setContent(newContent);
                    updated = true;
                    break;
                }
            }
            return updated && saveEvolutionLog(skillName, log);
        }

        @Override
        public EvolutionLog loadFullEvolutionLog(String skillName) {
            return loadEvolutionLog(skillName);
        }

        @Override
        public String appendRecord(String skillName, EvolutionRecord record) {
            if (record == null) {
                return null;
            }
            try {
                if (record.getChange() != null && record.getChange().getTarget() == com.openjiuwen.agent_evolving.signal.EvolutionTarget.SCRIPT) {
                    persistScript(skillName, record);
                }

                EvolutionLog log = loadEvolutionLog(skillName);
                String mergeTarget = record.getChange() != null ? record.getChange().getMergeTarget() : null;
                boolean replaced = false;
                if (mergeTarget != null && !mergeTarget.isBlank()) {
                    for (int i = 0; i < log.getEntries().size(); i++) {
                        EvolutionRecord existing = log.getEntries().get(i);
                        if (mergeTarget.equals(existing.getId())) {
                            log.getEntries().set(i, record);
                            replaced = true;
                            break;
                        }
                    }
                }
                if (!replaced) {
                    log.getEntries().add(record);
                }
                saveEvolutionLog(skillName, log);
                renderEvolutionMarkdown(skillName);
                return record.getId();
            } catch (RuntimeException exc) {
                throw exc;
            }
        }

        @Override
        public String archiveSkillBody(String skillName) {
            Path skillMd = resolveSkillDir(skillName).resolve("SKILL.md");
            if (!Files.exists(skillMd)) {
                return null;
            }
            Path archive = archiveDir(skillName).resolve("SKILL." + timestamp() + ".md");
            copy(skillMd, archive);
            return archive.getFileName().toString();
        }

        @Override
        public String archiveEvolutions(String skillName) {
            Path evolutions = evolutionsPath(skillName);
            if (!Files.exists(evolutions)) {
                return null;
            }
            Path archive = archiveDir(skillName).resolve("evolutions." + timestamp() + ".json");
            copy(evolutions, archive);
            return archive.getFileName().toString();
        }

        @Override
        public void clearEvolutions(String skillName) {
            saveEvolutionLog(skillName, EvolutionLog.empty(skillName));
        }

        @Override
        public String extractDescriptionFromSkillMd(String content) {
            Map<String, String> frontmatter = Frontmatter.parseFrontmatter(content != null ? content : "");
            if (frontmatter != null && frontmatter.get("description") != null) {
                return frontmatter.get("description");
            }
            return "";
        }

        private void persistScript(String skillName, EvolutionRecord record) {
            Path skillDir = resolveSkillDir(skillName);
            if (skillDir == null || record.getChange() == null) {
                return;
            }
            Path scriptsDir = skillDir.resolve("evolution").resolve("scripts");
            try {
                Files.createDirectories(scriptsDir);
                String lang = record.getChange().getScriptLanguage() != null
                        ? record.getChange().getScriptLanguage() : "py";
                String ext = switch (lang.toLowerCase(Locale.ROOT)) {
                    case "python" -> "py";
                    case "javascript" -> "js";
                    case "typescript" -> "ts";
                    case "shell", "bash" -> "sh";
                    default -> lang;
                };
                String filename = record.getChange().getScriptFilename() != null
                        ? record.getChange().getScriptFilename()
                        : record.getId() + "_script." + ext;
                Path scriptPath = scriptsDir.resolve(filename);
                Files.writeString(scriptPath, record.getChange().getContent() != null ? record.getChange().getContent() : "",
                        StandardCharsets.UTF_8);
                record.getChange().setScriptFilename(filename);
                record.getChange().setContent("Script: " + filename + "\n"
                        + "Language: " + (record.getChange().getScriptLanguage() != null ? record.getChange().getScriptLanguage() : "unknown") + "\n"
                        + "Purpose: " + (record.getChange().getScriptPurpose() != null ? record.getChange().getScriptPurpose() : ""));
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        private void renderEvolutionMarkdown(String skillName) {
            Path skillDir = resolveSkillDir(skillName);
            if (skillDir == null) {
                return;
            }
            EvolutionLog log = loadEvolutionLog(skillName);
            List<EvolutionRecord> activeEntries = log.getEntries().stream()
                    .filter(record -> record.getChange() == null || record.getChange().getSkipReason() == null
                            || record.getChange().getSkipReason().isBlank())
                    .toList();
            if (activeEntries.isEmpty()) {
                return;
            }

            Path evolutionDir = skillDir.resolve("evolution");
            try {
                Files.createDirectories(evolutionDir);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }

            Map<String, List<EvolutionRecord>> sectionGroups = new LinkedHashMap<>();
            List<EvolutionRecord> scriptEntries = new ArrayList<>();
            for (EvolutionRecord record : activeEntries) {
                if (record.getChange() == null) {
                    continue;
                }
                if (record.getChange().getTarget() == com.openjiuwen.agent_evolving.signal.EvolutionTarget.SCRIPT) {
                    scriptEntries.add(record);
                } else {
                    sectionGroups.computeIfAbsent(record.getChange().getSection(), ignored -> new ArrayList<>()).add(record);
                }
            }

            for (Map.Entry<String, List<EvolutionRecord>> entry : sectionGroups.entrySet()) {
                renderSectionFile(evolutionDir, entry.getKey(), entry.getValue());
            }
            if (!scriptEntries.isEmpty()) {
                renderScriptIndex(evolutionDir.resolve("scripts"), scriptEntries);
            }
            updateSkillMdIndex(skillDir, activeEntries);
        }

        private void renderSectionFile(Path evolutionDir, String section, List<EvolutionRecord> records) {
            String filename = section == null ? "troubleshooting" : section.toLowerCase(Locale.ROOT).replace(" ", "_");
            List<String> lines = new ArrayList<>();
            lines.add("# " + (section != null ? section : ""));
            lines.add("");
            lines.add("> Auto-generated from evolutions.json. Do not edit directly.");
            lines.add("");
            for (EvolutionRecord record : records) {
                String content = record.getChange() != null && record.getChange().getContent() != null
                        ? record.getChange().getContent() : "";
                String[] parts = content.split("\\R", 2);
                lines.add("### [" + record.getId() + "] " + parts[0]);
                if (parts.length > 1 && !parts[1].isBlank()) {
                    lines.add(parts[1].stripTrailing());
                }
                String appliedTag = record.isApplied() ? " | applied" : "";
                lines.add("");
                lines.add("*Source: " + record.getSource() + " | " + record.getTimestamp() + appliedTag + "*");
                lines.add("");
                lines.add("---");
                lines.add("");
            }
            try {
                Files.writeString(evolutionDir.resolve(filename + ".md"), String.join("\n", lines), StandardCharsets.UTF_8);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        private void renderScriptIndex(Path scriptsDir, List<EvolutionRecord> entries) {
            try {
                Files.createDirectories(scriptsDir);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
            List<String> lines = new ArrayList<>();
            lines.add("# Script Index");
            lines.add("");
            lines.add("> Auto-generated from evolutions.json. Do not edit directly.");
            lines.add("");
            lines.add("| File | Language | Purpose | Source |");
            lines.add("|------|----------|---------|--------|");
            for (EvolutionRecord record : entries) {
                String filename = record.getChange() != null && record.getChange().getScriptFilename() != null
                        ? record.getChange().getScriptFilename() : record.getId();
                String lang = record.getChange() != null && record.getChange().getScriptLanguage() != null
                        ? record.getChange().getScriptLanguage() : "unknown";
                String purpose = record.getChange() != null && record.getChange().getScriptPurpose() != null
                        ? record.getChange().getScriptPurpose() : "";
                String date = record.getTimestamp() != null && record.getTimestamp().length() >= 10
                        ? record.getTimestamp().substring(0, 10) : String.valueOf(record.getTimestamp());
                lines.add("| [" + filename + "](" + filename + ") | " + lang + " | " + purpose + " | " + date + " |");
            }
            lines.add("");
            try {
                Files.writeString(scriptsDir.resolve("_index.md"), String.join("\n", lines), StandardCharsets.UTF_8);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        private void updateSkillMdIndex(Path skillDir, List<EvolutionRecord> entries) {
            Path skillMdPath = skillDir.resolve("SKILL.md");
            if (!Files.exists(skillMdPath)) {
                List<Path> mdFiles = new ArrayList<>();
                try (var stream = Files.list(skillDir)) {
                    stream.filter(path -> path.getFileName().toString().endsWith(".md")).forEach(mdFiles::add);
                } catch (IOException exc) {
                    throw new UncheckedIOException(exc);
                }
                if (!mdFiles.isEmpty()) {
                    skillMdPath = mdFiles.get(0);
                } else {
                    return;
                }
            }

            int bodyCount = 0;
            int descCount = 0;
            int scriptCount = 0;
            Map<String, Integer> sectionCounts = new LinkedHashMap<>();
            for (EvolutionRecord record : entries) {
                if (record.getChange() == null) {
                    continue;
                }
                switch (record.getChange().getTarget()) {
                    case BODY -> bodyCount++;
                    case DESCRIPTION -> descCount++;
                    case SCRIPT -> scriptCount++;
                    default -> {
                    }
                }
                if (record.getChange().getTarget() != com.openjiuwen.agent_evolving.signal.EvolutionTarget.SCRIPT) {
                    String section = record.getChange().getSection();
                    sectionCounts.put(section, sectionCounts.getOrDefault(section, 0) + 1);
                }
            }

            String content;
            try {
                content = Files.readString(skillMdPath, StandardCharsets.UTF_8);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }

            StringBuilder indexBlock = new StringBuilder();
            indexBlock.append("<!-- evolution-index-start -->\n");
            indexBlock.append("## Evolution Experiences\n\n");
            indexBlock.append("This skill has accumulated **").append(entries.size()).append("** evolution experiences (");
            List<String> parts = new ArrayList<>();
            if (bodyCount > 0) {
                parts.add(bodyCount + " body");
            }
            if (descCount > 0) {
                parts.add(descCount + " description");
            }
            if (scriptCount > 0) {
                parts.add(scriptCount + " script");
            }
            indexBlock.append(String.join(", ", parts));
            indexBlock.append(").\n\n");
            indexBlock.append("| Type | Count | Details |\n");
            indexBlock.append("|------|-------|---------|\n");
            for (Map.Entry<String, Integer> entry : sectionCounts.entrySet()) {
                String filename = entry.getKey().toLowerCase(Locale.ROOT).replace(" ", "_") + ".md";
                indexBlock.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue())
                        .append(" | [-> evolution/").append(filename).append("](evolution/").append(filename).append(") |\n");
            }
            if (scriptCount > 0) {
                indexBlock.append("| Scripts | ").append(scriptCount)
                        .append(" | [-> evolution/scripts/_index.md](evolution/scripts/_index.md) |\n");
            }
            indexBlock.append("*Last updated: ").append(Instant.now().toString()).append("*\n");
            indexBlock.append("<!-- evolution-index-end -->\n");

            String updated;
            int start = content.indexOf("<!-- evolution-index-start -->");
            int end = content.indexOf("<!-- evolution-index-end -->");
            if (start >= 0 && end >= 0 && end > start) {
                int close = end + "<!-- evolution-index-end -->".length();
                updated = content.substring(0, start) + indexBlock + content.substring(close);
            } else {
                updated = content.stripTrailing() + "\n\n" + indexBlock;
            }

            try {
                Files.writeString(skillMdPath, updated, StandardCharsets.UTF_8);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        private Path evolutionsPath(String skillName) {
            return resolveSkillDir(skillName).resolve("evolutions.json");
        }

        private Path archiveDir(String skillName) {
            Path archive = resolveSkillDir(skillName).resolve("archive");
            try {
                Files.createDirectories(archive);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
            return archive;
        }

        private void copy(Path from, Path to) {
            try {
                Files.createDirectories(to.getParent());
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }

        private String timestamp() {
            return DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());
        }
    }
}
