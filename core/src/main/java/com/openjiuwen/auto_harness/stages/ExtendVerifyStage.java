/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.infra.CIGateRunner;
import com.openjiuwen.auto_harness.infra.ExtStaticCheckResult;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionStaticChecks;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionBuildArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validate manifest, imports, lint, and constructors.
 *
 * <p>Mirrors Python's {@code ExtendVerifyStage} and extension verification helpers in
 * {@code openjiuwen/auto_harness/stages/verify.py}.</p>
 */
public class ExtendVerifyStage extends VerifyStage {

    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern FAILURE_ID_PATTERN = Pattern.compile("failure_id=([a-zA-Z_]+):");

    private final DependencyInstaller dependencyInstaller;
    private final StaticCheckRunner staticCheckRunner;
    private final AcceptanceRunner acceptanceRunner;

    public ExtendVerifyStage() {
        this(
                ExtendVerifyStage::installExtensionDependencies,
                ExtendVerifyStage::runStaticChecks,
                ExtendVerifyStage::runAgentGeneratedExtAcceptance
        );
    }

    public ExtendVerifyStage(
            DependencyInstaller dependencyInstaller,
            StaticCheckRunner staticCheckRunner,
            AcceptanceRunner acceptanceRunner
    ) {
        this.dependencyInstaller = dependencyInstaller == null
                ? ExtendVerifyStage::installExtensionDependencies
                : dependencyInstaller;
        this.staticCheckRunner = staticCheckRunner == null
                ? ExtendVerifyStage::runStaticChecks
                : staticCheckRunner;
        this.acceptanceRunner = acceptanceRunner == null
                ? ExtendVerifyStage::runAgentGeneratedExtAcceptance
                : acceptanceRunner;
    }

    @Override
    public String name() {
        return "verify_ext";
    }

    @Override
    public String displayName() {
        return "验证扩展";
    }

    @Override
    public List<String> consumes() {
        return List.of("extension_build");
    }

    @Override
    public List<String> produces() {
        return List.of("extension_build", "verify_report");
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("ExtendVerifyStage requires TaskContext");
        }
        Object rawBuild = taskContext.requireArtifact("extension_build");
        if (!(rawBuild instanceof ExtensionBuildArtifact build)) {
            throw new IllegalArgumentException("extension_build artifact must be ExtensionBuildArtifact");
        }

        List<Object> events = new ArrayList<>();
        InstallResult installResult = dependencyInstaller.install(Path.of(build.getExtensionRoot()));
        if (!installResult.success()) {
            events.add(taskContext.message("[verify_ext] 依赖安装失败: " + installResult.error()));
        }

