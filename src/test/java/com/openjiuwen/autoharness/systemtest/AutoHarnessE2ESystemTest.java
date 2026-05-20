package com.openjiuwen.autoharness.systemtest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.autoharness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.autoharness.schema.AutoHarnessConfig;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.TaskStatus;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.systemtest.ApiConfigLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("system-test")
class AutoHarnessE2ESystemTest {
    private static final String ENABLE_ENV = "AUTO_HARNESS_E2E_SYSTEM_TEST";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void fullCycleShouldImplementVerifyCommitAndLocalPublishWithRealAgentWhenExplicitlyEnabled() throws Exception {
        assumeTrue("1".equals(System.getenv(ENABLE_ENV)),
                ENABLE_ENV + "=1 is required for real auto-harness E2E test");
        Map<String, String> api = loadApiConfigOrSkip();
        assumeTrue(hasRealText(api.get("API_KEY")),
                "real API_KEY is required in environment or APIKEY/apiconfig.json");
        assumeTrue(hasRealText(api.get("API_BASE")),
                "real API_BASE is required in environment or APIKEY/apiconfig.json");
        assumeTrue(hasRealText(api.get("MODEL_PROVIDER")),
                "real MODEL_PROVIDER is required in environment or APIKEY/apiconfig.json");
        assumeTrue(hasRealText(api.get("MODEL_NAME")),
                "real MODEL_NAME is required in environment or APIKEY/apiconfig.json");

        Path repo = tempDir.resolve("repo");
        Path origin = tempDir.resolve("origin.git");
        Files.createDirectories(repo);
        initGitRepo(repo, origin);
        Path ciGate = writeCiGate(repo);

        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .model(createModel(api))
                .workspace(repo.toString())
                .localRepo(repo.toString())
                .dataDir(tempDir.resolve("data").toString())
                .experienceDir(tempDir.resolve("experience").toString())
                .ciGateConfig(ciGate.toString())
                .gitRemote("")
                .gitBaseBranch("develop")
                .gitUserName("Auto Harness E2E")
                .gitUserEmail("auto-harness-e2e@example.com")
                .sessionBudgetSecs(600.0)
                .taskTimeoutSecs(300.0)
                .modelTimeoutSecs(120.0)
                .fixPhase1MaxRetries(3)
                .fixPhase2MaxRetries(0)
                .agentIterations(Map.of(
                        "implement", 30,
                        "assess", 30,
                        "plan", 15,
                        "select_pipeline", 10,
                        "eval", 10,
                        "pr_draft", 5,
                        "learnings", 5,
                        "explore_subagent", 20,
                        "browser_subagent", 20
                ))
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        OptimizationTask task = OptimizationTask.builder()
                .topic("创建字符串工具模块")
                .description("在 examples/auto_harness/e2e_string_utils.py 中创建 reverse_string(s: str) -> str，"
                        + "返回反转后的字符串。代码需要通过仓库配置的 CI gate。")
                .files(List.of("examples/auto_harness/e2e_string_utils.py"))
                .build();

        orchestrator.runSessionStream(List.of(task));
        List<CycleResult> results = orchestrator.getResults();

        assertThat(results).hasSize(1);
        CycleResult result = results.get(0);
        assertThat(result.isSuccess()).as(result.getError()).isTrue();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(result.getSummary()).isNotBlank();
    }

    private static Map<String, String> loadApiConfigOrSkip() {
        return resolveApiConfig(loadApiConfigIfPresent(), System.getenv(), loadOpenjiuwenSettings());
    }

