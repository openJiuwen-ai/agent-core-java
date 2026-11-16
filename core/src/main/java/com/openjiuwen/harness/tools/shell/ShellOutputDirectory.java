/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell;

import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Resolves shell output artifact directories that remain readable by the bound
 * local filesystem operation without widening its sandbox permissions.
 *
 * @since 0.1.17
 */
public final class ShellOutputDirectory {
    private ShellOutputDirectory() {
    }

    /**
     * Resolve storage inside an allowed root for restricted local operations.
     *
     * @param operation filesystem operation used to read the output, or null
     * @param legacyDirectory original temporary output directory
     * @return an allowed artifact directory, or the original directory for other operation modes
     */
    public static Path resolve(SysOperation operation, Path legacyDirectory) {
        if (operation == null || operation.getMode() != OperationMode.LOCAL
                || !(operation.getRunConfig() instanceof LocalWorkConfig config) || !config.isRestrictToSandbox()) {
            return legacyDirectory;
        }
        List<Path> configuredRoots = config.getSandboxRoot() == null
                ? List.of()
                : config.getSandboxRoot().stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize())
                .toList();
        List<Path> roots = configuredRoots.isEmpty()
                ? Stream.of(Cwd.getWorkspace(), Cwd.getProjectRoot())
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize())
                .toList()
                : configuredRoots;
        List<Path> candidates = new ArrayList<>();
        Stream.of(config.getWorkDir(), Cwd.getWorkspace(), Cwd.getCwd(), Cwd.getProjectRoot(), Cwd.getTenantRoot())
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize())
                .forEach(candidates::add);
        candidates.addAll(roots);
        Path root = candidates.stream()
                .filter(candidate -> roots.stream().anyMatch(candidate::startsWith))
                .filter(Cwd::isWithinTenantRoot)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No allowed root for shell output artifacts"));
        return root.resolve(".openjiuwen").resolve(legacyDirectory.getFileName());
    }
}
