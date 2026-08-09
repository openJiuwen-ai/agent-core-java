/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.checkpointing.EvolutionStore;
import com.openjiuwen.agentevolving.experience.PendingChange;
import com.openjiuwen.agentevolving.optimizer.LlmResilience;
import com.openjiuwen.agentevolving.optimizer.skill_call.TeamSkillExperienceOptimizer;
import com.openjiuwen.agentevolving.signal.TeamSignalDetector;
import com.openjiuwen.agentevolving.trajectory.StepKind;
import com.openjiuwen.agentevolving.trajectory.ToolCallDetail;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.rails.CallbackContext;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Team-skill evolution rail.
 *
 * <p>Mirrors Python's {@code TeamSkillEvolutionRail} in
 * {@code openjiuwen/harness/rails/evolution/team_skill_evolution_rail.py}.</p>
 */
public class TeamSkillEvolutionRail extends SkillEvolutionRail {

    private static final Set<String> TEAM_SKILL_KINDS = Set.of("team-skill", "swarm-skill");
    private static final Set<String> TEAM_TASK_NON_TERMINAL_STATES =
            Set.of("pending", "claimed", "in_progress", "blocked");
    private static final LlmResilience.LLMInvokePolicy SYSTEM_TEST_LLM_POLICY =
            new LlmResilience.LLMInvokePolicy(30.0d, 60.0d, 1, 0.0d, true);

    private final EvolutionStore store;
    private final Model llm;
    private final String model;
    private final String language;
    private final boolean teamAsyncEvolution;
    private final TeamSignalDetector teamSignalDetector;
    private final TeamSkillExperienceOptimizer generator;
    private final Queue<OutputSchema> pendingApprovalEvents = new ArrayDeque<>();
    private final Map<String, PendingChange> pendingApprovalSnapshots = new LinkedHashMap<>();

    public TeamSkillEvolutionRail(Path skillsDir) {
        this(skillsDir, null, "", "cn", true, true);
    }

    public TeamSkillEvolutionRail(Path skillsDir,
                                  Model llm,
                                  String model,
                                  boolean autoSave,
                                  boolean asyncEvolution) {
        this(skillsDir, llm, model, "cn", autoSave, asyncEvolution);
    }

    public TeamSkillEvolutionRail(Path skillsDir,
                                  Model llm,
                                  String model,
                                  String language,
                                  boolean autoSave,
                                  boolean asyncEvolution) {
        super(skillsDir);
        this.store = new EvolutionStore((skillsDir == null ? Path.of("skills") : skillsDir).toString());
        this.llm = llm;
        this.model = model == null ? "" : model;
        this.language = language == null || language.isBlank() ? "cn" : language;
        this.teamAsyncEvolution = asyncEvolution;
        setAutoSave(autoSave);
        this.teamSignalDetector = llm == null ? null : new TeamSignalDetector(
                llm,
                this.model,
                this.language,
                SYSTEM_TEST_LLM_POLICY,
                SYSTEM_TEST_LLM_POLICY,
                SYSTEM_TEST_LLM_POLICY
        );
        this.generator = llm == null ? null : new TeamSkillExperienceOptimizer(
                llm,
                this.model,
                this.language,
                null,
                SYSTEM_TEST_LLM_POLICY,
                store
        );
    }

    @Override
    public boolean isAsyncEvolution() {
        return teamAsyncEvolution;
    }

    public EvolutionStore getStore() {
        return store;
    }

    public EvolutionStore store() {
        return store;
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        super.afterInvoke(ctx);
        maybeRunPassiveTeamSkillEvolution();
    }

    public List<OutputSchema> drainPendingApprovalEvents() {
        List<OutputSchema> events = new ArrayList<>();
        while (!pendingApprovalEvents.isEmpty()) {
            events.add(pendingApprovalEvents.remove());
        }
        return events;
    }

