/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.core.foundation.llm.Model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Auto Harness schema DTOs and config helpers.
 * <p>
 * Mirrors Python's module-level schema and helper functions in
 * {@code openjiuwen/auto_harness/schema.py}.
 */
public final class AutoHarnessSchema {

    public static final String PIPELINE_PREFERENCE_AUTO = "auto";
    public static final String DEFAULT_REPO_URL = "https://gitcode.com/openJiuwen/agent-core.git";

    private static final Logger LOGGER = Logger.getLogger(AutoHarnessSchema.class.getName());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final List<String> DEFAULT_IMMUTABLE_FILES = List.of(
            "openjiuwen/auto_harness/prompts/identity.md",
            "openjiuwen/auto_harness/resources/ci_gate.yaml",
            "openjiuwen/harness/rails/security/prompt_security_rail.py"
    );
    private static final List<String> DEFAULT_COMMUNITY_SKILL_REPOS = List.of(
            "https://github.com/anthropics/skills.git",
            "https://github.com/JimLiu/baoyu-skills.git"
    );
    private static final Map<String, String> PIPELINE_PREFERENCE_ALIASES = Map.ofEntries(
            Map.entry("", PIPELINE_PREFERENCE_AUTO),
            Map.entry(PIPELINE_PREFERENCE_AUTO, PIPELINE_PREFERENCE_AUTO),
            Map.entry("meta", AutoHarnessPipelineNames.META_EVOLVE_PIPELINE),
            Map.entry(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE, AutoHarnessPipelineNames.META_EVOLVE_PIPELINE),
            Map.entry("extended", AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE),
            Map.entry(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE, AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE),
            Map.entry("extended_harness_pipeline", AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE),
            Map.entry("pr_pipeline", AutoHarnessPipelineNames.META_EVOLVE_PIPELINE)
    );
    private static final Set<String> BUILT_IN_PIPELINES = Set.of(
            AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
            AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE
    );
    private static final String DEFAULT_CONFIG_TEMPLATE = """
            repo_url: "https://gitcode.com/openJiuwen/agent-core.git"
            pipeline: "auto"
            git:
              remote: ""
              base_branch: "develop-auto-harness"
              user_name: ""
              user_email: ""
              fork_owner: ""
              upstream_owner: "openJiuwen"
              upstream_repo: "agent-core"
            gitcode:
              username: ""
              access_token_env: "GITCODE_ACCESS_TOKEN"
            budget:
              session_secs: 900000
              cost_limit_usd: 10.0
              task_timeout_secs: 300000
              model_timeout_secs: 300000
              max_tasks_per_session: 5
            ci_gate:
              config_path: ""
              python_executable: ""
              install_command: ""
            fix_loop:
              phase1_max_retries: 10
              phase2_max_retries: 9
            agent:
              implement: 60
              assess: 60
              plan: 30
              select_pipeline: 20
              eval: 20
              pr_draft: 10
              learnings: 10
            extensions:
              stage_registrars: []
              pipeline_registrars: []
            """;

    private AutoHarnessSchema() {
    }

    /**
     * Normalize user-facing pipeline preference values.
     *
     * @param value raw preference
     * @return normalized pipeline preference
     */
    public static String normalizePipelinePreference(Object value) {
        String raw = value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
        String normalized = PIPELINE_PREFERENCE_ALIASES.get(raw);
        if (normalized != null) {
            return normalized;
        }
        String pipelineName = AutoHarnessPipelineNames.normalizePipelineName(raw);
        if (BUILT_IN_PIPELINES.contains(pipelineName)) {
            return pipelineName;
        }
        LOGGER.warning("Invalid auto-harness pipeline preference " + value + ", using auto");
        return PIPELINE_PREFERENCE_AUTO;
    }

    /**
     * Return platform-aware venv python paths under {@code baseDir}.
     *
     * @param baseDir base directory
     * @return candidate Python executable paths
     */
    public static List<Path> venvPythonCandidates(String baseDir) {
        Path venv = Path.of(baseDir).resolve(".venv");
        if (isWindows()) {
            return List.of(venv.resolve("Scripts").resolve("python.exe"));
        }
        return List.of(venv.resolve("bin").resolve("python"));
    }

    /**
     * Return built-in immutable files for the bundled Phase 1 pipeline.
     *
     * @return immutable file paths
     */
    public static List<String> defaultImmutableFiles() {
        return new ArrayList<>(DEFAULT_IMMUTABLE_FILES);
    }

    /**
     * Load Auto Harness config from YAML.
     *
     * @param configPath config path
     * @return loaded config
     */
    public static AutoHarnessConfig loadAutoHarnessConfig(String configPath) {
        return loadAutoHarnessConfig(configPath, "");
    }