    private static Map<String, String> loadApiConfigIfPresent() {
        try {
            return ApiConfigLoader.load();
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    static Map<String, String> resolveApiConfig(Map<String, String> loaded, Map<String, String> env) {
        return resolveApiConfig(loaded, env, Map.of());
    }

    static Map<String, String> resolveApiConfig(Map<String, String> loaded, Map<String, String> env,
                                                Map<String, String> settings) {
        java.util.LinkedHashMap<String, String> api = new java.util.LinkedHashMap<>(loaded);
        putSettings(api, settings, "API_BASE", "apiBase");
        putSettings(api, settings, "API_KEY", "apiKey");
        putSettings(api, settings, "MODEL_NAME", "model");
        putSettings(api, settings, "MODEL_PROVIDER", "provider");
        api.putIfAbsent("MODEL_NAME", "GLM-5");
        api.putIfAbsent("MODEL_PROVIDER", "OpenAI");
        putEnv(api, env, "API_BASE", new String[] {"API_BASE", "OPENJIUWEN_API_BASE"});
        putEnv(api, env, "API_KEY", new String[] {"API_KEY", "OPENJIUWEN_API_KEY"});
        putEnv(api, env, "MODEL_NAME", new String[] {"MODEL_NAME", "OPENJIUWEN_MODEL"});
        putEnv(api, env, "MODEL_PROVIDER", new String[] {"MODEL_PROVIDER", "OPENJIUWEN_PROVIDER"});
        putEnv(api, env, "MODEL_TIMEOUT", new String[] {"MODEL_TIMEOUT"});
        putEnv(api, env, "LLM_SSL_VERIFY", new String[] {"LLM_SSL_VERIFY"});
        putEnv(api, env, "LLM_SSL_CERT", new String[] {"LLM_SSL_CERT"});
        return api;
    }

    private static void putEnv(Map<String, String> target, Map<String, String> env, String targetKey, String[] keys) {
        for (String key : keys) {
            String envValue = env.get(key);
            if (hasText(envValue)) {
                target.put(targetKey, envValue);
                return;
            }
        }
    }

    private static void putSettings(Map<String, String> target, Map<String, String> settings, String targetKey,
                                    String settingsKey) {
        String value = settings.get(settingsKey);
        if (hasText(value)) {
            target.put(targetKey, value);
        }
    }

    private static Map<String, String> loadOpenjiuwenSettings() {
        Path settings = Path.of(System.getProperty("user.home"), ".openjiuwen", "settings.json");
        if (!Files.isRegularFile(settings)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(settings.toFile(), new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static Model createModel(Map<String, String> api) {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(api.get("MODEL_PROVIDER"))
                .apiKey(api.get("API_KEY"))
                .apiBase(api.get("API_BASE"))
                .timeout(parseDouble(api.get("MODEL_TIMEOUT"), 120.0))
                .maxRetries(2)
                .verifySsl(Boolean.parseBoolean(api.getOrDefault("LLM_SSL_VERIFY", "true")))
                .sslCert(api.get("LLM_SSL_CERT"))
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(api.get("MODEL_NAME"))
                .temperature(0.2)
                .topP(0.9)
                .maxTokens(4096)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private static Path writeCiGate(Path repo) throws Exception {
        Path ciGate = repo.resolve("ci_gate_e2e.yaml");
        Files.writeString(ciGate, """
                ci_gates:
                  - name: file_exists
                    command: "test -f examples/auto_harness/e2e_string_utils.py"
                    required: true
                  - name: function_exists
                    command: "grep -q 'def reverse_string' examples/auto_harness/e2e_string_utils.py"
                    required: true
                  - name: behavior
                    command: |
                      python - <<'PY'
                      import importlib.util
                      spec = importlib.util.spec_from_file_location('e2e_string_utils', 'examples/auto_harness/e2e_string_utils.py')
                      module = importlib.util.module_from_spec(spec)
                      spec.loader.exec_module(module)
                      assert module.reverse_string('abc') == 'cba'
                      PY
                    required: true
                """);
        return ciGate;
    }

    private static void initGitRepo(Path repo, Path origin) throws Exception {
        run(tempDirParent(origin), "git", "init", "--bare", origin.toString());
        run(repo, "git", "init");
        run(repo, "git", "config", "user.email", "auto-harness-e2e@example.com");
        run(repo, "git", "config", "user.name", "Auto Harness E2E");
        run(repo, "git", "checkout", "-b", "develop");
        run(repo, "git", "remote", "add", "origin", origin.toString());
        Files.writeString(repo.resolve("README.md"), "# Auto Harness E2E\n");
        Files.createDirectories(repo.resolve("examples/auto_harness"));
        Files.writeString(repo.resolve("examples/auto_harness/.gitkeep"), "");
        run(repo, "git", "add", ".");
        run(repo, "git", "commit", "-m", "init");
        run(repo, "git", "push", "-u", "origin", "develop");
    }

    private static Path tempDirParent(Path path) {
        Path parent = path.getParent();
        return parent == null ? Path.of(".") : parent;
    }

    private static String run(Path cwd, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertThat(code).as(String.join(" ", command) + "\n" + output).isZero();
        return output;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static double parseDouble(String value, double fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean hasRealText(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return !normalized.startsWith("your-") && !normalized.contains("your-");
    }
}
