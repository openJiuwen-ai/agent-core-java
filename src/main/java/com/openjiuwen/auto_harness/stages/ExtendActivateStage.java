/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ActivateDecision;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSlot;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Activate stage: preview, confirm, then hot-load.
 *
 * <p>Mirrors Python's {@code ExtendActivateStage} in
 * {@code openjiuwen/auto_harness/stages/activate.py}.</p>
 */
public class ExtendActivateStage extends TaskStage {

    private static final Logger LOGGER = Logger.getLogger(ExtendActivateStage.class.getName());
    private static final String RUNTIME_EXTENSION = "runtime_extension";
    private static final String VERIFY_REPORT = "verify_report";
    private static final String ACTIVATE_DECISION = "activate_decision";

    private final ActivateGuideAgentFactory guideAgentFactory;

    public ExtendActivateStage() {
        this(config -> inputs -> AutoHarnessAgentFactory.createActivateGuideAgent(config, null).stream(inputs));
    }

    public ExtendActivateStage(ActivateGuideAgentFactory guideAgentFactory) {
        this.guideAgentFactory = guideAgentFactory == null
                ? config -> inputs -> Collections.emptyIterator()
                : guideAgentFactory;
    }

    @Override
    public String name() {
        return "activate_ext";
    }

    @Override
    public String displayName() {
        return "激活扩展";
    }

    @Override
    public String slot() {
        return StageSlot.ACTIVATE.value();
    }

    @Override
    public List<String> consumes() {
        return List.of(RUNTIME_EXTENSION, VERIFY_REPORT);
    }

    @Override
    public List<String> produces() {
        return List.of(ACTIVATE_DECISION);
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("ExtendActivateStage requires a TaskContext");
        }
        RuntimeExtensionArtifact runtimeExt = requireRuntimeExtension(taskContext.requireArtifact(RUNTIME_EXTENSION));
        Object verifyReport = taskContext.getArtifact(VERIFY_REPORT, Map.of());
        String sessionRuntimePath = taskContext.getOrchestrator().ensureSessionRuntimeDir().toString();
        List<Map<String, String>> runtimeExtensions = runtimeExtensionsSnapshot(sessionRuntimePath);

