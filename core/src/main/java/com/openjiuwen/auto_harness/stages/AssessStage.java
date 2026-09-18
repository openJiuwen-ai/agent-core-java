/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.infra.EditScope;
import com.openjiuwen.auto_harness.infra.Parsers;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Abstract base for all assess-family stages.
 *
 * <p>Mirrors Python's {@code AssessStage} and module helpers in
 * {@code openjiuwen/auto_harness/stages/assess.py}.</p>
 */
public abstract class AssessStage extends SessionStage {

    private static final Logger LOGGER = Logger.getLogger(AssessStage.class.getName());
    private static final int RECENT_EXPERIENCE_LIMIT = 10;
    private static final int SOURCE_PREVIEW_LIMIT = 8;
    private static final int MIN_AGENT_REPORT_LENGTH = 100;

    public static final AssessAgentFactory DEFAULT_AGENT_FACTORY = (config, extraRails) -> {
        List<DeepAgentRail> rails = new ArrayList<>();
        if (extraRails != null) {
            for (Object rail : extraRails) {
                if (rail instanceof DeepAgentRail deepAgentRail) {
                    rails.add(deepAgentRail);
                }
            }
        }
        DeepAgent agent = AutoHarnessAgentFactory.createAssessAgent(config, rails);
        return agent::stream;
    };

    @Override
    public String name() {
        return "assess";
    }

    @Override
    public String displayName() {
        return "评估当前状态";
    }

    @Override
    public String description() {
        return "Assess current repository state.";
    }

    @Override
    public String slot() {
        return "assess";
    }

    @Override
    public List<String> produces() {
        return List.of("assessment");
    }

    public static String runAssessWithFallback(
            AutoHarnessConfig config,
            ExperienceStore experienceStore
    ) {
        return runAssessWithFallback(config, experienceStore, DEFAULT_AGENT_FACTORY);
    }

    public static String runAssessWithFallback(
            AutoHarnessConfig config,
            ExperienceStore experienceStore,
            AssessAgentFactory agentFactory
    ) {
        try {
            return assessWithAgent(config, experienceStore, agentFactory);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Agent assess failed, using fallback", exception);
            return fallbackAssess(config, experienceStore);
        }
    }

