/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ActivateDecision;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.auto_harness.stages.ExtendActivateStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.DeepAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Activate stage parity tests.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.activate} in
 * {@code openjiuwen/auto_harness/stages/activate.py}.</p>
 */
class TestExtendActivateStage {

    @Test
    void acceptQueuesHarnessConfigAndStreamsGuide(@TempDir Path tempDir) throws Exception {
        Path runtimePath = createRuntimeExtension(tempDir, "demo_ext");
        Path configPath = runtimePath.resolve("harness_config.yaml");
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        DeepAgent agent = new DeepAgent();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, agent);
        Path sessionChild = orchestrator.ensureSessionRuntimeDir().resolve("existing_ext");
        Files.createDirectories(sessionChild);
        Files.writeString(sessionChild.resolve("harness_config.yaml"), "schema_version: harness_config.v0.1\n");
        TaskContext ctx = context(orchestrator);
        ctx.putArtifact("runtime_extension", RuntimeExtensionArtifact.builder()
                .extensionName("demo_ext")
                .runtimePath(runtimePath.toString())
                .configPath(configPath.toString())
                .build());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder()
                .ciResult(Map.of("rails", 1, "tools", 1, "skills", 1))
                .build());
        ctx.putArtifact("extension_target", ExtensionDesign.builder()
                .gapId("gap-1")
                .components(List.of("rail"))
                .filePlan(Map.of("root", "extensions/demo_ext"))
                .build());
        AtomicReference<Map<String, Object>> guideInputs = new AtomicReference<>();
        ExtendActivateStage stage = new ExtendActivateStage(ignored -> inputs -> {
            guideInputs.set(inputs);
            return List.of(
                    new OutputSchema("llm_output", 0, Map.of("content", "Guide text")),
                    new OutputSchema("message", 0, Map.of("content", "ignored"))
            ).iterator();
        });

        Iterator<Object> stream = stage.stream(ctx);
        OutputSchema ready = assertInstanceOf(OutputSchema.class, stream.next());
        assertEquals("extension_ready", ready.getType());
        Map<?, ?> readyPayload = assertInstanceOf(Map.class, ready.getPayload());
        assertEquals("demo_ext", readyPayload.get("extension_name"));
        assertEquals(Map.of("rails", 1, "tools", 1, "skills", 1), readyPayload.get("components_summary"));
        assertTrue(String.valueOf(readyPayload.get("runtime_extensions")).contains("existing_ext"));

        OutputSchema interaction = assertInstanceOf(OutputSchema.class, stream.next());
        Map<?, ?> interactionPayload = assertInstanceOf(Map.class, interaction.getPayload());
        assertEquals("__interaction__", interaction.getType());
        assertEquals("activate_confirm", interactionPayload.get("interaction_type"));
        orchestrator.resolveInteraction(
                String.valueOf(interactionPayload.get("interaction_id")),
                Map.of("action", "accept", "feedback", "go")
        );

        List<Object> rest = collect(stream);

        assertEquals(List.of(configPath.toString()), agent.getPendingHarnessConfigs());
        assertTrue(String.valueOf(guideInputs.get().get("query")).contains("扩展 demo_ext 已热加载到 deep agent"));
        assertTrue(rest.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .anyMatch(output -> "activate_testing_guide".equals(output.getType())
                        && String.valueOf(output.getPayload()).contains("Guide text")));
        StageResult result = assertInstanceOf(StageResult.class, rest.get(rest.size() - 1));
        assertEquals("success", result.getStatus());
        ActivateDecision decision = assertInstanceOf(
                ActivateDecision.class,
                result.getArtifacts().get("activate_decision")
        );
        assertEquals("accept", decision.getAction());
        assertEquals("go", decision.getFeedback());
    }

    @Test
    void rejectRemovesRuntimeAndReturnsFailedResult(@TempDir Path tempDir) throws Exception {
        Path runtimePath = tempDir.resolve("reject_ext");
        Files.createDirectories(runtimePath);
        Files.writeString(runtimePath.resolve("payload.txt"), "generated");
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, new DeepAgent());
        TaskContext ctx = context(orchestrator);
        ctx.putArtifact("runtime_extension", RuntimeExtensionArtifact.builder()
                .extensionName("reject_ext")
                .runtimePath(runtimePath.toString())
                .configPath(runtimePath.resolve("harness_config.yaml").toString())
                .build());

        Iterator<Object> stream = new ExtendActivateStage().stream(ctx);
        stream.next();
        OutputSchema interaction = assertInstanceOf(OutputSchema.class, stream.next());
        Map<?, ?> interactionPayload = assertInstanceOf(Map.class, interaction.getPayload());
        orchestrator.resolveInteraction(
                String.valueOf(interactionPayload.get("interaction_id")),
                Map.of("action", "reject")
        );

        List<Object> rest = collect(stream);

        assertFalse(Files.exists(runtimePath));
        StageResult result = assertInstanceOf(StageResult.class, rest.get(0));
        assertEquals("failed", result.getStatus());
        assertEquals("用户拒绝扩展", result.getError());
    }

    private static TaskContext context(AutoHarnessOrchestrator orchestrator) {
        return new TaskContext(
                orchestrator,
                OptimizationTask.builder().topic("task-1").build(),
                new TaskRuntime()
        );
    }

    private static Path createRuntimeExtension(Path tempDir, String name) throws IOException {
        Path runtimePath = tempDir.resolve(name);
        Files.createDirectories(runtimePath.resolve("skills").resolve("skill_one"));
        Files.writeString(runtimePath.resolve("skills").resolve("skill_one").resolve("SKILL.md"), "# skill\n");
        Files.writeString(runtimePath.resolve("harness_config.yaml"), """
                schema_version: harness_config.v0.1
                resources:
                  rails:
                    - type: package
                      module: openjiuwen.extensions.harness.demo_ext.rails
                      class: DemoRail
                  tools:
                    - type: package
                      name: demo_tool
                  skills:
                    dirs:
                      - skills
                """);
        return runtimePath;
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }
}