    /**
     * Load Auto Harness config from YAML.
     *
     * @param configPath config path
     * @param workspaceHint current workspace hint
     * @return loaded config
     */
    public static AutoHarnessConfig loadAutoHarnessConfig(String configPath, String workspaceHint) {
        Path path = Path.of(configPath);
        if (!Files.isRegularFile(path)) {
            boolean bootstrapped = bootstrapConfigFile(path);
            LOGGER.info("Config file not found: " + configPath + ", using defaults");
            AutoHarnessConfig cfg = new AutoHarnessConfig();
            cfg.setConfigPath(path.toString());
            cfg.setConfigBootstrapped(bootstrapped);
            cfg.setSuggestedLocalRepo(detectLocalRepo(workspaceHint));
            if (isBlank(cfg.getDataDir())) {
                cfg.setDataDir(parentString(path));
            }
            return cfg;
        }

        try {
            Map<String, Object> data = readYamlMap(path);
            AutoHarnessConfig cfg = AutoHarnessConfig.loadFromDict(data);
            cfg.setConfigPath(path.toString());
            cfg.setSuggestedLocalRepo(detectLocalRepo(workspaceHint));
            if (isBlank(cfg.getDataDir())) {
                cfg.setDataDir(parentString(path));
            }
            return cfg;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load config: " + configPath + ", using defaults", e);
            AutoHarnessConfig cfg = new AutoHarnessConfig();
            cfg.setConfigPath(path.toString());
            cfg.setSuggestedLocalRepo(detectLocalRepo(workspaceHint));
            if (isBlank(cfg.getDataDir())) {
                cfg.setDataDir(parentString(path));
            }
            return cfg;
        }
    }

    /**
     * Return whether {@code path} is an obvious template/example value.
     *
     * @param path local repo path
     * @return true when the path is a template/example value
     */
    public static boolean isPlaceholderLocalRepo(String path) {
        String normalized = path == null ? "" : path.strip();
        return Set.of("/home/user/code/agent-core", "/home/user/repo").contains(normalized);
    }

