/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.CallbackFramework;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Immutable fingerprint of every process-global registry dimension the
 * Lifecycle tests must keep clean: the
 * shared {@link ResourceMgr} (card registry, ownership table, per-type
 * storages, tag index) and the shared {@link CallbackFramework}
 * (callbacks, chains, filters). The registries only expose mutating
 * facades, so snapshots read the backing maps through reflection; a
 * missing reflection path fails the snapshot itself, so silent coverage
 * loss is impossible. Empty ownership sets carry no live claim and are
 * skipped, matching the delta-zero contract about residue.
 *
 * @since 0.1.16
 */
public final class GlobalRegistrySnapshot {
    private final TreeMap<String, String> fingerprint;

    private GlobalRegistrySnapshot(TreeMap<String, String> fingerprint) {
        this.fingerprint = fingerprint;
    }

    /**
     * Captures the current state of every global registry dimension.
     *
     * @return snapshot of the shared ResourceMgr and CallbackFramework state
     */
    public static GlobalRegistrySnapshot take() {
        TreeMap<String, String> fp = new TreeMap<>();
        fingerprintResources(fp);
        fingerprintTagIndex(fp);
        fingerprintCallbacks(fp);
        return new GlobalRegistrySnapshot(fp);
    }

    /**
     * Asserts this snapshot differs from {@code before} in no entry: no
     * registry key added, removed, or changed its value descriptor. Used
     * to prove create/init/destroy cycles leave zero global residue.
     *
     * @param before the reference snapshot taken earlier in the same test
     */
    public void assertDeltaZero(GlobalRegistrySnapshot before) {
        TreeSet<String> added = new TreeSet<>(fingerprint.keySet());
        added.removeAll(before.fingerprint.keySet());
        TreeSet<String> removed = new TreeSet<>(before.fingerprint.keySet());
        removed.removeAll(fingerprint.keySet());
        TreeSet<String> changed = new TreeSet<>();
        for (Map.Entry<String, String> entry : fingerprint.entrySet()) {
            String previous = before.fingerprint.get(entry.getKey());
            if (previous != null && !previous.equals(entry.getValue())) {
                changed.add(entry.getKey() + ": " + previous + " -> " + entry.getValue());
            }
        }
        assertSoftly(softly -> {
            softly.assertThat(added).as("global registry entries added").isEmpty();
            softly.assertThat(removed).as("global registry entries removed").isEmpty();
            softly.assertThat(changed).as("global registry entries changed").isEmpty();
        });
    }

    /**
     * Returns whether any global resource entry still carries the given
     * owner token in its ownership set.
     *
     * @param ownerToken owner token to look for
     * @return true when at least one entry keeps the owner's claim
     */
    public boolean hasOwner(String ownerToken) {
        ResourceMgr mgr = Runner.resourceMgr();
        Map<?, ?> owners = fieldValue(mgr, ResourceMgr.class, "idToOwners");
        for (Object value : owners.values()) {
            if (value instanceof Set<?> set && set.contains(ownerToken)) {
                return true;
            }
        }
        return false;
    }

