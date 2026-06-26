/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * Evaluation pipeline runner for single-run and skill-evolution benchmark loops.
 *
 * <p>Mirrors Python's {@code EvolutionPipeline} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/pipeline.py}.</p>
 */
public class EvolutionPipeline {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final PipelineConfig config;
    private final BaseAgentAdapter agent;
    private final BaseBenchAdapter bench;
    private final SkillManager skillManager;
    private String baseImageTag;

    public EvolutionPipeline(PipelineConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.agent = createAgent(config.getAgent(), config.getAgentConfig());
        this.bench = createBench(config.getBenchmark(), config.getBenchConfig());
        this.skillManager = config.isEvolutionMode() ? new SkillManager(config) : null;
    }

    public PipelineConfig getConfig() {
        return config;
    }

    public BaseAgentAdapter getAgent() {
        return agent;
    }

    public BaseBenchAdapter getBench() {
        return bench;
    }

    public static BaseBenchAdapter createBench(String name, Map<String, Object> config) {
        return AdapterRegistry.createBenchmark(name, safeMap(config));
    }

    protected BaseAgentAdapter createAgent(String name, Map<String, Object> agentConfig) {
        return AdapterRegistry.createAgent(name, safeMap(agentConfig));
    }

    public CompletableFuture<List<PipelineResult>> run() {
        return CompletableFuture.supplyAsync(this::runSync);
    }

    public List<PipelineResult> runSync() {
        LOGGER.info("{}", "=".repeat(60));
        LOGGER.info("Initializing benchmark: {}", config.getBenchmark());
        LOGGER.info("{}", "=".repeat(60));

        if (!bench.cloneRepo()) {
            LOGGER.error("Failed to clone benchmark repository");
            return List.of();
        }

        List<Task> tasks = new ArrayList<>(bench.loadTasks() == null ? List.of() : bench.loadTasks());
        if (config.getTaskIds() != null && !config.getTaskIds().isEmpty()) {
            tasks = tasks.stream()
                    .filter(task -> config.getTaskIds().contains(task.getTaskId()))
                    .toList();
        }

        if (tasks.isEmpty()) {
            LOGGER.info("No tasks to run");
            return List.of();
        }

        LOGGER.info("{}", "=".repeat(60));
        String mode = config.isEvolutionMode() ? "EVOLUTION" : "SINGLE-RUN";
        LOGGER.info("Pipeline: {} mode | {} tasks", mode, tasks.size());
        LOGGER.info("Agent: {} | Benchmark: {}", config.getAgent(), config.getBenchmark());
        if (config.isEvolutionMode()) {
            LOGGER.info("Max iterations: {}", config.getMaxIterations());
            LOGGER.info("Convergence: {}", config.isConvergenceCheck() ? "enabled" : "disabled");
        }
        LOGGER.info("{}", "=".repeat(60));

        List<PipelineResult> results = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            Task task = tasks.get(index);
            LOGGER.info("[{}/{}] Task: {}", index + 1, tasks.size(), task.getTaskId());
            try {
                results.add(runTask(task).join());
            } catch (RuntimeException exception) {
                Throwable cause = unwrap(exception);
                LOGGER.error("  Task failed: {}", cause.getMessage());
                PipelineResult result = new PipelineResult();
                result.setTaskId(task.getTaskId());
                result.setAgentName(config.getAgent());
                result.setBenchmarkName(config.getBenchmark());
                result.setTotalIterations(0);
                result.setConvergenceAchieved(false);
                result.setConvergenceType("error");
                result.setMetrics(Map.of("error", String.valueOf(cause.getMessage())));
                results.add(result);
            }
        }

