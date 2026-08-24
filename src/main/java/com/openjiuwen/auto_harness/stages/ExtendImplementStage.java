/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.infra.SkillSourceManager;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionBuildArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Materialize one extension design into the task worktree.
 *
 * <p>Mirrors Python's {@code ExtendImplementStage} in
 * {@code openjiuwen/auto_harness/stages/implement.py}.</p>
 */
public class ExtendImplementStage extends ImplementStage {

    private static final Logger LOGGER = Logger.getLogger(ExtendImplementStage.class.getName());

    @Override
    public String name() {
        return "implement_ext";
    }

    @Override
    public String displayName() {
        return "实现扩展";
    }

    @Override
    public List<String> consumes() {
        return List.of("extension_target");
    }

    @Override
    public List<String> produces() {
        return List.of("extension_build");
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("ExtendImplementStage requires TaskContext");
        }
        Object artifact = taskContext.requireArtifact("extension_target");
        if (!(artifact instanceof ExtensionDesign design)) {
            throw new IllegalArgumentException("extension_target artifact must be ExtensionDesign");
        }
        DeepAgent agent = taskContext.getRuntime().getTaskAgent() instanceof DeepAgent deepAgent ? deepAgent : null;
        if (agent == null) {
            String error = "No task_agent available for implement_ext stage";
            LOGGER.severe(error);
            return List.of((Object) failed(taskContext, error)).iterator();
        }
        Path extensionRoot = resolveExtensionRoot(taskContext.getRuntime().getWtPath(), design);
        Path configPath = resolveConfigPath(taskContext.getRuntime().getWtPath(), design);
        try {
            Files.createDirectories(extensionRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create extension root: " + extensionRoot, e);
        }
        copyCommunitySkillIfConfigured(taskContext, design, extensionRoot);
        String prompt = buildImplementExtPrompt(design, extensionRoot, configPath);
        List<Object> events = new ArrayList<>();
        events.add(BaseExecutionContext.message("开始实现扩展: " + design.getExtensionName()));
        Iterator<?> iterator = agent.stream(Map.of("query", prompt));
        String implementError = "";
        while (iterator.hasNext()) {
            Object chunk = restoreOutputSchemaIfPresent(iterator.next());
            Object scoped = BaseStage.scopeOutputEventStage(chunk, name());
            events.add(scoped);
            implementError = extractControllerTaskFailedError(chunk);
            if (!implementError.isEmpty()) {
                break;
            }
        }
        if (!implementError.isEmpty()) {
            events.add(failed(taskContext, implementError));
            return events.iterator();
        }
        if (!Files.exists(extensionRoot)) {
            String error = "Agent did not create extension root: " + extensionRoot;
            events.add(failed(taskContext, error));
            return events.iterator();
        }
        ExtensionBuildArtifact build = ExtensionBuildArtifact.builder()
                .extensionName(design.getExtensionName())
                .extensionRoot(extensionRoot.toAbsolutePath().normalize().toString())
                .configPath(configPath.toAbsolutePath().normalize().toString())
                .build();
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("extension_build", build);
        events.add(StageResult.builder()
                .artifacts(artifacts)
                .messages(List.of("Implemented extension: " + design.getExtensionName()))
                .build());
        return events.iterator();
    }

    public static Path resolveExtensionRoot(String wtPath, ExtensionDesign design) {
        String root = design.getFilePlan().getOrDefault(
                "root",
                "openjiuwen/extensions/harness/" + design.getExtensionName()
        );
        return Path.of(wtPath == null || wtPath.isBlank() ? "." : wtPath).resolve(root);
    }

    public static Path resolveConfigPath(String wtPath, ExtensionDesign design) {
        String manifest = design.getFilePlan().getOrDefault(
                "manifest",
                "openjiuwen/extensions/harness/" + design.getExtensionName() + "/harness_config.yaml"
        );
        return Path.of(wtPath == null || wtPath.isBlank() ? "." : wtPath).resolve(manifest);
    }

    private static void copyCommunitySkillIfConfigured(TaskContext taskContext, ExtensionDesign design, Path extensionRoot) {
        String skillSource = design.getSkillSource();
        if (skillSource == null || skillSource.isBlank()) {
            return;
        }
        String skillName = skillSource.startsWith("community:") ? skillSource.substring("community:".length()) : skillSource;
        Optional<Path> copied = SkillSourceManager.copySkillToExtension(
                skillName,
                extensionRoot,
                taskContext.getOrchestrator().getConfig()
        );
        if (copied.isEmpty()) {
            LOGGER.warning("Community skill '" + skillName + "' not found in cache, falling back to agent-generated skill");
            design.setSkillSource("");
        }
    }

    private static StageResult failed(TaskContext taskContext, String error) {
        taskContext.getTask().setStatus(TaskStatus.FAILED);
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("task_result", CycleResult.builder()
                .success(false)
                .error(error)
                .build());
        return StageResult.builder()
                .status("failed")
                .artifacts(artifacts)
                .messages(List.of(error))
                .error(error)
                .build();
    }
}