    private static void fingerprintResources(TreeMap<String, String> fp) {
        ResourceMgr mgr = Runner.resourceMgr();
        fingerprintValues(fp, "resourceMgr/idToCard", fieldValue(mgr, ResourceMgr.class, "idToCard"));
        fingerprintValues(fp, "resourceMgr/idToOwners", fieldValue(mgr, ResourceMgr.class, "idToOwners"));
        ResourceRegistry registry = fieldValue(mgr, ResourceMgr.class, "resourceRegistry");
        ToolMgr toolMgr = fieldValue(registry, ResourceRegistry.class, "toolMgr");
        fingerprintValues(fp, "resourceMgr/tools", fieldValue(toolMgr, ToolMgr.class, "tools"));
        fingerprintValues(fp, "resourceMgr/mcpServerNameToIds",
                fieldValue(toolMgr, ToolMgr.class, "mcpServerNameToIds"));
        fingerprintValues(fp, "resourceMgr/mcpServerResources",
                fieldValue(toolMgr, ToolMgr.class, "mcpServerResources"));
        fingerprintValues(fp, "resourceMgr/sysOpResources",
                fieldValue(toolMgr, ToolMgr.class, "sysOpResources"));
        fingerprintProviders(fp, "resourceMgr/workflows", "workflowMgr", registry);
        fingerprintProviders(fp, "resourceMgr/models", "modelMgr", registry);
        fingerprintProviders(fp, "resourceMgr/agents", "agentMgr", registry);
        fingerprintProviders(fp, "resourceMgr/agentGroups", "agentGroupMgr", registry);
        PromptMgr promptMgr = fieldValue(registry, ResourceRegistry.class, "promptMgr");
        fingerprintValues(fp, "resourceMgr/prompts", fieldValue(promptMgr, PromptMgr.class, "repo"));
        SysOperationMgr sysOperationMgr = fieldValue(registry, ResourceRegistry.class, "sysOperationMgr");
        fingerprintValues(fp, "resourceMgr/sysOperations",
                fieldValue(sysOperationMgr, SysOperationMgr.class, "sysOperations"));
    }

    private static void fingerprintProviders(TreeMap<String, String> fp, String path, String mgrField,
            ResourceRegistry registry) {
        AbstractManager<?> manager = fieldValue(registry, ResourceRegistry.class, mgrField);
        fingerprintValues(fp, path, fieldValue(manager, AbstractManager.class, "providers"));
    }

    private static void fingerprintValues(TreeMap<String, String> fp, String path, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Collection<?> collection && collection.isEmpty()) {
                // Buckets drained by unregister (callback lists emptied but
                // their keys kept) carry no live registration; skipping them
                // keeps the delta-zero contract about residue, not key shapes.
                continue;
            }
            fp.put(path + "/" + entry.getKey(), describe(value));
        }
    }

    private static String describe(Object value) {
        if (value instanceof Collection<?> collection) {
            return "#" + collection.size();
        }
        return value.getClass().getSimpleName();
    }

    private static void fingerprintTagIndex(TreeMap<String, String> fp) {
        TagMgr tagMgr = fieldValue(Runner.resourceMgr(), ResourceMgr.class, "tagMgr");
        ReentrantLock tagLock = fieldValue(tagMgr, TagMgr.class, "lock");
        tagLock.lock();
        try {
            Map<?, ?> forward = fieldValue(tagMgr, TagMgr.class, "resourceTags");
            for (Map.Entry<?, ?> entry : forward.entrySet()) {
                fp.put("tagMgr/resourceTags/" + entry.getKey(), sortedValues((Set<?>) entry.getValue()));
            }
            Map<?, ?> reverse = fieldValue(tagMgr, TagMgr.class, "tagToResource");
            for (Map.Entry<?, ?> entry : reverse.entrySet()) {
                fp.put("tagMgr/tagToResource/" + entry.getKey(), sortedValues((Set<?>) entry.getValue()));
            }
        } finally {
            tagLock.unlock();
        }
    }

    private static String sortedValues(Set<?> values) {
        return new TreeSet<>(values).toString();
    }

    private static void fingerprintCallbacks(TreeMap<String, String> fp) {
        CallbackFramework framework = Runner.callbackFramework();
        fingerprintValues(fp, "callbacks", fieldValue(framework, CallbackFramework.class, "callbacks"));
        fingerprintValues(fp, "chains", fieldValue(framework, CallbackFramework.class, "chains"));
        fingerprintValues(fp, "filters", fieldValue(framework, CallbackFramework.class, "filters"));
        List<?> globalFilters = fieldValue(framework, CallbackFramework.class, "globalFilters");
        fp.put("callbacks/globalFilters", "#" + globalFilters.size());
        Map<?, ?> callbackFilters = fieldValue(framework, CallbackFramework.class, "callbackFilters");
        fp.put("callbacks/callbackFilters", "#" + callbackFilters.size());
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldValue(Object target, Class<?> declaringType, String name) {
        try {
            Field field = declaringType.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "global registry reflection path missing: " + declaringType.getSimpleName() + "." + name, e);
        }
    }
}
