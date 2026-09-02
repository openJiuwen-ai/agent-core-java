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
import com.openjiuwen.autoharness.schema.Gap;
import com.openjiuwen.autoharness.schema.StageResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public class AssessStage used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class AssessStage extends SessionStage {
    private static final Logger LOG = LoggerFactory.getLogger(AssessStage.class);

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return "assess";
    }

    /**
     * description.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String description() {
        return "Assess current repository state.";
    }

    /**
     * produces.
     *
     * @return List<String>
     * @since 0.1.7
     */
    @Override
    public java.util.List<String> produces() {
        return java.util.List.of("assessment");
    }

    /**
     * run.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StageResult run(BaseExecutionContext ctx) {
        List<Object> events = stream(ctx);
        for (int index = events.size() - 1; index >= 0; index--) {
            if (events.get(index) instanceof StageResult result) {
                return result;
            }
        }
        return StageResult.builder().status("failed").error("assess stage did not return StageResult").build();
    }

    /**
     * stream.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            return List.of(StageResult.builder()
                    .artifacts(Map.of("assessment", AssessmentArtifact.builder().report(description()).build()))
                    .build());
        }
        List<Object> events = new ArrayList<>();
        events.add(BaseExecutionContext.message("[Phase A1] 评估当前状态..."));
        String assessment = "";
        AutoHarnessConfig config = sessionContext.getOrchestrator().getConfig();
        ExperienceStore store = sessionContext.getOrchestrator().getExperienceStore();
        Object agent = AutoHarnessFactory.createAssessAgent(config);
        String query;
        try {
            query = buildQuery(config, store.listRecent(10), detectPythonCheckStrategy(config.getWorkspace()));
        } catch (IOException ex) {
            query = buildQuery(config, List.of(), detectPythonCheckStrategy(config.getWorkspace()));
        }
        for (Object chunk : streamAgent(agent, query)) {
            String text = Parsers.extractText(chunk);
            if (!text.isEmpty()) {
                assessment += text;
            }
            events.add(chunk);
        }
        if (assessment.isBlank()) {
            assessment = fallbackAssess(config, safeRecent(store));
        }
        Map<String, Object> artifacts = new java.util.LinkedHashMap<>();
        if (!assessment.isBlank()) {
            writeDebugArtifact(config.runsPath().toString(), "latest_assessment.md", assessment);
            artifacts.put("assessment", AssessmentArtifact.builder().report(assessment).build());
        }
        events.add(StageResult.builder().artifacts(artifacts).build());
        return events;
    }

    /**
     * runAssessWithFallback.
     * 
     * @param config config
     * @param store store
     * @return the result
     * @since 0.1.7
     */
    public static String runAssessWithFallback(AutoHarnessConfig config, ExperienceStore store) {
        try {
            return assessWithAgent(config, store);
        } catch (IllegalStateException | IOException ex) {
            LOG.warn("Agent assess failed, using fallback", ex);
            return fallbackAssess(effectiveConfig(config), safeRecent(store));
        }
    }

    /**
     * assessWithAgent.
     * 
     * @param config config
     * @param store store
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public static String assessWithAgent(AutoHarnessConfig config, ExperienceStore store) throws IOException {
        AutoHarnessConfig effective = effectiveConfig(config);
        Object agent = AutoHarnessFactory.createAssessAgent(effective);
        String query = buildQuery(effective, store == null ? List.of() : store.listRecent(10),
                detectPythonCheckStrategy(effective.getWorkspace()));
        String report = "";
        for (Object chunk : streamAgent(agent, query)) {
            report += Parsers.extractText(chunk);
        }
        if (report.isBlank() || report.length() < 100) {
            LOG.warn("Agent report too short ({} chars), falling back", report.length());
            return fallbackAssess(effective, safeRecent(store));
        }
        return report;
    }

    /**
     * runGapAnalysis.
     * 
     * @param config config
     * @param competitor competitor
     * @param harnessState harnessState
     * @return the result
     * @since 0.1.7
     */
    public static List<Gap> runGapAnalysis(AutoHarnessConfig config, String competitor, String harnessState) {
        try {
            return analyzeGapsWithAgent(config, competitor, harnessState);
        } catch (RuntimeException ex) {
            LOG.warn("Agent gap analysis failed", ex);
            return List.of();
        }
    }

    /**
     * analyzeGapsWithAgent.
     * 
     * @param config config
     * @param competitor competitor
     * @param harnessState harnessState
     * @return the result
     * @since 0.1.7
     */
    public static List<Gap> analyzeGapsWithAgent(AutoHarnessConfig config, String competitor, String harnessState) {
        AutoHarnessConfig effective = effectiveConfig(config);
        Object agent = AutoHarnessFactory.createAssessAgent(effective);
        String query = "分析 harness 与 " + value(competitor) + " 的差距。\n\n" + "当前 harness 状态:\n"
                + truncate(value(harnessState), 3000) + "\n\n" + "输出 markdown 表格，列：\n" + "竞品 | 功能 | 当前状态 | 差距描述 | "
                + "影响(0-1) | 可行性(0-1) | " + "建议方案 | 目标文件\n";
        String output = "";
        for (Object chunk : streamAgent(agent, query)) {
            output += Parsers.extractText(chunk);
        }
        return Parsers.parseGaps(output);
    }

    /**
     * buildQuery.
     * 
     * @param config config
     * @param recent recent
     * @param checkStrategy checkStrategy
     * @return the result
     * @since 0.1.7
     */
    public static String buildQuery(AutoHarnessConfig config, List<Experience> recent, String checkStrategy) {
        AutoHarnessConfig effective = effectiveConfig(config);
        String workspace = hasText(effective.getWorkspace()) ? effective.getWorkspace() : ".";
        return "当前日期: " + LocalDate.now() + "\n" + "工作目录: " + workspace + "\n\n" + "本轮目标: "
                + valueOrDefault(effective.getOptimizationGoal(), "无") + "\n\n" + "重点竞品: "
                + valueOrDefault(effective.getCompetitor(), "无") + "\n\n"
                + EditScope.renderEditScope("本轮评估需要遵守的可落地变更范围") + "\n\n" + "Python 检查策略建议:\n" + value(checkStrategy)
                + "\n\n" + "近期经验:\n" + formatExperiences(recent) + "\n\n" + "请按照你的系统提示执行评估任务。"
                + "你的建议和后续任务候选必须落在上述可落地变更范围内。" + "不要把 `openjiuwen/auto_harness/**` 或其他范围外源码目录" + " 作为本轮建议修改目标。"
                + "优先遵循给出的 Python 检查策略建议，" + "不要臆测 allowlist 或 Makefile 行为。" + "如果提供了本轮目标，请围绕该目标缩小评估范围。"
                + "如果提供了重点竞品，请把差距分析作为评估重点。";
    }

    /**
     * formatPythonCheckStrategy.
     * 
     * @param stagedFiles stagedFiles
     * @param modifiedFiles modifiedFiles
     * @param untrackedFiles untrackedFiles
     * @return the result
     * @since 0.1.7
     */
    public static String formatPythonCheckStrategy(List<String> stagedFiles, List<String> modifiedFiles,
            List<String> untrackedFiles) {
        List<String> staged = distinct(stagedFiles);
        List<String> modified = distinct(modifiedFiles);
        List<String> untracked = distinct(untrackedFiles);
        if (!staged.isEmpty()) {
            return "检测到已暂存的 Python 文件。\n" + "- staged: " + preview(staged) + "\n"
                    + "- 先运行 `make check` 与 `make type-check`，因为 Makefile 会基于 staged files 选择目标。\n"
                    + "- 若失败，按真实报错记录，不要归因于 allowlist。";
        }
        List<String> delta = new ArrayList<>();
        delta.addAll(modified);
        delta.addAll(untracked);
        delta = distinct(delta);
        if (!delta.isEmpty()) {
            return "未检测到 staged Python 文件，但检测到工作区中的 Python 增量文件。\n" + "- delta: " + preview(delta) + "\n"
                    + "- 不要运行 `make check COMMITS=1` 或 `make type-check COMMITS=1`，因为这类命令可能因未选中文件而直接失败。\n"
                    + "- 改为对这些增量文件显式运行 `uv run ruff check <files>` 与 `uv run mypy <files>`。\n"
                    + "- 若文件较多，聚焦 openjiuwen/harness 和 openjiuwen/core 的相关 Python 文件。";
        }
        return "当前只读快照中没有检测到 staged 或工作区 Python 增量文件。\n"
                + "- 不要运行 `make check COMMITS=1` 或 `make type-check COMMITS=1`，因为 Makefile"
                + " 可能因未选中文件返回 `No Python files selected`。\n"
                + "- 将 lint/type-check 标记为未执行，并明确原因是“当前快照无可供 delta 检查的 Python 文件”。\n"
                + "- 若时间允许，可运行 `uv run pytest tests/unit_tests -q` 作为仓库健康度采样。";
    }

    /**
     * fallbackAssess.
     * 
     * @param config config
     * @param recent recent
     * @return the result
     * @since 0.1.7
     */
    public static String fallbackAssess(AutoHarnessConfig config, List<Experience> recent) {
        AutoHarnessConfig effective = effectiveConfig(config);
        return String.join("\n",
                List.of("# 自动评估报告\n", "## 当前状态\n", collectSourceSummary(effective.getWorkspace()), "\n### 近期变更\n",
                        collectRecentChanges(effective.getWorkspace()), "\n## 近期经验\n", formatExperiences(recent),
                        "\n## 改进方向\n", deriveDirections(recent)));
    }

    /**
     * detectPythonCheckStrategy.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    private static String detectPythonCheckStrategy(String workspace) {
        return formatPythonCheckStrategy(runGitLines(workspace, "diff", "--name-only", "--cached", "--", "*.py"),
                modifiedPythonFiles(workspace),
                runGitLines(workspace, "ls-files", "--others", "--exclude-standard", "--", "*.py"));
    }

    /**
     * modifiedPythonFiles.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    private static List<String> modifiedPythonFiles(String workspace) {
        List<String> staged = runGitLines(workspace, "diff", "--name-only", "--cached", "--", "*.py");
        List<String> changed = runGitLines(workspace, "diff", "--name-only", "HEAD", "--", "*.py");
        return changed.stream().filter(path -> !staged.contains(path)).toList();
    }

    /**
     * runGitLines.
     * 
     * @param workspace workspace
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private static List<String> runGitLines(String workspace, String... args) {
        try {
            // Merge stderr + close stdin: avoids pipe deadlocks (unused stderr / interactive wait)
            List<String> command = concat("git", args);
            command.add(1, "--no-pager");
            Process process = new ProcessBuilder(command)
                    .directory(Path.of(hasText(workspace) ? workspace : ".").toFile()).redirectErrorStream(true)
                    .start();
            process.getOutputStream().close();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            if (code != 0) {
                return List.of();
            }
            return stdout.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IllegalStateException | IOException | InterruptedException ex) {
            // return empty and let the stage continue.
            return List.of();
        }
    }

    /**
     * collectRecentChanges.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    private static String collectRecentChanges(String workspace) {
        List<String> lines = runGitLines(workspace, "log", "--oneline", "-20");
        return lines.isEmpty() ? "_无 git 历史_" : String.join("\n", lines);
    }

    /**
     * collectSourceSummary.
     * 
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    private static String collectSourceSummary(String workspace) {
        Path root = Path.of(hasText(workspace) ? workspace : ".");
        List<String> keyDirs = List.of("openjiuwen/core", "openjiuwen/harness", "tests/unit_tests", "examples", "docs");
        List<String> lines = new ArrayList<>();
        for (String dir : keyDirs) {
            Path path = root.resolve(dir);
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    long count = stream.filter(item -> item.toString().endsWith(".py")).count();
                    lines.add("- `" + dir + "/`: " + count + " .py files");
                } catch (IOException ex) {
                    lines.add("- `" + dir + "/`: _not found_");
                }
            } else {
                lines.add("- `" + dir + "/`: _not found_");
            }
        }
        return String.join("\n", lines);
    }

    /**
     * formatExperiences.
     * 
     * @param experiences experiences
     * @return the result
     * @since 0.1.7
     */
    private static String formatExperiences(List<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return "_无近期经验记录_";
        }
        List<String> lines = new ArrayList<>();
        for (Experience exp : experiences) {
            lines.add("- [" + typeValue(exp.getType()) + "] **" + value(exp.getTopic()) + "**: "
                    + valueOrDefault(exp.getSummary(), exp.getOutcome()));
        }
        return String.join("\n", lines);
    }

    /**
     * deriveDirections.
     * 
     * @param experiences experiences
     * @return the result
     * @since 0.1.7
     */
    private static String deriveDirections(List<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return "- 收集更多运行数据后再生成改进方向";
        }
        List<String> failures =
            experiences.stream().filter(exp -> exp.getType() == ExperienceType.FAILURE).map(Experience::getTopic)
                    .filter(AssessStage::hasText).distinct().sorted().map(topic -> "- 修复近期失败: " + topic).toList();
        return failures.isEmpty() ? "- 继续当前优化方向，暂无明显瓶颈" : String.join("\n", failures);
    }

    /**
     * streamAgent.
     * 
     * @param agent agent
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> streamAgent(Object agent, String query) {
        if (agent == null) {
            return List.of();
        }
        try {
            Object stream = agent.getClass().getMethod("stream", Map.class).invoke(agent, Map.of("query", query));
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

    /**
     * writeDebugArtifact.
     * 
     * @param runsDir runsDir
     * @param filename filename
     * @param content content
     * @since 0.1.7
     */
    private static void writeDebugArtifact(String runsDir, String filename, String content) {
        try {
            Path path = Path.of(runsDir).resolve(filename);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.warn("Failed to write assess debug artifact", ex);
        }
    }

    /**
     * safeRecent.
     * 
     * @param store store
     * @return the result
     * @since 0.1.7
     */
    private static List<Experience> safeRecent(ExperienceStore store) {
        try {
            return store == null ? List.of() : store.listRecent(10);
        } catch (IOException ex) {
            return List.of();
        }
    }

    /**
     * effectiveConfig.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static AutoHarnessConfig effectiveConfig(AutoHarnessConfig config) {
        return config != null ? config : AutoHarnessConfig.builder().build();
    }

    /**
     * distinct.
     * 
     * @param items items
     * @return the result
     * @since 0.1.7
     */
    private static List<String> distinct(List<String> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().filter(AssessStage::hasText).distinct().toList();
    }

    /**
     * preview.
     * 
     * @param items items
     * @return the result
     * @since 0.1.7
     */
    private static String preview(List<String> items) {
        return String.join(", ", items.stream().limit(8).toList());
    }

    /**
     * concat.
     * 
     * @param first first
     * @param rest rest
     * @return the result
     * @since 0.1.7
     */
    private static List<String> concat(String first, String[] rest) {
        List<String> command = new ArrayList<>();
        command.add(first);
        command.addAll(List.of(rest));
        return command;
    }

    /**
     * typeValue.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    private static String typeValue(ExperienceType type) {
        return type == null ? "insight" : type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * value.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String value(String value) {
        return value == null ? "" : value;
    }

    /**
     * valueOrDefault.
     * 
     * @param value value
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * truncate.
     * 
     * @param value value
     * @param maxChars maxChars
     * @return the result
     * @since 0.1.7
     */
    private static String truncate(String value, int maxChars) {
        String text = value(value);
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    /**
     * hasText.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