    private static boolean bootstrapConfigFile(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, DEFAULT_CONFIG_TEMPLATE, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to bootstrap config: " + path, e);
            return false;
        }
    }

    private static String detectLocalRepo(String workspaceHint) {
        List<Path> candidates = new ArrayList<>();
        if (!isBlank(workspaceHint)) {
            Path hint = Path.of(workspaceHint);
            candidates.add(hint);
            candidates.add(hint.resolve("agent-core"));
        }
        Path cwd = Path.of("").toAbsolutePath();
        candidates.add(cwd);
        candidates.add(cwd.resolve("agent-core"));

        Set<String> seen = new java.util.LinkedHashSet<>();
        for (Path candidate : candidates) {
            Path resolved = candidate.toAbsolutePath().normalize();
            String key = resolved.toString();
            if (!seen.add(key)) {
                continue;
            }
            if (looksLikeRepoRoot(resolved)) {
                return key;
            }
        }
        return "";
    }

    private static boolean looksLikeRepoRoot(Path path) {
        return Files.isDirectory(path)
                && Files.exists(path.resolve(".git"))
                && Files.isRegularFile(path.resolve("pyproject.toml"))
                && Files.isDirectory(path.resolve("openjiuwen"));
    }

    private static Map<String, Object> readYamlMap(Path path) throws IOException {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        Object loaded = new Yaml().load(raw);
        if (!(loaded instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return JSON_MAPPER.convertValue(map, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private static String parentString(Path path) {
        Path parent = path.getParent();
        return parent == null ? "." : parent.toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(stringValue(item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(stringValue(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String newShortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static double epochSeconds() {
        return Instant.now().toEpochMilli() / 1000.0;
    }

    private static List<String> defaultHighImpactPrefixes() {
        return new ArrayList<>(List.of("openjiuwen/core/"));
    }

    private static Map<String, Integer> defaultAgentIterations() {
        Map<String, Integer> iterations = new LinkedHashMap<>();
        iterations.put("implement", 30);
        iterations.put("assess", 30);
        iterations.put("plan", 15);
        iterations.put("select_pipeline", 10);
        iterations.put("eval", 10);
        iterations.put("pr_draft", 5);
        iterations.put("learnings", 5);
        iterations.put("explore_subagent", 20);
        iterations.put("browser_subagent", 20);
        iterations.put("merge_ext", 8);
        return iterations;
    }

    /**
     * Mirrors Python's {@code TaskStatus} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    public enum TaskStatus {
        PENDING("pending"),
        RUNNING("running"),
        SUCCESS("success"),
        FAILED("failed"),
        TIMEOUT("timeout"),
        REVERTED("reverted");

        private final String value;

        TaskStatus(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Mirrors Python's {@code ExperienceType} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    public enum ExperienceType {
        OPTIMIZATION("optimization"),
        FAILURE("failure"),
        INSIGHT("insight");

        private final String value;

        ExperienceType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Mirrors Python's {@code StageSlot} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    public enum StageSlot {
        ASSESS("assess"),
        PLAN("plan"),
        IMPLEMENT("implement"),
        VERIFY("verify"),
        ACTIVATE("activate"),
        COMMIT("commit"),
        PUBLISH("publish"),
        LEARNINGS("learnings");

        private final String value;

        StageSlot(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Mirrors Python's {@code Gap} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Gap {
        @Builder.Default
        private String id = "";
        @Builder.Default
        private String competitor = "";
        @Builder.Default
        private String feature = "";
        @Builder.Default
        @JsonProperty("current_state")
        private String currentState = "";
        @Builder.Default
        @JsonProperty("gap_description")
        private String gapDescription = "";
        @Builder.Default
        private double impact = 0.0;
        @Builder.Default
        private double feasibility = 0.0;
        @Builder.Default
        @JsonProperty("suggested_approach")
        private String suggestedApproach = "";
        @Builder.Default
        @JsonProperty("target_files")
        private List<String> targetFiles = new ArrayList<>();

        public double getPriority() {
            return impact * feasibility;
        }
    }

    /**
     * Mirrors Python's {@code OptimizationTask} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptimizationTask {
        private String topic;
        @Builder.Default
        private String description = "";
        @Builder.Default
        private List<String> files = new ArrayList<>();
        @JsonProperty("issue_ref")
        private String issueRef;
        @Builder.Default
        @JsonProperty("expected_effect")
        private String expectedEffect = "";
        @Builder.Default
        @JsonProperty("pipeline_name")
        private String pipelineName = "";
        @Builder.Default
        private TaskStatus status = TaskStatus.PENDING;
    }

    /**
     * Mirrors Python's {@code Experience} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Experience {
        @Builder.Default
        private ExperienceType type = ExperienceType.OPTIMIZATION;
        @Builder.Default
        private String topic = "";
        @Builder.Default
        private String summary = "";
        @Builder.Default
        private String outcome = "";
        @Builder.Default
        private String details = "";
        @Builder.Default
        @JsonProperty("pr_url")
        private String prUrl = "";
        @Builder.Default
        @JsonProperty("files_changed")
        private List<String> filesChanged = new ArrayList<>();
        @Builder.Default
        private String signal = "";
        @Builder.Default
        private String strategy = "";
        @Builder.Default
        @JsonProperty("causal_chain")
        private String causalChain = "";
        @Builder.Default
        @JsonProperty("signal_frequency")
        private int signalFrequency = 0;
        @Builder.Default
        private String id = newShortId();
        @Builder.Default
        private double timestamp = epochSeconds();
    }

    /**
     * Mirrors Python's {@code ResearchContext} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResearchContext {
        @Builder.Default
        private List<Experience> experiences = new ArrayList<>();
        @Builder.Default
        @JsonProperty("source_files")
        private Map<String, String> sourceFiles = new LinkedHashMap<>();
        @JsonProperty("gap_report")
        private String gapReport;
    }

    /**
     * Mirrors Python's {@code CycleResult} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CycleResult {
        @Builder.Default
        private boolean success = false;
        @Builder.Default
        private String summary = "";
        @Builder.Default
        @JsonProperty("pr_url")
        private String prUrl = "";
        @Builder.Default
        private String error = "";
        @Builder.Default
        private boolean reverted = false;
        @Builder.Default
        @JsonProperty("error_log")
        private String errorLog = "";
    }

    /**
     * Mirrors Python's {@code AssessmentArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssessmentArtifact {
        @Builder.Default
        private String report = "";
    }

    /**
     * Mirrors Python's {@code TaskPlanArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskPlanArtifact {
        @Builder.Default
        private List<OptimizationTask> tasks = new ArrayList<>();
        @Builder.Default
        @JsonProperty("raw_plan")
        private String rawPlan = "";
    }

    /**
     * Mirrors Python's {@code PipelineSelectionArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PipelineSelectionArtifact {
        @Builder.Default
        @JsonProperty("pipeline_name")
        private String pipelineName = AutoHarnessPipelineNames.META_EVOLVE_PIPELINE;
        @Builder.Default
        private String reason = "";
        @Builder.Default
        private List<String> alternatives = new ArrayList<>();
        @Builder.Default
        private double confidence = 0.0;
        @Builder.Default
        @JsonProperty("risk_level")
        private String riskLevel = "";
        @Builder.Default
        @JsonProperty("required_inputs")
        private List<String> requiredInputs = new ArrayList<>();
        @Builder.Default
        @JsonProperty("fallback_pipeline")
        private String fallbackPipeline = "";
    }

    /**
     * Mirrors Python's {@code GapAnalysisArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GapAnalysisArtifact {
        @Builder.Default
        private List<Gap> gaps = new ArrayList<>();
        @Builder.Default
        @JsonProperty("competitor_summary")
        private String competitorSummary = "";
        @Builder.Default
        @JsonProperty("raw_analysis")
        private String rawAnalysis = "";
    }

    /**
     * Mirrors Python's {@code ExtensionDesign} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtensionDesign {
        @Builder.Default
        @JsonProperty("gap_id")
        private String gapId = "";
        @Builder.Default
        @JsonProperty("extension_name")
        private String extensionName = "";
        @Builder.Default
        private String kind = "capability";
        @Builder.Default
        @JsonProperty("depends_on")
        private List<String> dependsOn = new ArrayList<>();
        @Builder.Default
        @JsonProperty("applies_to")
        private List<String> appliesTo = new ArrayList<>();
        @Builder.Default
        private List<String> components = new ArrayList<>();
        @Builder.Default
        @JsonProperty("file_plan")
        private Map<String, String> filePlan = new LinkedHashMap<>();
        @Builder.Default
        @JsonProperty("harness_config_patch")
        private Map<String, Object> harnessConfigPatch = new LinkedHashMap<>();
        @Builder.Default
        @JsonProperty("skill_source")
        private String skillSource = "";
    }

    /**
     * Mirrors Python's {@code ExtensionDesignArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtensionDesignArtifact {
        @Builder.Default
        private List<ExtensionDesign> designs = new ArrayList<>();
        @Builder.Default
        @JsonProperty("package_name")
        private String packageName = "";
    }

    /**
     * Mirrors Python's {@code ExtensionBuildArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtensionBuildArtifact {
        @Builder.Default
        @JsonProperty("extension_name")
        private String extensionName = "";
        @Builder.Default
        @JsonProperty("extension_root")
        private String extensionRoot = "";
        @Builder.Default
        @JsonProperty("config_path")
        private String configPath = "";
    }

    /**
     * Mirrors Python's {@code SessionResultsArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionResultsArtifact {
        @Builder.Default
        private List<CycleResult> results = new ArrayList<>();
    }

    /**
     * Mirrors Python's {@code CodeChangeArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeChangeArtifact {
        @Builder.Default
        private List<Experience> related = new ArrayList<>();
        @Builder.Default
        @JsonProperty("edited_files")
        private List<String> editedFiles = new ArrayList<>();
    }

    /**
     * Mirrors Python's {@code VerifyReportArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VerifyReportArtifact {
        @Builder.Default
        @JsonProperty("ci_result")
        private Map<String, Object> ciResult = new LinkedHashMap<>();
        @Builder.Default
        @JsonProperty("fix_errors")
        private String fixErrors = "";
        @Builder.Default
        private boolean reverted = false;
        @Builder.Default
        private String error = "";
    }

    /**
     * Mirrors Python's {@code CommitArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitArtifact {
        private CommitFacts facts;
        @Builder.Default
        @JsonProperty("status_text")
        private String statusText = "";
        @Builder.Default
        @JsonProperty("last_commit_stat")
        private String lastCommitStat = "";
        @Builder.Default
        @JsonProperty("branch_name")
        private String branchName = "";
        @Builder.Default
        private boolean committed = false;
        @Builder.Default
        private String error = "";
    }

    /**
     * Mirrors Python's {@code PullRequestArtifact} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequestArtifact {
        @Builder.Default
        @JsonProperty("pr_url")
        private String prUrl = "";
        @Builder.Default
        private String summary = "";
    }

    /**
     * Mirrors Python's {@code PullRequestDraft} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PullRequestDraft {
        @Builder.Default
        private String title = "";
        @Builder.Default
        private String body = "";
        @Builder.Default
        private String kind = "";
    }

    /**
     * Mirrors Python's {@code CommitFacts} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitFacts {
        @Builder.Default
        @JsonProperty("branch_name")
        private String branchName = "";
        @Builder.Default
        @JsonProperty("task_declared_files")
        private List<String> taskDeclaredFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("preexisting_dirty_files")
        private List<String> preexistingDirtyFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("current_dirty_files")
        private List<String> currentDirtyFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("tracked_modified_files")
        private List<String> trackedModifiedFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("untracked_files")
        private List<String> untrackedFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("edited_files")
        private List<String> editedFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("allowed_files")
        private List<String> allowedFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("derived_test_files")
        private List<String> derivedTestFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("legacy_related_test_files")
        private List<String> legacyRelatedTestFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("verify_related_files")
        private List<String> verifyRelatedFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("diff_stat")
        private String diffStat = "";
    }

    /**
     * Mirrors Python's {@code ProjectProfile} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjectProfile {
        @Builder.Default
        private String name = "agent-core";
        @Builder.Default
        @JsonProperty("repo_url")
        private String repoUrl = DEFAULT_REPO_URL;
        @Builder.Default
        @JsonProperty("repo_slug")
        private String repoSlug = "openJiuwen/agent-core";
        @Builder.Default
        private String platform = "gitcode";
        @Builder.Default
        @JsonProperty("immutable_files")
        private List<String> immutableFiles = defaultImmutableFiles();
        @Builder.Default
        @JsonProperty("high_impact_prefixes")
        private List<String> highImpactPrefixes = defaultHighImpactPrefixes();
        @Builder.Default
        @JsonProperty("default_base_branch")
        private String defaultBaseBranch = "develop";
        @Builder.Default
        @JsonProperty("default_ci_profile")
        private String defaultCiProfile = "default";
    }

    /**
     * Mirrors Python's {@code AutoHarnessPaths} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AutoHarnessPaths {
        @Builder.Default
        @JsonProperty("data_dir")
        private String dataDir = "";
        @Builder.Default
        @JsonProperty("experience_dir")
        private String experienceDir = "";
        @Builder.Default
        @JsonProperty("worktrees_dir")
        private String worktreesDir = "";
        @Builder.Default
        @JsonProperty("runs_dir")
        private String runsDir = "";
        @Builder.Default
        @JsonProperty("cache_repo_dir")
        private String cacheRepoDir = "";
        @Builder.Default
        @JsonProperty("runtime_extensions_dir")
        private String runtimeExtensionsDir = "";
    }

    /**
     * Mirrors Python's {@code AutoHarnessRuntimeState} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AutoHarnessRuntimeState {
        @Builder.Default
        @JsonProperty("current_workspace")
        private String currentWorkspace = "";
        @Builder.Default
        @JsonProperty("selected_pipeline")
        private String selectedPipeline = "";
        @Builder.Default
        @JsonProperty("config_bootstrapped")
        private boolean configBootstrapped = false;
        @Builder.Default
        @JsonProperty("suggested_local_repo")
        private String suggestedLocalRepo = "";
        @Builder.Default
        @JsonProperty("session_id")
        private String sessionId = newShortId();
    }

    /**
     * Mirrors Python's {@code StageResult} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StageResult {
        @Builder.Default
        private String status = "success";
        @Builder.Default
        private Map<String, Object> artifacts = new LinkedHashMap<>();
        @Builder.Default
        private List<String> messages = new ArrayList<>();
        @Builder.Default
        private Map<String, Object> metrics = new LinkedHashMap<>();
        @Builder.Default
        private String error = "";
    }

    /**
     * Mirrors Python's {@code StageSpec} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StageSpec {
        private String name;
        @JsonProperty("stage_cls")
        private Class<?> stageCls;
        @Builder.Default
        private String scope = "session";
        @Builder.Default
        private List<String> consumes = new ArrayList<>();
        @Builder.Default
        private List<String> produces = new ArrayList<>();
        @Builder.Default
        private String description = "";
        @Builder.Default
        private String slot = "";
    }

    /**
     * Mirrors Python's {@code PipelineSpec} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PipelineSpec {
        private String name;
        @JsonProperty("pipeline_cls")
        private Class<?> pipelineCls;
        @Builder.Default
        private String description = "";
        @Builder.Default
        @JsonProperty("expected_outputs")
        private List<String> expectedOutputs = new ArrayList<>();
    }

    /**
     * Mirrors Python's {@code AutoHarnessConfig} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AutoHarnessConfig {
        private Model model;
        @JsonProperty("plan_model")
        private Model planModel;
        @Builder.Default
        @JsonProperty("data_dir")
        private String dataDir = "";
        @Builder.Default
        @JsonProperty("local_repo")
        private String localRepo = "";
        @Builder.Default
        @JsonProperty("repo_url")
        private String repoUrl = DEFAULT_REPO_URL;
        @Builder.Default
        @JsonProperty("skills_dirs")
        private List<String> skillsDirs = new ArrayList<>();
        @Builder.Default
        @JsonProperty("community_skill_repos")
        private List<String> communitySkillRepos = new ArrayList<>(DEFAULT_COMMUNITY_SKILL_REPOS);
        @Builder.Default
        @JsonProperty("community_skill_cache_dir")
        private String communitySkillCacheDir = "";
        @Builder.Default
        @JsonProperty("stage_registrars")
        private List<String> stageRegistrars = new ArrayList<>();
        @Builder.Default
        @JsonProperty("pipeline_registrars")
        private List<String> pipelineRegistrars = new ArrayList<>();
        @Builder.Default
        private String language = "cn";
        @Builder.Default
        @JsonProperty("optimization_goal")
        private String optimizationGoal = "";
        @Builder.Default
        @JsonProperty("pipeline_preference")
        private String pipelinePreference = PIPELINE_PREFERENCE_AUTO;
        @Builder.Default
        @JsonProperty("session_budget_secs")
        private double sessionBudgetSecs = 900000.0;
        @Builder.Default
        @JsonProperty("cost_limit_usd")
        private double costLimitUsd = 10.0;
        @Builder.Default
        @JsonProperty("task_timeout_secs")
        private double taskTimeoutSecs = 300000.0;
        @Builder.Default
        @JsonProperty("model_timeout_secs")
        private double modelTimeoutSecs = 300000.0;
        @Builder.Default
        @JsonProperty("max_tasks_per_session")
        private int maxTasksPerSession = 10;
        @Builder.Default
        @JsonProperty("self_driven_slots")
        private int selfDrivenSlots = 1;
        @Builder.Default
        @JsonProperty("extension_verify_concurrency")
        private int extensionVerifyConcurrency = 4;
        @Builder.Default
        @JsonProperty("git_remote")
        private String gitRemote = "";
        @Builder.Default
        @JsonProperty("git_base_branch")
        private String gitBaseBranch = "develop";
        @Builder.Default
        @JsonProperty("git_user_name")
        private String gitUserName = "";
        @Builder.Default
        @JsonProperty("git_user_email")
        private String gitUserEmail = "";
        @Builder.Default
        @JsonProperty("fork_owner")
        private String forkOwner = "";
        @Builder.Default
        @JsonProperty("upstream_owner")
        private String upstreamOwner = "openJiuwen";
        @Builder.Default
        @JsonProperty("upstream_repo")
        private String upstreamRepo = "agent-core";
        @Builder.Default
        @JsonProperty("gitcode_username")
        private String gitcodeUsername = "";
        @Builder.Default
        @JsonProperty("gitcode_token")
        private String gitcodeToken = "";
        @Builder.Default
        @JsonProperty("gitcode_token_env")
        private String gitcodeTokenEnv = "GITCODE_ACCESS_TOKEN";
        @Builder.Default
        @JsonProperty("ci_gate_config")
        private String ciGateConfig = "";
        @Builder.Default
        @JsonProperty("ci_gate_python_executable")
        private String ciGatePythonExecutable = "";
        @Builder.Default
        @JsonProperty("ci_gate_install_command")
        private String ciGateInstallCommand = "";
        @Builder.Default
        @JsonProperty("fix_phase1_max_retries")
        private int fixPhase1MaxRetries = 10;
        @Builder.Default
        @JsonProperty("fix_phase2_max_retries")
        private int fixPhase2MaxRetries = 9;
        @Builder.Default
        @JsonProperty("immutable_files")
        private List<String> immutableFiles = new ArrayList<>();
        @Builder.Default
        @JsonProperty("high_impact_prefixes")
        private List<String> highImpactPrefixes = defaultHighImpactPrefixes();
        @Builder.Default
        @JsonProperty("agent_iterations")
        private Map<String, Integer> agentIterations = defaultAgentIterations();
        @Builder.Default
        private String workspace = "";
        @Builder.Default
        @JsonProperty("config_path")
        private String configPath = "";
        @Builder.Default
        @JsonProperty("config_bootstrapped")
        private boolean configBootstrapped = false;
        @Builder.Default
        @JsonProperty("suggested_local_repo")
        private String suggestedLocalRepo = "";
        @Builder.Default
        @JsonProperty("experience_dir")
        private String experienceDir = "";

        public String getResolvedExperienceDir() {
            if (!isBlank(experienceDir)) {
                return experienceDir;
            }
            if (!isBlank(dataDir)) {
                return Path.of(dataDir).resolve("experience").toString();
            }
            return ".auto_harness/experience/";
        }

        public String getWorktreesDir() {
            if (!isBlank(dataDir)) {
                return Path.of(dataDir).resolve("worktrees").toString();
            }
            return ".auto_harness/worktrees/";
        }

        public String getRunsDir() {
            if (!isBlank(dataDir)) {
                return Path.of(dataDir).resolve("runs").toString();
            }
            return ".auto_harness/runs/";
        }

        public String getCacheRepoDir() {
            String repoName = resolveRepoName();
            if (!isBlank(dataDir)) {
                return Path.of(dataDir).resolve("repo").resolve(repoName).toString();
            }
            return ".auto_harness/repo/" + repoName;
        }

        public String getRuntimeExtensionsDir() {
            if (!isBlank(dataDir)) {
                return Path.of(dataDir).resolve("runtime_extensions").toString();
            }
            return ".auto_harness/runtime_extensions/";
        }

        public String getResolvedCommunitySkillCacheDir() {
            if (!isBlank(communitySkillCacheDir)) {
                return communitySkillCacheDir;
            }
            if (!isBlank(dataDir)) {
                return Path.of(dataDir).resolve("skills-cache").toString();
            }
            return ".auto_harness/skills-cache/";
        }

        public String resolveRepoName() {
            for (String candidate : List.of(upstreamRepo, repoUrlStem(repoUrl))) {
                String name = candidate == null ? "" : candidate.strip();
                if (!name.isBlank()) {
                    return removeGitSuffix(name);
                }
            }
            return "repository";
        }

        public String resolveGitcodeToken() {
            if (!isBlank(gitcodeToken)) {
                return gitcodeToken;
            }
            return System.getenv().getOrDefault(gitcodeTokenEnv, "");
        }

        public String resolveGitcodeUsername() {
            if (!isBlank(gitcodeUsername)) {
                return gitcodeUsername;
            }
            if (!isBlank(forkOwner)) {
                return forkOwner;
            }
            return "";
        }

        public String resolveCiGatePythonExecutable() {
            if (!isBlank(ciGatePythonExecutable)) {
                return ciGatePythonExecutable;
            }

            List<Path> candidates = new ArrayList<>();
            if (!isBlank(workspace)) {
                candidates.addAll(venvPythonCandidates(workspace));
            }
            if (!isBlank(localRepo)) {
                candidates.addAll(venvPythonCandidates(localRepo));
            }
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
            return "python";
        }

        public int resolveAgentIterations(String stageName, int defaultValue) {
            try {
                Integer value = agentIterations.get(stageName);
                return value == null ? defaultValue : value;
            } catch (RuntimeException e) {
                return defaultValue;
            }
        }

        public List<String> resolveImmutableFiles() {
            if (immutableFiles != null && !immutableFiles.isEmpty()) {
                return new ArrayList<>(immutableFiles);
            }
            return defaultImmutableFiles();
        }

        public ProjectProfile buildProjectProfile() {
            return ProjectProfile.builder()
                    .repoUrl(repoUrl)
                    .immutableFiles(resolveImmutableFiles())
                    .highImpactPrefixes(new ArrayList<>(highImpactPrefixes))
                    .defaultBaseBranch(isBlank(gitBaseBranch) ? "develop" : gitBaseBranch)
                    .build();
        }

        public AutoHarnessPaths buildPaths() {
            return AutoHarnessPaths.builder()
                    .dataDir(dataDir)
                    .experienceDir(getResolvedExperienceDir())
                    .worktreesDir(getWorktreesDir())
                    .runsDir(getRunsDir())
                    .cacheRepoDir(getCacheRepoDir())
                    .runtimeExtensionsDir(getRuntimeExtensionsDir())
                    .build();
        }

        public static AutoHarnessConfig loadFromDict(Map<String, Object> data) {
            Map<String, Object> source = data == null ? Map.of() : data;
            AutoHarnessConfig cfg = new AutoHarnessConfig();

            if (source.containsKey("data_dir")) {
                cfg.setDataDir(stringValue(source.get("data_dir")));
            }
            if (source.containsKey("local_repo")) {
                cfg.setLocalRepo(stringValue(source.get("local_repo")));
            }
            if (source.containsKey("repo_url")) {
                cfg.setRepoUrl(stringValue(source.get("repo_url")));
            }
            if (source.get("skills_dirs") instanceof List<?>) {
                cfg.setSkillsDirs(stringList(source.get("skills_dirs")));
            }
            if (source.get("community_skill_repos") instanceof List<?>) {
                cfg.setCommunitySkillRepos(stringList(source.get("community_skill_repos")));
            }
            if (source.containsKey("community_skill_cache_dir")) {
                cfg.setCommunitySkillCacheDir(stringValue(source.get("community_skill_cache_dir")));
            }
            if (source.get("stage_registrars") instanceof List<?>) {
                cfg.setStageRegistrars(stringList(source.get("stage_registrars")));
            }
            if (source.get("pipeline_registrars") instanceof List<?>) {
                cfg.setPipelineRegistrars(stringList(source.get("pipeline_registrars")));
            }
            if (source.get("immutable_files") instanceof List<?>) {
                cfg.setImmutableFiles(stringList(source.get("immutable_files")));
            }
            if (source.containsKey("language")) {
                cfg.setLanguage(stringValue(source.get("language")));
            }
            if (source.containsKey("pipeline")) {
                cfg.setPipelinePreference(normalizePipelinePreference(source.get("pipeline")));
            }
            if (source.containsKey("pipeline_preference")) {
                cfg.setPipelinePreference(normalizePipelinePreference(source.get("pipeline_preference")));
            }
            if (source.containsKey("workspace")) {
                cfg.setWorkspace(stringValue(source.get("workspace")));
            }
            if (source.containsKey("experience_dir")) {
                cfg.setExperienceDir(stringValue(source.get("experience_dir")));
            }

            Map<String, Object> git = mapValue(source.get("git"));
            if (!git.isEmpty()) {
                if (git.containsKey("remote")) {
                    cfg.setGitRemote(stringValue(git.get("remote")));
                }
                if (git.containsKey("base_branch")) {
                    cfg.setGitBaseBranch(stringValue(git.get("base_branch")));
                }
                if (git.containsKey("user_name")) {
                    cfg.setGitUserName(stringValue(git.get("user_name")));
                }
                if (git.containsKey("user_email")) {
                    cfg.setGitUserEmail(stringValue(git.get("user_email")));
                }
                if (git.containsKey("fork_owner")) {
                    cfg.setForkOwner(stringValue(git.get("fork_owner")));
                }
                if (git.containsKey("upstream_owner")) {
                    cfg.setUpstreamOwner(stringValue(git.get("upstream_owner")));
                }
                if (git.containsKey("upstream_repo")) {
                    cfg.setUpstreamRepo(stringValue(git.get("upstream_repo")));
                }
            }

            Map<String, Object> gitcode = mapValue(source.get("gitcode"));
            if (!gitcode.isEmpty()) {
                if (gitcode.containsKey("username")) {
                    cfg.setGitcodeUsername(stringValue(gitcode.get("username")));
                }
                if (gitcode.containsKey("access_token_env")) {
                    cfg.setGitcodeTokenEnv(stringValue(gitcode.get("access_token_env")));
                }
                if (gitcode.containsKey("access_token")) {
                    cfg.setGitcodeToken(stringValue(gitcode.get("access_token")));
                }
            }

            Map<String, Object> budget = mapValue(source.get("budget"));
            if (!budget.isEmpty()) {
                cfg.setSessionBudgetSecs(doubleValue(budget.get("session_secs"), cfg.getSessionBudgetSecs()));
                cfg.setCostLimitUsd(doubleValue(budget.get("cost_limit_usd"), cfg.getCostLimitUsd()));
                cfg.setTaskTimeoutSecs(doubleValue(budget.get("task_timeout_secs"), cfg.getTaskTimeoutSecs()));
                cfg.setModelTimeoutSecs(doubleValue(budget.get("model_timeout_secs"), cfg.getModelTimeoutSecs()));
                cfg.setMaxTasksPerSession(intValue(budget.get("max_tasks_per_session"), cfg.getMaxTasksPerSession()));
            }

            Map<String, Object> ci = mapValue(source.get("ci_gate"));
            if (!ci.isEmpty()) {
                if (ci.containsKey("config_path")) {
                    cfg.setCiGateConfig(stringValue(ci.get("config_path")));
                }
                if (ci.containsKey("python_executable")) {
                    cfg.setCiGatePythonExecutable(stringValue(ci.get("python_executable")));
                }
                if (ci.containsKey("install_command")) {
                    cfg.setCiGateInstallCommand(stringValue(ci.get("install_command")));
                }
            }

            Map<String, Object> fixLoop = mapValue(source.get("fix_loop"));
            if (!fixLoop.isEmpty()) {
                cfg.setFixPhase1MaxRetries(intValue(fixLoop.get("phase1_max_retries"), cfg.getFixPhase1MaxRetries()));
                cfg.setFixPhase2MaxRetries(intValue(fixLoop.get("phase2_max_retries"), cfg.getFixPhase2MaxRetries()));
            }

            Map<String, Object> agent = mapValue(source.get("agent"));
            for (Map.Entry<String, Object> entry : agent.entrySet()) {
                try {
                    cfg.getAgentIterations().put(entry.getKey(), intValue(entry.getValue(), cfg.getAgentIterations().getOrDefault(entry.getKey(), 0)));
                } catch (RuntimeException ignored) {
                    // Match Python's best-effort agent iteration parsing.
                }
            }

            Map<String, Object> extensions = mapValue(source.get("extensions"));
            if (extensions.get("stage_registrars") instanceof List<?>) {
                cfg.setStageRegistrars(stringList(extensions.get("stage_registrars")));
            }
            if (extensions.get("pipeline_registrars") instanceof List<?>) {
                cfg.setPipelineRegistrars(stringList(extensions.get("pipeline_registrars")));
            }

            return cfg;
        }

        private static String repoUrlStem(String repoUrl) {
            String trimmed = stringValue(repoUrl).strip();
            while (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
            String name = slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
            return removeGitSuffix(name);
        }

        private static String removeGitSuffix(String value) {
            return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
        }
    }

    /**
     * Mirrors Python's {@code ActivateDecision} in
     * {@code openjiuwen/auto_harness/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActivateDecision {
        @Builder.Default
        private String action = "accept";
        @Builder.Default
        private String feedback = "";
    }
}