    public static Iterator<Object> runAssessStream(
            AutoHarnessConfig config,
            ExperienceStore experienceStore,
            List<OptimizationTask> inputTasks,
            List<?> extraRails,
            AssessAgentFactory agentFactory
    ) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        String query = buildQuery(resolvedConfig, experienceStore, inputTasks);
        AssessAgent agent = safeFactory(agentFactory).create(resolvedConfig, extraRails);
        return objectIterator(agent.stream(Map.of("query", query)));
    }

    public static List<Gap> runGapAnalysis(
            AutoHarnessConfig config,
            String harnessState
    ) {
        return runGapAnalysis(config, harnessState, DEFAULT_AGENT_FACTORY);
    }

    public static List<Gap> runGapAnalysis(
            AutoHarnessConfig config,
            String harnessState,
            AssessAgentFactory agentFactory
    ) {
        try {
            return analyzeGapsWithAgent(config, harnessState, agentFactory);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Agent gap analysis failed", exception);
            return List.of();
        }
    }

    public static String buildQuery(
            AutoHarnessConfig config,
            ExperienceStore experienceStore,
            List<OptimizationTask> inputTasks
    ) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        List<Experience> recent = recentExperiences(experienceStore, RECENT_EXPERIENCE_LIMIT);
        String experiencesText = formatExperiences(recent);
        String today = LocalDate.now().toString();
        String workspace = nonBlankOrDefault(resolvedConfig.getWorkspace(), ".");
        String checkStrategy = detectPythonCheckStrategy(workspace);
        String editScope = EditScope.renderEditScope("本轮评估需要遵守的可落地变更范围");
        String taskFocus = formatTaskFocus(inputTasks == null ? List.of() : inputTasks);
        String goal = nonBlankOrDefault(resolvedConfig.getOptimizationGoal(), "无");
        return "当前日期: " + today + "\n"
                + "工作目录: " + workspace + "\n\n"
                + "本轮目标: " + goal + "\n\n"
                + taskFocus + "\n\n"
                + editScope + "\n\n"
                + "Python 检查策略建议:\n"
                + checkStrategy + "\n\n"
                + "近期经验:\n" + experiencesText + "\n\n"
                + "请按照你的系统提示执行评估任务。"
                + "你的建议和后续任务候选必须落在上述可落地变更范围内。"
                + "不要把 `openjiuwen/auto_harness/**` 或其他范围外源码目录"
                + " 作为本轮建议修改目标。"
                + "优先遵循给出的 Python 检查策略建议，"
                + "不要臆测 allowlist 或 Makefile 行为。"
                + "如果提供了本轮目标，请围绕该目标缩小评估范围。"
                + "如果提供了重点竞品，请把差距分析作为评估重点。";
    }

    public static String formatTaskFocus(List<OptimizationTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "本轮显式输入任务: 无";
        }
        List<String> lines = new ArrayList<>();
        lines.add("本轮显式输入任务（必须作为调研焦点，不得跳过调研）:");
        for (OptimizationTask task : tasks) {
            String topic = nullToEmpty(task.getTopic());
            String detail = isBlank(task.getDescription()) ? topic : task.getDescription();
            String files = task.getFiles() == null || task.getFiles().isEmpty()
                    ? "未指定"
                    : String.join(", ", task.getFiles());
            lines.add("- " + topic + ": " + detail + "; files=" + files);
        }
        return String.join("\n", lines);
    }

    public static String formatPythonCheckStrategy(
            List<String> stagedFiles,
            List<String> modifiedFiles,
            List<String> untrackedFiles
    ) {
        List<String> staged = unique(stagedFiles);
        List<String> modified = unique(modifiedFiles);
        List<String> untracked = unique(untrackedFiles);

        if (!staged.isEmpty()) {
            return "检测到已暂存的 Python 文件。\n"
                    + "- staged: " + preview(staged) + "\n"
                    + "- 先运行 `make check` 与 `make type-check`，"
                    + "因为 Makefile 会基于 staged files 选择目标。\n"
                    + "- 若失败，按真实报错记录，不要归因于 allowlist。";
        }

        List<String> deltaFiles = unique(concat(modified, untracked));
        if (!deltaFiles.isEmpty()) {
            return "未检测到 staged Python 文件，但检测到工作区中的 Python 增量文件。\n"
                    + "- delta: " + preview(deltaFiles) + "\n"
                    + "- 不要运行 `make check COMMITS=1` 或 "
                    + "`make type-check COMMITS=1`，"
                    + "因为这类命令可能因未选中文件而直接失败。\n"
                    + "- 改为对这些增量文件显式运行 "
                    + "`uv run ruff check <files>` 与 `uv run mypy <files>`。\n"
                    + "- 若文件较多，聚焦 openjiuwen/harness 和 "
                    + "openjiuwen/core 的相关 Python 文件。";
        }

        return "当前只读快照中没有检测到 staged 或工作区 Python 增量文件。\n"
                + "- 不要运行 `make check COMMITS=1` 或 "
                + "`make type-check COMMITS=1`，"
                + "因为 Makefile 可能因未选中文件返回 "
                + "`No Python files selected`。\n"
                + "- 将 lint/type-check 标记为未执行，并明确原因是"
                + "“当前快照无可供 delta 检查的 Python 文件”。\n"
                + "- 若时间允许，可运行 `uv run pytest tests/unit_tests -q` "
                + "作为仓库健康度采样。";
    }

    public static String detectPythonCheckStrategy(String workspace) {
        List<String> staged = runGitLines(workspace, "diff", "--name-only", "--cached", "--", "*.py");
        List<String> changedSinceHead = runGitLines(workspace, "diff", "--name-only", "HEAD", "--", "*.py");
        List<String> untracked = runGitLines(workspace, "ls-files", "--others", "--exclude-standard", "--", "*.py");
        List<String> modified = new ArrayList<>();
        for (String path : changedSinceHead) {
            if (!staged.contains(path)) {
                modified.add(path);
            }
        }
        return formatPythonCheckStrategy(staged, modified, untracked);
    }

    public static String buildGapQuery(List<OptimizationTask> tasks, String goal) {
        StringBuilder taskSummary = new StringBuilder();
        for (OptimizationTask task : tasks == null ? List.<OptimizationTask>of() : tasks) {
            if (!taskSummary.isEmpty()) {
                taskSummary.append('\n');
            }
            String topic = nullToEmpty(task.getTopic());
            String detail = isBlank(task.getDescription()) ? topic : task.getDescription();
            taskSummary.append("- ").append(topic).append(": ").append(detail);
        }
        StringBuilder query = new StringBuilder();
        query.append("当前阶段: assess_ext\n")
                .append("当前 pipeline: extended_evolve_pipeline\n")
                .append("评估模式: runtime_extension_gap_assessment\n\n")
                .append("分析用户目标能力与当前 harness/runtime extension ")
                .append("可用能力之间的缺口。\n")
                .append("不要默认研究 Claude Code、Cursor、Aider 或主流编码 agent；")
                .append("只有用户明确要求吸收某个竞品、工具或产品能力时，")
                .append("才做对应竞品调研。\n\n");
        if (!isBlank(goal)) {
            query.append("本轮目标:\n").append(goal).append("\n\n");
        }
        if (!taskSummary.isEmpty()) {
            query.append("已知需求:\n").append(taskSummary).append("\n\n");
        }
        query.append("输出 markdown 表格，列：\n")
                .append("竞品 | 功能 | 当前状态 | 差距描述 | ")
                .append("影响(0-1) | 可行性(0-1) | ")
                .append("建议方案 | 目标文件\n\n")
                .append("说明：为兼容解析器，保留“竞品”列；")
                .append("但在本模式下该列表示来源/参考对象，")
                .append("不一定是真实竞品。可填写“用户需求”、")
                .append("“办公自动化”、“PPT生成工具”、“领域范式”，")
                .append("或用户明确提到的产品名。\n");
        return query.toString();
    }

    public static List<Gap> buildGaps(List<OptimizationTask> tasks) {
        List<Gap> gaps = new ArrayList<>();
        int index = 1;
        for (OptimizationTask task : tasks == null ? List.<OptimizationTask>of() : tasks) {
            String topic = nullToEmpty(task.getTopic());
            String description = isBlank(task.getDescription()) ? topic : task.getDescription();
            String suggested = firstNonBlank(task.getExpectedEffect(), task.getDescription(), topic);
            gaps.add(Gap.builder()
                    .id("gap_" + index++)
                    .competitor("")
                    .feature(topic)
                    .currentState("missing capability absorption workflow")
                    .gapDescription(description)
                    .impact(0.8)
                    .feasibility(0.8)
                    .suggestedApproach(suggested)
                    .targetFiles(task.getFiles() == null ? List.of() : new ArrayList<>(task.getFiles()))
                    .build());
        }
        return gaps;
    }

    public static String gapSummary(Gap gap) {
        String feature = firstNonBlank(gap == null ? "" : gap.getFeature(), gap == null ? "" : gap.getId(), "runtime gap")
                .strip();
        String description = gap == null ? "" : nullToEmpty(gap.getGapDescription()).strip();
        if (!description.isEmpty() && !description.equals(feature)) {
            return feature + " - " + abbreviate(description, 80);
        }
        return feature;
    }

    static String fallbackAssess(AutoHarnessConfig config, ExperienceStore experienceStore) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        List<Experience> recent = recentExperiences(experienceStore, RECENT_EXPERIENCE_LIMIT);
        String workspace = resolvedConfig.getWorkspace();
        String changes = collectRecentChanges(workspace);
        String source = collectSourceSummary(workspace);
        return String.join(
                "\n",
                "# 自动评估报告\n",
                "## 当前状态\n",
                source,
                "\n### 近期变更\n",
                isBlank(changes) ? "_无 git 历史_" : changes,
                "\n## 近期经验\n",
                formatExperiences(recent),
                "\n## 改进方向\n",
                deriveDirections(recent)
        );
    }

    static String collectAgentText(Iterator<?> stream) {
        StringBuilder output = new StringBuilder();
        Iterator<?> iterator = stream == null ? List.of().iterator() : stream;
        while (iterator.hasNext()) {
            output.append(Parsers.extractText(iterator.next()));
        }
        return output.toString();
    }

    static AssessAgentFactory safeFactory(AssessAgentFactory agentFactory) {
        return agentFactory == null ? DEFAULT_AGENT_FACTORY : agentFactory;
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

    private static String assessWithAgent(
            AutoHarnessConfig config,
            ExperienceStore experienceStore,
            AssessAgentFactory agentFactory
    ) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        String query = buildQuery(resolvedConfig, experienceStore, List.of());
        AssessAgent agent = safeFactory(agentFactory).create(resolvedConfig, null);
        String report = collectAgentText(agent.stream(Map.of("query", query)));
        if (isBlank(report) || report.length() < MIN_AGENT_REPORT_LENGTH) {
            LOGGER.warning("Agent report too short (" + report.length() + " chars), falling back");
            return fallbackAssess(resolvedConfig, experienceStore);
        }
        return report;
    }

    private static List<Gap> analyzeGapsWithAgent(
            AutoHarnessConfig config,
            String harnessState,
            AssessAgentFactory agentFactory
    ) {
        AutoHarnessConfig resolvedConfig = config == null ? new AutoHarnessConfig() : config;
        String state = nullToEmpty(harnessState);
        String clipped = state.length() > 3000 ? state.substring(0, 3000) : state;
        String query = "分析 harness 与主流编码 agent 的差距。\n\n"
                + "当前 harness 状态:\n"
                + clipped + "\n\n"
                + "输出 markdown 表格，列：\n"
                + "竞品 | 功能 | 当前状态 | 差距描述 | "
                + "影响(0-1) | 可行性(0-1) | "
                + "建议方案 | 目标文件\n";
        AssessAgent agent = safeFactory(agentFactory).create(resolvedConfig, null);
        String output = collectAgentText(agent.stream(Map.of("query", query)));
        return Parsers.parseGaps(output);
    }

    private static List<Experience> recentExperiences(ExperienceStore experienceStore, int limit) {
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
            return "_无近期经验记录_";
        }
        List<String> lines = new ArrayList<>();
        for (Experience experience : experiences) {
            ExperienceType type = experience.getType();
            String typeValue = type == null ? "" : type.value();
            String summary = isBlank(experience.getSummary()) ? nullToEmpty(experience.getOutcome()) : experience.getSummary();
            lines.add("- [" + typeValue + "] **" + nullToEmpty(experience.getTopic()) + "**: " + summary);
        }
        return String.join("\n", lines);
    }

    private static String deriveDirections(List<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return "- 收集更多运行数据后再生成改进方向";
        }
        List<String> topics = experiences.stream()
                .filter(experience -> ExperienceType.FAILURE.equals(experience.getType()))
                .map(Experience::getTopic)
                .filter(topic -> !isBlank(topic))
                .distinct()
                .sorted()
                .toList();
        if (!topics.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (String topic : topics) {
                lines.add("- 修复近期失败: " + topic);
            }
            return String.join("\n", lines);
        }
        return "- 继续当前优化方向，暂无明显瓶颈";
    }

    private static List<String> runGitLines(String workspace, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(args));
            ProcessBuilder builder = new ProcessBuilder(command);
            Path cwd = Path.of(nonBlankOrDefault(workspace, "."));
            if (!looksLikeGitWorkspace(cwd)) {
                return List.of();
            }
            if (Files.isDirectory(cwd)) {
                builder.directory(cwd.toFile());
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                closeQuietly(process.getInputStream());
                return List.of();
            }
            String stdout;
            try (InputStream stdoutStream = process.getInputStream()) {
                stdout = new String(stdoutStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (process.exitValue() != 0) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (String line : stdout.split("\\R")) {
                String stripped = line.strip();
                if (!stripped.isEmpty()) {
                    lines.add(stripped);
                }
            }
            return lines;
        } catch (IOException exception) {
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private static boolean looksLikeGitWorkspace(Path cwd) {
        Path current = cwd == null ? Path.of(".").toAbsolutePath().normalize() : cwd.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static void closeQuietly(InputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException ignored) {
            // Best-effort cleanup for failed git probing.
        }
    }

    private static String collectRecentChanges(String workspace) {
        return String.join("\n", runGitLines(workspace, "log", "--oneline", "-20"));
    }

    private static String collectSourceSummary(String workspace) {
        Path root = Path.of(nonBlankOrDefault(workspace, "."));
        List<String> keyDirs = List.of(
                "openjiuwen/core",
                "openjiuwen/harness",
                "tests/unit_tests",
                "examples",
                "docs"
        );
        List<String> lines = new ArrayList<>();
        for (String dir : keyDirs) {
            Path path = root.resolve(dir);
            if (Files.isDirectory(path)) {
                lines.add("- `" + dir + "/`: " + countPythonFiles(path) + " .py files");
            } else {
                lines.add("- `" + dir + "/`: _not found_");
            }
        }
        return String.join("\n", lines);
    }

    private static long countPythonFiles(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(Files::isRegularFile)
                    .filter(item -> item.getFileName().toString().endsWith(".py"))
                    .count();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static List<String> unique(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values == null ? List.of() : values));
    }

    private static List<String> concat(List<String> left, List<String> right) {
        List<String> result = new ArrayList<>(left == null ? List.of() : left);
        result.addAll(right == null ? List.of() : right);
        return result;
    }

    private static String preview(List<String> values) {
        return String.join(", ", values.subList(0, Math.min(SOURCE_PREVIEW_LIMIT, values.size())));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static String abbreviate(String value, int maxLength) {
        String text = nullToEmpty(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nonBlankOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    /**
     * Streaming surface used by the assess agent.
     *
     * <p>Mirrors Python's assess agent stream contract in
     * {@code openjiuwen/auto_harness/stages/assess.py}.</p>
     */
    @FunctionalInterface
    public interface AssessAgent {
        Iterator<?> stream(Map<String, Object> inputs);
    }

    /**
     * Factory for the assessment agent.
     *
     * <p>Mirrors Python's late import of {@code create_assess_agent} in
     * {@code openjiuwen/auto_harness/stages/assess.py}.</p>
     */
    @FunctionalInterface
    public interface AssessAgentFactory {
        AssessAgent create(AutoHarnessConfig config, List<?> extraRails);
    }
}
