/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.config;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts legacy {@link com.openjiuwen.harness.schema.DeepAgentConfig} into the
 * runtime {@link DeepAgentConfig} used by {@code deep_agent.DeepAgent}.
 */
public final class DeepAgentConfigConverter {

    private DeepAgentConfigConverter() {
    }

    /**
     * Convert a legacy schema config into a mutable runtime config.
     *
     * @param legacy legacy schema config (may be null)
     * @return runtime config
     */
    public static DeepAgentConfig toRuntime(com.openjiuwen.harness.schema.DeepAgentConfig legacy) {
        if (legacy == null) {
            return DeepAgentConfig.builder().build();
        }
        DeepAgentConfig runtime = DeepAgentConfig.builder().build();
        applyLegacy(runtime, legacy);
        return runtime;
    }

    /**
     * Copy legacy fields onto an existing runtime config instance.
     *
     * @param target mutable runtime config
     * @param legacy legacy schema config
     */
    public static void applyLegacy(DeepAgentConfig target, com.openjiuwen.harness.schema.DeepAgentConfig legacy) {
        if (target == null || legacy == null) {
            return;
        }
        target.setModel(legacy.getModel());
        target.setSystemPrompt(legacy.getSystemPrompt() == null ? "" : legacy.getSystemPrompt());
        target.setMaxIterations(legacy.getMaxIterations());
        target.setTaskLoopEnabled(legacy.isEnableTaskLoop());
        target.setTaskPlanningEnabled(legacy.isEnablePlanMode());
        target.setLanguage(legacy.getLanguage());
        target.setDefaultMode(legacy.getDefaultMode() == null
                ? com.openjiuwen.harness.schema.AgentMode.NORMAL
                : legacy.getDefaultMode());
        target.setWorkspacePath(resolveWorkspacePath(legacy.getWorkspace()));
        target.setAutoCreateWorkspace(legacy.isAutoCreateWorkspace());
        target.setCompletionTimeout(legacy.getCompletionTimeout());
        target.setBackend(legacy.getBackend());
        target.setPromptMode(legacy.getPromptMode());
        target.setEnableSkillDiscovery(legacy.isEnableSkillDiscovery());
        target.setAsyncSubagentEnabled(legacy.isEnableAsyncSubagent());
        target.setGeneralPurposeAgentEnabled(legacy.isAddGeneralPurposeAgent());
        if (legacy.getSysOperation() instanceof SysOperation sysOperation) {
            target.setSysOperation(sysOperation);
        }
        target.setPermissionHost(legacy.getPermissionHost());
        if (legacy.getPermissions() != null) {
            Map<String, Object> permissions = new LinkedHashMap<>();
            permissions.put("enabled", true);
            permissions.put("section", legacy.getPermissions());
            target.setPermissions(permissions);
        }

        List<Object> tools = new ArrayList<>();
        if (legacy.getTools() != null) {
            for (Tool tool : legacy.getTools()) {
                tools.add(tool);
            }
        }
        target.setTools(tools);

        List<Object> rails = new ArrayList<>();
        if (legacy.getRails() != null) {
            rails.addAll(legacy.getRails());
        }
        target.setRails(rails);

        List<McpServerConfig> mcps = new ArrayList<>();
        if (legacy.getMcps() != null) {
            for (Object mcp : legacy.getMcps()) {
                if (mcp instanceof McpServerConfig mcpServerConfig) {
                    mcps.add(mcpServerConfig);
                }
            }
        }
        target.setMcps(mcps);

        // Keep legacy SubAgentConfig instances so callers can assert identity / Map keys.
        List<Object> subagents = new ArrayList<>();
        if (legacy.getSubagents() != null) {
            for (Map.Entry<String, com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig> entry
                    : legacy.getSubagents().entrySet()) {
                com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig spec = entry.getValue();
                if (spec != null && (spec.getName() == null || spec.getName().isBlank())
                        && entry.getKey() != null && !entry.getKey().isBlank()) {
                    spec.setName(entry.getKey());
                }
                subagents.add(spec);
            }
        }
        target.setSubagents(subagents);

        List<String> skills = new ArrayList<>();
        Object skillsObj = legacy.getSkills();
        if (skillsObj instanceof CharSequence text && !text.toString().isBlank()) {
            skills.add(text.toString());
        } else if (skillsObj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    skills.add(String.valueOf(item));
                }
            }
        }
        target.setSkills(skills);
        target.setSkillDirectories(new ArrayList<>(skills));
        target.setEnableReadImageMultimodal(legacy.isEnableReadImageMultimodal());
    }

    /**
     * Convert a legacy nested subagent spec into a runtime {@link SubAgentConfig}.
     *
     * @param legacy legacy nested spec
     * @param fallbackName map key / requested type when card name is blank
     * @return runtime subagent config
     */
    public static SubAgentConfig toRuntimeSubagent(
            com.openjiuwen.harness.schema.DeepAgentConfig.SubAgentConfig legacy,
            String fallbackName
    ) {
        if (legacy == null) {
            return SubAgentConfig.builder().build();
        }
        AgentCard card = legacy.getCard();
        if (card == null) {
            String name = legacy.getName() != null && !legacy.getName().isBlank()
                    ? legacy.getName()
                    : (fallbackName == null || fallbackName.isBlank() ? "subagent" : fallbackName);
            card = new AgentCard(name, name, legacy.getDescription() == null ? name : legacy.getDescription());
        } else if ((card.getName() == null || card.getName().isBlank())
                && fallbackName != null && !fallbackName.isBlank()) {
            card.setName(fallbackName);
            if (card.getId() == null || card.getId().isBlank()) {
                card.setId(fallbackName);
            }
        }

        SubAgentConfig.SubAgentConfigBuilder builder = SubAgentConfig.builder()
                .agentCard(card)
                .systemPrompt(legacy.getSystemPrompt() == null ? "" : legacy.getSystemPrompt())
                .language(legacy.getLanguage() == null ? "cn" : legacy.getLanguage())
                .maxIterations(legacy.getMaxIterations() == null ? 15 : legacy.getMaxIterations())
                .isTaskLoopEnabled(legacy.isEnableTaskLoop())
                .factoryName(legacy.getFactoryName() == null ? "" : legacy.getFactoryName())
                .factoryKwargs(legacy.getFactoryKwargs() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(legacy.getFactoryKwargs()))
                .metadata(legacy.getMetadata() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(legacy.getMetadata()))
                .model(legacy.getModel())
                .backend(legacy.getBackend())
                .promptMode(legacy.getPromptMode())
                .isRestrictToWorkDir(legacy.isRestrictToWorkDir())
                .workspacePath(resolveWorkspacePath(legacy.getWorkspace()));

        if (legacy.getTools() != null) {
            builder.tools(new ArrayList<>(legacy.getTools()));
        }
        if (legacy.getRails() != null) {
            builder.rails(new ArrayList<>(legacy.getRails()));
        }
        if (legacy.getSkills() != null) {
            List<String> skillDirs = new ArrayList<>();
            for (Object skill : legacy.getSkills()) {
                if (skill != null) {
                    skillDirs.add(String.valueOf(skill));
                }
            }
            builder.skills(skillDirs);
            builder.skillDirectories(new ArrayList<>(skillDirs));
        }
        if (legacy.getSysOperation() instanceof SysOperation sysOperation) {
            builder.sysOperation(sysOperation);
        }
        if (legacy.getMcps() != null) {
            List<McpServerConfig> mcps = new ArrayList<>();
            for (Object mcp : legacy.getMcps()) {
                if (mcp instanceof McpServerConfig typed) {
                    mcps.add(typed);
                }
            }
            builder.mcps(mcps);
        }
        if (legacy.getConfig() != null) {
            DeepAgentConfig nested = toRuntime(legacy.getConfig());
            if (nested.getTools() != null && !nested.getTools().isEmpty()) {
                builder.tools(new ArrayList<>(nested.getTools()));
            }
            if (nested.getRails() != null && !nested.getRails().isEmpty()) {
                builder.rails(new ArrayList<>(nested.getRails()));
            }
            if (nested.getModel() != null) {
                builder.model(nested.getModel());
            }
            if (nested.getBackend() != null) {
                builder.backend(nested.getBackend());
            }
            if (nested.getSysOperation() != null) {
                builder.sysOperation(nested.getSysOperation());
            }
            if (nested.getWorkspacePath() != null && !nested.getWorkspacePath().isBlank()) {
                builder.workspacePath(nested.getWorkspacePath());
            }
            builder.systemPrompt(nested.getSystemPrompt());
            builder.maxIterations(nested.getMaxIterations());
            builder.isTaskLoopEnabled(nested.isEnableTaskLoop());
            builder.language(nested.getLanguage() == null ? "cn" : nested.getLanguage());
            builder.promptMode(nested.getPromptMode());
        }
        return builder.build();
    }

    private static String resolveWorkspacePath(Object workspace) {
        if (workspace == null) {
            // Preserve explicit null so ContextAssembleRail can drop workspace/context sections.
            return null;
        }
        if (workspace instanceof Workspace typed) {
            return typed.root().toString();
        }
        if (workspace instanceof Path path) {
            return path.toString();
        }
        if (workspace instanceof CharSequence text && !text.toString().isBlank()) {
            return text.toString();
        }
        return DeepAgentConfig.DEFAULT_WORKSPACE_PATH;
    }
}
