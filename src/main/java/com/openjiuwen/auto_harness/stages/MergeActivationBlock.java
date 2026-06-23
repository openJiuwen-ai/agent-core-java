/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.infra.ExtStaticCheckResult;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.MergeRuntimeExtensionsResult;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.MergedExtensionError;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.SkillPathKey;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.SourcePathKey;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionStaticChecks;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline.VerifiedExtensionTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.single_agent.rail.AgentRail;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merge multiple verified extensions and run static checks.
 *
 * <p>Mirrors Python's {@code MergeActivationBlock} and module helpers in
 * {@code openjiuwen/auto_harness/stages/merge.py}.</p>
 */
public class MergeActivationBlock {

    public static final String NAME = "merge_ext";

    private static final Logger LOGGER = Logger.getLogger(MergeActivationBlock.class.getName());
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_STATIC_ERROR_CHARS = 6000;
    private static final Pattern PURE_SNAKE_CASE = Pattern.compile("^[a-z][a-z0-9_]{0,39}$");
    private static final Pattern EMBEDDED_SNAKE_CASE = Pattern.compile("[a-z][a-z0-9_]{2,39}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RuntimeMerger merger;
    private final StaticChecker staticChecker;
    private final MergeAgentFactory agentFactory;
    private final NamingModel namingModel;

    public MergeActivationBlock() {
        this(
                RuntimeExtensionMerger::mergeRuntimeExtensions,
                RuntimeExtensionStaticChecks::runStaticChecksAgainstRuntime,
                (config, workspaceOverride, extraRails) -> {
                    DeepAgent agent = AutoHarnessAgentFactory.createMergeExtAgent(
                            config,
                            workspaceOverride,
                            extraRails
                    );
                    return agent::stream;
                },
                MergeActivationBlock::callNamingModel
        );
    }

    public MergeActivationBlock(
            RuntimeMerger merger,
            StaticChecker staticChecker,
            MergeAgentFactory agentFactory,
            NamingModel namingModel
    ) {
        this.merger = merger == null ? RuntimeExtensionMerger::mergeRuntimeExtensions : merger;
        this.staticChecker = staticChecker == null
                ? RuntimeExtensionStaticChecks::runStaticChecksAgainstRuntime
                : staticChecker;
        this.agentFactory = agentFactory == null ? (config, workspace, rails) -> inputs -> List.of().iterator()
                : agentFactory;
        this.namingModel = namingModel == null ? MergeActivationBlock::callNamingModel : namingModel;
    }

    public String name() {
        return NAME;
    }

    public Iterator<Object> stream(
            AutoHarnessOrchestrator orchestrator,
            List<VerifiedExtensionTask> verifiedTasks
    ) {
        return stream(orchestrator, verifiedTasks, "");
    }

    public Iterator<Object> stream(
            AutoHarnessOrchestrator orchestrator,
            List<VerifiedExtensionTask> verifiedTasks,
            String packageName
    ) {
        Objects.requireNonNull(orchestrator, "orchestrator must not be null");
        List<VerifiedExtensionTask> tasks = verifiedTasks == null ? List.of() : verifiedTasks;
        Path sessionRoot = orchestrator.ensureSessionRuntimeDir();
        List<RuntimeExtensionArtifact> runtimeExts = runtimeExtensions(tasks);
        List<ExtensionDesign> designs = designs(tasks);
        String mergedName;
        if (packageName != null && !packageName.isBlank()) {
            mergedName = packageName;
            LOGGER.info(() -> "[MergeActivate] using pre-generated package_name: " + mergedName);
        } else {
            mergedName = deriveMergedName(orchestrator.getConfig(), designs, namingModel);
        }

        List<Object> events = new ArrayList<>();
        events.add(mergeEvent("running", mergedName, 0, ""));

        MergeRuntimeExtensionsResult mergeResult;
        try {
            mergeResult = merger.merge(runtimeExts, sessionRoot, mergedName);
        } catch (MergedExtensionError exception) {
            events.add(mergeEvent("failed", mergedName, 0, exception.getMessage()));
            throw exception;
        }

        RuntimeExtensionArtifact merged = mergeResult.runtimeExt();
        ExtStaticCheckResult result = runStaticChecks(
                merged,
                "merge_" + orchestrator.getRuntime().getSessionId()
        );
        MergeAgent agent = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (result.getErrors().isEmpty()) {
                LOGGER.info("[MergeActivate] merge static check no errors");
                break;
            }
            if (agent == null) {
                agent = agentFactory.create(
                        orchestrator.getConfig(),
                        merged.getRuntimePath(),
                        deepRails(orchestrator.getStreamRails())
                );
            }
            String prompt = buildMergeFixPrompt(merged, mergeResult, result.getErrors(), attempt, MAX_ATTEMPTS);
            streamMergeAgentTurn(
                    agent,
                    prompt,
                    "merge-" + nullToEmpty(merged.getExtensionName()) + "-fix-" + attempt
            ).forEachRemaining(events::add);

            result = runStaticChecks(
                    merged,
                    "merge_" + orchestrator.getRuntime().getSessionId() + "_" + shortUuid()
            );
        }

        if (!result.getErrors().isEmpty()) {
            events.add(mergeEvent(
                    "failed",
                    mergedName,
                    MAX_ATTEMPTS,
                    String.join("; ", result.getErrors())
            ));
            throw new MergedExtensionError(
                    "merged extension static checks failed after " + MAX_ATTEMPTS + " repair rounds"
            );
        }

        cleanupSourceExtensionDirs(runtimeExts);
        String successLog = "[MergeActivate] merge success, name=" + mergedName
                + ", tools_count: " + result.getToolsCount()
                + ", rails_count: " + result.getRailsCount()
                + ", skills_count: " + result.getSkillsCount();
        LOGGER.info(successLog);
        events.add(new MergeSuccessResult(merged));
        events.add(mergeEvent("success", mergedName, 0, ""));
        return events.iterator();
    }