        ExtStaticCheckResult staticResult = new ExtStaticCheckResult();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            RuntimeExtensionArtifact runtime = RuntimeExtensionArtifact.builder()
                    .extensionName(build.getExtensionName())
                    .runtimePath(build.getExtensionRoot())
                    .configPath(build.getConfigPath())
                    .build();
            staticResult = staticCheckRunner.check(
                    runtime,
                    "verify_" + taskContext.getOrchestrator().getRuntime().getSessionId()
                            + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
            );
            if (staticResult.getErrors().isEmpty()) {
                break;
            }
            if (!(taskContext.getRuntime() != null && taskContext.getRuntime().getTaskAgent() instanceof DeepAgent agent)
                    || attempt >= MAX_ATTEMPTS) {
                break;
            }
            String errorText = String.join("; ", staticResult.getErrors());
            events.add(taskContext.message("[verify_ext] 结构/静态校验失败，修复扩展实现后重试\n"
                    + summarizeText(errorText, 6, 800)));
            String prompt = buildExtStaticFixPrompt(build, errorText);
            streamVerifyExtAgentTurn(agent, prompt, "verify-ext-" + build.getExtensionName() + "-static-fix")
                    .forEachRemaining(event -> events.add(BaseStage.scopeOutputEventStage(event, "verify_ext")));
        }

        if (!staticResult.getErrors().isEmpty()) {
            String errorText = String.join("; ", staticResult.getErrors());
            events.add(failedStageResult(
                    "Extension verify failed: " + errorText,
                    errorText,
                    staticResult,
                    "verify_report"
            ));
            return events.iterator();
        }

        AcceptanceRun acceptance = acceptanceRunner.run(
                taskContext,
                build,
                staticResult.getRailsCount(),
                staticResult.getToolsCount(),
                staticResult.getSkillsCount()
        );
        events.addAll(acceptance.events());
        if (!acceptance.result().passed()) {
            String error = acceptance.result().errors();
            events.add(failedStageResult(
                    "Extension acceptance tests failed: " + error,
                    error,
                    staticResult,
                    "verify_report"
            ));
            return events.iterator();
        }

        RuntimeExtensionArtifact runtimeExt = PromoteRuntime.promoteRuntime(taskContext);
        Map<String, Object> ciResult = new LinkedHashMap<>();
        ciResult.put("passed", true);
        ciResult.put("rails", staticResult.getRailsCount());
        ciResult.put("tools", staticResult.getToolsCount());
        ciResult.put("skills", staticResult.getSkillsCount());
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("extension_build", build);
        artifacts.put("verify_report", VerifyReportArtifact.builder()
                .ciResult(ciResult)
                .build());
        artifacts.put("runtime_extension", runtimeExt);
        events.add(StageResult.builder()
                .artifacts(artifacts)
                .messages(List.of("Verified extension scaffold: " + build.getExtensionName()))
                .build());
        return events.iterator();
    }

    public static InstallResult installExtensionDependencies(Path extensionRoot) {
        Path reqFile = extensionRoot.resolve("requirements.txt");
        if (!Files.exists(reqFile)) {
            return new InstallResult(true, "");
        }
        Map<String, String> env = buildInstallEnv();
        if (!checkPipAvailable(env)) {
            InstallResult bootstrap = bootstrapPip(env);
            if (!bootstrap.success()) {
                return bootstrap;
            }
        }
        return runPipInstall(reqFile, env);
    }

    public static Map<String, String> buildInstallEnv() {
        Map<String, String> env = new LinkedHashMap<>(System.getenv());
        env.put("CI", "1");
        env.remove("VIRTUAL_ENV");
        String python = System.getProperty("java.home") == null ? "python" : "python";
        env.put("AUTO_HARNESS_PYTHON", python);
        return env;
    }

    public static boolean checkPipAvailable(Map<String, String> env) {
        return runProcess(List.of("python", "-m", "pip", "--version"), env).success();
    }

    public static InstallResult bootstrapPip(Map<String, String> env) {
        ProcessResult result = runProcess(List.of("python", "-m", "ensurepip", "--upgrade"), env);
        return result.success()
                ? new InstallResult(true, "")
                : new InstallResult(false, "pip_bootstrap_failed: " + summarizeText(result.output(), 6, 500));
    }

    public static InstallResult runPipInstall(Path reqFile, Map<String, String> env) {
        ProcessResult result = runProcess(List.of("python", "-m", "pip", "install", "-r", reqFile.toString()), env);
        return result.success()
                ? new InstallResult(true, "")
                : new InstallResult(false, "pip_install_failed: " + summarizeText(result.output(), 6, 500));
    }

    public static ExtStaticCheckResult runStaticChecks(
            RuntimeExtensionArtifact runtimeExt,
            String sessionIdPrefix
    ) {
        try {
            return RuntimeExtensionStaticChecks.runStaticChecksAgainstRuntime(runtimeExt, sessionIdPrefix);
        } catch (IOException | RuntimeException e) {
            ExtStaticCheckResult result = new ExtStaticCheckResult();
            result.getErrors().add(e.getMessage() == null ? e.toString() : e.getMessage());
            return result;
        }
    }

    public static AcceptanceRun runAgentGeneratedExtAcceptance(
            TaskContext ctx,
            ExtensionBuildArtifact build,
            int railsCount,
            int toolsCount,
            int skillsCount
    ) {
        return runAgentGeneratedExtAcceptance(
                ctx,
                build,
                railsCount,
                toolsCount,
                skillsCount,
                ExtendVerifyStage::runPytestFile
        );
    }

    public static AcceptanceRun runAgentGeneratedExtAcceptance(
            TaskContext ctx,
            ExtensionBuildArtifact build,
            int railsCount,
            int toolsCount,
            int skillsCount,
            PytestFileRunner pytestFileRunner
    ) {
        if (!(ctx.getRuntime() != null && ctx.getRuntime().getTaskAgent() instanceof DeepAgent agent)) {
            return new AcceptanceRun(List.of(), new CIResult(false, "acceptance_test_agent_missing"));
        }
        PytestFileRunner runner = pytestFileRunner == null ? ExtendVerifyStage::runPytestFile : pytestFileRunner;
        Path testDir = Path.of(ctx.getRuntime().getWtPath(), ".auto_harness_verify", build.getExtensionName());
        Path testFile = testDir.resolve("test_runtime_extension_acceptance.py");
        String pythonExecutable = ctx.getOrchestrator().getConfig().resolveCiGatePythonExecutable();
        List<Object> events = new ArrayList<>();
        String lastError = "";
        boolean testGenerated = false;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Files.createDirectories(testDir);
            } catch (IOException e) {
                return new AcceptanceRun(events, new CIResult(false, e.getMessage()));
            }
            if (!testGenerated) {
                events.add(ctx.message("[verify_ext] 生成 runtime extension 验收测试 (attempt "
                        + attempt + "/" + MAX_ATTEMPTS + ")"));
                String prompt = buildExtAcceptanceTestPrompt(
                        build,
                        testFile,
                        pythonExecutable,
                        railsCount,
                        toolsCount,
                        skillsCount,
                        lastError
                );
                streamVerifyExtAgentTurn(agent, prompt, "verify-ext-" + build.getExtensionName() + "-generate")
                        .forEachRemaining(event -> events.add(BaseStage.scopeOutputEventStage(event, "verify_ext")));
                if (!Files.isRegularFile(testFile)) {
                    lastError = "verify_ext_test_not_generated: expected test file at " + testFile;
                } else {
                    testGenerated = true;
                }
            }
            if (!testGenerated) {
                continue;
            }
            CIResult result = runner.run(pythonExecutable, testFile, Path.of(ctx.getRuntime().getWtPath()));
            if (result.passed()) {
                events.add(ctx.message("[verify_ext] runtime extension 验收测试通过"));
                return new AcceptanceRun(events, result);
            }
            lastError = result.errors();
            if (attempt >= MAX_ATTEMPTS) {
                break;
            }
            events.add(ctx.message("[verify_ext] 验收测试失败，修复扩展实现后复跑同一测试\n"
                    + summarizeText(lastError, 6, 800)));
            String fixPrompt = buildExtAcceptanceFixPrompt(build, testFile, lastError, pythonExecutable);
            streamVerifyExtAgentTurn(agent, fixPrompt, "verify-ext-" + build.getExtensionName() + "-fix")
                    .forEachRemaining(event -> events.add(BaseStage.scopeOutputEventStage(event, "verify_ext")));
        }
        return new AcceptanceRun(events, new CIResult(false,
                lastError.isBlank() ? "verify_ext_acceptance_failed" : lastError));
    }

    public static Iterator<Object> streamVerifyExtAgentTurn(
            DeepAgent agent,
            String prompt,
            String sessionIdPrefix
    ) {
        if (agent == null) {
            return List.of().iterator();
        }
        String sessionId = sessionIdPrefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        AgentSession session = new AgentSession(
                sessionId,
                null,
                agent.getCard(),
                null,
                false,
                null
        );
        session.preRun(Map.of("inputs", Map.of("query", prompt)));
        return VerifyStageObjectIterator.of(agent.stream(Map.<String, Object>of("query", prompt), session), session);
    }

    public static String buildExtAcceptanceTestPrompt(
            ExtensionBuildArtifact build,
            Path testFile,
            String pythonExecutable,
            int railsCount,
            int toolsCount,
            int skillsCount,
            String previousError
    ) {
        return "请为 runtime extension 生成验收测试。\n"
                + "你正在执行 verify_ext 阶段。请严格遵循 verify_ext skill 规范生成 pytest 验收测试。\n"
                + "扩展名称: " + build.getExtensionName() + "\n"
                + "扩展根目录: " + build.getExtensionRoot() + "\n"
                + "harness_config: " + build.getConfigPath() + "\n"
                + "测试文件必须写入: " + testFile + "\n"
                + "pytest 解释器: " + pythonExecutable + "\n"
                + "组件数量: rails=" + railsCount + ", tools=" + toolsCount + ", skills=" + skillsCount + "\n"
                + "必须覆盖 L1/L2/L3，禁止修改扩展实现以外的文件。\n"
                + "路径动态解析: 禁止硬编码任何绝对路径，必须从 __file__ 或环境变量动态推断。\n"
                + "动态导入: 必须从 harness_config.yaml 实际声明的 module/class 获取，"
                + "禁止假设特定 module path。\n"
                + "module 必须以 openjiuwen.extensions.harness.<extension_name> 开头。\n"
                + "L3 运行时验收必须覆盖 ToolOutput.success、设计声明字段和可观测副作用。\n"
                + "文件产物验收 (仅文件生成类 Tool): 传入 pytest tmp_path 下的 output_path，"
                + "断言返回 success=true, path/absolute_path 存在, exists=true, size_bytes>0。\n"
                + "PPTX: zipfile 校验 [Content_Types].xml + ppt/presentation.xml + slide*.xml；"
                + "DOCX: zipfile 校验 [Content_Types].xml + word/document.xml；PDF: 文件头 %PDF；"
                + "JSON: json.load 重解析 + 关键字段；禁止 JSON/Markdown 冒充 PPTX/DOCX/PDF。\n"
                + (previousError == null || previousError.isBlank() ? "" : "上次失败:\n" + previousError);
    }

    public static String buildExtStaticFixPrompt(ExtensionBuildArtifact build, String staticErrors) {
        return "verify_ext 的结构/静态校验失败。请只修改扩展 package 内的实现文件和 harness_config.yaml。\n"
                + "扩展名称: " + build.getExtensionName() + "\n"
                + "扩展根目录: " + build.getExtensionRoot() + "\n"
                + "harness_config: " + build.getConfigPath() + "\n\n"
                + "常见修复要求: harness_config.yaml 必须符合 schema；resources 只声明实际生成的 rails/tools/skills；"
                + "Tool class 必须可无参构造；SKILL.md 必须有合法 frontmatter。\n\n"
                + "rail/tool 条目必须使用 type: package，并同时包含 module 和 class；module 必须以 "
                + "openjiuwen.extensions.harness.<extension_name>. 开头。所有自测 import 和实例化都必须以 "
                + "harness_config.yaml 中实际声明的 module/class 为唯一来源，不要手写或猜测路径。\n\n"
                + "失败信息:\n" + preview(staticErrors, 6000);
    }

    public static String buildExtAcceptanceFixPrompt(
            ExtensionBuildArtifact build,
            Path testFile,
            String pytestOutput,
            String pythonExecutable
    ) {
        String failureIds = extractFailureIdsFromPytestOutput(pytestOutput);
        return "verify_ext 验收测试失败。请根据 failure_id 精准定位并修复扩展实现。\n\n"
                + "扩展根目录: " + build.getExtensionRoot() + "\n"
                + "harness_config: " + build.getConfigPath() + "\n"
                + "测试文件: " + testFile + "\n"
                + "pytest 解释器: " + pythonExecutable + "\n\n"
                + (failureIds.isBlank() ? "" : "检测到的 failure_id:\n" + failureIds + "\n")
                + "修复约束: 只允许修改扩展根目录内实现文件，不要修改测试文件绕过失败。\n\n"
                + "文件产物修复: PPTX/DOCX/PDF/JSON 必须生成真实文件并可被对应格式校验，"
                + "禁止 JSON/Markdown 冒充，禁止返回 JSON/Markdown 占位并标记 success=true。\n\n"
                + "pytest 输出:\n" + preview(pytestOutput, 5000);
    }

    public static String extractFailureIdsFromPytestOutput(String pytestOutput) {
        Matcher matcher = FAILURE_ID_PATTERN.matcher(pytestOutput == null ? "" : pytestOutput);
        java.util.Set<String> ids = new java.util.TreeSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        if (ids.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        List<String> l1 = List.of("manifest_invalid", "entry_point_not_allowed", "module_import_failed",
                "class_init_failed", "skill_manifest_invalid");
        List<String> l2 = List.of("harness_load_failed", "rail_not_registered", "tool_not_registered", "skill_not_loaded");
        List<String> l3 = List.of("tool_not_called", "tool_result_failed", "tool_result_schema_missing",
                "artifact_not_created", "artifact_format_invalid", "artifact_placeholder_output",
                "rail_hook_not_observed", "rail_tool_state_not_shared");
        appendFailureGroup(lines, "L1 结构类:", ids, l1);
        appendFailureGroup(lines, "L2 热加载类:", ids, l2);
        appendFailureGroup(lines, "L3 运行时类:", ids, l3);
        List<String> other = ids.stream().filter(id -> !l1.contains(id) && !l2.contains(id) && !l3.contains(id)).toList();
        appendFailureGroup(lines, "其他:", ids, other);
        return String.join("\n", lines);
    }

    public static CIResult runPytestFile(String pythonExecutable, Path testFile, Path cwd) {
        ProcessResult result = runProcess(
                List.of(pythonExecutable, "-m", "pytest", testFile.toString(), "-q", "-o", "addopts="),
                Map.of("CI", "1", "AUTO_HARNESS_PYTHON", pythonExecutable),
                cwd
        );
        String output = result.output();
        return new CIResult(result.success(), output.length() <= 6000
                ? output
                : output.substring(output.length() - 6000));
    }

    public static List<String> checkImports(Path extensionRoot) {
        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(extensionRoot)) {
            return errors;
        }
        try (var paths = Files.walk(extensionRoot)) {
            paths.filter(path -> path.getFileName() != null && path.getFileName().toString().endsWith(".py"))
                    .filter(path -> !"__init__.py".equals(path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            errors.add("Import failed for " + path.getFileName() + ": " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            errors.add(e.getMessage());
        }
        return errors;
    }

    private static StageResult failedStageResult(
            String message,
            String error,
            ExtStaticCheckResult staticResult,
            String artifactName
    ) {
        Map<String, Object> ciResult = new LinkedHashMap<>();
        ciResult.put("passed", false);
        ciResult.put("rails", staticResult.getRailsCount());
        ciResult.put("tools", staticResult.getToolsCount());
        ciResult.put("skills", staticResult.getSkillsCount());
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put(artifactName, VerifyReportArtifact.builder()
                .ciResult(ciResult)
                .error(error)
                .build());
        artifacts.put("task_result", CycleResult.builder()
                .success(false)
                .error(message)
                .errorLog(error)
                .build());
        return StageResult.builder()
                .status("failed")
                .artifacts(artifacts)
                .messages(List.of(message))
                .error(error)
                .build();
    }

    private static void appendFailureGroup(
            List<String> lines,
            String header,
            java.util.Set<String> allIds,
            List<String> groupIds
    ) {
        List<String> found = groupIds.stream().filter(allIds::contains).toList();
        if (found.isEmpty()) {
            return;
        }
        lines.add(header);
        found.forEach(id -> lines.add("  - " + id));
    }

    private static ProcessResult runProcess(List<String> command, Map<String, String> env) {
        return runProcess(command, env, null);
    }

    private static ProcessResult runProcess(List<String> command, Map<String, String> env, Path cwd) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (cwd != null) {
                builder.directory(cwd.toFile());
            }
            builder.environment().putAll(env);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = CIGateRunner.decodeStdout(process.getInputStream().readAllBytes());
            int code = process.waitFor();
            return new ProcessResult(code == 0, output);
        } catch (IOException e) {
            return new ProcessResult(false, e.getMessage() == null ? e.toString() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessResult(false, "interrupted");
        }
    }

    private static String preview(String value, int maxChars) {
        String text = value == null ? "" : value;
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    @FunctionalInterface
    public interface DependencyInstaller {
        InstallResult install(Path extensionRoot);
    }

    @FunctionalInterface
    public interface StaticCheckRunner {
        ExtStaticCheckResult check(RuntimeExtensionArtifact runtimeExt, String sessionIdPrefix);
    }

    @FunctionalInterface
    public interface AcceptanceRunner {
        AcceptanceRun run(TaskContext ctx, ExtensionBuildArtifact build, int railsCount, int toolsCount, int skillsCount);
    }

    @FunctionalInterface
    public interface PytestFileRunner {
        CIResult run(String pythonExecutable, Path testFile, Path cwd);
    }

    public record InstallResult(boolean success, String error) {
    }

    public record CIResult(boolean passed, String errors) {
    }

    public record AcceptanceRun(List<Object> events, CIResult result) {
    }

    private record ProcessResult(boolean success, String output) {
    }

    private static final class VerifyStageObjectIterator {
        private static Iterator<Object> of(Iterator<?> iterator) {
            return of(iterator, null);
        }

        private static Iterator<Object> of(Iterator<?> iterator, AgentSession session) {
            Iterator<?> source = iterator == null ? List.of().iterator() : iterator;
            return new Iterator<>() {
                private boolean closed;

                @Override
                public boolean hasNext() {
                    boolean hasNext = source.hasNext();
                    if (!hasNext) {
                        closeSession();
                    }
                    return hasNext;
                }

                @Override
                public Object next() {
                    try {
                        return source.next();
                    } catch (RuntimeException e) {
                        closeSession();
                        throw e;
                    }
                }

                private void closeSession() {
                    if (closed || session == null) {
                        return;
                    }
                    session.postRun();
                    closed = true;
                }
            };
        }
    }
}
