/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.DeepAgentFactory;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds runtime config from resolved harness config resources.
 *
 * <p>Mirrors Python's {@code HarnessConfigBuilder} and helpers in
 * {@code openjiuwen/harness/harness_config/builder.py}.</p>
 */
public final class HarnessConfigBuilder {

    private HarnessConfigBuilder() {
    }

    public static DeepAgentConfig build(ResolvedHarnessConfig resolved, Object model, Path workspaceRoot) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setModel(model);
        if (resolved != null && resolved.getConfig() != null) {
            HarnessConfig harnessConfig = resolved.getConfig();
            config.setLanguage(harnessConfig.getLanguage());
            config.setWorkspace(workspaceRoot);
            if (harnessConfig.getResources() != null) {
                config.setTools(resolveTools(harnessConfig.getResources()));
                config.setRails(resolveRails(harnessConfig.getResources()));
            }
        }
        return config;
    }

    public static String generateHarnessConfigYaml() {
        HarnessConfig config = HarnessConfig.builder()
                .id("deep-agent")
                .name("DeepAgent")
                .description("Generated DeepAgent harness config")
                .build();
        return config.toYaml();
    }

    public static List<Tool> resolveBuiltinTools(String groupName, Object sysOperation) {
        return new ArrayList<>();
    }

    public static List<Tool> resolveTools(HarnessConfig.ResourcesSchema resourcesSchema) {
        return new ArrayList<>();
    }

    public static List<DeepAgentRail> resolveRails(HarnessConfig.ResourcesSchema resourcesSchema) {
        return new ArrayList<>();
    }

    public static List<Map<String, Object>> toolsToYamlSpecs(List<Tool> tools) {
        List<Map<String, Object>> specs = new ArrayList<>();
        for (Tool tool : DeepAgentFactory.normalizeTools(tools)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "instance");
            item.put("name", tool.getCard().getName());
            item.put("class", tool.getClass().getName());
            specs.add(item);
        }
        return specs;
    }

    public static List<Map<String, Object>> railsToYamlSpecs(List<DeepAgentRail> rails) {
        List<Map<String, Object>> specs = new ArrayList<>();
        if (rails == null) {
            return specs;
        }
        for (DeepAgentRail rail : rails) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "instance");
            item.put("name", rail.getClass().getSimpleName());
            item.put("class", rail.getClass().getName());
            specs.add(item);
        }
        return specs;
    }
}