        printSummary(results);
        saveResultsSummary(results);
        return results;
    }

    protected void saveResultsSummary(List<PipelineResult> results) {
        ensureDirectory(config.getResultsDir());

        Map<String, Object> summaryData = new LinkedHashMap<>();
        summaryData.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        summaryData.put("agent", config.getAgent());
        summaryData.put("benchmark", config.getBenchmark());
        summaryData.put("evolution_mode", config.isEvolutionMode());
        summaryData.put("max_iterations", config.getMaxIterations());
        summaryData.put("total_tasks", results.size());
        summaryData.put("passed_tasks", results.stream().filter(PipelineResult::isConvergenceAchieved).count());
        summaryData.put("failed_tasks", results.stream().filter(result -> !result.isConvergenceAchieved()).count());
        summaryData.put("tasks", results.stream().map(PipelineResult::toDict).toList());

        Path summaryPath = config.getResultsDir().resolve("summary.json");
        writeJson(summaryPath, summaryData);
        LOGGER.info("Results summary saved to: {}", summaryPath);
    }

    public CompletableFuture<PipelineResult> runTask(Task task) {
        if (config.isEvolutionMode()) {
            return runEvolution(task);
        }
        return runSingle(task);
    }

    protected CompletableFuture<PipelineResult> runSingle(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            DockerEnvironment env = createAndStartEnv(task).join();
            try {
                bench.prepareEnvironment(task, env).join();

                Boolean setupOk = agent.setup(env).join();
                if (!Boolean.TRUE.equals(setupOk)) {
                    throw new RuntimeException("Agent setup failed");
                }

                if (task.isHasSkills()) {
                    loadTaskSkills(env, task).join();
                }

                Path logsDir = config.getResultsDir().resolve(task.getTaskId());
                ensureDirectory(logsDir);
                agent.setLogsDir(logsDir);

                AgentContext context = new AgentContext();
                context.setIteration(1);
                context.setHasSkill(task.isHasSkills());
                AgentRunResult agentResult = agent.run(env, task, context).join();

                if (config.isSaveTrajectory()) {
                    writeText(logsDir.resolve("agent_output.txt"), nullToEmpty(agentResult.getRawOutput()));
                    if (agentResult.getTrajectory() != null && !agentResult.getTrajectory().isEmpty()) {
                        writeJson(logsDir.resolve("trajectory.json"), agentResult.getTrajectory());
                    }
                }

                EvalResult evalResult = bench.evaluate(env, task).join();
                IterationResult iterationResult = new IterationResult();
                iterationResult.setIteration(1);
                iterationResult.setAgentResult(agentResult);
                iterationResult.setEvalResult(evalResult);
                iterationResult.setSkillDelta(new SkillDelta());

                if (config.isSaveTrajectory()) {
                    saveIterationResult(logsDir, iterationResult);
                }

                PipelineResult result = new PipelineResult();
                result.setTaskId(task.getTaskId());
                result.setAgentName(config.getAgent());
                result.setBenchmarkName(config.getBenchmark());
                result.setTotalIterations(1);
                result.setConvergenceAchieved(evalResult.isPassed());
                result.setConvergenceType(evalResult.isPassed() ? "single_pass" : "single_fail");
                result.setResults(List.of(iterationResult));
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("pass_rate", evalResult.getPassRate());
                metrics.put("passed", evalResult.isPassed());
                result.setMetrics(metrics);
                result.setOutputDir(logsDir);
                return result;
            } finally {
                env.stop().join();
            }
        });
    }

    protected CompletableFuture<PipelineResult> runEvolution(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            if (skillManager == null) {
                throw new RuntimeException("skill_manager has not been initialized");
            }
            skillManager.initForTask(task.getTaskId());

            DockerEnvironment env = createAndStartEnv(task).join();
            try {
                bench.prepareEnvironment(task, env).join();

                Boolean setupOk = agent.setup(env).join();
                if (!Boolean.TRUE.equals(setupOk)) {
                    throw new RuntimeException("Agent setup failed");
                }

                Path logsDir = config.getResultsDir().resolve(task.getTaskId());
                ensureDirectory(logsDir);
                agent.setLogsDir(logsDir);

                Map<String, String> allSkills = skillManager.loadAllSkills(false);
                boolean hasSkill = !allSkills.isEmpty();
                if (hasSkill) {
                    boolean hasEvolutions = !skillManager.getAllEvolutions().isEmpty();
                    if (hasEvolutions) {
                        agent.setSkillContext(skillManager.getResolvedSkillName(), skillManager.getAllSkillNames());
                        agent.loadSkills(
                                env,
                                allSkills,
                                skillManager.getAllEvolutions(),
                                skillManager.getAllEvolutionFiles()).join();
                        LOGGER.info("  Loaded {} evolved skill(s) from previous iterations", allSkills.size());
                    } else {
                        LOGGER.info("  Evolution mode: Agent will create skill from scratch");
                    }
                }

                List<IterationResult> iterationResults = new ArrayList<>();
                boolean convergenceAchieved = false;
                String convergenceType = "";
                int consecutiveNoChange = 0;

                for (int iteration = 1; iteration <= config.getMaxIterations(); iteration++) {
                    LOGGER.info("  --- Iteration {}/{} ---", iteration, config.getMaxIterations());

                    String evolutionSuggestions = null;
                    if (iteration > 1 && !iterationResults.isEmpty()) {
                        IterationResult previous = iterationResults.get(iterationResults.size() - 1);
                        if (!previous.getEvalResult().isPassed()) {
                            evolutionSuggestions = buildEvolutionSuggestions(previous);
                        }
                    }

                    AgentContext context = new AgentContext();
                    context.setIteration(iteration);
                    context.setHasSkill(hasSkill);
                    context.setPreviousResult(iterationResults.isEmpty()
                            ? null
                            : iterationResults.get(iterationResults.size() - 1));
                    context.setEvolutionSuggestions(evolutionSuggestions);

                    AgentRunResult agentResult = agent.run(env, task, context).join();
                    EvalResult evalResult = bench.evaluate(env, task).join();

                    SkillDelta skillDelta = new SkillDelta();
                    boolean skillChanged = false;

                    SkillDelta captured = agent.captureSkills(env).join();
                    if (captured != null && captured.isChanged()) {
                        skillDelta = captured;
                        String content = captured.getSkills().getOrDefault(skillManager.getResolvedSkillName(), "");
                        skillChanged = skillManager.hasSkillChanged(content);

                        skillManager.saveAllSkills(
                                captured.getSkills(),
                                iteration,
                                captured.getEvolutions(),
                                captured.getEvolutionFiles());
                        for (String skillName : captured.getSkills().keySet()) {
                            skillManager.renderEvolutionToSkillMdFor(skillName);
                        }

                        allSkills = skillManager.loadAllSkills();
                        hasSkill = true;

                        agent.setSkillContext(skillManager.getResolvedSkillName(), skillManager.getAllSkillNames());
                        agent.loadSkills(
                                env,
                                allSkills,
                                skillManager.getAllEvolutions(),
                                skillManager.getAllEvolutionFiles()).join();
                    }

                    IterationResult iterationResult = new IterationResult();
                    iterationResult.setIteration(iteration);
                    iterationResult.setAgentResult(agentResult);
                    iterationResult.setEvalResult(evalResult);
                    iterationResult.setSkillDelta(skillDelta);
                    iterationResult.setSkillChanged(skillChanged);
                    iterationResult.setStartedAt(LocalDateTime.now());
                    iterationResult.setCompletedAt(LocalDateTime.now());
                    iterationResults.add(iterationResult);

                    if (config.isSaveTrajectory()) {
                        saveIterationResult(logsDir, iterationResult);
                    }

                    LOGGER.info(
                            "  Result: pass_rate={}%, skill_changed={}",
                            String.format(Locale.ROOT, "%.1f", evalResult.getPassRate() * 100.0d),
                            skillChanged);

                    if (evalResult.isPassed()) {
                        convergenceAchieved = true;
                        convergenceType = "all_tests_pass";
                        LOGGER.info("  All tests passed!");
                        break;
                    }

                    if (config.isConvergenceCheck() && !skillChanged) {
                        consecutiveNoChange++;
                        if (consecutiveNoChange >= config.getConvergenceThreshold()) {
                            convergenceAchieved = false;
                            convergenceType = "convergence_no_change";
                            LOGGER.info("  Convergence: no skill change for {} iterations", consecutiveNoChange);
                            break;
                        }
                    } else {
                        consecutiveNoChange = 0;
                    }

                    if (iteration >= config.getStagnationPatience()
                            && !hasImprovedBeyondFirstIteration(iterationResults)
                            && iteration >= config.getMaxIterations()) {
                        convergenceType = "max_iterations";
                        LOGGER.info("  Stagnation detected, stopping");
                        break;
                    }
                }

                if (convergenceType.isBlank()) {
                    convergenceType = "max_iterations";
                }

                if (config.isSaveTrajectory()) {
                    Map<String, String> capturedEvolutionJson = agent.getCapturedEvolutionJson();
                    if (capturedEvolutionJson != null && !capturedEvolutionJson.isEmpty()) {
                        for (Map.Entry<String, String> entry : capturedEvolutionJson.entrySet()) {
                            writeText(logsDir.resolve(entry.getKey()), entry.getValue());
                            LOGGER.info("  Saved {} from agent", entry.getKey());
                        }
                    }
                }

                PipelineResult result = new PipelineResult();
                result.setTaskId(task.getTaskId());
                result.setAgentName(config.getAgent());
                result.setBenchmarkName(config.getBenchmark());
                result.setTotalIterations(iterationResults.size());
                result.setConvergenceAchieved(convergenceAchieved);
                result.setConvergenceType(convergenceType);
                result.setResults(iterationResults);
                result.setMetrics(computeEvolutionMetrics(iterationResults));
                result.setOutputDir(logsDir);
                return result;
            } finally {
                env.stop().join();
            }
        });
    }

    protected CompletableFuture<DockerEnvironment> createAndStartEnv(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> envSpec = safeMap(task.getEnvironmentSpec());
            Path dockerfile = pathValue(envSpec, "dockerfile", "");
            Path buildContext = pathValue(envSpec, "build_context", "");

            if (!Files.exists(dockerfile)) {
                throw new UncheckedIOException(new FileNotFoundException("Dockerfile not found: " + dockerfile));
            }
            if (!Files.isDirectory(buildContext)) {
                throw new IllegalStateException("Build context not found: " + buildContext);
            }

            Path tempContext = createTempDirectory("evpipeline_ctx_");
            copyDirectoryContents(buildContext, tempContext);
            String originalDockerfileContent = readText(dockerfile);

            LOGGER.info("  Original Dockerfile length: {} chars", originalDockerfileContent.length());
            if (originalDockerfileContent.isEmpty()) {
                LOGGER.warning("  Warning: Original Dockerfile is empty!");
            } else {
                String firstLine = originalDockerfileContent.split("\\R", 2)[0];
                LOGGER.info("  Original Dockerfile first line: {}", firstLine.length() > 50
                        ? firstLine.substring(0, 50)
                        : firstLine);
            }

            Map<String, Object> sourceConfig = agent.getSourceFiles();
            String installMode = sourceConfig == null ? null : stringValue(sourceConfig.get("mode"), null);
            if (("git".equals(installMode) || "pypi".equals(installMode) || "local".equals(installMode))
                    && baseImageTag == null) {
                buildBaseImage(tempContext, originalDockerfileContent, sourceConfig);
            }

            Path actualDockerfile = buildTaskImage(task, tempContext, originalDockerfileContent);
            String imageTag = "evpipeline_" + task.getTaskId() + ":latest";
            DockerEnvironment env = createDockerEnvironment(
                    imageTag,
                    intValue(envSpec.get("cpus"), 1),
                    intValue(envSpec.get("memory_mb"), 2048),
                    intValue(envSpec.get("timeout"), 900));

            LOGGER.info("  Building image: {}", imageTag);
            env.build(actualDockerfile, tempContext, 300, true, null);
            LOGGER.info("  Starting container...");
            return env;
        }).thenCompose(env -> env.start().thenApply(ignored -> env));
    }

    protected DockerEnvironment createDockerEnvironment(String imageTag, int cpus, int memoryMb, int timeout) {
        return new DockerEnvironment(imageTag, null, cpus, memoryMb, timeout);
    }

    protected void buildBaseImage(
            Path tempContext,
            String originalDockerfileContent,
            Map<String, Object> sourceConfig) {
        String installMode = stringValue(sourceConfig.get("mode"), "");
        List<String> packages = stringList(sourceConfig.get("packages"));

        if ("local".equals(installMode) && sourceConfig.get("sources") instanceof Map<?, ?> sources) {
            for (Map.Entry<?, ?> entry : sources.entrySet()) {
                Path sourcePath = Path.of(String.valueOf(entry.getValue()));
                Path targetPath = tempContext.resolve(String.valueOf(entry.getKey()));
                if (Files.exists(sourcePath)) {
                    deleteRecursively(targetPath);
                    copyRecursively(sourcePath, targetPath);
                    LOGGER.info("  Copied local source: {} -> {}", sourcePath, targetPath);
                } else {
                    LOGGER.warning("  Warning: Local source not found: {}", sourcePath);
                }
            }
        }

        List<String> installLines = new ArrayList<>();
        installLines.add("ARG PIP_MIRROR=https://pypi.tuna.tsinghua.edu.cn/simple");
        installLines.add("ENV PIP_INDEX_URL=${PIP_MIRROR}");
        installLines.add("ENV PIP_TIMEOUT=120");
        installLines.add("ENV PIP_DEFAULT_TIMEOUT=120");
        installLines.add("RUN if [ -f /etc/apt/sources.list ]; then "
                + "sed -i \"s|http://deb.debian.org|http://mirrors.aliyun.com|g\" /etc/apt/sources.list; fi");
        installLines.add("RUN if [ -f /etc/apt/sources.list ]; then "
                + "sed -i \"s|http://security.debian.org|http://mirrors.aliyun.com|g\" /etc/apt/sources.list; fi");
        installLines.add("RUN apt-get update && apt-get install -y curl python3-pip "
                + "&& rm -rf /var/lib/apt/lists/*");
        installLines.add("RUN python3 -m pip install --break-system-packages "
                + "-i ${PIP_MIRROR} pytest==8.4.1 pytest-json-ctrf==0.3.5");
        if ("git".equals(installMode)) {
            installLines.add("RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*");
        }
        for (String pkg : packages) {
            installLines.add("RUN python3 -m pip install --break-system-packages -i ${PIP_MIRROR} \"" + pkg + "\"");
        }
        if ("local".equals(installMode) && sourceConfig.get("sources") instanceof Map<?, ?> sources) {
            for (Object pkgName : sources.keySet()) {
                installLines.add("COPY " + pkgName + "/ /opt/" + pkgName + "/");
                installLines.add("RUN python3 -m pip install --break-system-packages -e /opt/" + pkgName);
            }
        }
        installLines.add("RUN python3 -m pip install --break-system-packages -i ${PIP_MIRROR} uv==0.9.7");
        installLines.add("ENV EVOLUTION_AUTO_SCAN=true");

        LOGGER.info("  Using {} mode, packages: {}", installMode, packages);

        String baseContent = insertAfterFirstFrom(
                originalDockerfileContent,
                "# === Auto-generated base image enhancements ===\n" + String.join("\n", installLines));
        baseContent = baseContent + "\n\n# === Base image setup complete ===";

        String hashSeed = agent.name() + ":" + installMode + ":" + String.join(",", packages)
                + ":" + (System.currentTimeMillis() / 1000L);
        baseImageTag = "evpipeline_base:" + md5(hashSeed).substring(0, 8);
        LOGGER.info("  Building base image ({} cache): {}", agent.name(), baseImageTag);

        Path dockerfileBase = tempContext.resolve("Dockerfile.base");
        writeText(dockerfileBase, baseContent);
        writeText(Path.of("debug_Dockerfile.base"), baseContent);

        DockerEnvironment baseEnv = new DockerEnvironment(baseImageTag);
        Map<String, String> buildArgs = Map.of(
                "PIP_MIRROR",
                stringValue(config.getAgentConfig().get("pip_mirror"), "https://pypi.tuna.tsinghua.edu.cn/simple"));
        baseEnv.build(dockerfileBase, tempContext, 1800, false, buildArgs);
        LOGGER.info("  Base image cached: {}", baseImageTag);
    }

    protected Path buildTaskImage(Task task, Path tempContext, String originalDockerfileContent) {
        String pipMirror = stringValue(
                config.getAgentConfig().get("pip_mirror"),
                "https://pypi.tuna.tsinghua.edu.cn/simple");
        StringBuilder builder = new StringBuilder();

        if (baseImageTag != null) {
            builder.append("FROM ").append(baseImageTag).append("\n\n")
                    .append("# === Task-specific setup ===\n")
                    .append("ARG PIP_MIRROR=").append(pipMirror).append("\n")
                    .append("ENV PIP_INDEX_URL=${PIP_MIRROR}\n")
                    .append("ENV PIP_TIMEOUT=120\n")
                    .append("ENV PIP_DEFAULT_TIMEOUT=120\n");
        } else {
            String fromLine = firstFromLine(originalDockerfileContent, "FROM python:3.12-slim");
            builder.append(fromLine).append("\n\n")
                    .append("# === Task-specific setup ===\n")
                    .append("ARG PIP_MIRROR=").append(pipMirror).append("\n")
                    .append("ENV PIP_INDEX_URL=${PIP_MIRROR}\n")
                    .append("ENV PIP_TIMEOUT=120\n")
                    .append("ENV PIP_DEFAULT_TIMEOUT=120\n\n")
                    .append("# Replace apt sources with Aliyun mirror\n")
                    .append("RUN if [ -f /etc/apt/sources.list ]; then ")
                    .append("sed -i \"s|http://deb.debian.org|http://mirrors.aliyun.com|g\" ")
                    .append("/etc/apt/sources.list; fi\n")
                    .append("RUN if [ -f /etc/apt/sources.list ]; then ")
                    .append("sed -i \"s|http://security.debian.org|http://mirrors.aliyun.com|g\" ")
                    .append("/etc/apt/sources.list; fi\n")
                    .append("RUN apt-get update && apt-get install -y curl python3-pip && ")
                    .append("rm -rf /var/lib/apt/lists/*\n")
                    .append("RUN python3 -m pip install --break-system-packages ")
                    .append("pytest==8.4.1 pytest-json-ctrf==0.3.5\n");
        }

        for (String line : originalDockerfileContent.split("\\R")) {
            if (line.startsWith("FROM")) {
                continue;
            }
            if (!line.isBlank()) {
                builder.append(line).append('\n');
            }
        }

        ensureDirectory(tempContext.resolve("skills"));
        builder.append("\n# === Jiuwenswarm workspace setup ===\n")
                .append("RUN mkdir -p /root/.jiuwenswarm/agent/workspace/skills ")
                .append("/workspace/tests /workspace/logs/verifier\n")
                .append("COPY skills /root/.jiuwenswarm/agent/workspace/skills\n");

        Path taskDockerfile = tempContext.resolve("Dockerfile.task");
        writeText(taskDockerfile, builder.toString());
        return taskDockerfile;
    }

    protected CompletableFuture<Void> loadTaskSkills(DockerEnvironment env, Task task) {
        Path skillsDir = pathValue(safeMap(task.getEnvironmentSpec()), "skills_dir", "");
        if (!Files.exists(skillsDir)) {
            return CompletableFuture.completedFuture(null);
        }
        return agent.loadSkillsFromDir(env, skillsDir).thenAccept(loaded -> {
            if (loaded != null && !loaded.isEmpty()) {
                agent.setSkillContext(loaded.get(0), loaded);
                LOGGER.info("  Loaded {} task skills", loaded.size());
            }
        });
    }

    public static String buildEvolutionSuggestions(IterationResult previousResult) {
        EvalResult evalResult = previousResult.getEvalResult();
        List<String> parts = new ArrayList<>();

        if (evalResult.isPassed()) {
            return "All tests passed in the previous iteration. No changes needed.";
        }

        parts.add(String.format(Locale.ROOT, "Previous iteration pass rate: %.1f%%", evalResult.getPassRate() * 100.0d));

        if (evalResult.getFailedTests() != null && !evalResult.getFailedTests().isEmpty()) {
            parts.add("Failed tests (" + evalResult.getFailedTests().size() + "):");
            evalResult.getFailedTests().stream().limit(5).forEach(test -> parts.add("  - " + test));
        }

        SkillDelta delta = previousResult.getSkillDelta();
        if (delta != null && delta.isChanged()) {
            parts.add("Skills were modified in the previous iteration.");
            delta.getSkills().keySet().forEach(skillName -> parts.add("  - Modified: " + skillName));
        }

        if (previousResult.isSkillChanged()) {
            parts.add("The skill content changed but tests still fail. "
                    + "Consider reviewing the skill for accuracy and completeness.");
        } else {
            parts.add("The skill was NOT modified in the previous iteration. "
                    + "Consider whether the skill needs updates to address the failing tests.");
        }

        return String.join("\n", parts);
    }

    public static Map<String, Object> computeEvolutionMetrics(List<IterationResult> results) {
        if (results == null || results.isEmpty()) {
            return Map.of();
        }

        List<Double> passRates = results.stream()
                .map(result -> result.getEvalResult().getPassRate())
                .toList();
        long skillChanges = results.stream().filter(IterationResult::isSkillChanged).count();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("final_pass_rate", passRates.get(passRates.size() - 1));
        metrics.put("best_pass_rate", passRates.stream().mapToDouble(Double::doubleValue).max().orElse(0.0d));
        metrics.put("first_pass_rate", passRates.get(0));
        metrics.put("improvement", passRates.get(passRates.size() - 1) - passRates.get(0));
        metrics.put("skill_changes", skillChanges);
        metrics.put("total_iterations", results.size());
        metrics.put("converged", results.get(results.size() - 1).getEvalResult().isPassed());
        return metrics;
    }

    public static void saveIterationResult(Path logsDir, IterationResult result) {
        Path iterationDir = logsDir.resolve(String.format(Locale.ROOT, "iteration_%03d", result.getIteration()));
        ensureDirectory(iterationDir);

        AgentRunResult agentResult = result.getAgentResult();
        EvalResult evalResult = result.getEvalResult();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("iteration", result.getIteration());
        summary.put("pass_rate", evalResult.getPassRate());
        summary.put("passed", evalResult.isPassed());
        summary.put("skill_changed", result.isSkillChanged());
        summary.put("execution_time", agentResult.getExecutionTime());
        summary.put("tokens_used", agentResult.getTokensUsed());
        summary.put("failed_tests", evalResult.getFailedTests());
        summary.put("started_at", result.getStartedAt() == null ? null : result.getStartedAt().toString());
        summary.put("completed_at", result.getCompletedAt() == null ? null : result.getCompletedAt().toString());
        writeJson(iterationDir.resolve("summary.json"), summary);

        if (notBlank(agentResult.getRawOutput())) {
            writeText(iterationDir.resolve("agent_output.txt"), agentResult.getRawOutput());
        }
        if (notBlank(agentResult.getStderr())) {
            writeText(iterationDir.resolve("agent_stderr.txt"), agentResult.getStderr());
        }
        if (notBlank(evalResult.getTestOutput())) {
            writeText(iterationDir.resolve("test_output.txt"), evalResult.getTestOutput());
        }
        if (agentResult.getTrajectory() != null && !agentResult.getTrajectory().isEmpty()) {
            writeJson(iterationDir.resolve("trajectory.json"), agentResult.getTrajectory());
        }
        if (agentResult.getEvolutionEvents() != null && !agentResult.getEvolutionEvents().isEmpty()) {
            writeJson(iterationDir.resolve("evolution_events.json"), agentResult.getEvolutionEvents());
        }
        if (agentResult.getMetadata() != null && !agentResult.getMetadata().isEmpty()) {
            writeJson(iterationDir.resolve("agent_metadata.json"), agentResult.getMetadata());
        }
        if (agentResult.getLlmLogs() != null && !agentResult.getLlmLogs().isEmpty()) {
            for (Map.Entry<String, String> entry : agentResult.getLlmLogs().entrySet()) {
                writeText(iterationDir.resolve(entry.getKey()), entry.getValue());
                LOGGER.info("  Saved LLM log: {} ({} chars)", entry.getKey(), entry.getValue().length());
            }
        }

        SkillDelta delta = result.getSkillDelta();
        if (delta != null && delta.isChanged() && delta.getSkills() != null && !delta.getSkills().isEmpty()) {
            Path skillsDir = iterationDir.resolve("skills");
            ensureDirectory(skillsDir);
            for (Map.Entry<String, String> entry : delta.getSkills().entrySet()) {
                writeText(skillsDir.resolve(entry.getKey() + ".md"), entry.getValue());
            }
        }
        if (delta != null && delta.getEvolutionFiles() != null && !delta.getEvolutionFiles().isEmpty()) {
            Path evolutionDir = iterationDir.resolve("evolution");
            ensureDirectory(evolutionDir);
            for (Map.Entry<String, Map<String, String>> skillEntry : delta.getEvolutionFiles().entrySet()) {
                Path skillEvolutionDir = evolutionDir.resolve(skillEntry.getKey());
                ensureDirectory(skillEvolutionDir);
                for (Map.Entry<String, String> fileEntry : skillEntry.getValue().entrySet()) {
                    writeText(skillEvolutionDir.resolve(fileEntry.getKey()), fileEntry.getValue());
                }
            }
        }
    }

    public static void printSummary(List<PipelineResult> results) {
        LOGGER.info("\n{}", "=".repeat(60));
        LOGGER.info("SUMMARY");
        LOGGER.info("{}", "=".repeat(60));

        for (PipelineResult result : results) {
            String status = result.isConvergenceAchieved() ? "PASS" : "FAIL";
            String mode = result.getConvergenceType() == null || result.getConvergenceType().isBlank()
                    ? ""
                    : "(" + result.getConvergenceType() + ")";
            Object rate = result.getMetrics().getOrDefault(
                    "final_pass_rate",
                    result.getMetrics().getOrDefault("pass_rate", 0.0d));
            LOGGER.info(
                    "  {}: {} {} iterations={} pass_rate={}%",
                    result.getTaskId(),
                    status,
                    mode,
                    result.getTotalIterations(),
                    String.format(Locale.ROOT, "%.1f", numberValue(rate) * 100.0d));
        }

        int total = results.size();
        long passed = results.stream().filter(PipelineResult::isConvergenceAchieved).count();
        LOGGER.info("Total: {}/{} tasks passed", passed, total);
    }

    public static PipelineConfig buildDefaultConfig(BuildConfigArgs args) {
        BuildConfigArgs safeArgs = args == null ? new BuildConfigArgs() : args;
        String resultsDir = safeArgs.getResultsDir();
        if (resultsDir == null) {
            resultsDir = safeArgs.isEvolutionMode() ? "./evolution_results" : "./single_run_results";
        }

        PipelineConfig config = new PipelineConfig();
        config.setEvolutionMode(safeArgs.isEvolutionMode());
        config.setMaxIterations(safeArgs.isEvolutionMode() ? safeArgs.getMaxIterations() : 1);
        config.setResultsDir(Path.of(resultsDir));
        config.setTaskIds(safeArgs.getTaskIds() == null ? new ArrayList<>() : new ArrayList<>(safeArgs.getTaskIds()));

        Map<String, Object> agentConfig = new LinkedHashMap<>();
        agentConfig.put("api_key", stringValue(safeArgs.getApiKey(), ""));
        agentConfig.put("api_base", stringValue(safeArgs.getApiBase(), ""));
        agentConfig.put("model_name", safeArgs.getModelName());
        agentConfig.put("evolution_enabled", safeArgs.isEvolutionMode());
        agentConfig.put("evolution_wait_time", safeArgs.getEvolutionWaitTime());
        agentConfig.put("agent_timeout", safeArgs.getAgentTimeout());
        agentConfig.put("skill_persistence_dir", safeArgs.getSkillPersistenceDir());
        config.setAgentConfig(agentConfig);

        Map<String, Object> benchConfig = new LinkedHashMap<>();
        benchConfig.put("tasks_dir", safeArgs.getTasksDir());
        benchConfig.put("workspace_dir", safeArgs.getWorkspaceDir());
        config.setBenchConfig(benchConfig);
        return config;
    }

    public static void main(String[] args) {
        Map<String, List<String>> options = parseCommandLine(args);
        PipelineConfig config;
        String configPath = optionValue(options, "config", null);
        if (configPath != null) {
            config = PipelineConfig.fromYaml(Path.of(configPath));
            if (options.containsKey("task-ids")) {
                config.setTaskIds(optionValues(options, "task-ids"));
            }
            if (options.containsKey("agent")) {
                config.setAgent(optionValue(options, "agent", config.getAgent()));
            }
            if (options.containsKey("benchmark")) {
                config.setBenchmark(optionValue(options, "benchmark", config.getBenchmark()));
            }
            config.setEvolutionMode(options.containsKey("evolution"));
            config.getAgentConfig().put("evolution_enabled", config.isEvolutionMode());
            if (options.containsKey("max-iterations")) {
                config.setMaxIterations(intValue(optionValue(options, "max-iterations", "5"), config.getMaxIterations()));
            }
            if (options.containsKey("results-dir")) {
                config.setResultsDir(Path.of(optionValue(options, "results-dir", config.getResultsDir().toString())));
            }
        } else {
            BuildConfigArgs buildArgs = new BuildConfigArgs();
            buildArgs.setTasksDir(optionValue(options, "tasks-dir", "tasks"));
            buildArgs.setApiKey(optionValue(options, "api-key", null));
            buildArgs.setApiBase(optionValue(options, "api-base", null));
            buildArgs.setModelName(optionValue(options, "model", "glm-5"));
            buildArgs.setEvolutionMode(options.containsKey("evolution"));
            buildArgs.setMaxIterations(intValue(optionValue(options, "max-iterations", "5"), 5));
            buildArgs.setTaskIds(optionValues(options, "task-ids"));
            buildArgs.setResultsDir(optionValue(options, "results-dir", null));
            buildArgs.setWorkspaceDir(optionValue(options, "workspace-dir", "/workspace"));
            buildArgs.setEvolutionWaitTime(intValue(optionValue(options, "evolution-wait", "60"), 60));
            buildArgs.setAgentTimeout(intValue(optionValue(options, "agent-timeout", "880"), 880));
            buildArgs.setSkillPersistenceDir(optionValue(
                    options,
                    "skill-dir",
                    "~/.jiuwenswarm/agent/workspace/skills"));
            config = buildDefaultConfig(buildArgs);
            config.setAgent(optionValue(options, "agent", "jiuwenswarm"));
            config.setBenchmark(optionValue(options, "benchmark", "skillsbench"));
            config.setConvergenceThreshold(intValue(optionValue(options, "convergence-threshold", "2"), 2));
            config.setStagnationPatience(intValue(optionValue(options, "stagnation-patience", "3"), 3));
        }

        new EvolutionPipeline(config).run().join();
    }

    private static boolean hasImprovedBeyondFirstIteration(List<IterationResult> results) {
        if (results.size() <= 1) {
            return false;
        }
        double first = results.get(0).getEvalResult().getPassRate();
        return results.stream().skip(1).anyMatch(result -> result.getEvalResult().getPassRate() > first);
    }

    private static String insertAfterFirstFrom(String original, String insertion) {
        List<String> lines = new ArrayList<>(List.of(original.split("\\R", -1)));
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).strip().startsWith("FROM ")) {
                lines.add(index + 1, "\n" + insertion);
                return String.join("\n", lines);
            }
        }
        LOGGER.warning("  Warning: No FROM directive found in original Dockerfile");
        return "FROM python:3.11-slim\n\n" + insertion + "\n\n" + original;
    }

    private static String firstFromLine(String original, String fallback) {
        for (String line : original.split("\\R")) {
            if (line.startsWith("FROM")) {
                return line;
            }
        }
        return fallback;
    }

    private static Map<String, List<String>> parseCommandLine(String[] args) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (!token.startsWith("--")) {
                continue;
            }
            String key = token.substring(2);
            List<String> values = new ArrayList<>();
            while (index + 1 < args.length && !args[index + 1].startsWith("--")) {
                values.add(args[++index]);
            }
            if (values.isEmpty()) {
                values.add("true");
            }
            options.put(key, values);
        }
        return options;
    }

    private static String optionValue(Map<String, List<String>> options, String name, String fallback) {
        List<String> values = options.get(name);
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.get(0);
    }

    private static List<String> optionValues(Map<String, List<String>> options, String name) {
        List<String> values = options.get(name);
        if (values == null || values.isEmpty() || (values.size() == 1 && "true".equals(values.get(0)))) {
            return new ArrayList<>();
        }
        return new ArrayList<>(values);
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void copyDirectoryContents(Path source, Path target) {
        try (Stream<Path> children = Files.list(source)) {
            children.forEach(child -> {
                Path destination = target.resolve(child.getFileName().toString());
                if (Files.isDirectory(child)) {
                    copyRecursively(child, destination);
                } else {
                    copyFile(child, destination);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void copyRecursively(Path source, Path target) {
        try (Stream<Path> paths = Files.walk(source)) {
            paths.sorted(Comparator.comparing(Path::getNameCount))
                    .forEach(path -> {
                        Path destination = target.resolve(source.relativize(path).toString());
                        if (Files.isDirectory(path)) {
                            ensureDirectory(destination);
                        } else {
                            copyFile(path, destination);
                        }
                    });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void copyFile(Path source, Path target) {
        try {
            ensureDirectory(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void writeJson(Path path, Object value) {
        try {
            ensureDirectory(path.getParent());
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void writeText(Path path, String content) {
        try {
            ensureDirectory(path.getParent());
            Files.writeString(path, nullToEmpty(content), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void ensureDirectory(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute MD5", exception);
        }
    }

    private static Map<String, Object> safeMap(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }

    private static Path pathValue(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return Path.of(value == null ? fallback : String.valueOf(value));
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
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

    private static double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof RuntimeException)
                && current.getCause() != null
                && current != current.getCause()) {
            current = current.getCause();
        }
        return current;
    }
}
