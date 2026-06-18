/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CodeChangeArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionBuildArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.stages.ExtendImplementStage;
import com.openjiuwen.auto_harness.stages.ImplementStage;
import com.openjiuwen.auto_harness.stages.MetaImplementStage;
import com.openjiuwen.auto_harness.stages.PromoteRuntime;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.DeepAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's implement-stage helpers in
 * {@code openjiuwen/auto_harness/stages/implement.py}.</p>
 */
class TestImplementStage {

    @TempDir
    private Path tempDir;

    @Test
    void promptAndEvaluatorHelpersMatchPythonShape() {
        OptimizationTask task = OptimizationTask.builder()
                .topic("topic")
                .description("desc")
                .files(List.of("openjiuwen/core/foo.py"))
                .build();
        Experience exp = Experience.builder()
                .type(ExperienceType.FAILURE)
                .topic("lint")
                .summary("ruff failed")
                .build();

        String prompt = ImplementStage.buildImplementPrompt(task, List.of(exp));

        assertThat(prompt).contains("任务: topic");
        assertThat(prompt).contains("- [failure] lint: ruff failed");
        assertThat(prompt).contains("本轮实现阶段允许改动的路径");
        assertThat(prompt).contains("严禁执行 git add、git commit");
        assertThat(ImplementStage.buildPromptDebugStats(prompt)).containsKeys("chars", "lines", "bytes");
        assertThat(ImplementStage.formatCiStatusForEvaluator(Map.of()))
                .contains("结论: blocking failure")
                .contains("未执行任何门禁");
    }

    @Test
    void extractRepoEditCandidatesFiltersPreexistingAndOutOfScope() {
        List<String> candidates = ImplementStage.extractRepoEditCandidates(
                " M openjiuwen/core/foo.py\n?? docs/zh/guide.md\nR  old.py -> tests/unit_tests/test_foo.py\n M README.md",
                List.of("openjiuwen/core/foo.py", "openjiuwen/auto_harness/stages/implement.py"),
                List.of("docs/zh/guide.md")
        );

        assertThat(candidates).containsExactly("openjiuwen/core/foo.py", "tests/unit_tests/test_foo.py");
    }

    @Test
    void metaImplementStageReportsEditedFilesAfterAgentStream() {
        FakeGitOperations git = new FakeGitOperations();
        git.statusText = " M openjiuwen/core/foo.py";
        git.diffFiles = List.of("openjiuwen/core/foo.py");
        AutoHarnessOrchestrator orchestrator = orchestrator(git);
        TaskContext ctx = taskContext(orchestrator);
        ctx.getRuntime().setTaskAgent(new ScriptedAgent(List.of(Map.of("type", "agent_chunk"))));

        List<Object> events = toList(new MetaImplementStage().stream(ctx));

        assertThat(events).hasSize(3);
        assertThat(events.get(1)).isEqualTo(Map.of("type", "agent_chunk"));
        StageResult result = (StageResult) events.get(2);
        assertThat(result.getStatus()).isEqualTo("success");
        CodeChangeArtifact artifact = (CodeChangeArtifact) result.getArtifacts().get("code_change");
        assertThat(artifact.getEditedFiles()).containsExactly("openjiuwen/core/foo.py");
    }

