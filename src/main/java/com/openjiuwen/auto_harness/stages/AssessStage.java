package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.harness.DeepAgent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Assess the repository state for the current session.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.assess}.</p>
 */
public class AssessStage extends SessionStage {
    private static final Logger LOGGER = Logger.getLogger(AssessStage.class.getName());
    private static final AssessAgentFactory DEFAULT_AGENT_FACTORY = config -> {
        DeepAgent agent = AutoHarnessAgentFactory.createAssessAgent(config);
        return inputs -> agent.stream(inputs, null, List.of(StreamMode.OUTPUT));
    };

    @Override public String name() { return "assess"; }
    @Override public StageResult run(Object context) { return new StageResult(); }

    public interface AssessAgent {
        Iterator<Object> stream(Map<String, Object> inputs);
    }

    public interface AssessAgentFactory {
        AssessAgent create(AutoHarnessConfig config);
    }

    /**
     * Generate the assess report with fallback behavior.
     *
     * <p>Mirrors Python's {@code _run_assess_with_fallback}.</p>
     */
    public static String runAssessWithFallback(AutoHarnessConfig config, ExperienceStore experienceStore) {
        return runAssessWithFallback(config, experienceStore, DEFAULT_AGENT_FACTORY);
    }

    public static String runAssessWithFallback(
            AutoHarnessConfig config,
            ExperienceStore experienceStore,
            AssessAgentFactory agentFactory) {
        try {
            return assessWithAgent(config, experienceStore, agentFactory);
        } catch (RuntimeException e) {
            LOGGER.warning("Agent assess failed, using fallback: " + e.getMessage());
            return fallbackAssess(config, experienceStore);
        }
    }

    public static Iterator<Object> runAssessStream(AutoHarnessConfig config, ExperienceStore experienceStore) {
        return runAssessStream(config, experienceStore, DEFAULT_AGENT_FACTORY);
    }

    public static Iterator<Object> runAssessStream(
            AutoHarnessConfig config,
            ExperienceStore experienceStore,
            AssessAgentFactory agentFactory) {
        AssessAgent agent = agentFactory.create(config);
        String query = buildQuery(config, experienceStore);
        return agent.stream(Map.of("query", query));
    }

    public static String buildQuery(AutoHarnessConfig config, ExperienceStore experienceStore) {
        return buildQuery(config, experienceStore, detectPythonCheckStrategy(resolveWorkspace(config)));
    }

    public static String buildQuery(
            AutoHarnessConfig config,
            ExperienceStore experienceStore,
            String checkStrategy) {
        String experiencesText = formatExperiences(experienceStore.listRecent(10));
        String today = LocalDate.now().toString();
        String workspace = resolveWorkspace(config);
        String editScope = renderEditScope(
                "\u672c\u8f6e\u8bc4\u4f30\u9700\u8981\u9075\u5b88\u7684\u53ef\u843d\u5730\u53d8\u66f4\u8303\u56f4"
        );
        return "当前日期: " + today + "\n"
                + "工作目录: " + workspace + "\n\n"
                + "本轮目标: " + defaultIfBlank(config.getOptimizationGoal(), "\u65e0") + "\n\n"
                + "重点竞品: " + defaultIfBlank(config.getCompetitor(), "\u65e0") + "\n\n"
                + editScope + "\n\n"
                + "Python \u68c0\u67e5\u7b56\u7565\u5efa\u8bae\n"
                + defaultIfBlank(checkStrategy, formatPythonCheckStrategy(List.of(), List.of(), List.of())) + "\n\n"
                + "\u8fd1\u671f\u7ecf\u9a8c:\n" + experiencesText + "\n\n"
                + "\u8bf7\u6309\u7167\u4f60\u7684\u7cfb\u7edf\u63d0\u793a\u6267\u884c\u8bc4\u4f30\u4efb\u52a1\u3002"
                + "\u4f60\u7684\u5efa\u8bae\u548c\u540e\u7eed\u4efb\u52a1\u5019\u9009\u5fc5\u987b\u843d\u5730\u5728\u4e0a\u8ff0\u53ef\u843d\u5730\u53d8\u66f4\u8303\u56f4\u5185\u3002"
                + "\u4e0d\u8981\u628a `openjiuwen/auto_harness/**` \u6216\u5176\u4ed6\u8303\u56f4\u5916\u6e90\u7801\u76ee\u5f55"
                + " \u4f5c\u4e3a\u672c\u8f6e\u5efa\u8bae\u4fee\u6539\u76ee\u6807\u3002"
                + "\u4f18\u5148\u9075\u5faa\u7ed9\u51fa\u7684 Python \u68c0\u67e5\u7b56\u7565\u5efa\u8bae\uff0c"
                + "\u4e0d\u8981\u81c6\u6d4b allowlist \u6216 Makefile \u884c\u4e3a\u3002"
                + "\u5982\u679c\u63d0\u4f9b\u4e86\u672c\u8f6e\u76ee\u6807\uff0c\u8bf7\u56f4\u7ed5\u8be5\u76ee\u6807\u7f29\u5c0f\u8bc4\u4f30\u8303\u56f4\u3002"
                + "\u5982\u679c\u63d0\u4f9b\u4e86\u91cd\u70b9\u7ade\u54c1\uff0c\u8bf7\u628a\u5dee\u8ddd\u5206\u6790\u4f5c\u4e3a\u8bc4\u4f30\u91cd\u70b9\u3002";
    }

