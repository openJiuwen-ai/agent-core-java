/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.adapters.benchmarks;

import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.AdapterRegistry;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.BaseBenchAdapter;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.DockerEnvironment;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.EvalResult;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.ExecResult;
import com.openjiuwen.agentevolving.evaluator.evaluator_pipeline.Task;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SkillsBench benchmark adapter.
 *
 * <p>Mirrors Python's {@code SkillsBenchAdapter} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/benchmarks/skillsbench.py}.</p>
 */
public class SkillsBenchAdapter extends BaseBenchAdapter {

    static {
        AdapterRegistry.registerBenchmark("skillsbench", SkillsBenchAdapter.class);
    }

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final Pattern PASSED_PATTERN = Pattern.compile("(\\d+)\\s+passed");
    private static final Pattern FAILED_PATTERN = Pattern.compile("FAILED\\s+(.+?)\\s+-");
    private static final Pattern FAILED_COUNT_PATTERN = Pattern.compile("(\\d+)\\s+failed");
    private static final Pattern ERROR_COUNT_PATTERN = Pattern.compile("(\\d+)\\s+error");
    private static final Pattern ERROR_PATTERN = Pattern.compile("ERROR\\s+(.+?)\\s+-");

    private final String repoUrl;
    private Path repoPath;
    private Path tasksDir;
    private final String workspaceDir;
    private final String skillsMode;

    public SkillsBenchAdapter() {
        this(null);
    }

    public SkillsBenchAdapter(Map<String, Object> config) {
        super(config);
        Map<String, Object> effectiveConfig = getConfig();
        this.repoUrl = stringValue(effectiveConfig.get("repo_url"), "");
        this.repoPath = Path.of(stringValue(effectiveConfig.get("repo_path"), "./skillsbench"));
        this.tasksDir = Path.of(stringValue(effectiveConfig.get("tasks_dir"), "tasks"));
        this.workspaceDir = stringValue(effectiveConfig.get("workspace_dir"), "/workspace");
        this.skillsMode = stringValue(effectiveConfig.get("skills_mode"), "with_skills");
    }

    @Override
    public String name() {
        return "skillsbench";
    }

    @Override
    public boolean cloneRepo() {
        if (repoUrl.isBlank()) {
            LOGGER.info("  No repo_url configured, using local tasks dir: {}", tasksDir);
            return true;
        }

        LOGGER.info("  Cloning skillsbench repo: {} -> {}", repoUrl, repoPath);
        if (Files.exists(repoPath)) {
            LOGGER.info("  Repo already exists, pulling latest changes...");
            ProcessResult result = runProcess(List.of(gitPath(), "pull"), repoPath, Duration.ofSeconds(120));
            if (result.returnCode == 0) {
                LOGGER.info("  Repo updated successfully");
            } else {
                LOGGER.warning("  Git pull failed: {}", result.stderr);
            }
        } else {
            ProcessResult result = runProcess(
                    List.of(gitPath(), "clone", repoUrl, repoPath.toString()),
                    null,
                    Duration.ofSeconds(300));
            if (result.returnCode != 0) {
                LOGGER.error("  Git clone failed: {}", result.stderr);
                return false;
            }
            LOGGER.info("  Repo cloned successfully");
        }

        Path tasksInRepo = repoPath.resolve("tasks");
        if (Files.isDirectory(tasksInRepo)) {
            tasksDir = tasksInRepo;
            LOGGER.info("  Updated tasks_dir to: {}", tasksDir);
        } else {
            LOGGER.warning("  tasks directory not found in repo, using default: {}", tasksDir);
        }
        return true;
    }

    @Override
    public List<Task> loadTasks() {
        if (!Files.isDirectory(tasksDir)) {
            LOGGER.warning("Tasks directory not found: {}", tasksDir);
            return List.of();
        }

        try (var children = Files.list(tasksDir)) {
            List<Task> tasks = children
                    .filter(Files::isDirectory)
                    .sorted()
                    .map(this::loadSingleTask)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            LOGGER.info("Loaded {} tasks from {}", tasks.size(), tasksDir);
            return tasks;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read tasks from " + tasksDir, exception);
        }
    }

