/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import examples.gitcode_evolver_common.infrastructure.EvolverMavenProjectVersion;

import java.nio.file.Path;

/** Resolves a trusted Maven project version without loading Maven plugins or external XML. */
final class MavenProjectVersionResolver {
    private MavenProjectVersionResolver() {
    }

    static String resolve(Path sourceWorktree) {
        try {
            return EvolverMavenProjectVersion.resolve(sourceWorktree);
        } catch (EvolverMavenProjectVersion.ProjectVersionException ex) {
            throw new ProjectVersionException(ex.getMessage(), ex);
        }
    }

    static void ensureTargetMountpoint(Path worktree) {
        try {
            EvolverMavenProjectVersion.ensureTargetMountpoint(worktree);
        } catch (EvolverMavenProjectVersion.ProjectVersionException ex) {
            throw new ProjectVersionException(ex.getMessage(), ex);
        }
    }

    /** Stable build-contract error without exposing host paths or XML content. */
    static final class ProjectVersionException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        ProjectVersionException(String message) {
            super(message);
        }

        ProjectVersionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
