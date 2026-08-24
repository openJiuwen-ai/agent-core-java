/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.deepagents.DeepAgentsFactory;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
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

    /**
     * Python {@code _BUILTIN_RAIL_REGISTRY} currently only registers task_planning.
     */
    private static final Map<String, Class<? extends DeepAgentRail>> BUILTIN_RAIL_REGISTRY = Map.of(
            "task_planning", TaskPlanningRail.class
    );

    private HarnessConfigBuilder() {
    }

    public static DeepAgentConfig build(ResolvedHarnessConfig resolved, Object model, Path workspaceRoot) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setModel(model);
        if (resolved != null) {
            if (resolved.getSystemPrompt() != null) {
                config.setSystemPrompt(resolved.getSystemPrompt());
            }
            if (resolved.getConfig() != null) {
                HarnessConfig harnessConfig = resolved.getConfig();
                config.setLanguage(harnessConfig.getLanguage());
                config.setWorkspace(workspaceRoot);
                if (harnessConfig.getName() != null && !String.valueOf(harnessConfig.getName()).isBlank()) {
                    String agentName = String.valueOf(harnessConfig.getName());
                    config.setCard(new com.openjiuwen.core.singleagent.schema.AgentCard(
                            agentName, agentName, agentName));
                }
                if ((config.getSystemPrompt() == null || config.getSystemPrompt().isBlank())
                        && harnessConfig.getPrompts() != null
                        && harnessConfig.getPrompts().getSections() != null) {
                    for (HarnessConfig.SectionSchema section : harnessConfig.getPrompts().getSections()) {
                        Object raw = section == null ? null : section.getContent();
                        if (raw instanceof CharSequence text && !text.toString().isBlank()) {
                            config.setSystemPrompt(text.toString());
                            break;
                        }
                    }
                }
                if (harnessConfig.getResources() != null) {
                    config.setTools(resolveTools(harnessConfig.getResources()));
                    config.setRails(resolveRails(harnessConfig.getResources()));
                }
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
        List<DeepAgentRail> rails = new ArrayList<>();
        if (resourcesSchema == null || resourcesSchema.getRails() == null) {
            return rails;
        }
        for (HarnessConfig.RailResourceSchema spec : resourcesSchema.getRails()) {
            if (spec == null || spec.getType() == null) {
                continue;
            }
            switch (spec.getType()) {
                case "builtin" -> rails.add(instantiateBuiltinRail(spec.getName()));
                case "package" -> rails.add(instantiatePackageRail(spec.getModule(), spec.getClassName()));
                case "entry_point" -> throw new IllegalArgumentException(
                        "Entry point rails are not supported: '" + spec.getName() + "'");
                default -> throw new IllegalArgumentException("Unknown rail resource type: '" + spec.getType() + "'");
            }
        }
        return rails;
    }

    private static DeepAgentRail instantiateBuiltinRail(String name) {
        Class<? extends DeepAgentRail> railClass = BUILTIN_RAIL_REGISTRY.get(name == null ? "" : name);
        if (railClass == null) {
            throw new IllegalArgumentException(
                    "Unknown builtin rail: '" + name + "'. Valid names: " + BUILTIN_RAIL_REGISTRY.keySet().stream().sorted().toList());
        }
        return instantiateRail(railClass);
    }

    private static DeepAgentRail instantiatePackageRail(String module, String className) {
        if (module == null || module.isBlank() || className == null || className.isBlank()) {
            throw new IllegalArgumentException("package rail requires module and class");
        }
        String dotted = module + "." + className;
        try {
            Class<?> loaded = Class.forName(dotted);
            if (!DeepAgentRail.class.isAssignableFrom(loaded)) {
                throw new IllegalArgumentException("Cannot load '" + dotted + "': not a DeepAgentRail");
            }
            @SuppressWarnings("unchecked")
            Class<? extends DeepAgentRail> railClass = (Class<? extends DeepAgentRail>) loaded;
            return instantiateRail(railClass);
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Cannot load '" + dotted + "': " + ex.getMessage(), ex);
        }
    }

    private static DeepAgentRail instantiateRail(Class<? extends DeepAgentRail> railClass) {
        try {
            return railClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot instantiate '" + railClass.getName() + "': " + ex.getMessage(), ex);
        }
    }

    public static List<Map<String, Object>> toolsToYamlSpecs(List<Tool> tools) {
        List<Map<String, Object>> specs = new ArrayList<>();
        for (Tool tool : DeepAgentsFactory.normalizeTools(tools)) {
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
            String builtinName = builtinNameOf(rail);
            if (builtinName != null) {
                item.put("type", "builtin");
                item.put("name", builtinName);
            } else {
                item.put("type", "package");
                item.put("module", rail.getClass().getPackageName());
                item.put("class", rail.getClass().getSimpleName());
            }
            specs.add(item);
        }
        return specs;
    }

    private static String builtinNameOf(DeepAgentRail rail) {
        if (rail == null) {
            return null;
        }
        for (Map.Entry<String, Class<? extends DeepAgentRail>> entry : BUILTIN_RAIL_REGISTRY.entrySet()) {
            if (entry.getValue().equals(rail.getClass())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
