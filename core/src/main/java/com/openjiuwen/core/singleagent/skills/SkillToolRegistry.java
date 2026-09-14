/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory registry for Skill-owned tools.
 */
public class SkillToolRegistry {
    private final Map<String, SkillToolBinding> bindings = new LinkedHashMap<>();

    public synchronized void register(SkillToolBinding binding) {
        if (binding == null) {
            return;
        }
        mergeBinding(bindings, binding);
    }

    private void mergeBinding(Map<String, SkillToolBinding> target, SkillToolBinding binding) {
        SkillToolBinding existing = target.get(binding.getSkillName());
        if (existing == null) {
            target.put(binding.getSkillName(), binding);
            return;
        }
        Set<String> existingToolNames = new LinkedHashSet<>();
        for (Tool tool : existing.getTools()) {
            existingToolNames.add(SkillToolBinding.toolName(tool));
        }
        for (Tool tool : binding.getTools()) {
            String toolName = SkillToolBinding.toolName(tool);
            if (existingToolNames.contains(toolName)) {
                throw SkillToolBinding.duplicateToolName(binding.getSkillName(), toolName);
            }
        }
        List<Tool> mergedTools = new ArrayList<>(existing.getTools());
        mergedTools.addAll(binding.getTools());
        target.put(binding.getSkillName(), SkillToolBinding.builder()
                .skillName(binding.getSkillName())
                .tools(mergedTools)
                .build());
    }

    public synchronized void registerAll(List<SkillToolBinding> bindings) {
        if (bindings == null) {
            return;
        }
        Map<String, SkillToolBinding> updated = new LinkedHashMap<>(this.bindings);
        for (SkillToolBinding binding : bindings) {
            if (binding != null) {
                mergeBinding(updated, binding);
            }
        }
        this.bindings.clear();
        this.bindings.putAll(updated);
    }

    public synchronized boolean hasSkill(String skillName) {
        return bindings.containsKey(skillName);
    }

    public synchronized List<Tool> listToolsForActiveSkills(List<String> activeSkillNames) {
        List<Tool> result = new ArrayList<>();
        for (List<Tool> tools : listToolsByActiveSkill(activeSkillNames).values()) {
            result.addAll(tools);
        }
        return List.copyOf(result);
    }

    public synchronized Map<String, List<Tool>> listToolsByActiveSkill(List<String> activeSkillNames) {
        Map<String, List<Tool>> result = new LinkedHashMap<>();
        for (String skillName : orderedActiveSkillNames(activeSkillNames)) {
            SkillToolBinding binding = bindings.get(skillName);
            if (binding != null) {
                result.put(skillName, binding.getTools());
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    public synchronized Optional<Tool> findToolForActiveSkills(String toolName, List<String> activeSkillNames) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        for (Tool tool : listToolsForActiveSkills(activeSkillNames)) {
            if (toolName.equals(SkillToolBinding.toolName(tool))) {
                return Optional.of(tool);
            }
        }
        return Optional.empty();
    }

    private static List<String> orderedActiveSkillNames(List<String> activeSkillNames) {
        if (activeSkillNames == null || activeSkillNames.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String skillName : activeSkillNames) {
            if (skillName != null && !skillName.isBlank()) {
                normalized.add(skillName);
            }
        }
        return List.copyOf(normalized);
    }
}
