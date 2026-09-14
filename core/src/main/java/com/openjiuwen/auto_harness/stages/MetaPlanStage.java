/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.infra.EditScope;
import com.openjiuwen.auto_harness.infra.Parsers;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AssessmentArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskPlanArtifact;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generate the task plan for the current session.
 *
 * <p>Mirrors Python's {@code MetaPlanStage} and module helpers in
 * {@code openjiuwen/auto_harness/stages/plan.py}.</p>
 */
public class MetaPlanStage extends PlanStage {

    private static final Logger LOGGER = Logger.getLogger(MetaPlanStage.class.getName());
    private static final int RECENT_EXPERIENCE_LIMIT = 5;

    public static final PlanAgentFactory DEFAULT_AGENT_FACTORY = (config, extraRails) -> {
        DeepAgent agent = AutoHarnessAgentFactory.createPlanAgent(config, deepAgentRails(extraRails));
        return agent::stream;
    };

    private final PlanAgentFactory agentFactory;

    public MetaPlanStage() {
        this(DEFAULT_AGENT_FACTORY);
    }

    public MetaPlanStage(PlanAgentFactory agentFactory) {
        this.agentFactory = agentFactory == null ? DEFAULT_AGENT_FACTORY : agentFactory;
    }

    @Override
    public List<String> consumes() {
        return List.of("assessment");
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            throw new IllegalArgumentException("MetaPlanStage requires a SessionContext");
        }

        List<OptimizationTask> inputTasks = readInputTasks(sessionContext);
        String assessment = readAssessment(sessionContext.getArtifact("assessment"));
        List<Object> events = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        StringBuilder planText = new StringBuilder();

        Iterator<Object> stream = runPlanStream(
                sessionContext.getOrchestrator().getConfig(),
                assessment,
                sessionContext.getOrchestrator().getExperienceStore(),
                inputTasks,
                sessionContext.getOrchestrator().getStreamRails(),
                agentFactory
        );
        while (stream.hasNext()) {
            Object chunk = stream.next();
            String text = Parsers.extractText(chunk);
            if (!text.isEmpty()) {
                planText.append(text);
            }
            events.add(chunk);
        }

        String rawPlan = planText.toString();
        if (!rawPlan.strip().isEmpty()) {
            Path path = writeLatestPlan(sessionContext.getOrchestrator().getPaths().getRunsDir(), rawPlan);
            messages.add("规划原始输出已保存: " + path);
        }

        List<OptimizationTask> tasks = new ArrayList<>(Parsers.parseTasks(rawPlan));
        if (tasks.size() > 1) {
            tasks = new ArrayList<>(tasks.subList(0, 1));
            messages.add("规划阶段只保留最高优先级的 1 个任务");
        }
        if (tasks.isEmpty()) {
            if (!inputTasks.isEmpty()) {
                tasks = new ArrayList<>(inputTasks.subList(0, 1));
                messages.add("规划阶段未生成任务，回退执行最高优先级输入任务");
            } else {
                messages.add("规划阶段未生成任务，session 结束");
            }
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("task_plan", TaskPlanArtifact.builder()
                .tasks(tasks)
                .rawPlan(rawPlan)
                .build());
        events.add(StageResult.builder()
                .artifacts(artifacts)
                .messages(messages)
                .build());
        return events.iterator();
    }

