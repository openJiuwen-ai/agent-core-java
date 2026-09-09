/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.deepagents.DeepAgentsFactory;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.security.ApprovalOverrideEntry;
import com.openjiuwen.harness.security.PermissionsSection;

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

    private static final String TRUSTED_CLASS_PREFIX = "com.openjiuwen.";

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
                if (harnessConfig.getPermissions() != null && !harnessConfig.getPermissions().isEmpty()) {
                    config.setPermissions(toPermissionsSection(harnessConfig.getPermissions()));
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
        if (!dotted.startsWith(TRUSTED_CLASS_PREFIX)) {
            throw new IllegalArgumentException("Rail class is not trusted: " + dotted);
        }
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

    /**
     * Project the declarative {@code permissions} map onto the typed
     * {@link PermissionsSection}; unknown keys (e.g. {@code file_guard}) ride along as
     * extensions so the section round-trips losslessly through the rail factory.
     *
     * @param permissions raw permissions map
     * @return typed permissions section
     */
    private static PermissionsSection toPermissionsSection(Map<String, Object> permissions) {
        PermissionsSection section = new PermissionsSection();
        for (Map.Entry<String, Object> entry : permissions.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "enabled" -> section.setEnabled(value instanceof Boolean isEnabled
                        ? isEnabled : Boolean.parseBoolean(String.valueOf(value)));
                case "schema" -> section.setSchema(value == null ? null : String.valueOf(value));
                case "defaults" -> {
                    if (value instanceof Map<?, ?> map) {
                        section.setDefaults(stringKeyMap(map));
                    }
                }
                case "tools" -> {
                    if (value instanceof Map<?, ?> map) {
                        section.setTools(stringKeyMap(map));
                    }
                }
                case "rules" -> {
                    if (value instanceof List<?> list) {
                        section.setRules(mapList(list));
                    }
                }
                case "approval_overrides" -> section.setApprovalOverrides(overrideEntries(value));
                case "external_directory" -> {
                    if (value instanceof Map<?, ?> map) {
                        Map<String, String> external = new LinkedHashMap<>();
                        map.forEach((k, v) -> external.put(String.valueOf(k), String.valueOf(v)));
                        section.setExternalDirectory(external);
                    }
                }
                default -> section.putExtension(key, value);
            }
        }
        return section;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static List<Map<String, Object>> mapList(List<?> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) {
            result.add(item instanceof Map<?, ?> map ? stringKeyMap(map) : null);
        }
        return result;
    }

    private static List<ApprovalOverrideEntry> overrideEntries(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<ApprovalOverrideEntry> entries = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            ApprovalOverrideEntry entry = new ApprovalOverrideEntry();
            entry.setId(map.get("id") == null ? null : String.valueOf(map.get("id")));
            entry.setTools(stringList(map.get("tools")));
            entry.setMatchType(map.get("match_type") == null ? null : String.valueOf(map.get("match_type")));
            entry.setPattern(map.get("pattern") == null ? null : String.valueOf(map.get("pattern")));
            entry.setAction(map.get("action") == null ? null : String.valueOf(map.get("action")));
            entries.add(entry);
        }
        return entries;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof String text) {
            return List.of(text);
        }
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }
}
