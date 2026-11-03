/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Metadata for a registered harness config.
 *
 * <p>Mirrors Python's {@code HarnessConfigInfo} in
 * {@code openjiuwen/harness/harness_config/registry.py}.</p>
 */
public class HarnessConfigInfo {

    private String id;
    private String name;
    private String version;
    private String packageName;
    private Path configPath;
    private boolean enabled = true;

    public HarnessConfigInfo() {
    }

    public HarnessConfigInfo(String id, String name) {
        this(id, name, null, null, null, true);
    }

    public HarnessConfigInfo(String id, String name, String version, String packageName, Path configPath) {
        this(id, name, version, packageName, configPath, true);
    }

    public HarnessConfigInfo(String id,
                             String name,
                             String version,
                             String packageName,
                             Path configPath,
                             boolean enabled) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name == null || name.isBlank() ? id : name;
        this.version = version;
        this.packageName = packageName;
        this.configPath = configPath;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? id : name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public void setConfigPath(Path configPath) {
        this.configPath = configPath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
