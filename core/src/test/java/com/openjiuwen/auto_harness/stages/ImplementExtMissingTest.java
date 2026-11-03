/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionBuildArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's implement_ext missing tests in
 * {@code tests/unit_tests/auto_harness/stages/test_implement_ext.py}.
 */
class ImplementExtMissingTest {

    @TempDir
    private Path tempDir;

    @Test
    void testImplementExtPromptRespectsDeclaredComponents() {
        ExtensionDesign design = buildDesign();
        design.setComponents(List.of("rail"));

        String prompt = ImplementStage.buildImplementExtPrompt(
                design,
                tempDir.resolve("demo_ext"),
                tempDir.resolve("demo_ext").resolve("harness_config.yaml")
        );

        assertThat(prompt).contains("ExtensionDesign.components");
        assertThat(prompt).contains("rail");
        assertThat(prompt).doesNotContain("Tool + Skill cooperation");
        assertThat(prompt).doesNotContain("ToolCard.description");
    }

    @Test
    void testImplementExtPromptGuidesToolSkillPptExtension() {
        ExtensionDesign design = buildDesign();
        design.setExtensionName("huawei_ppt_generator");
        design.setComponents(List.of("tool", "skill"));
        design.setFilePlan(new LinkedHashMap<>(Map.of(
                "root", "openjiuwen/extensions/harness/huawei_ppt_generator",
                "manifest", "openjiuwen/extensions/harness/huawei_ppt_generator/harness_config.yaml"
        )));

        String prompt = ImplementStage.buildImplementExtPrompt(
                design,
                tempDir.resolve("huawei_ppt_generator"),
                tempDir.resolve("huawei_ppt_generator").resolve("harness_config.yaml")
        );

        assertThat(prompt).contains("huawei_ppt_generator");
        assertThat(prompt).contains("tool");
        assertThat(prompt).contains("skill-creator");
        assertThat(prompt).contains("assets/");
        assertThat(prompt).contains("references/");
        assertThat(prompt).contains("zipfile");
        assertThat(prompt).contains("ppt/presentation.xml");
        assertThat(prompt).contains("requirements collection");
        assertThat(prompt).contains("module and class");
        assertThat(prompt).contains("openjiuwen.extensions.harness.<extension_name>.");
    }

    @Test
    void testImplementExtWritesExtensionScaffold() throws Exception {
        TaskContext ctx = makeTaskContext(true);
        ctx.putArtifact("extension_target", buildDesign());

        StageResult result = lastStageResult(toList(new ExtendImplementStage().stream(ctx)));

        assertThat(result.getError()).isBlank();
        ExtensionBuildArtifact build = (ExtensionBuildArtifact) result.getArtifacts().get("extension_build");
        assertThat(build).isNotNull();
        assertThat(Path.of(build.getExtensionRoot())).isDirectory();
        assertThat(Path.of(build.getConfigPath())).isRegularFile();
    }

    @Test
    void testImplementExtFailsWithoutAgent() throws Exception {
        TaskContext ctx = makeTaskContext(false);
        ctx.putArtifact("extension_target", buildDesign());
        Logger logger = Logger.getLogger(ExtendImplementStage.class.getName());
        Level previousLevel = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();

        StageResult result;
        try {
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.OFF);
            result = lastStageResult(toList(new ExtendImplementStage().stream(ctx)));
        } finally {
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }

        assertThat(result.getError()).contains("No task_agent");
        assertThat(ctx.getTask().getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    @Test
    void testImplementExtScopesNestedAgentStageEvents() throws Exception {
        TaskContext ctx = makeTaskContext(true);
        ctx.getRuntime().setTaskAgent(new StageResultAgent(Path.of(ctx.getRuntime().getWtPath())));
        ctx.putArtifact("extension_target", buildDesign());

        List<Object> events = toList(new ExtendImplementStage().stream(ctx));

        OutputSchema scoped = (OutputSchema) events.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .filter(item -> "stage_result".equals(item.getType()))
                .findFirst()
                .orElseThrow();
        assertThat(((Map<?, ?>) scoped.getPayload()).get("stage")).isEqualTo("implement_ext");
    }

    private TaskContext makeTaskContext(boolean withAgent) throws IOException {
        Path wtPath = tempDir.resolve("wt");
        Files.createDirectories(wtPath);
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .build();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setWtPath(wtPath.toString());
        if (withAgent) {
            runtime.setTaskAgent(new ScaffoldAgent(wtPath));
        }
        return new TaskContext(
                new AutoHarnessOrchestrator(config),
                OptimizationTask.builder().topic("implement ext").build(),
                runtime
        );
    }

    private static ExtensionDesign buildDesign() {
        Map<String, String> filePlan = new LinkedHashMap<>();
        filePlan.put("root", "openjiuwen/extensions/harness/demo_ext");
        filePlan.put("manifest", "openjiuwen/extensions/harness/demo_ext/harness_config.yaml");
        return ExtensionDesign.builder()
                .gapId("gap_1")
                .extensionName("demo_ext")
                .filePlan(filePlan)
                .harnessConfigPatch(Map.of(
                        "resources", Map.of(
                                "rails", List.of(Map.of(
                                        "type", "package",
                                        "module", "openjiuwen.extensions.harness.demo_ext.rails.extension_rail",
                                        "class", "ExtensionRail"
                                )),
                                "tools", List.of(Map.of(
                                        "type", "package",
                                        "module", "openjiuwen.extensions.harness.demo_ext.tools.extension_tool",
                                        "class", "ExtensionTool"
                                ))
                        )
                ))
                .build();
    }

    private static void writeScaffold(Path wtPath) {
        Path ext = wtPath.resolve("openjiuwen")
                .resolve("extensions")
                .resolve("harness")
                .resolve("demo_ext");
        try {
            Files.createDirectories(ext.resolve("rails"));
            Files.createDirectories(ext.resolve("tools"));
            Files.writeString(ext.resolve("__init__.py"), "", StandardCharsets.UTF_8);
            Files.writeString(ext.resolve("rails").resolve("__init__.py"), "", StandardCharsets.UTF_8);
            Files.writeString(ext.resolve("tools").resolve("__init__.py"), "", StandardCharsets.UTF_8);
            Files.writeString(
                    ext.resolve("rails").resolve("extension_rail.py"),
                    "class ExtensionRail: pass\n",
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    ext.resolve("tools").resolve("extension_tool.py"),
                    "class ExtensionTool: pass\n",
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    ext.resolve("tools").resolve("helper.py"),
                    "EXTENSION_NAME = 'demo_ext'\n",
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    ext.resolve("harness_config.yaml"),
                    "schema_version: harness_config.v0.1\nname: demo_ext\n",
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static StageResult lastStageResult(List<Object> events) {
        return (StageResult) events.get(events.size() - 1);
    }

    private static class ScaffoldAgent extends DeepAgent {
        private final Path wtPath;

        ScaffoldAgent(Path wtPath) {
            this.wtPath = wtPath;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            writeScaffold(wtPath);
            return (Iterator<Object>) (Iterator<?>) List.<Map<String, Object>>of().iterator();
        }
    }

    private static final class StageResultAgent extends ScaffoldAgent {
        StageResultAgent(Path wtPath) {
            super(wtPath);
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs) {
            super.stream(inputs);
            OutputSchema output = new OutputSchema(
                    "stage_result",
                    0,
                    Map.of("stage", "implement", "status", "success")
            );
            return (Iterator<Object>) (Iterator<?>) List.of(Map.of(
                    "type", output.getType(),
                    "payload", output.getPayload(),
                    "_output", output
            )).iterator();
        }
    }
}