    public List<OutputSchema> drainPendingApprovalEvents(boolean wait, double timeoutSeconds) {
        return drainPendingApprovalEvents();
    }

    public CompletionStage<Void> approveRecord(String requestId) {
        PendingChange pending = pendingApprovalSnapshots.remove(requestId);
        if (pending == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        for (EvolutionRecord record : pending.getPayload()) {
            stage = stage.thenCompose(ignored -> store.appendRecord(pending.getSkillName(), record));
        }
        return stage;
    }

    public CompletionStage<Void> onApproveRecord(String requestId) {
        return approveRecord(requestId);
    }

    public CompletionStage<Void> on_approve_record(String requestId) {
        return approveRecord(requestId);
    }

    protected Map<String, Object> snapshotForEvolution(CallbackContext ctx) {
        Map<String, Object> snapshot = new LinkedHashMap<>(super.snapshotForEvolution(ctx));
        snapshot.put("team_skill", true);
        return snapshot;
    }

    public static boolean isCompletedTeamTaskView(Object result) {
        String text = result == null ? "" : String.valueOf(result).toLowerCase();
        if (!text.contains("completed")) {
            return false;
        }
        for (String state : TEAM_TASK_NON_TERMINAL_STATES) {
            if (text.contains(state)) {
                return false;
            }
        }
        return true;
    }

    private void maybeRunPassiveTeamSkillEvolution() {
        if (!isAutoScan() || teamSignalDetector == null || generator == null || !hasCompletedTeamTaskView()) {
            return;
        }
        String skillName = detectUsedTeamSkill();
        if (skillName == null || !store.skillDefinitionExists(skillName)) {
            return;
        }
        Trajectory trajectory = buildTeamTrajectory();
        String skillContent = store.readSkillContent(skillName).toCompletableFuture().join();
        List<Map<String, Object>> issues = new ArrayList<>();
        try {
            List<Map<String, String>> detectedIssues = teamSignalDetector.detectTrajectoryIssues(trajectory, skillContent)
                    .toCompletableFuture()
                    .join();
            for (Map<String, String> issue : detectedIssues) {
                issues.add(new LinkedHashMap<>(issue));
            }
        } catch (RuntimeException exception) {
            return;
        }
        if (issues.isEmpty()) {
            return;
        }
        List<Map<String, Object>> generatorIssues = new ArrayList<>();
        for (Map<String, Object> issue : issues) {
            Map<String, Object> copiedIssue = new LinkedHashMap<>();
            copiedIssue.putAll(issue);
            generatorIssues.add(copiedIssue);
        }
        EvolutionRecord record;
        try {
            record = generator.generateTrajectoryPatch(trajectory, skillName, skillContent, generatorIssues);
        } catch (RuntimeException exception) {
            return;
        }
        if (record == null) {
            return;
        }
        List<EvolutionRecord> records = List.of(record);
        if (isAutoSave()) {
            for (EvolutionRecord item : records) {
                store.appendRecord(skillName, item).toCompletableFuture().join();
            }
            return;
        }
        stageApproval(skillName, records, trajectory);
    }

    private void stageApproval(String skillName, List<EvolutionRecord> records, Trajectory trajectory) {
        String requestId = "team_skill_evolve_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        PendingChange pending = PendingChange.make(skillName, records, trajectory, trajectory.toMessages());
        pending.setChangeId(requestId);
        pendingApprovalSnapshots.put(requestId, pending);
        pendingApprovalEvents.add(ApprovalEvents.buildTeamSkillApprovalEventFromRecords(
                skillName,
                requestId,
                records,
                language,
                "team"
        ));
    }

    private boolean hasCompletedTeamTaskView() {
        for (Map<String, Object> step : buildTrajectory()) {
            Map<String, Object> values = valuesOf(step);
            if (!Objects.equals("view_task", stringValue(values.get("tool_name")))) {
                continue;
            }
            Object result = firstPresent(values, "tool_result", "call_result");
            if (isCompletedTeamTaskView(result)) {
                return true;
            }
        }
        return false;
    }

    private String detectUsedTeamSkill() {
        Set<String> teamSkillNames = new LinkedHashSet<>();
        for (String name : store.listSkillNames()) {
            if (isTeamSkill(name)) {
                teamSkillNames.add(name);
            }
        }
        if (teamSkillNames.isEmpty()) {
            return null;
        }
        for (Map<String, Object> step : buildTrajectory()) {
            Map<String, Object> values = valuesOf(step);
            Object args = firstPresent(values, "tool_args", "call_args");
            if (Objects.equals("skill_tool", stringValue(values.get("tool_name")))) {
                String fromPayload = skillNameFromPayload(args, teamSkillNames);
                if (fromPayload != null) {
                    return fromPayload;
                }
            }
            String combined = stringValue(args) + " " + stringValue(firstPresent(values, "tool_result", "call_result"));
            for (String name : teamSkillNames) {
                if (combined.contains(name)) {
                    return name;
                }
            }
        }
        return null;
    }

    private boolean isTeamSkill(String name) {
        Path skillDir = store.resolveSkillDir(name);
        if (skillDir == null) {
            return false;
        }
        Path skillMd = skillDir.resolve("SKILL.md");
        if (!java.nio.file.Files.isRegularFile(skillMd)) {
            return false;
        }
        try {
            String content = java.nio.file.Files.readString(skillMd);
            for (String line : content.split("\\R")) {
                String stripped = line.strip();
                if ("---".equals(stripped) && !content.startsWith("---")) {
                    break;
                }
                if (stripped.startsWith("kind:")) {
                    String kind = stripped.substring("kind:".length()).strip().replace("\"", "").replace("'", "");
                    return TEAM_SKILL_KINDS.contains(kind);
                }
            }
        } catch (java.io.IOException exception) {
            return false;
        }
        return false;
    }

    private Trajectory buildTeamTrajectory() {
        List<TrajectoryStep> steps = new ArrayList<>();
        String sessionId = "team-skill-evolution";
        for (Map<String, Object> step : buildTrajectory()) {
            Map<String, Object> values = valuesOf(step);
            Object session = firstPresent(values, "conversation_id", "session_id");
            if (session != null && !String.valueOf(session).isBlank()) {
                sessionId = String.valueOf(session);
            }
            String toolName = stringValue(values.get("tool_name"));
            if (toolName.isBlank()) {
                continue;
            }
            ToolCallDetail detail = ToolCallDetail.builder()
                    .toolName(toolName)
                    .callArgs(firstPresent(values, "tool_args", "call_args"))
                    .callResult(firstPresent(values, "tool_result", "call_result"))
                    .build();
            steps.add(TrajectoryStep.builder()
                    .kind(StepKind.TOOL)
                    .operatorId(toolName)
                    .detail(detail)
                    .meta(Map.of("operator_id", toolName))
                    .build());
        }
        return Trajectory.builder()
                .executionId("team-skill-evolution")
                .sessionId(sessionId)
                .source("online")
                .steps(steps)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> valuesOf(Map<String, Object> step) {
        Object values = step.get("values");
        if (values instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return Map.of();
    }

    private static Object firstPresent(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static String skillNameFromPayload(Object payload, Set<String> knownTeamSkills) {
        if (payload instanceof Map<?, ?> map) {
            Object skillName = map.get("skill_name");
            if (skillName != null && knownTeamSkills.contains(String.valueOf(skillName))) {
                return String.valueOf(skillName);
            }
            Object path = map.get("path");
            String fromPath = skillNameFromText(path, knownTeamSkills);
            if (fromPath != null) {
                return fromPath;
            }
            return skillNameFromText(map.get("relative_file_path"), knownTeamSkills);
        }
        return skillNameFromText(payload, knownTeamSkills);
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

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
