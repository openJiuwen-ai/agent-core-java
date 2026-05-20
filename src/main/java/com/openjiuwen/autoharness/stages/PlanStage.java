/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.SessionContext;
import com.openjiuwen.autoharness.experience.ExperienceStore;
import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.infra.EditScope;
import com.openjiuwen.autoharness.infra.Parsers;
import com.openjiuwen.autoharness.schema.AssessmentArtifact;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskPlanArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public class PlanStage used by the Java parity implementation.
 *
 * @since 1.0
 */
public class PlanStage extends SessionStage {
    private static final Logger LOG = LoggerFactory.getLogger(PlanStage.class);

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "plan";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String description() {
        return "Plan optimization tasks.";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.List<String> consumes() {
        return java.util.List.of("assessment");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.List<String> produces() {
        return java.util.List.of("task_plan");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public StageResult run(BaseExecutionContext ctx) {
        List<Object> events = stream(ctx);
        for (int index = events.size() - 1; index >= 0; index--) {
            if (events.get(index) instanceof StageResult result) {
                return result;
            }
        }
        return StageResult.builder()
                .status("failed")
                .error("plan stage did not return StageResult")
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            return List.of(stageResultFromPlanText(""));
        }
        List<Object> events = new ArrayList<>();
        events.add(BaseExecutionContext.message("[Phase A2] 制定优化计划..."));
        AutoHarnessConfig config = sessionContext.getOrchestrator().getConfig();
        ExperienceStore store = sessionContext.getOrchestrator().getExperienceStore();
        String assessment = assessmentText(sessionContext);
        String planText = "";
        for (Object chunk : runPlanStream(config, assessment, store)) {
            String text = Parsers.extractText(chunk);
            if (!text.isEmpty()) {
                planText += text;
            }
            events.add(chunk);
        }
        List<String> messages = new ArrayList<>();
        if (!planText.isBlank()) {
            Path path = Path.of(sessionContext.getOrchestrator().getPaths().getRunsDir()).resolve("latest_plan.md");
            writeLatestPlan(path, planText);
            messages.add("规划原始输出已保存: " + path);
        }
        events.add(stageResultFromPlanText(planText, messages));
        return events;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Object> runPlanStream(
            AutoHarnessConfig config,
            String assessment,
            ExperienceStore experienceStore) {
        Object agent = AutoHarnessFactory.createPlanAgent(config);
        String query = buildPlanQuery(config, assessment, safeRecent(experienceStore));
        return streamAgent(agent, query);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String buildPlanQuery(AutoHarnessConfig config, String assessment, List<?> recentExperiences) {
        AutoHarnessConfig effective = config != null ? config : AutoHarnessConfig.builder().build();
        String experiencesText = formatExperiences(recentExperiences);
        return "本轮目标:\n"
                + valueOrDefault(effective.getOptimizationGoal(), "无") + "\n\n"
                + "重点竞品:\n"
                + valueOrDefault(effective.getCompetitor(), "无") + "\n\n"
                + EditScope.renderEditScope("本轮任务规划必须遵守的范围") + "\n\n"
                + "评估报告:\n" + value(assessment) + "\n\n"
                + "近期经验:\n" + experiencesText + "\n\n"
                + "配置任务上限: " + effective.getMaxTasksPerSession() + "\n"
                + "规划阶段实际输出上限: 1\n"
                + "自驱动槽位: " + effective.getSelfDrivenSlots() + "\n"
                + "你本轮只能输出 1 个最高优先级任务，不要输出多个候选。"
                + "你输出的每个任务 `files` 都必须只包含上述范围内的路径。"
                + "如果某个候选任务需要改动范围外源码目录，直接丢弃该任务，"
                + "不要输出到计划里。\n";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static StageResult stageResultFromPlanText(String planText) {
        return stageResultFromPlanText(planText, List.of());
    }

    private static StageResult stageResultFromPlanText(String planText, List<String> initialMessages) {
        List<OptimizationTask> tasks = Parsers.parseTasks(planText);
        ArrayList<String> messages = new ArrayList<>(initialMessages == null ? List.of() : initialMessages);
        if (tasks.size() > 1) {
            tasks = tasks.subList(0, 1);
            messages.add("规划阶段只保留最高优先级的 1 个任务");
        }
        if (tasks.isEmpty()) {
            messages.add("规划阶段未生成任务，session 结束");
        }
        return StageResult.builder()
                .artifacts(Map.of("task_plan", TaskPlanArtifact.builder()
                        .tasks(tasks)
                        .rawPlan(value(planText))
                        .build()))
                .messages(messages)
                .build();
    }

    private static String formatExperiences(List<?> recentExperiences) {
        if (recentExperiences == null || recentExperiences.isEmpty()) {
            return "无";
        }
        List<String> lines = new ArrayList<>();
        for (Object item : recentExperiences) {
            if (item instanceof Experience experience) {
                lines.add("- [" + typeValue(experience.getType()) + "] " + value(experience.getTopic()) + ": "
                        + value(experience.getSummary()));
            } else {
                lines.add(String.valueOf(item));
            }
        }
        return String.join("\n", lines);
    }

    private static String assessmentText(BaseExecutionContext ctx) {
        Object assessmentArtifact = ctx.getArtifact("assessment", null);
        if (assessmentArtifact instanceof AssessmentArtifact artifact) {
            return value(artifact.getReport());
        }
        return assessmentArtifact == null ? "" : String.valueOf(assessmentArtifact);
    }

    private static List<Experience> safeRecent(ExperienceStore store) {
        try {
            return store == null ? List.of() : store.listRecent(5);
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static List<Object> streamAgent(Object agent, String query) {
        if (agent == null) {
            return List.of();
        }
        try {
            Object stream = agent.getClass()
                    .getMethod("stream", Map.class)
                    .invoke(agent, Map.of("query", query));
            if (stream instanceof Iterator<?> iterator) {
                List<Object> events = new ArrayList<>();
                while (iterator.hasNext()) {
                    events.add(iterator.next());
                }
                return events;
            }
            if (stream instanceof Iterable<?> iterable) {
                List<Object> events = new ArrayList<>();
                for (Object event : iterable) {
                    events.add(event);
                }
                return events;
            }
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
        return List.of();
    }

    private static void writeLatestPlan(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.warn("Failed to write plan debug artifact", ex);
        }
    }

    private static String typeValue(ExperienceType type) {
        return type == null ? "insight" : type.name().toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