    @Test
    void metaImplementStageFailsOnControllerTaskFailedChunk() {
        AutoHarnessOrchestrator orchestrator = orchestrator(new FakeGitOperations());
        TaskContext ctx = taskContext(orchestrator);
        ctx.getRuntime().setTaskAgent(new ScriptedAgent(List.of(Map.of(
                "type", "controller_output",
                "payload", Map.of("type", "task_failed", "data", List.of(Map.of("text", "model timeout")))
        ))));

        StageResult result = (StageResult) toList(new MetaImplementStage().stream(ctx)).get(2);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).contains("model timeout");
        assertThat(ctx.getTask().getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void extendImplementStageBuildsExtensionArtifactAndScopesOutputSchema() {
        AutoHarnessOrchestrator orchestrator = orchestrator(new FakeGitOperations());
        TaskContext ctx = taskContext(orchestrator);
        ctx.getRuntime().setWtPath(tempDir.toString());
        ctx.getRuntime().setTaskAgent(new ScriptedAgent(List.of(new OutputSchema(
                "message",
                0,
                new LinkedHashMap<>(Map.of("content", "working"))
        ))));
        ExtensionDesign design = ExtensionDesign.builder()
                .extensionName("ppt_builder")
                .gapId("gap-1")
                .components(List.of("tool", "skill"))
                .build();
        ctx.putArtifact("extension_target", design);

        List<Object> events = toList(new ExtendImplementStage().stream(ctx));

        assertThat(events.get(1)).isInstanceOf(OutputSchema.class);
        assertThat(((OutputSchema) events.get(1)).getPayload()).isEqualTo(Map.of(
                "content", "working",
                "stage", "implement_ext"
        ));
        StageResult result = (StageResult) events.get(2);
        ExtensionBuildArtifact build = (ExtensionBuildArtifact) result.getArtifacts().get("extension_build");
        assertThat(build.getExtensionName()).isEqualTo("ppt_builder");
        assertThat(Files.exists(Path.of(build.getExtensionRoot()))).isTrue();
        assertThat(build.getConfigPath()).endsWith("harness_config.yaml");
    }

    @Test
    void promoteRuntimeCopiesBuildIntoSessionRuntimeDir() throws Exception {
        AutoHarnessOrchestrator orchestrator = orchestrator(new FakeGitOperations());
        TaskContext ctx = taskContext(orchestrator);
        Path source = tempDir.resolve("source-ext");
        Files.createDirectories(source);
        Files.writeString(source.resolve("harness_config.yaml"), "name: demo\n");
        Files.writeString(source.resolve("tool.py"), "print('ok')\n");
        ctx.putArtifact("extension_build", ExtensionBuildArtifact.builder()
                .extensionName("demo_ext")
                .extensionRoot(source.toString())
                .configPath(source.resolve("harness_config.yaml").toString())
                .build());

        var artifact = PromoteRuntime.promoteRuntime(ctx);

        assertThat(artifact.getExtensionName()).isEqualTo("demo_ext");
        assertThat(Files.exists(Path.of(artifact.getRuntimePath()).resolve("tool.py"))).isTrue();
        assertThat(artifact.getConfigPath()).endsWith("harness_config.yaml");
    }

    private AutoHarnessOrchestrator orchestrator(FakeGitOperations git) {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        orchestrator.setGit(git);
        return orchestrator;
    }

    private TaskContext taskContext(AutoHarnessOrchestrator orchestrator) {
        OptimizationTask task = OptimizationTask.builder()
                .topic("topic")
                .description("desc")
                .files(List.of("openjiuwen/core/foo.py"))
                .status(TaskStatus.RUNNING)
                .build();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setRelated(List.of());
        return new TaskContext(orchestrator, task, runtime);
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static final class FakeGitOperations extends GitOperations {
        private String statusText = "";
        private List<String> diffFiles = List.of();

        private FakeGitOperations() {
            super("");
        }

        @Override
        public String statusPorcelain() {
            return statusText;
        }

        @Override
        public List<String> diffNameOnly(String revision) {
            return diffFiles;
        }
    }

    private static final class ScriptedAgent extends DeepAgent {
        private final List<Object> events;

        private ScriptedAgent(List<Object> events) {
            this.events = events;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Map<String, Object>> stream(Map<String, Object> inputs) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object event : events) {
                if (event instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                } else if (event instanceof OutputSchema output) {
                    result.add(Map.of(
                            "type", output.getType(),
                            "payload", output.getPayload(),
                            "_output", output
                    ));
                }
            }
            return result.iterator();
        }
    }
}