        String interactionId = "activate:" + nullToEmpty(runtimeExt.getExtensionName());
        CompletableFuture<Object> future = taskContext.getOrchestrator().createInteraction(interactionId);
        List<Object> prefixEvents = List.of(
                extensionReady(runtimeExt, verifyReport, sessionRuntimePath, runtimeExtensions),
                interactionRequest(runtimeExt, interactionId, sessionRuntimePath)
        );
        return new ActivationIterator(taskContext, runtimeExt, verifyReport, future, prefixEvents);
    }

    public static ActivateDecision parseDecision(Object response) {
        if (response instanceof Map<?, ?> map) {
            ActivateDecision decision = new ActivateDecision();
            decision.setAction(stringOrDefault(map.get("action"), "accept"));
            decision.setFeedback(stringOrDefault(map.get("feedback"), ""));
            return decision;
        }
        return ActivateDecision.builder().action("accept").build();
    }

    public static Map<String, Object> safeVerifyReport(Object report) {
        if (report instanceof VerifyReportArtifact typed) {
            return new LinkedHashMap<>(typed.getCiResult());
        }
        Map<String, Object> serializable = new LinkedHashMap<>();
        if (report instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String
                        || value instanceof Number
                        || value instanceof Boolean
                        || value instanceof List<?>) {
                    serializable.put(String.valueOf(entry.getKey()), value);
                }
            }
        }
        return serializable;
    }

    public static Map<String, Object> componentsSummary(Object report) {
        Map<String, Object> ci = new LinkedHashMap<>();
        if (report instanceof VerifyReportArtifact typed) {
            ci.putAll(typed.getCiResult());
        } else if (report instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                ci.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rails", ci.getOrDefault("rails", 0));
        summary.put("tools", ci.getOrDefault("tools", 0));
        summary.put("skills", ci.getOrDefault("skills", 0));
        return summary;
    }

    public static LoadedComponents previewExtensionComponents(RuntimeExtensionArtifact runtimeExt) {
        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(Path.of(nullToEmpty(runtimeExt.getConfigPath())));
        HarnessConfig.ResourcesSchema resources = resolved.getConfig() == null ? null : resolved.getConfig().getResources();
        List<String> rails = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        List<String> skills = new ArrayList<>();
        if (resources != null) {
            for (HarnessConfig.RailResourceSchema rail : nullToEmpty(resources.getRails())) {
                rails.add(componentName(rail.getClassName(), rail.getName(), rail.getModule()));
            }
            for (HarnessConfig.ToolResourceSchema tool : nullToEmpty(resources.getTools())) {
                tools.add(componentName(tool.getClassName(), tool.getName(), tool.getModule()));
            }
            if (resources.getSkills() != null && resources.getSkills().getDirs() != null) {
                Path root = Path.of(nullToEmpty(runtimeExt.getRuntimePath())).toAbsolutePath().normalize();
                for (String skillDir : resources.getSkills().getDirs()) {
                    Path skillRoot = root.resolve(nullToEmpty(skillDir)).normalize();
                    if (!Files.isDirectory(skillRoot)) {
                        continue;
                    }
                    try (Stream<Path> children = Files.list(skillRoot)) {
                        children.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                                .filter(path -> Files.isDirectory(path) && Files.isRegularFile(path.resolve("SKILL.md")))
                                .map(path -> path.getFileName().toString())
                                .forEach(skills::add);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to scan skill directory: " + skillRoot, e);
                    }
                }
            }
        }
        return new LoadedComponents(rails, tools, skills);
    }

    public static List<Map<String, String>> runtimeExtensionsSnapshot(String sessionRuntimePath) {
        Path root = Path.of(nullToEmpty(sessionRuntimePath));
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Map<String, String>> items = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            children.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .filter(Files::isDirectory)
                    .forEach(child -> {
                        Path configPath = child.resolve("harness_config.yaml");
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("extension_name", child.getFileName().toString());
                        item.put("runtime_path", child.toAbsolutePath().normalize().toString());
                        item.put(
                                "config_path",
                                Files.exists(configPath) ? configPath.toAbsolutePath().normalize().toString() : ""
                        );
                        items.add(item);
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan runtime extensions: " + root, e);
        }
        return items;
    }

    public static void cleanupRuntime(RuntimeExtensionArtifact runtimeExt) {
        String rawPath = runtimeExt == null ? "" : nullToEmpty(runtimeExt.getRuntimePath());
        if (rawPath.isBlank()) {
            return;
        }
        Path runtimePath = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.exists(runtimePath)) {
            return;
        }
        if (!Files.isDirectory(runtimePath) || runtimePath.getParent() == null || runtimePath.getFileName() == null) {
            throw new IllegalArgumentException("Refusing to remove invalid runtime path: " + runtimePath);
        }
        try {
            Files.walkFileTree(runtimePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to remove runtime path: " + runtimePath, e);
        }
    }

    public static void unloadExtension(RuntimeExtensionArtifact runtimeExt, String sessionId) {
        cleanupRuntime(runtimeExt);
    }

    public static String buildTestingGuideQuery(
            RuntimeExtensionArtifact runtimeExt,
            LoadedComponents loaded,
            Object verifyReport,
            Object design
    ) {
        String designInfo = "";
        if (design instanceof ExtensionDesign extensionDesign) {
            designInfo = "扩展设计目标: gap_id=" + nullToEmpty(extensionDesign.getGapId())
                    + ", components=" + nullToEmpty(extensionDesign.getComponents())
                    + ", file_plan=" + nullToEmpty(extensionDesign.getFilePlan());
        }
        String verifyInfo = "";
        if (verifyReport instanceof VerifyReportArtifact typed) {
            verifyInfo = String.valueOf(typed.getCiResult());
        } else if (verifyReport instanceof Map<?, ?> map) {
            verifyInfo = String.valueOf(map);
        }
        String extName = nullToEmpty(runtimeExt.getExtensionName());
        return "扩展 " + extName + " 已热加载到 deep agent。\n\n"
                + "已加载组件:\n"
                + "- Rails: " + nonEmptyListOrNone(loaded.getRails()) + "\n"
                + "- Tools: " + nonEmptyListOrNone(loaded.getTools()) + "\n"
                + "- Skills: " + nonEmptyListOrNone(loaded.getSkills()) + "\n\n"
                + designInfo + "\n\n"
                + "验证报告: " + verifyInfo + "\n\n"
                + "请生成一份简洁的测试引导，包含:\n"
                + "1. 任务总结 - 这个扩展做了什么（1-2 句话）\n"
                + "2. 推荐测试 case - 3-5 个具体的测试场景，每个包含输入示例和预期行为\n"
                + "3. 预期效果 - 用户应该观察到什么变化\n"
                + "4. 注意事项 - 可能的边界情况或已知限制\n\n"
                + "用户将退出 auto-harness，在普通 query 模式下测试。\n"
                + "如需卸载扩展: /auto-harness deactivate " + extName + "\n\n"
                + "用 markdown 格式输出，保持简洁。";
    }

    private static OutputSchema extensionReady(
            RuntimeExtensionArtifact runtimeExt,
            Object verifyReport,
            String sessionRuntimePath,
            List<Map<String, String>> runtimeExtensions
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("extension_name", runtimeExt.getExtensionName());
        payload.put("runtime_path", sessionRuntimePath);
        payload.put("session_runtime_path", sessionRuntimePath);
        payload.put("extension_runtime_path", runtimeExt.getRuntimePath());
        payload.put("config_path", runtimeExt.getConfigPath());
        payload.put("runtime_extensions", runtimeExtensions);
        payload.put("verify_report", safeVerifyReport(verifyReport));
        payload.put("components_summary", componentsSummary(verifyReport));
        return new OutputSchema("extension_ready", 0, payload);
    }

    private static OutputSchema interactionRequest(
            RuntimeExtensionArtifact runtimeExt,
            String interactionId,
            String sessionRuntimePath
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("interaction_type", "activate_confirm");
        payload.put("interaction_id", interactionId);
        payload.put("extension_name", runtimeExt.getExtensionName());
        payload.put("runtime_path", runtimeExt.getRuntimePath());
        payload.put("session_runtime_path", sessionRuntimePath);
        payload.put("extension_runtime_path", runtimeExt.getRuntimePath());
        payload.put("options", List.of("accept", "reject"));
        return new OutputSchema("__interaction__", 0, payload);
    }

    private Iterator<Object> streamTestingGuide(
            TaskContext ctx,
            RuntimeExtensionArtifact runtimeExt,
            LoadedComponents loaded,
            Object verifyReport
    ) {
        Object design = ctx.getArtifact("extension_target");
        ActivateGuideAgent agent = guideAgentFactory.create(ctx.getOrchestrator().getConfig());
        String query = buildTestingGuideQuery(runtimeExt, loaded, verifyReport, design);
        List<Object> chunks = new ArrayList<>();
        Iterator<?> stream = agent.stream(Map.of("query", query));
        while (stream.hasNext()) {
            Object chunk = stream.next();
            if ("llm_output".equals(chunkType(chunk))) {
                chunks.add(chunk);
            }
        }
        return chunks.iterator();
    }

    private List<Object> buildContinuation(
            TaskContext ctx,
            RuntimeExtensionArtifact runtimeExt,
            Object verifyReport,
            ActivateDecision decision
    ) {
        if ("reject".equals(decision.getAction())) {
            cleanupRuntime(runtimeExt);
            return List.of(failedResult("用户拒绝扩展", decision));
        }

        DeepAgent agent = ctx.getOrchestrator().getAgent();
        if (agent == null) {
            LOGGER.warning("No DeepAgent on orchestrator, skipping hot-load enqueue");
            return List.of(successResult(decision));
        }

        agent.enqueueHarnessConfig(runtimeExt.getConfigPath());
        LoadedComponents loaded = previewExtensionComponents(runtimeExt);
        List<Object> events = new ArrayList<>();
        events.add(BaseExecutionContext.message(
                "扩展 " + nullToEmpty(runtimeExt.getExtensionName())
                        + " 已排队热加载: "
                        + loaded.getRails().size() + " rails, "
                        + loaded.getTools().size() + " tools, "
                        + loaded.getSkills().size() + " skills\n"
                        + "下次普通 query 时生效。"
        ));

        StringBuilder guideParts = new StringBuilder();
        Iterator<Object> guideStream = streamTestingGuide(ctx, runtimeExt, loaded, verifyReport);
        while (guideStream.hasNext()) {
            Object chunk = guideStream.next();
            Map<String, Object> payload = payloadMap(chunk);
            if (payload != null) {
                guideParts.append(nullToEmpty(payload.get("content")));
            }
            events.add(chunk);
        }
        String guideText = guideParts.toString().strip();
        if (!guideText.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("extension_name", runtimeExt.getExtensionName());
            payload.put("text", guideText);
            events.add(new OutputSchema("activate_testing_guide", 0, payload));
        }
        events.add(successResult(decision));
        return events;
    }

    private static StageResult successResult(ActivateDecision decision) {
        return StageResult.builder()
                .status("success")
                .artifacts(Map.of(ACTIVATE_DECISION, decision))
                .build();
    }

    private static StageResult failedResult(String error, ActivateDecision decision) {
        return StageResult.builder()
                .status("failed")
                .error(error)
                .artifacts(Map.of(ACTIVATE_DECISION, decision))
                .build();
    }

    private static RuntimeExtensionArtifact requireRuntimeExtension(Object value) {
        if (value instanceof RuntimeExtensionArtifact typed) {
            return typed;
        }
        throw new IllegalStateException("runtime_extension artifact must be RuntimeExtensionArtifact");
    }

    private static String componentName(String className, String name, String module) {
        for (String value : new String[] {className, name, module}) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String nonEmptyListOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "无" : values.toString();
    }

    private static String chunkType(Object chunk) {
        if (chunk instanceof OutputSchema output) {
            return output.getType();
        }
        if (chunk instanceof Map<?, ?> map) {
            Object value = map.get("type");
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadMap(Object chunk) {
        if (chunk instanceof OutputSchema output && output.getPayload() instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (chunk instanceof Map<?, ?> map && map.get("payload") instanceof Map<?, ?> payload) {
            return (Map<String, Object>) payload;
        }
        return null;
    }

    private static String stringOrDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Hot-loaded extension components.
     *
     * <p>Mirrors Python's {@code LoadedComponents} in
     * {@code openjiuwen/auto_harness/stages/activate.py}.</p>
     */
    public static final class LoadedComponents {
        private final List<String> rails;
        private final List<String> tools;
        private final List<String> skills;

        public LoadedComponents(List<String> rails, List<String> tools, List<String> skills) {
            this.rails = new ArrayList<>(rails == null ? List.of() : rails);
            this.tools = new ArrayList<>(tools == null ? List.of() : tools);
            this.skills = new ArrayList<>(skills == null ? List.of() : skills);
        }

        public List<String> getRails() {
            return new ArrayList<>(rails);
        }

        public List<String> getTools() {
            return new ArrayList<>(tools);
        }

        public List<String> getSkills() {
            return new ArrayList<>(skills);
        }
    }

    /**
     * Factory for the activation guide agent.
     *
     * <p>Mirrors Python's late import of {@code create_activate_guide_agent} in
     * {@code openjiuwen/auto_harness/stages/activate.py}.</p>
     */
    @FunctionalInterface
    public interface ActivateGuideAgentFactory {
        ActivateGuideAgent create(AutoHarnessConfig config);
    }

    /**
     * Streaming surface used by the activation guide agent.
     *
     * <p>Mirrors Python's guide agent stream contract in
     * {@code openjiuwen/auto_harness/stages/activate.py}.</p>
     */
    @FunctionalInterface
    public interface ActivateGuideAgent {
        Iterator<?> stream(Map<String, Object> inputs);
    }

    private final class ActivationIterator implements Iterator<Object> {
        private final TaskContext ctx;
        private final RuntimeExtensionArtifact runtimeExt;
        private final Object verifyReport;
        private final CompletableFuture<Object> future;
        private final List<Object> prefixEvents;
        private List<Object> continuationEvents;
        private int index;
        private int continuationIndex;

        private ActivationIterator(
                TaskContext ctx,
                RuntimeExtensionArtifact runtimeExt,
                Object verifyReport,
                CompletableFuture<Object> future,
                List<Object> prefixEvents
        ) {
            this.ctx = ctx;
            this.runtimeExt = runtimeExt;
            this.verifyReport = verifyReport;
            this.future = future;
            this.prefixEvents = prefixEvents;
        }

        @Override
        public boolean hasNext() {
            if (index < prefixEvents.size()) {
                return true;
            }
            ensureContinuation();
            return continuationIndex < continuationEvents.size();
        }

        @Override
        public Object next() {
            if (index < prefixEvents.size()) {
                return prefixEvents.get(index++);
            }
            ensureContinuation();
            if (continuationIndex >= continuationEvents.size()) {
                throw new NoSuchElementException();
            }
            return continuationEvents.get(continuationIndex++);
        }

        private void ensureContinuation() {
            if (continuationEvents != null) {
                return;
            }
            ActivateDecision decision = parseDecision(future.join());
            continuationEvents = buildContinuation(ctx, runtimeExt, verifyReport, decision);
        }
    }
}