    public static String deriveMergedName(
            AutoHarnessConfig config,
            List<ExtensionDesign> designs
    ) {
        return deriveMergedName(config, designs, MergeActivationBlock::callNamingModel);
    }

    static String deriveMergedName(
            AutoHarnessConfig config,
            List<ExtensionDesign> designs,
            NamingModel namingModel
    ) {
        List<ExtensionDesign> safeDesigns = designs == null ? List.of() : designs;
        if (safeDesigns.isEmpty()) {
            return "merged_extensions";
        }
        if (safeDesigns.size() == 1) {
            return nullToEmpty(safeDesigns.get(0).getExtensionName());
        }

        List<String> extensionNames = safeDesigns.stream()
                .map(ExtensionDesign::getExtensionName)
                .toList();
        String prompt = "根据以下 runtime extension 名称列表，生成一个语义化的合并扩展名称。\n"
                + "规则：\n"
                + "1. snake_case 格式\n"
                + "2. 理解共同能力，表达主要功能组合\n"
                + "3. 不超过 40 字符\n"
                + "扩展列表: " + jsonCompact(extensionNames) + "\n\n"
                + "只输出名称，不要解释。示例输出格式：office_ppt_generator\n";
        try {
            String output = namingModel.call(config, prompt);
            String name = parseNameOutput(output);
            if (name != null) {
                LOGGER.info(() -> "[MergeActivate] agent generated merged name: " + name
                        + " from designs: " + extensionNames);
                return name;
            }
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "[MergeActivate] naming agent failed, using fallback", exception);
        }
        return deriveMergedNameFallback(safeDesigns);
    }

    public static String callNamingModel(AutoHarnessConfig config, String prompt) {
        if (config == null || config.getModel() == null) {
            throw new IllegalStateException("config.model is None, cannot call naming agent");
        }
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .maxTokens(100)
                .timeout((float) config.getModelTimeoutSecs())
                .build();
        AssistantMessage response = config.getModel().invoke(prompt, options).toCompletableFuture().join();
        return contentAsText(response == null ? null : response.getContent()).strip();
    }

    public static String parseNameOutput(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        String stripped = output.strip();
        Matcher pure = PURE_SNAKE_CASE.matcher(stripped);
        if (pure.matches()) {
            return pure.group(0);
        }
        List<String> matches = new ArrayList<>();
        Matcher embedded = EMBEDDED_SNAKE_CASE.matcher(stripped);
        while (embedded.find()) {
            matches.add(embedded.group());
        }
        matches.sort(Comparator.comparingInt(String::length).reversed());
        for (String match : matches) {
            if (match.length() >= 3) {
                return match;
            }
        }
        return null;
    }

    public static String deriveMergedNameFallback(List<ExtensionDesign> designs) {
        List<ExtensionDesign> safeDesigns = designs == null ? List.of() : designs;
        if (safeDesigns.isEmpty()) {
            return "merged_extensions";
        }
        if (safeDesigns.size() == 1) {
            return nullToEmpty(safeDesigns.get(0).getExtensionName());
        }
        List<ExtensionDesign> ordered = new ArrayList<>();
        for (ExtensionDesign design : safeDesigns) {
            if (!"constraint".equals(nullToEmpty(design.getKind()))) {
                ordered.add(design);
            }
        }
        for (ExtensionDesign design : safeDesigns) {
            if ("constraint".equals(nullToEmpty(design.getKind()))) {
                ordered.add(design);
            }
        }
        ExtensionDesign primary = ordered.get(0);
        return nullToEmpty(primary.getExtensionName()) + "_merged";
    }

    public static OutputSchema mergeEvent(
            String status,
            String mergedName,
            int repairRounds,
            String error
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", "activate");
        payload.put("parent_stage", "activate");
        payload.put("extension_stage", NAME);
        payload.put("extension_name", isBlank(mergedName) ? "merged_extensions" : mergedName);
        payload.put("status", nullToEmpty(status));
        payload.put("repair_rounds", repairRounds);
        payload.put("error", nullToEmpty(error));
        payload.put("messages", List.of());
        payload.put("metrics", Map.of());
        return new OutputSchema("stage_result", 0, payload);
    }

    public static String buildMergeFixPrompt(
            RuntimeExtensionArtifact merged,
            MergeRuntimeExtensionsResult mergeResult,
            List<String> staticErrors,
            int attempt,
            int maxAttempts
    ) {
        RuntimeExtensionArtifact safeMerged = merged == null ? new RuntimeExtensionArtifact() : merged;
        MergeRuntimeExtensionsResult safeResult = mergeResult == null
                ? new MergeRuntimeExtensionsResult(safeMerged, Map.of(), Map.of(), List.of())
                : mergeResult;
        String renameSummary = formatSourceMapSummary(safeResult.renameMap());
        String skillRenameSummary = formatSkillMapSummary(safeResult.skillRenameMap());
        String sourceSummary = jsonPretty(safeResult.sourceExtsSummary());
        String errorsText = truncate(String.join("\n", staticErrors == null ? List.of() : staticErrors), MAX_STATIC_ERROR_CHARS);
        String mergedName = nullToEmpty(safeMerged.getExtensionName());
        String mergedPrefix = "openjiuwen.extensions.harness." + mergedName;

        return "合并产物的静态校验失败（manifest schema / "
                + "组件加载 / ruff）。\n"
                + "请只修改 " + mergedName + "/ 内文件，把校验跑过去。\n\n"
                + mergedName + " 根目录: " + nullToEmpty(safeMerged.getRuntimePath()) + "\n"
                + "harness_config: " + nullToEmpty(safeMerged.getConfigPath()) + "\n"
                + "来源扩展: " + sourceSummary + "\n\n"
                + "合并器已经做过的事：\n"
                + "1. 把每个源扩展的文件扁平复制进 " + mergedName + "/\n"
                + "2. 同相对路径冲突的文件按 <stem>__<src_ext>.<suffix> 改名"
                + "（其它文件保留原名）\n"
                + "3. 改写绝对/相对 import 中的 src_ext 前缀为 " + mergedName + "\n"
                + "4. 改写 harness_config.yaml 中所有 module 字段为 "
                + mergedPrefix + ".*\n"
                + "5. skills/ 仅在 skill 名冲突时按 <skill_name>__<src_ext> 改名，"
                + "不同名 skill 保持原名\n"
                + "6. 所有包目录 __init__.py 已重写为空文件，"
                + "不复制源扩展里的 __init__.py 内容\n\n"
                + "本次合并的具体改名摘要（只列非 identity 条目）：\n"
                + "- 文件 rename_map: " + renameSummary + "\n"
                + "- Skill skill_rename_map: " + skillRenameSummary + "\n\n"
                + "这些合并器动作是可信前提，不要反向撤销。"
                + "静态失败的高概率原因通常是：\n"
                + "- 动态 import / importlib 字符串没有被 AST rewrite 覆盖\n"
                + "- __file__ / Path 派生路径仍假设原 extension 根目录\n"
                + "- skill frontmatter 或配置文本里残留旧 module/path\n"
                + "- 相对 import 形态特殊，合并器没有识别到\n"
                + "- manifest module 指向的对象存在，但构造函数依赖旧路径或旧包名\n\n"
                + "修复硬约束（破坏即失败）：\n"
                + "- 只能修改 " + mergedName + "/ 内文件；"
                + "不能改源扩展、harness 主代码、auto_harness 主代码\n"
                + "- harness_config.yaml 中所有 module 必须仍以 "
                + mergedPrefix + " 开头\n"
                + "- extension_name 不要碰\n"
                + "- 不要给已被 rename 的文件再改名；"
                + "不要给未冲突文件加 __<src_ext> 后缀\n"
                + "- 优先修复：动态 import / __file__ 派生路径 / "
                + "skill frontmatter 旧路径 / 漏改的相对 import\n"
                + "- 不要修业务逻辑\n\n"
                + "修复轮次: " + attempt + "/" + maxAttempts + "\n\n"
                + "失败信息:\n" + errorsText;
    }

    public static String formatSourceMapSummary(Map<SourcePathKey, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return "none";
        }
        List<String> lines = new ArrayList<>();
        mapping.entrySet().stream()
                .sorted(Comparator.comparing(entry ->
                        entry.getKey().extensionName() + "\u0000" + entry.getKey().relativePath()))
                .forEach(entry -> lines.add("  (" + entry.getKey().extensionName()
                        + ", " + entry.getKey().relativePath() + ") -> " + entry.getValue()));
        return String.join("\n", lines);
    }

    public static String formatSkillMapSummary(Map<SkillPathKey, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return "none";
        }
        List<String> lines = new ArrayList<>();
        mapping.entrySet().stream()
                .sorted(Comparator.comparing(entry ->
                        entry.getKey().extensionName() + "\u0000" + entry.getKey().skillName()))
                .forEach(entry -> lines.add("  (" + entry.getKey().extensionName()
                        + ", " + entry.getKey().skillName() + ") -> " + entry.getValue()));
        return String.join("\n", lines);
    }

    public static Iterator<Object> streamMergeAgentTurn(
            MergeAgent agent,
            String prompt,
            String sessionIdPrefix
    ) {
        if (agent == null) {
            return List.of().iterator();
        }
        String sessionId = nullToEmpty(sessionIdPrefix) + "-" + shortUuid();
        AgentSession session = new AgentSession(sessionId, null, Map.of("id", sessionId), null, false, null);
        session.preRun(Map.of("inputs", Map.of("query", nullToEmpty(prompt))));
        List<Object> chunks = new ArrayList<>();
        try {
            Iterator<?> stream = agent.stream(Map.of("query", nullToEmpty(prompt)));
            while (stream.hasNext()) {
                chunks.add(restoreOutputSchemaIfPresent(stream.next()));
            }
            return chunks.iterator();
        } finally {
            session.postRun();
        }
    }

    private ExtStaticCheckResult runStaticChecks(RuntimeExtensionArtifact merged, String sessionIdPrefix) {
        try {
            return staticChecker.check(merged, sessionIdPrefix);
        } catch (IOException exception) {
            throw new MergedExtensionError("merged extension static checks failed: " + exception.getMessage(), exception);
        }
    }

    private static List<RuntimeExtensionArtifact> runtimeExtensions(List<VerifiedExtensionTask> verifiedTasks) {
        List<RuntimeExtensionArtifact> artifacts = new ArrayList<>();
        for (VerifiedExtensionTask task : verifiedTasks == null ? List.<VerifiedExtensionTask>of() : verifiedTasks) {
            Object artifact = task.ctx().requireArtifact("runtime_extension");
            if (!(artifact instanceof RuntimeExtensionArtifact typed)) {
                throw new IllegalStateException("runtime_extension artifact must be RuntimeExtensionArtifact");
            }
            artifacts.add(typed);
        }
        return artifacts;
    }

    private static List<ExtensionDesign> designs(List<VerifiedExtensionTask> verifiedTasks) {
        List<ExtensionDesign> result = new ArrayList<>();
        for (VerifiedExtensionTask task : verifiedTasks == null ? List.<VerifiedExtensionTask>of() : verifiedTasks) {
            result.add(task.design());
        }
        return result;
    }

    private static List<DeepAgentRail> deepRails(List<AgentRail> rails) {
        List<DeepAgentRail> result = new ArrayList<>();
        for (AgentRail rail : rails == null ? List.<AgentRail>of() : rails) {
            result.add(AutoHarnessAgentFactory.bridge(rail));
        }
        return result;
    }

    private static Object restoreOutputSchemaIfPresent(Object chunk) {
        if (chunk instanceof Map<?, ?> map) {
            Object output = map.get("_output");
            if (output instanceof OutputSchema schema) {
                return schema;
            }
        }
        return chunk;
    }

    private static void cleanupSourceExtensionDirs(List<RuntimeExtensionArtifact> artifacts) {
        for (RuntimeExtensionArtifact artifact : artifacts == null ? List.<RuntimeExtensionArtifact>of() : artifacts) {
            Path sourcePath = Path.of(nullToEmpty(artifact.getRuntimePath()));
            if (!Files.exists(sourcePath)) {
                continue;
            }
            LOGGER.info(() -> "[MergeActivate] cleanup source extension dir: " + sourcePath);
            try (var walk = Files.walk(sourcePath)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException ignored) {
                // Mirrors Python's shutil.rmtree(..., ignore_errors=True).
            }
        }
    }

    private static String contentAsText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text != null) {
                        builder.append(text);
                    }
                } else if (item instanceof String text) {
                    builder.append(text);
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    private static String jsonCompact(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private static String jsonPretty(Object value) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private static String truncate(String value, int maxChars) {
        String raw = value == null ? "" : value;
        return raw.length() <= maxChars ? raw : raw.substring(0, maxChars);
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Structured result yielded on merge success.
     *
     * <p>Mirrors Python's {@code MergeSuccessResult} in
     * {@code openjiuwen/auto_harness/stages/merge.py}.</p>
     *
     * @param artifact merged runtime extension artifact
     */
    public record MergeSuccessResult(RuntimeExtensionArtifact artifact) {
    }

    /**
     * Injectable merge operation for tests.
     *
     * <p>Mirrors Python's call to {@code merge_runtime_extensions} in
     * {@code openjiuwen/auto_harness/stages/merge.py}.</p>
     */
    @FunctionalInterface
    public interface RuntimeMerger {
        MergeRuntimeExtensionsResult merge(
                List<RuntimeExtensionArtifact> artifacts,
                Path sessionRoot,
                String mergedName
        );
    }

    /**
     * Injectable static-check operation for tests.
     *
     * <p>Mirrors Python's call to {@code run_static_checks_against_runtime} in
     * {@code openjiuwen/auto_harness/stages/merge.py}.</p>
     */
    @FunctionalInterface
    public interface StaticChecker {
        ExtStaticCheckResult check(RuntimeExtensionArtifact runtimeExt, String sessionIdPrefix) throws IOException;
    }

    /**
     * Streaming merge-agent surface.
     *
     * <p>Mirrors Python's {@code DeepAgent.stream} call in
     * {@code openjiuwen/auto_harness/stages/merge.py}.</p>
     */
    @FunctionalInterface
    public interface MergeAgent {
        Iterator<?> stream(Map<String, Object> inputs);
    }

    /**
     * Factory for merge repair agents.
     *
     * <p>Mirrors Python's late import of {@code create_merge_ext_agent} in
     * {@code openjiuwen/auto_harness/stages/merge.py}.</p>
     */
    @FunctionalInterface
    public interface MergeAgentFactory {
        MergeAgent create(AutoHarnessConfig config, String workspaceOverride, List<DeepAgentRail> extraRails);
    }

    /**
     * Lightweight naming model boundary.
     *
     * <p>Mirrors Python's {@code _call_naming_model} in
     * {@code openjiuwen/auto_harness/stages/merge.py}.</p>
     */
    @FunctionalInterface
    public interface NamingModel {
        String call(AutoHarnessConfig config, String prompt);
    }
}
