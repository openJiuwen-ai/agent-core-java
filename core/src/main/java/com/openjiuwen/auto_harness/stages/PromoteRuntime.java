/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionBuildArtifact;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * Runtime-extension promotion helper.
 *
 * <p>Mirrors Python's {@code promote_runtime} in
 * {@code openjiuwen/auto_harness/stages/implement.py}.</p>
 */
public final class PromoteRuntime {

    private PromoteRuntime() {
    }

    public static RuntimeExtensionArtifact promoteRuntime(TaskContext ctx) {
        Object artifact = ctx.requireArtifact("extension_build");
        if (!(artifact instanceof ExtensionBuildArtifact build)) {
            throw new IllegalArgumentException("extension_build artifact must be ExtensionBuildArtifact");
        }
        Path sessionRoot = ctx.getOrchestrator().ensureSessionRuntimeDir();
        Path destination = sessionRoot.resolve(build.getExtensionName());
        try {
            deleteTree(destination);
            copyTree(Path.of(build.getExtensionRoot()), destination);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to promote runtime extension: " + build.getExtensionName(), e);
        }
        Path configPath = destination.resolve("harness_config.yaml").toAbsolutePath().normalize();
        return RuntimeExtensionArtifact.builder()
                .extensionName(build.getExtensionName())
                .runtimePath(destination.toAbsolutePath().normalize().toString())
                .configPath(configPath.toString())
                .build();
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path dest = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
