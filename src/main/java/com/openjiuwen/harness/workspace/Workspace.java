/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Minimal workspace descriptor for the first Java harness port.
 *
 * <p>Mirrors Python's {@code Workspace} in
 * {@code openjiuwen.harness.workspace.workspace}.
 *
 * <p>Defers richer directory-schema behaviors to later migration steps.
 */
public class Workspace {

    private final String rootPath;
    private final String language;

    public Workspace() {
        this("./", "cn");
    }

    public Workspace(String rootPath, String language) {
        this.rootPath = (rootPath == null || rootPath.isBlank()) ? "./" : rootPath;
        this.language = (language == null || language.isBlank()) ? "cn" : language;
    }

    public String getRootPath() {
        return rootPath;
    }

    public String getLanguage() {
        return language;
    }

    public Path root() {
        return Paths.get(rootPath).normalize();
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return root();
        }
        return root().resolve(relativePath).normalize();
    }
}