    public static Iterator<Object> runPlanStream(
            AutoHarnessConfig config,
            String assessment,
            ExperienceStore experienceStore,
            List<OptimizationTask> inputTasks,
            List<?> extraRails,
            PlanAgentFactory agentFactory
    ) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        PlanAgent agent = safeFactory(agentFactory).create(resolvedConfig, extraRails);
        String query = buildPlanQuery(resolvedConfig, assessment, experienceStore, inputTasks);
        return objectIterator(agent.stream(Map.of("query", query)));
    }

    public static String buildPlanQuery(
            AutoHarnessConfig config,
            String assessment,
            ExperienceStore experienceStore,
            List<OptimizationTask> inputTasks
    ) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        String experiencesText = formatExperiences(listRecent(experienceStore, RECENT_EXPERIENCE_LIMIT));
        String editScope = EditScope.renderEditScope("本轮任务规划必须遵守的范围");
        String taskFocus = formatInputTasks(inputTasks == null ? List.of() : inputTasks);
        String goal = isBlank(resolvedConfig.getOptimizationGoal()) ? "无" : resolvedConfig.getOptimizationGoal();
        return "本轮目标:\n"
                + goal + "\n\n"
                + taskFocus + "\n\n"
                + editScope + "\n\n"
                + "评估报告:\n" + nullToEmpty(assessment) + "\n\n"
                + "近期经验:\n" + experiencesText + "\n\n"
                + "配置任务上限: " + resolvedConfig.getMaxTasksPerSession() + "\n"
                + "规划阶段实际输出上限: 1\n"
                + "自驱动槽位: " + resolvedConfig.getSelfDrivenSlots() + "\n"
                + "你本轮只能输出 1 个最高优先级任务，不要输出多个候选。"
                + "你输出的每个任务 `files` 都必须只包含上述范围内的路径。"
                + "如果某个候选任务需要改动范围外源码目录，直接丢弃该任务，不要输出到计划里。\n";
    }

    public static String formatInputTasks(List<OptimizationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "显式输入任务: 无";
        }
        List<String> lines = new ArrayList<>();
        lines.add("显式输入任务（作为规划焦点；如调研后仍合理，优先产出对应任务）:");
        for (OptimizationTask task : tasks) {
            String topic = nullToEmpty(task.getTopic());
            String description = isBlank(task.getDescription()) ? topic : task.getDescription();
            String files = task.getFiles() == null || task.getFiles().isEmpty()
                    ? "未指定"
                    : String.join(", ", task.getFiles());
            lines.add("- " + topic + ": " + description + "; files=" + files);
        }
        return String.join("\n", lines);
    }

    static List<OptimizationTask> readInputTasks(SessionContext sessionContext) {
        Object inputTasks = sessionContext.getArtifact("input_tasks", List.of());
        if (!(inputTasks instanceof List<?> list)) {
            return List.of();
        }
        List<OptimizationTask> tasks = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof OptimizationTask task) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    static List<DeepAgentRail> deepAgentRails(List<?> extraRails) {
        List<DeepAgentRail> rails = new ArrayList<>();
        for (Object rail : extraRails == null ? List.of() : extraRails) {
            if (rail instanceof DeepAgentRail deepAgentRail) {
                rails.add(deepAgentRail);
            }
        }
        return rails;
    }

    static Iterator<Object> objectIterator(Iterator<?> iterator) {
        Iterator<?> source = iterator == null ? List.of().iterator() : iterator;
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return source.hasNext();
            }

            @Override
            public Object next() {
                return source.next();
            }
        };
    }

    static PlanAgentFactory safeFactory(PlanAgentFactory factory) {
        return factory == null ? DEFAULT_AGENT_FACTORY : factory;
    }

    private static String readAssessment(Object assessmentArtifact) {
        if (assessmentArtifact instanceof AssessmentArtifact artifact) {
            return nullToEmpty(artifact.getReport());
        }
        return "";
    }

    private static Path writeLatestPlan(String runsDir, String rawPlan) {
        Path path = Path.of(runsDir == null || runsDir.isBlank() ? "." : runsDir)
                .resolve("latest_plan.md");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, rawPlan, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write latest plan: " + path, e);
        }
    }

    private static List<Experience> listRecent(ExperienceStore experienceStore, int limit) {
        if (experienceStore == null) {
            return List.of();
        }
        try {
            return experienceStore.listRecent(limit).join();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to list recent experiences", exception);
            return List.of();
        }
    }

    private static String formatExperiences(List<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return "无";
        }
        List<String> lines = new ArrayList<>();
        for (Experience experience : experiences) {
            ExperienceType type = experience.getType();
            String typeValue = type == null ? "" : type.value();
            String summary = isBlank(experience.getSummary())
                    ? nullToEmpty(experience.getOutcome())
                    : experience.getSummary();
            lines.add("- [" + typeValue + "] " + nullToEmpty(experience.getTopic()) + ": " + summary);
        }
        return String.join("\n", lines);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Streaming surface used by the plan agent.
     *
     * <p>Mirrors Python's late import of {@code create_plan_agent} in
     * {@code openjiuwen/auto_harness/stages/plan.py}.</p>
     */
    @FunctionalInterface
    public interface PlanAgent {
        Iterator<?> stream(Map<String, Object> inputs);
    }

    /**
     * Factory for the plan agent.
     *
     * <p>Mirrors Python's late import of {@code create_plan_agent} in
     * {@code openjiuwen/auto_harness/stages/plan.py}.</p>
     */
    @FunctionalInterface
    public interface PlanAgentFactory {
        PlanAgent create(AutoHarnessConfig config, List<?> extraRails);
    }
}
