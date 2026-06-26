/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registry holding resource manager buckets.
 *
 * <p>Mirrors Python's {@code ResourceRegistry} in
 * {@code openjiuwen/core/runner/resources_manager/resource_registry.py}.</p>
 */
public class ResourceRegistry {

    private final ResourceBucket toolMgr = new ResourceBucket("tool");
    private final ResourceBucket workflowMgr = new ResourceBucket("workflow");
    private final ResourceBucket promptMgr = new ResourceBucket("prompt");
    private final ResourceBucket modelMgr = new ResourceBucket("model");
    private final ResourceBucket agentMgr = new ResourceBucket("agent");
    private final ResourceBucket agentTeamMgr = new ResourceBucket("agent_team");
    private final ResourceBucket sysOperationMgr = new ResourceBucket("sys_operation");

    /**
     * Removes one resource id from the first bucket that contains it.
     *
     * <p>The lookup order is the Python order: tool, workflow, agent,
     * agent-team, prompt, model, sys-operation.</p>
     *
     * @param resourceId resource id
     */
    public void removeById(String resourceId) {
        if (tool().remove(resourceId) != null) {
            return;
        }
        if (workflow().remove(resourceId) != null) {
            return;
        }
        if (agent().remove(resourceId) != null) {
            return;
        }
        if (agentTeam().remove(resourceId) != null) {
            return;
        }
        if (prompt().remove(resourceId) != null) {
            return;
        }
        if (model().remove(resourceId) != null) {
            return;
        }
        sysOperation().remove(resourceId);
    }

    public ResourceBucket tool() {
        return toolMgr;
    }

    public ResourceBucket prompt() {
        return promptMgr;
    }

    public ResourceBucket model() {
        return modelMgr;
    }

    public ResourceBucket workflow() {
        return workflowMgr;
    }

    public ResourceBucket agent() {
        return agentMgr;
    }

    public ResourceBucket agentTeam() {
        return agentTeamMgr;
    }

    public ResourceBucket sysOperation() {
        return sysOperationMgr;
    }

    /**
     * Minimal bucket used until the dedicated resource manager translations in
     * this batch replace the generic storage with typed managers.
     *
     * <p>Mirrors the manager fields initialized by Python's
     * {@code ResourceRegistry.__init__} in
     * {@code openjiuwen/core/runner/resources_manager/resource_registry.py}.</p>
     */
    public static final class ResourceBucket {
        private final String kind;
        private final Map<String, Object> resources = new LinkedHashMap<>();

        public ResourceBucket(String kind) {
            this.kind = kind;
        }

        public String kind() {
            return kind;
        }

        public void put(String resourceId, Object resource) {
            resources.put(resourceId, resource);
        }

        public Object get(String resourceId) {
            return resources.get(resourceId);
        }

        public Object remove(String resourceId) {
            return resources.remove(resourceId);
        }

        public boolean contains(String resourceId) {
            return resources.containsKey(resourceId);
        }

        public int size() {
            return resources.size();
        }

        public Map<String, Object> snapshot() {
            return new LinkedHashMap<>(resources);
        }
    }
}