    public static String detectPythonCheckStrategy(String workspace) {
        List<String> staged = runGitLines(workspace, "diff", "--name-only", "--cached", "--", "*.py");
        List<String> changedSinceHead = runGitLines(workspace, "diff", "--name-only", "HEAD", "--", "*.py");
        List<String> untracked = runGitLines(workspace, "ls-files", "--others", "--exclude-standard", "--", "*.py");
        Set<String> stagedSet = new LinkedHashSet<>(staged);
        List<String> modified = changedSinceHead.stream()
                .filter(path -> !stagedSet.contains(path))
                .toList();
        return formatPythonCheckStrategy(staged, modified, untracked);
    }

    public static String formatPythonCheckStrategy(
            List<String> stagedFiles,
            List<String> modifiedFiles,
            List<String> untrackedFiles) {
        List<String> staged = distinct(stagedFiles);
        List<String> modified = distinct(modifiedFiles);
        List<String> untracked = distinct(untrackedFiles);
        if (!staged.isEmpty()) {
            return "\u68c0\u6d4b\u5230\u5df2\u6682\u5b58\u7684 Python \u6587\u4ef6\u3002\n"
                    + "- staged: " + preview(staged) + "\n"
                    + "- \u5148\u8fd0\u884c `make check` \u4e0e `make type-check`\uff0c"
                    + "\u56e0\u4e3a Makefile \u4f1a\u57fa\u4e8e staged files \u9009\u62e9\u76ee\u6807\u3002\n"
                    + "- \u82e5\u5931\u8d25\uff0c\u6309\u771f\u5b9e\u62a5\u9519\u8bb0\u5f55\uff0c\u4e0d\u8981\u5f52\u56e0\u4e8e allowlist\u3002";
        }

        List<String> deltaFiles = distinct(Stream.concat(modified.stream(), untracked.stream()).toList());
        if (!deltaFiles.isEmpty()) {
            return "\u672a\u68c0\u6d4b\u5230 staged Python \u6587\u4ef6\uff0c\u4f46\u68c0\u6d4b\u5230\u5de5\u4f5c\u533a\u4e2d\u7684 Python \u589e\u91cf\u6587\u4ef6\u3002\n"
                    + "- delta: " + preview(deltaFiles) + "\n"
                    + "- \u4e0d\u8981\u8fd0\u884c `make check COMMITS=1` \u6216 `make type-check COMMITS=1`\uff0c"
                    + "\u56e0\u4e3a\u8fd9\u7c7b\u547d\u4ee4\u53ef\u80fd\u56e0\u672a\u9009\u4e2d\u6587\u4ef6\u800c\u76f4\u63a5\u5931\u8d25\u3002\n"
                    + "- \u6539\u4e3a\u5bf9\u8fd9\u4e9b\u589e\u91cf\u6587\u4ef6\u663e\u5f0f\u8fd0\u884c "
                    + "`uv run ruff check <files>` \u4e0e `uv run mypy <files>`\u3002\n"
                    + "- \u82e5\u6587\u4ef6\u8f83\u591a\uff0c\u805a\u7126 openjiuwen/harness \u548c openjiuwen/core "
                    + "\u7684\u76f8\u5173 Python \u6587\u4ef6\u3002";
        }

        return "\u5f53\u524d\u53ea\u8bfb\u5feb\u7167\u4e2d\u6ca1\u6709\u68c0\u6d4b\u5230 staged \u6216\u5de5\u4f5c\u533a Python \u589e\u91cf\u6587\u4ef6\u3002\n"
                + "- \u4e0d\u8981\u8fd0\u884c `make check COMMITS=1` \u6216 `make type-check COMMITS=1`\uff0c"
                + "\u56e0\u4e3a Makefile \u53ef\u80fd\u56e0\u672a\u9009\u4e2d\u6587\u4ef6\u8fd4\u56de `No Python files selected`\u3002\n"
                + "- \u5c06 lint/type-check \u6807\u8bb0\u4e3a\u672a\u6267\u884c\uff0c"
                + "\u5e76\u660e\u786e\u539f\u56e0\u662f\u201c\u5f53\u524d\u5feb\u7167\u65e0\u53ef\u4f9b delta \u68c0\u67e5\u7684 Python \u6587\u4ef6\u201d\u3002\n"
                + "- \u82e5\u65f6\u95f4\u5141\u8bb8\uff0c\u53ef\u8fd0\u884c `uv run pytest tests/unit_tests -q` "
                + "\u4f5c\u4e3a\u4ed3\u5e93\u5065\u5eb7\u5ea6\u91c7\u6837\u3002";
    }

