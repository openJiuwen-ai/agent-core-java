/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import java.util.List;

/**
 * Package bridge for harness config exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.harness_config} module in
 * {@code openjiuwen/harness/harness_config/__init__.py}.</p>
 */
public final class HarnessConfigPackage {

    public static final String PYTHON_MODULE = "openjiuwen/harness/harness_config/__init__.py";
    public static final Class<HarnessConfig> HARNESS_CONFIG = HarnessConfig.class;
    public static final Class<HarnessConfigBuilder> HARNESS_CONFIG_BUILDER = HarnessConfigBuilder.class;
    public static final Class<HarnessConfigInfo> HARNESS_CONFIG_INFO = HarnessConfigInfo.class;
    public static final Class<HarnessConfigLoader> HARNESS_CONFIG_LOADER = HarnessConfigLoader.class;
    public static final Class<HarnessConfigRegistry> HARNESS_CONFIG_REGISTRY = HarnessConfigRegistry.class;
    public static final Class<?> RESOLVED_FILE_SECTION = ResolvedFileSection.class;
    public static final Class<ResolvedHarnessConfig> RESOLVED_HARNESS_CONFIG = ResolvedHarnessConfig.class;
    public static final Class<?> RESOLVED_SECTION = ResolvedSection.class;
    public static final String GENERATE_HARNESS_CONFIG_YAML = "generate_harness_config_yaml";

    public static final List<String> ALL = List.of(
            "HarnessConfig",
            "HarnessConfigBuilder",
            "HarnessConfigInfo",
            "HarnessConfigLoader",
            "HarnessConfigRegistry",
            "ResolvedFileSection",
            "ResolvedHarnessConfig",
            "ResolvedSection",
            "generate_harness_config_yaml"
    );

    private HarnessConfigPackage() {
    }

    public static List<Object> exports() {
        return List.of(
                HarnessConfig.class,
                HarnessConfigBuilder.class,
                HarnessConfigInfo.class,
                HarnessConfigLoader.class,
                HarnessConfigRegistry.class,
                ResolvedFileSection.class,
                ResolvedHarnessConfig.class,
                ResolvedSection.class,
                GENERATE_HARNESS_CONFIG_YAML
        );
    }

    public static Object getAttribute(String name) {
        return switch (name) {
            case "HarnessConfig" -> HarnessConfig.class;
            case "HarnessConfigBuilder" -> HarnessConfigBuilder.class;
            case "HarnessConfigInfo" -> HarnessConfigInfo.class;
            case "HarnessConfigLoader" -> HarnessConfigLoader.class;
            case "HarnessConfigRegistry" -> HarnessConfigRegistry.class;
            case "ResolvedFileSection" -> ResolvedFileSection.class;
            case "ResolvedHarnessConfig" -> ResolvedHarnessConfig.class;
            case "ResolvedSection" -> ResolvedSection.class;
            case "generate_harness_config_yaml" -> GENERATE_HARNESS_CONFIG_YAML;
            default -> throw new IllegalArgumentException(
                    "module 'openjiuwen.harness.harness_config' has no attribute '" + name + "'"
            );
        };
    }

    public static String generateHarnessConfigYaml() {
        return HarnessConfigBuilder.generateHarnessConfigYaml();
    }
}
