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

    /**
     * Registers an explicit tool group and rejects duplicate or reserved names.
     *
     * @param toolGroup group to register
     * @throws IllegalArgumentException when the group is null or invalid
     */
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
            if (toolGroup.members().isEmpty()) {
                Loggers.TOOL.warning("Registered empty tool group: {}", toolGroup.name());
            }
            toolGroupsByName.put(toolGroup.name(), toolGroup);
        }
    }

    /**
     * Registers or replaces the reserved implicit group for one Skill.
     *
     * @param toolGroup implicit Skill group to register
     * @throws IllegalArgumentException when the group is null or invalid
     */
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

    /**
     * Removes a Skill-owned implicit group.
     *
     * @param groupName name of the implicit Skill group to remove
     */
    public void unregisterSkillToolGroup(String groupName) {
        if (groupName == null || groupName.isBlank()
                || !groupName.trim().startsWith(SKILL_GROUP_PREFIX)) {
            return;
        }
        toolGroupsByName.remove(groupName.trim());
    }

    /**
     * Removes a registered group classification; unknown names are ignored.
     *
     * <p>Unregistration does not revoke access to tools. Tools that no longer
     * belong to any registered group fall back to ungrouped semantics. Use
     * session deactivation or remove the tool from the ability registry to
     * restrict access.
     *
     * @param groupName name of the group to remove
     */
    public void unregisterToolGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }
        toolGroupsByName.remove(groupName.trim());
    }

    /**
     * Returns whether any tool names have been revoked by group unregistration.
     *
     * @return always {@code false}; revoked-name tombstones are no longer maintained
     * @deprecated Group unregistration removes a classification and does not revoke
     *     tool access. Deactivate the group for a session or remove the tool from
     *     the ability registry instead.
     */
    @Deprecated(forRemoval = true)
    public boolean hasRevokedToolNames() {
        return false;
    }

    /**
     * Returns a registered group by its normalized name.
     *
     * @param groupName name of the group to find
     * @return matching group, or empty when not registered
     */
    public Optional<ToolGroup> getToolGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return Optional.empty();
        }
        synchronized (toolGroupsByName) {
            return Optional.ofNullable(toolGroupsByName.get(groupName.trim()));
        }
    }

    /**
     * Returns a stable snapshot of all registered groups.
     *
     * @return immutable list of registered groups
     */
    public List<ToolGroup> getGroups() {
        synchronized (toolGroupsByName) {
            return List.copyOf(toolGroupsByName.values());
        }
    }

    /**
     * Returns whether any caller-created, non-{@code skill:} group exists.
     *
     * <p>Implicit Skill groups are not counted because they back the legacy
     * direct skill activation path and must not turn on ungrouped-skill
     * passthrough by themselves.
     *
     * @return true when at least one explicit tool group is registered
     */
    public boolean hasExplicitToolGroups() {
        synchronized (toolGroupsByName) {
            return toolGroupsByName.values().stream()
                    .anyMatch(toolGroup -> !toolGroup.name().startsWith(SKILL_GROUP_PREFIX));
        }
    }

    /**
     * Filters registered tool names using the session's active group names.
     *
     * @param activeGroupNames groups enabled for this session
     * @param registeredToolNames tools currently available to the agent
     * @return immutable sorted list of eligible tool names
     */
    public List<String> getActiveToolNames(
            Collection<String> activeGroupNames, Collection<String> registeredToolNames) {
        Set<String> normalizedActiveGroupNames = normalizeNames(activeGroupNames);
        Set<String> normalizedRegisteredToolNames = normalizeNames(registeredToolNames);
        List<String> activeToolNames = new ArrayList<>();
        synchronized (toolGroupsByName) {
            Map<String, List<ToolGroup>> owningGroupsByToolName = findOwningGroupsByToolName();
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

    /**
     * Determines whether a tool is eligible for direct execution in this session.
     *
     * @param toolName name of the requested tool
     * @param activeGroupNames groups enabled for this session
     * @return true when the tool is eligible
     */
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
                            .filter(toolGroup -> hasMember(toolGroup, GroupMemberType.TOOL,
                                    normalizedToolName))
                            .toList();
            return isToolEligible(normalizedToolName, owningGroups, normalizedActiveGroupNames);
        }
    }

    private Map<String, List<ToolGroup>> findOwningGroupsByToolName() {
        Map<String, List<ToolGroup>> owningGroupsByToolName = new HashMap<>();
        for (ToolGroup toolGroup : toolGroupsByName.values()) {
            for (GroupMemberRef member : toolGroup.members()) {
                if (member.type() == GroupMemberType.TOOL) {
                    owningGroupsByToolName
                            .computeIfAbsent(member.id(), ignored -> new ArrayList<>())
                            .add(toolGroup);
                }
            }
        }
        return owningGroupsByToolName;
    }

    private static boolean hasMember(
            ToolGroup toolGroup, GroupMemberType type, String id) {
        return toolGroup.members().stream()
                .anyMatch(member -> member.type() == type && member.id().equals(id));
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

    /**
     * Returns the skill names referenced by SKILL members of the given
     * active groups.
     *
     * @param activeGroupNames groups enabled for this session
     * @return immutable sorted set of skill names from active groups
     * @since 0.1.19
     */
    public Set<String> getActiveSkillNames(Collection<String> activeGroupNames) {
        Set<String> normalizedActiveGroupNames = normalizeNames(activeGroupNames);
        Set<String> skillNames = new LinkedHashSet<>();
        synchronized (toolGroupsByName) {
            for (ToolGroup toolGroup : toolGroupsByName.values()) {
                if (!normalizedActiveGroupNames.contains(toolGroup.name())) {
                    continue;
                }
                for (GroupMemberRef member : toolGroup.members()) {
                    if (member.type() == GroupMemberType.SKILL) {
                        skillNames.add(member.id());
                    }
                }
            }
        }
        return Collections.unmodifiableSet(skillNames);
    }

    /**
     * Returns the skill names that are not referenced by SKILL members of
     * any registered group.
     *
     * @param registeredSkillNames all skill names currently registered in
     *     the SkillManager
     * @return immutable sorted set of ungrouped skill names
     * @since 0.1.19
     */
    public Set<String> getUngroupedSkillNames(Collection<String> registeredSkillNames) {
        Set<String> groupedSkillNames = new LinkedHashSet<>();
        synchronized (toolGroupsByName) {
            for (ToolGroup toolGroup : toolGroupsByName.values()) {
                for (GroupMemberRef member : toolGroup.members()) {
                    if (member.type() == GroupMemberType.SKILL) {
                        groupedSkillNames.add(member.id());
                    }
                }
            }
        }
        Set<String> ungrouped = new LinkedHashSet<>();
        for (String skillName : registeredSkillNames) {
            if (skillName != null && !skillName.isBlank()
                    && !groupedSkillNames.contains(skillName.trim())) {
                ungrouped.add(skillName.trim());
            }
        }
        return Collections.unmodifiableSet(ungrouped);
    }
}
