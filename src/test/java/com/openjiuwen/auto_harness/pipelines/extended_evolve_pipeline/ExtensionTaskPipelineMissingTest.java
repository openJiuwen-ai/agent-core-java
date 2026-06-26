/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines.extended_evolve_pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.auto_harness.stages.ExtendActivateStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests/unit_tests/auto_harness/pipelines/test_extension_task_pipeline.py}.
 */
class ExtensionTaskPipelineMissingTest {

    @TempDir
    Path tempDir;

    @Test
    void extensionTaskPipelineRunsEndToEnd() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator();
        ExtensionDesign design = design("demo_ext");
        OptimizationTask task = ExtensionTaskPipeline.buildExtensionTask(design);
        task.setStatus(TaskStatus.RUNNING);
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());
        ctx.putArtifact("extension_target", design);

        ExtensionTaskPipeline pipeline = new ExtensionTaskPipeline(name ->
                new ScriptedStage(name, slotFor(name), true, messageFor(name, design.getExtensionName())));

        List<Object> events = toList(pipeline.stream(ctx));

        assertThat(messageContents(events)).contains(
                "Implemented extension: demo_ext",
                "Verified extension scaffold: demo_ext",
                "Extension activated: demo_ext"
        );
        CycleResult result = (CycleResult) ctx.getArtifact("task_result");
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSummary()).isEqualTo("Extension activated: demo_ext");
    }

    @Test
    void extensionReadyPayloadPointsToSessionRuntimeRoot() throws Exception {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        Path sessionRoot = orchestrator.ensureSessionRuntimeDir();
        for (String name : List.of("first_ext", "second_ext")) {
            Path extensionDir = sessionRoot.resolve(name);
            Files.createDirectories(extensionDir);
            Files.writeString(
                    extensionDir.resolve("harness_config.yaml"),
                    "schema_version: harness_config.v0.1\nname: " + name + "\n",
                    StandardCharsets.UTF_8
            );
        }

        OptimizationTask task = OptimizationTask.builder()
                .topic("runtime-extension:first_ext")
                .build();
        TaskContext ctx = new TaskContext(orchestrator, task, new TaskRuntime());
        Path firstExt = sessionRoot.resolve("first_ext");
        ctx.putArtifact(
                "runtime_extension",
                RuntimeExtensionArtifact.builder()
                        .extensionName("first_ext")
                        .runtimePath(firstExt.toString())
                        .configPath(firstExt.resolve("harness_config.yaml").toString())
                        .build()
        );

        Iterator<Object> stream = new ExtendActivateStage(configValue -> inputs -> List.of().iterator()).stream(ctx);
        OutputSchema ready = (OutputSchema) stream.next();
        Map<String, Object> payload = payload(ready);

        assertThat(ready.getType()).isEqualTo("extension_ready");
        assertThat(payload.get("runtime_path")).isEqualTo(sessionRoot.toString());
        assertThat(payload.get("session_runtime_path")).isEqualTo(sessionRoot.toString());
        assertThat(payload.get("extension_runtime_path")).isEqualTo(firstExt.toString());
        assertThat(extensionNames(payload.get("runtime_extensions"))).containsExactly("first_ext", "second_ext");
    }

    private static ExtensionDesign design(String name) {
        Map<String, String> filePlan = new LinkedHashMap<>();
        filePlan.put("root", "openjiuwen/extensions/harness/" + name);
        filePlan.put("manifest", "openjiuwen/extensions/harness/" + name + "/harness_config.yaml");
        return ExtensionDesign.builder()
                .gapId("gap_1")
                .extensionName(name)
                .components(List.of("rail", "tool"))
                .filePlan(filePlan)
                .build();
    }

    private static String slotFor(String stageName) {
        return switch (stageName) {
            case "implement_ext" -> "implement";
            case "verify_ext" -> "verify";
            case "activate_ext" -> "activate";
            default -> stageName;
        };
    }

    private static String messageFor(String stageName, String extensionName) {
        return switch (stageName) {
            case "implement_ext" -> "Implemented extension: " + extensionName;
            case "verify_ext" -> "Verified extension scaffold: " + extensionName;
            case "activate_ext" -> "Extension activated: " + extensionName;
            default -> stageName + "-done";
        };
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> events = new ArrayList<>();
        iterator.forEachRemaining(events::add);
        return events;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema output) {
        return (Map<String, Object>) output.getPayload();
    }

    @SuppressWarnings("unchecked")
    private static List<String> extensionNames(Object value) {
        List<String> names = new ArrayList<>();
        for (Map<String, String> item : (List<Map<String, String>>) value) {
            names.add(item.get("extension_name"));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<String> messageContents(List<Object> events) {
        List<String> contents = new ArrayList<>();
        for (Object event : events) {
            if (!(event instanceof OutputSchema output) || !"message".equals(output.getType())) {
                continue;
            }
            contents.add(String.valueOf(((Map<String, Object>) output.getPayload()).get("content")));
        }
        return contents;
    }

    private static final class ScriptedStage extends BaseStage {
        private final String name;
        private final String slot;
        private final boolean success;
        private final String message;

        private ScriptedStage(String name, String slot, boolean success, String message) {
            this.name = name;
            this.slot = slot;
            this.success = success;
            this.message = message;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String slot() {
            return slot;
        }

        @Override
        public Iterator<Object> stream(BaseExecutionContext ctx) {
            StageResult result = StageResult.builder()
                    .status(success ? "success" : "failed")
                    .messages(List.of(message))
                    .error(success ? "" : name + " failed")
                    .build();
            return List.of((Object) result).iterator();
        }
    }
}
