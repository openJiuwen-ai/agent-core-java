/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.toolgroup;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry and stateless filter for named tool groups.
 *
 * @since 0.1.17
 */
public final class ToolGroupManager {

    private static final String SKILL_GROUP_PREFIX = "skill:";

    private final Map<String, ToolGroup> toolGroupsByName =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** Registers an explicit tool group and rejects duplicate or reserved names. */
    public void registerToolGroup(ToolGroup toolGroup) {
        if (toolGroup == null) {
            throw new IllegalArgumentException("Tool group must not be null");
        }
        if (toolGroup.name() != null && toolGroup.name().startsWith(SKILL_GROUP_PREFIX)) {
            throw new IllegalArgumentException(
                    "Reserved prefix '"
                            + SKILL_GROUP_PREFIX
                            + "' cannot be used for explicit tool groups");
        }
        synchronized (toolGroupsByName) {
            if (toolGroupsByName.containsKey(toolGroup.name())) {
                throw new IllegalArgumentException(
                        "Duplicate tool group name: " + toolGroup.name());
            }
            if (toolGroup.toolNames().isEmpty()) {
                Loggers.TOOL.warning("Registered empty tool group: {}", toolGroup.name());
            }
            toolGroupsByName.put(toolGroup.name(), toolGroup);
        }
    }

    /** Registers or replaces the reserved implicit group for one Skill. */
    public void registerSkillToolGroup(ToolGroup toolGroup) {
        if (toolGroup == null) {
            throw new IllegalArgumentException("Tool group must not be null");
        }
        if (toolGroup.name() == null || !toolGroup.name().startsWith(SKILL_GROUP_PREFIX)) {
            throw new IllegalArgumentException(
                    "Skill tool group name must start with " + SKILL_GROUP_PREFIX);
        }
        synchronized (toolGroupsByName) {
            toolGroupsByName.put(toolGroup.name(), toolGroup);
        }
    }

    /** Removes a registered group; unknown names are ignored. */
    public void unregisterToolGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }
        synchronized (toolGroupsByName) {
            toolGroupsByName.remove(groupName.trim());
        }
    }

    /** Returns a registered group by its normalized name. */
    public Optional<ToolGroup> getToolGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return Optional.empty();
        }
        synchronized (toolGroupsByName) {
            return Optional.ofNullable(toolGroupsByName.get(groupName.trim()));
        }
    }

    /** Returns a stable snapshot of all registered groups. */
    public List<ToolGroup> getGroups() {
        synchronized (toolGroupsByName) {
            return List.copyOf(toolGroupsByName.values());
        }
    }

    /** Filters registered tool names using the session's active group names. */
    public List<String> getActiveToolNames(
            Collection<String> activeGroupNames, Collection<String> registeredToolNames) {
        Set<String> normalizedActiveGroupNames = normalizeNames(activeGroupNames);
        Set<String> normalizedRegisteredToolNames = normalizeNames(registeredToolNames);
        List<String> activeToolNames = new ArrayList<>();
        synchronized (toolGroupsByName) {
            Map<String, List<ToolGroup>> owningGroupsByToolName = findOwningGroupsByToolName();
            for (ToolGroup toolGroup : toolGroupsByName.values()) {
                for (String toolName : toolGroup.toolNames()) {
                    owningGroupsByToolName
                            .computeIfAbsent(toolName, ignored -> new ArrayList<>())
                            .add(toolGroup);
                }
            }
            for (String toolName : normalizedRegisteredToolNames) {
                if (isToolEligible(
                        toolName,
                        owningGroupsByToolName.get(toolName),
                        normalizedActiveGroupNames)) {
                    activeToolNames.add(toolName);
                }
            }
        }
        return activeToolNames.stream().distinct().sorted().toList();
    }

    /** Determines whether a tool is eligible for direct execution in this session. */
    public boolean isToolEligibleForExecution(
            String toolName, Collection<String> activeGroupNames) {
        String normalizedToolName = toolName == null ? "" : toolName.trim();
        if (normalizedToolName.isEmpty()) {
            return false;
        }
        Set<String> normalizedActiveGroupNames = normalizeNames(activeGroupNames);
        synchronized (toolGroupsByName) {
            List<ToolGroup> owningGroups =
                    toolGroupsByName.values().stream()
                            .filter(toolGroup -> toolGroup.toolNames().contains(normalizedToolName))
                            .toList();
            return isToolEligible(normalizedToolName, owningGroups, normalizedActiveGroupNames);
        }
    }

    private Map<String, List<ToolGroup>> findOwningGroupsByToolName() {
        Map<String, List<ToolGroup>> owningGroupsByToolName = new HashMap<>();
        for (ToolGroup toolGroup : toolGroupsByName.values()) {
            for (String toolName : toolGroup.toolNames()) {
                owningGroupsByToolName
                        .computeIfAbsent(toolName, ignored -> new ArrayList<>())
                        .add(toolGroup);
            }
        }
        return owningGroupsByToolName;
    }

    private static boolean isToolEligible(
            String toolName, List<ToolGroup> owningGroups, Set<String> activeGroupNames) {
        if (owningGroups == null || owningGroups.isEmpty()) {
            return true;
        }
        List<ToolGroup> explicitOwningGroups =
                owningGroups.stream()
                        .filter(toolGroup -> !toolGroup.name().startsWith(SKILL_GROUP_PREFIX))
                        .toList();
        if (!explicitOwningGroups.isEmpty()) {
            return explicitOwningGroups.stream()
                    .anyMatch(toolGroup -> activeGroupNames.contains(toolGroup.name()));
        }
        return owningGroups.stream()
                .anyMatch(toolGroup -> activeGroupNames.contains(toolGroup.name()));
    }

    private static Set<String> normalizeNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                normalizedNames.add(name.trim());
            }
        }
        return normalizedNames;
    }
}