    private static String assessWithAgent(
            AutoHarnessConfig config,
            ExperienceStore experienceStore,
            AssessAgentFactory agentFactory) {
        StringBuilder report = new StringBuilder();
        Iterator<Object> chunks = runAssessStream(config, experienceStore, agentFactory);
        while (chunks.hasNext()) {
            report.append(extractContent(chunks.next()));
        }
        if (report.isEmpty() || report.length() < 100) {
            LOGGER.warning("Agent report too short (" + report.length() + " chars), falling back");
            return fallbackAssess(config, experienceStore);
        }
        return report.toString();
    }

    public static String extractContent(Object chunk) {
        Object payload = null;
        if (chunk instanceof OutputSchema outputSchema) {
            payload = outputSchema.getPayload();
        } else if (chunk != null) {
            payload = readPayload(chunk);
        }
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            return content != null ? String.valueOf(content) : "";
        }
        return "";
    }

    public static String fallbackAssess(AutoHarnessConfig config, ExperienceStore experienceStore) {
        List<Experience> recent = experienceStore.listRecent(10);
        String workspace = resolveWorkspace(config);
        List<String> sections = List.of(
                "# \u81ea\u52a8\u8bc4\u4f30\u62a5\u544a\n",
                "## \u5f53\u524d\u72b6\u6001\n",
                collectSourceSummary(workspace),
                "\n### \u8fd1\u671f\u53d8\u66f4\n",
                defaultIfBlank(collectRecentChanges(workspace), "_\u65e0 git \u5386\u53f2_"),
                "\n## \u8fd1\u671f\u7ecf\u9a8c\n",
                formatExperiences(recent),
                "\n## \u6539\u8fdb\u65b9\u5411\n",
                deriveDirections(recent)
        );
        return String.join("\n", sections);
    }

    public static String formatExperiences(List<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return "_\u65e0\u8fd1\u671f\u7ecf\u9a8c\u8bb0\u5f55_";
        }
        List<String> lines = new ArrayList<>();
        for (Experience experience : experiences) {
            String summary = !blank(experience.getSummary()) ? experience.getSummary() : experience.getOutcome();
            lines.add("- [" + experience.getType() + "] **" + experience.getTopic() + "**: " + summary);
        }
        return String.join("\n", lines);
    }

    public static String deriveDirections(List<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return "- \u6536\u96c6\u66f4\u591a\u8fd0\u884c\u6570\u636e\u540e\u518d\u751f\u6210\u6539\u8fdb\u65b9\u5411";
        }
        Set<String> failureTopics = experiences.stream()
                .filter(exp -> "failure".equals(exp.getType().toString()))
                .map(Experience::getTopic)
                .filter(topic -> topic != null && !topic.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        if (!failureTopics.isEmpty()) {
            return failureTopics.stream()
                    .map(topic -> "- \u4fee\u590d\u8fd1\u671f\u5931\u8d25: " + topic)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
        return "- \u7ee7\u7eed\u5f53\u524d\u4f18\u5316\u65b9\u5411\uff0c\u6682\u65e0\u660e\u663e\u74f6\u9888";
    }

    private static List<String> runGitLines(String workspace, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(List.of(args));
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(Path.of(workspace).toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return List.of();
            }
            return output.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    private static String renderEditScope(String header) {
        return header + ":\n"
                + "- \u6e90\u7801\u8def\u5f84\u53ea\u5141\u8bb8 `openjiuwen/harness/**`\u3001`openjiuwen/core/**`\n"
                + "- `openjiuwen/harness/**`\u3001`openjiuwen/core/**` \u4e0b\u7684\u6a21\u5757\u5185 "
                + "README/Markdown \u89c6\u4e3a\u6e90\u7801\u76ee\u5f55\u5185\u5bb9\uff0c\u53ef\u6b63\u5e38\u4fee\u6539\uff0c\u4f8b\u5982 "
                + "`openjiuwen/harness/cli/README.md`\n"
                + "- \u914d\u5957\u6587\u4ef6\u5141\u8bb8\u65b0\u589e\u6216\u4fee\u6539 `tests/**`\u3001`examples/**`\n"
                + "- \u5982\u679c\u4efb\u52a1\u9700\u8981\u65b0\u589e\u6216\u66f4\u65b0\u4ed3\u5e93\u7ea7\u6587\u6863\uff0c"
                + "\u53ea\u80fd\u5199\u5165 `docs/en/` \u548c `docs/zh/` \u4e0b\u7684 Markdown \u6587\u4ef6\uff1b"
                + "\u4e0d\u8981\u5728 `docs/` \u6839\u76ee\u5f55\u6216\u5176\u4ed6\u5b50\u76ee\u5f55\u65b0\u589e\u6587\u6863\n"
                + "- \u4e0d\u8981\u4fee\u6539 `openjiuwen/auto_harness/**` \u6216\u5176\u4ed6\u6e90\u7801\u76ee\u5f55\n"
                + "- \u5982\u679c\u4efb\u52a1\u5fc5\u987b\u6539\u5230\u8303\u56f4\u5916\u8def\u5f84\uff0c"
                + "\u505c\u6b62\u5e76\u660e\u786e\u62a5\u544a\u8303\u56f4\u51b2\u7a81\uff0c\u4e0d\u8981\u81ea\u884c\u8d8a\u754c";
    }

    private static String collectRecentChanges(String workspace) {
        try {
            ProcessBuilder builder = new ProcessBuilder("git", "log", "--oneline", "-20");
            builder.directory(Path.of(workspace).toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            int exitCode = process.waitFor();
            return exitCode == 0 ? output : "";
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }

    private static String collectSourceSummary(String workspace) {
        Path root = Path.of(workspace);
        List<String> dirs = List.of("openjiuwen/core", "openjiuwen/harness", "tests/unit_tests", "examples", "docs");
        List<String> lines = new ArrayList<>();
        for (String dir : dirs) {
            Path path = root.resolve(dir);
            if (Files.isDirectory(path)) {
                lines.add("- `" + dir + "/`: " + countPythonFiles(path) + " .py files");
            } else {
                lines.add("- `" + dir + "/`: _not found_");
            }
        }
        return String.join("\n", lines);
    }

    private static long countPythonFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".py")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static Object readPayload(Object chunk) {
        try {
            Method getter = chunk.getClass().getMethod("getPayload");
            return getter.invoke(chunk);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field field = chunk.getClass().getDeclaredField("payload");
                field.setAccessible(true);
                return field.get(chunk);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static List<String> distinct(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static String preview(List<String> values) {
        return String.join(", ", values.stream().limit(8).toList());
    }

    private static String resolveWorkspace(AutoHarnessConfig config) {
        if (config == null || config.getWorkspace() == null || config.getWorkspace().isBlank()) {
            return ".";
        }
        return config.getWorkspace();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
