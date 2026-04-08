/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.base.TagUpdateStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Tag-based resource organization and filtering manager.
 * <p>
 * Mirrors Python's {@code TagMgr} in {@code resources_manager/tag_manager.py}.
 */
public class TagMgr {

    private static final Logger logger = LoggerFactory.getLogger(TagMgr.class);

    private final Map<String, Set<String>> resourceTags = new HashMap<>();
    private final Map<String, Set<String>> tagToResource = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public TagMgr() {
        tagToResource.put(Tag.GLOBAL, new HashSet<>());
    }

    /**
     * Clear all tag and resource mappings, restoring the initial state.
     */
    public void clear() {
        lock.lock();
        try {
            resourceTags.clear();
            tagToResource.clear();
            tagToResource.put(Tag.GLOBAL, new HashSet<>());
        } finally {
            lock.unlock();
        }
    }

    public boolean hasTag(String tag) {
        lock.lock();
        try {
            return tagToResource.containsKey(tag);
        } finally {
            lock.unlock();
        }
    }

    public List<String> listTags() {
        lock.lock();
        try {
            return tagToResource.entrySet().stream()
                    .filter(e -> !e.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } finally {
            lock.unlock();
        }
    }

    public boolean hasResource(String resourceId) {
        lock.lock();
        try {
            return resourceTags.containsKey(resourceId);
        } finally {
            lock.unlock();
        }
    }

    public boolean hasResourceTag(String resourceId, String tag) {
        lock.lock();
        try {
            Set<String> tags = resourceTags.get(resourceId);
            return tags != null && tags.contains(tag);
        } finally {
            lock.unlock();
        }
    }

    public List<String> getResourcesTags(String resourceId) {
        lock.lock();
        try {
            Set<String> tags = resourceTags.get(resourceId);
            return tags != null ? new ArrayList<>(tags) : null;
        } finally {
            lock.unlock();
        }
    }

    public List<String> tagResource(String resourceId, Object tags) {
        List<String> tagsToAdd = normalizeTags(tags);
        lock.lock();
        try {
            resourceTags.computeIfAbsent(resourceId, k -> new HashSet<>());

            if (tagsToAdd.contains(Tag.GLOBAL)) {
                setGlobalResource(resourceId);
                return List.of(Tag.GLOBAL);
            }

            return addResourceTags(resourceId, tagsToAdd);
        } finally {
            lock.unlock();
        }
    }

    public List<String> removeResource(String resourceId) {
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                return Collections.emptyList();
            }
            return doRemoveResource(resourceId);
        } finally {
            lock.unlock();
        }
    }

    public List<String> removeResourceTags(String resourceId, Object tags, boolean skipIfNotExists) {
        List<String> tagsToRemove = normalizeTags(tags);
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_REMOVE_RESOURCE_TAG_ERROR,
                        "resource_id", resourceId, "tags", String.valueOf(tags),
                        "reason", "Resource does not exist");
            }
            return doRemoveResourceTags(resourceId, tagsToRemove);
        } finally {
            lock.unlock();
        }
    }

    public List<String> updateResourceTags(String resourceId, Object tags, TagUpdateStrategy strategy) {
        List<String> newTags = normalizeTags(tags);
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_REPLACE_RESOURCE_TAG_ERROR,
                        "resource_id", resourceId, "tags", String.valueOf(tags),
                        "reason", "Resource does not exist");
            }

            if (newTags.contains(Tag.GLOBAL)) {
                setGlobalResource(resourceId);
                return List.of(Tag.GLOBAL);
            }

            if (strategy == TagUpdateStrategy.REPLACE) {
                return replaceResourceTags(resourceId, newTags);
            } else if (strategy == TagUpdateStrategy.MERGE) {
                return addResourceTags(resourceId, newTags);
            } else {
                throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_REPLACE_RESOURCE_TAG_ERROR,
                        "resource_id", resourceId, "tags", String.valueOf(tags),
                        "reason", "Unsupported strategy: " + strategy);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<String> removeTag(String tag, boolean skipIfNotExists) {
        lock.lock();
        try {
            if (!tagToResource.containsKey(tag)) {
                if (skipIfNotExists) {
                    return Collections.emptyList();
                }
                throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_REMOVE_TAG_ERROR,
                        "tag", tag, "reason", "Tag does not exist");
            }
            return doRemoveTag(tag);
        } finally {
            lock.unlock();
        }
    }

    public List<String> getTagResources(String tag) {
        lock.lock();
        try {
            Set<String> resources = tagToResource.get(tag);
            return resources != null ? new ArrayList<>(resources) : Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    public List<String> findResourcesByTags(Object tags, TagMatchStrategy strategy, boolean skipIfNotExists) {
        List<String> tagsToSearch = normalizeTags(tags);
        lock.lock();
        try {
            if (strategy == TagMatchStrategy.ANY) {
                Set<String> found = new HashSet<>();
                for (String tag : tagsToSearch) {
                    Set<String> resources = tagToResource.get(tag);
                    if (resources == null || resources.isEmpty()) {
                        if (!isBuiltinTag(tag) && !skipIfNotExists) {
                            throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_FIND_RESOURCE_ERROR,
                                    "tag", String.valueOf(tags), "strategy", strategy.getValue(),
                                    "reason", "Tag '" + tag + "' does not exist");
                        }
                    } else {
                        found.addAll(resources);
                    }
                }
                return new ArrayList<>(found);
            } else {
                // ALL strategy
                Set<String> result = null;
                for (String tag : tagsToSearch) {
                    Set<String> resources = tagToResource.get(tag);
                    if (resources == null || resources.isEmpty()) {
                        if (!isBuiltinTag(tag) && !skipIfNotExists) {
                            throw ErrorHelper.buildError(StatusCode.RESOURCE_TAG_FIND_RESOURCE_ERROR,
                                    "tag", String.valueOf(tags), "strategy", strategy.getValue(),
                                    "reason", "Tag '" + tag + "' does not exist");
                        }
                        return Collections.emptyList();
                    }
                    if (result == null) {
                        result = new HashSet<>(resources);
                    } else {
                        result.retainAll(resources);
                    }
                }
                return result != null ? new ArrayList<>(result) : Collections.emptyList();
            }
        } finally {
            lock.unlock();
        }
    }

    // ========== Internal Methods ==========

    private void setGlobalResource(String resourceId) {
        Set<String> oldTags = resourceTags.get(resourceId);
        if (oldTags != null) {
            for (String tag : oldTags) {
                Set<String> res = tagToResource.get(tag);
                if (res != null) {
                    res.remove(resourceId);
                }
            }
        }
        Set<String> newTagSet = new HashSet<>();
        newTagSet.add(Tag.GLOBAL);
        resourceTags.put(resourceId, newTagSet);
        tagToResource.computeIfAbsent(Tag.GLOBAL, k -> new HashSet<>()).add(resourceId);
    }

    private List<String> addResourceTags(String resourceId, List<String> tagsToAdd) {
        Set<String> currentTags = resourceTags.get(resourceId);
        // Remove GLOBAL if adding specific tags
        if (currentTags.contains(Tag.GLOBAL) && !tagsToAdd.contains(Tag.GLOBAL)) {
            currentTags.remove(Tag.GLOBAL);
            Set<String> globalRes = tagToResource.get(Tag.GLOBAL);
            if (globalRes != null) {
                globalRes.remove(resourceId);
            }
        }
        for (String tag : tagsToAdd) {
            currentTags.add(tag);
            tagToResource.computeIfAbsent(tag, k -> new HashSet<>()).add(resourceId);
        }
        return new ArrayList<>(currentTags);
    }

    private List<String> replaceResourceTags(String resourceId, List<String> newTags) {
        Set<String> oldTags = resourceTags.get(resourceId);
        if (oldTags != null) {
            for (String tag : oldTags) {
                Set<String> res = tagToResource.get(tag);
                if (res != null) {
                    res.remove(resourceId);
                }
            }
        }
        Set<String> newTagSet = new HashSet<>(newTags);
        resourceTags.put(resourceId, newTagSet);
        for (String tag : newTags) {
            tagToResource.computeIfAbsent(tag, k -> new HashSet<>()).add(resourceId);
        }
        return new ArrayList<>(newTagSet);
    }

    private List<String> doRemoveResource(String resourceId) {
        Set<String> tags = resourceTags.remove(resourceId);
        List<String> removedTags = new ArrayList<>();
        if (tags != null) {
            for (String tag : tags) {
                Set<String> res = tagToResource.get(tag);
                if (res != null) {
                    res.remove(resourceId);
                }
                removedTags.add(tag);
            }
        }
        return removedTags;
    }

    private List<String> doRemoveResourceTags(String resourceId, List<String> tagsToRemove) {
        Set<String> currentTags = resourceTags.get(resourceId);
        for (String tag : tagsToRemove) {
            currentTags.remove(tag);
            Set<String> res = tagToResource.get(tag);
            if (res != null) {
                res.remove(resourceId);
            }
        }
        return new ArrayList<>(currentTags);
    }

    private List<String> doRemoveTag(String tag) {
        Set<String> affectedResources = tagToResource.remove(tag);
        List<String> affected = new ArrayList<>();
        if (affectedResources != null) {
            for (String resourceId : affectedResources) {
                Set<String> tags = resourceTags.get(resourceId);
                if (tags != null) {
                    tags.remove(tag);
                }
                affected.add(resourceId);
            }
        }
        return affected;
    }

    private boolean isBuiltinTag(String tag) {
        return Tag.GLOBAL.equals(tag) || Tag.ALL.equals(tag)
                || Tag.ACTIVE.equals(tag) || Tag.INACTIVE.equals(tag);
    }

    @SuppressWarnings("unchecked")
    static List<String> normalizeTags(Object tags) {
        if (tags == null) {
            return Collections.emptyList();
        }
        if (tags instanceof String s) {
            return List.of(s);
        }
        if (tags instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of(String.valueOf(tags));
    }

    /**
     * Display current tag manager state.
     *
     * @param enableLog if true, logs the state via logger
     * @return formatted string describing current tag-resource mappings
     */
    public String display(boolean enableLog) {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("\nTag -> Resource IDs:\n");
            tagToResource.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        if (!entry.getValue().isEmpty()) {
                            sb.append("  tag['").append(entry.getKey()).append("']: [");
                            sb.append(entry.getValue().stream().sorted().collect(Collectors.joining(", ")));
                            sb.append("]\n");
                        }
                    });

            sb.append("\nResource -> Tags:\n");
            resourceTags.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        sb.append("  resource['").append(entry.getKey()).append("']: [");
                        sb.append(entry.getValue().stream().sorted().collect(Collectors.joining(", ")));
                        sb.append("]\n");
                    });

            sb.append("\nStatistics:\n");
            sb.append("  Total tags: ").append(tagToResource.size()).append('\n');
            sb.append("  Total resources: ").append(resourceTags.size()).append('\n');
            Set<String> globalResources = tagToResource.getOrDefault(Tag.GLOBAL, Collections.emptySet());
            sb.append("  GLOBAL resources: ").append(globalResources.size()).append('\n');

            String msg = sb.toString();
            if (enableLog) {
                logger.info("---- Tag Manager State ----\n{}", msg);
            }
            return msg;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Display with logging enabled by default.
     */
    public String display() {
        return display(true);
    }
}