    @Override
    public CompletableFuture<Void> prepareEnvironment(Task task, DockerEnvironment env) {
        return CompletableFuture.runAsync(() -> {
            awaitResult(env.exec("mkdir -p " + workspaceDir, 10, null, null));
            awaitResult(env.exec("ln -sf " + workspaceDir + "/tests /tests 2>/dev/null || true", 10, null, null));
            awaitResult(env.exec("ln -sf " + workspaceDir + "/logs /logs 2>/dev/null || true", 10, null, null));

            Map<String, Object> envSpec = task.getEnvironmentSpec();
            Path taskDir = pathValue(envSpec.get("task_dir"));
            Path testsDir = pathValue(envSpec.get("tests_dir"));
            if (Files.isDirectory(testsDir)) {
                awaitResult(env.exec("mkdir -p " + workspaceDir + "/tests", 10, null, null));
                copyFlatDirectoryWithOneLevelSubdirs(env, testsDir, workspaceDir + "/tests");
                LOGGER.info("    Tests copied to {}/tests", workspaceDir);
            }

            Path workspaceSrc = taskDir.resolve("workspace");
            if (Files.isDirectory(workspaceSrc)) {
                copyFlatDirectoryWithOneLevelSubdirs(env, workspaceSrc, workspaceDir);
                LOGGER.info("    Workspace files copied");
            }

            Path solutionDir = pathValue(envSpec.get("solution_dir"));
            if (Files.isDirectory(solutionDir)) {
                try (var children = Files.list(solutionDir)) {
                    for (Path child : children.filter(Files::isRegularFile).sorted().toList()) {
                        copyFile(env, child, workspaceDir + "/" + child.getFileName());
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to read solution dir " + solutionDir, exception);
                }
                LOGGER.info("    Solution files copied");
            }

            Path instructionPath = taskDir.resolve("instruction.md");
            if (Files.isRegularFile(instructionPath)) {
                copyFile(env, instructionPath, workspaceDir + "/instruction.md");
                LOGGER.info("    Instruction copied");
            }
        });
    }

    @Override
    public CompletableFuture<EvalResult> evaluate(DockerEnvironment env, Task task) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> envSpec = task.getEnvironmentSpec();
            String testCommand = stringValue(envSpec.get("test_command"), "python -m pytest tests/test_outputs.py -v")
                    .replace("/tests/test_outputs.py", "tests/test_outputs.py");
            int verifierTimeout = verifierTimeout(task.getMetadata().get("verifier"));
            int testTimeout = intValue(envSpec.get("test_timeout"), verifierTimeout);

            ExecResult result = awaitResult(env.exec(testCommand, testTimeout, workspaceDir, null));
            String output = result.getStdout() + result.getStderr();
            double passRate = calculatePassRate(output);
            List<String> failedTests = extractFailedTests(output);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("returncode", result.getReturncode());
            details.put("output", output);
            details.put("pass_rate", passRate);
            details.put("failed_tests", failedTests);

            EvalResult evalResult = new EvalResult();
            evalResult.setPassed(result.isSuccess() && passRate >= 1.0);
            evalResult.setPassRate(passRate);
            evalResult.setTestOutput(output);
            evalResult.setReturncode(result.getReturncode());
            evalResult.setFailedTests(failedTests);
            evalResult.setTestDetails(details);
            return evalResult;
        });
    }

    String getSkillsMode() {
        return skillsMode;
    }

    Optional<Task> loadSingleTask(Path taskDir) {
        Path instructionPath = taskDir.resolve("instruction.md");
        if (!Files.isRegularFile(instructionPath)) {
            return Optional.empty();
        }

        String instruction;
        try {
            instruction = Files.readString(instructionPath, StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read instruction " + instructionPath, exception);
        }
        if (instruction.isBlank()) {
            return Optional.empty();
        }

        Path skillsDirPath = taskDir.resolve("environment").resolve("skills");
        boolean hasSkills = Files.isDirectory(skillsDirPath);
        List<String> skills = hasSkills ? listChildDirectories(skillsDirPath) : List.of();

        Map<String, Object> envSpec = new LinkedHashMap<>();
        envSpec.put("type", "docker");
        envSpec.put("dockerfile", taskDir.resolve("environment").resolve("Dockerfile").toString());
        envSpec.put("build_context", taskDir.resolve("environment").toString());
        envSpec.put("tests_dir", taskDir.resolve("tests").toString());
        envSpec.put("solution_dir", taskDir.resolve("solution").toString());
        envSpec.put("skills_dir", hasSkills ? skillsDirPath.toString() : "");
        envSpec.put("task_dir", taskDir.toString());
        envSpec.put("cpus", intValue(getConfig().get("cpus"), 1));
        envSpec.put("memory_mb", intValue(getConfig().get("memory_mb"), 2048));
        envSpec.put("timeout", intValue(getConfig().get("timeout"), 900));
        envSpec.put("test_command", stringValue(
                getConfig().get("test_command"),
                "cd " + workspaceDir + " && bash tests/test.sh"));
        envSpec.put("test_timeout", intValue(getConfig().get("test_timeout"), 300));

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("task_dir", taskDir.toString());

        return Optional.of(new Task(taskDir.getFileName().toString(), instruction, metadata, envSpec, hasSkills, skills));
    }

    private static String gitPath() {
        String executable = isWindows() ? "git.exe" : "git";
        String pathValue = Optional.ofNullable(System.getenv("PATH"))
                .filter(path -> !path.isBlank())
                .orElse(System.getenv("Path"));
        if (pathValue != null && !pathValue.isBlank()) {
            for (String entry : pathValue.split(Pattern.quote(System.getProperty("path.separator")))) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(entry).resolve(executable);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
                if (!isWindows()) {
                    Path unixCandidate = Path.of(entry).resolve("git");
                    if (Files.isRegularFile(unixCandidate)) {
                        return unixCandidate.toString();
                    }
                }
            }
        }
        throw new IllegalStateException("git executable not found in PATH");
    }

    private static ProcessResult runProcess(List<String> command, Path workdir, Duration timeout) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workdir != null) {
                builder.directory(workdir.toFile());
            }
            Process process = builder.start();
            boolean finished = process.waitFor(timeout.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult("", "Process timed out after " + timeout.toSeconds() + "s", -1);
            }
            return new ProcessResult(
                    readStream(process.getInputStream()),
                    readStream(process.getErrorStream()),
                    process.exitValue());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to run process: " + String.join(" ", command), exception);
        }
    }

    private static void copyFlatDirectoryWithOneLevelSubdirs(DockerEnvironment env, Path sourceDir, String targetDir) {
        try (var children = Files.list(sourceDir)) {
            for (Path child : children.sorted().toList()) {
                if (Files.isRegularFile(child)) {
                    copyFile(env, child, targetDir + "/" + child.getFileName());
                    continue;
                }
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String childTarget = targetDir + "/" + child.getFileName();
                awaitResult(env.exec("mkdir -p " + childTarget, 10, null, null));
                try (var grandChildren = Files.list(child)) {
                    for (Path grandChild : grandChildren.filter(Files::isRegularFile).sorted().toList()) {
                        copyFile(env, grandChild, childTarget + "/" + grandChild.getFileName());
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy directory " + sourceDir, exception);
        }
    }

    private static void copyFile(DockerEnvironment env, Path source, String target) {
        if (!awaitResult(env.copyTo(source, target))) {
            throw new IllegalStateException("Failed to copy " + source + " -> " + target);
        }
    }

    private static double calculatePassRate(String output) {
        int passed = firstCount(PASSED_PATTERN, output);
        int failed = firstCount(FAILED_COUNT_PATTERN, output);
        int errors = firstCount(ERROR_COUNT_PATTERN, output);
        int total = passed + failed + errors;
        if (total == 0) {
            return 0.0;
        }
        return (double) passed / total;
    }

    private static List<String> extractFailedTests(String output) {
        List<String> failedTests = new ArrayList<>();
        collectMatches(FAILED_PATTERN, output, failedTests);
        collectMatches(ERROR_PATTERN, output, failedTests);
        return failedTests;
    }

    private static void collectMatches(Pattern pattern, String output, List<String> failedTests) {
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            String testName = matcher.group(1).trim();
            if (!failedTests.contains(testName)) {
                failedTests.add(testName);
            }
        }
    }

    private static int verifierTimeout(Object verifierConfig) {
        if (verifierConfig instanceof Map<?, ?> verifierMap) {
            Object timeoutSec = verifierMap.get("timeout_sec");
            return intValue(timeoutSec, 300);
        }
        return 300;
    }

    private static int firstCount(Pattern pattern, String output) {
        Matcher matcher = pattern.matcher(output);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static List<String> listChildDirectories(Path directory) {
        try (var children = Files.list(directory)) {
            return children.filter(Files::isDirectory)
                    .sorted()
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list child directories for " + directory, exception);
        }
    }

    private static Path pathValue(Object value) {
        return Path.of(String.valueOf(value));
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static String readStream(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static <T> T awaitResult(CompletableFuture<T> future) {
        return future.join();
    }

    private record ProcessResult(String stdout, String stderr, int returnCode) {
    }
}
