/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Tag-based resource organization and filtering manager.
 *
 * <p>Mirrors Python's {@code TagMgr} in
 * {@code openjiuwen/core/runner/resources_manager/tag_manager.py}.</p>
 */
public class TagManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TagManager.class);

    private final Map<String, Set<String>> resourceTags = new LinkedHashMap<>();
    private final Map<String, Set<String>> tagToResource = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public TagManager() {
        tagToResource.put(ResourceManagerBase.GLOBAL, new LinkedHashSet<>());
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
                    .filter(entry -> !entry.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(ArrayList::new));
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

    public List<String> tagResource(String resourceId, String tag) {
        return tagResourceInternal(resourceId, normalizeTags(tag), tag);
    }

    public List<String> tagResource(String resourceId, Collection<String> tags) {
        return tagResourceInternal(resourceId, normalizeTags(tags), tags);
    }

    public List<String> removeResource(String resourceId) {
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                return Collections.emptyList();
            }

            List<String> removedTags = removeResourceInternal(resourceId);
            LOGGER.info("Removed resource. resource_id={}, removed_tags={}", resourceId, removedTags);
            return removedTags;
        } finally {
            lock.unlock();
        }
    }

    public List<String> removeResourceTags(String resourceId, String tag) {
        return removeResourceTags(resourceId, tag, false);
    }

    public List<String> removeResourceTags(String resourceId, String tag, boolean skipIfNotExists) {
        return removeResourceTagsInternal(resourceId, normalizeTags(tag), tag, skipIfNotExists);
    }

    public List<String> removeResourceTags(String resourceId, Collection<String> tags) {
        return removeResourceTags(resourceId, tags, false);
    }

    public List<String> removeResourceTags(String resourceId, Collection<String> tags, boolean skipIfNotExists) {
        return removeResourceTagsInternal(resourceId, normalizeTags(tags), tags, skipIfNotExists);
    }

    public List<String> updateResourceTags(String resourceId, String tag, TagUpdateStrategy tagUpdateStrategy) {
        return updateResourceTagsInternal(resourceId, normalizeTags(tag), tag, tagUpdateStrategy);
    }

    public List<String> updateResourceTags(String resourceId, Collection<String> tags,
                                           TagUpdateStrategy tagUpdateStrategy) {
        return updateResourceTagsInternal(resourceId, normalizeTags(tags), tags, tagUpdateStrategy);
    }

    public List<String> removeTag(String tag) {
        return removeTag(tag, false);
    }

    public List<String> removeTag(String tag, boolean skipIfNotExists) {
        lock.lock();
        try {
            if (!tagToResource.containsKey(tag)) {
                if (skipIfNotExists) {
                    return Collections.emptyList();
                }
                throw buildError(StatusCode.RESOURCE_TAG_REMOVE_TAG_ERROR,
                        "tag", tag,
                        "reason", "Tag does not exist");
            }

            List<String> affectedResources = removeTagInternal(tag);
            LOGGER.info("Removed tag. tag='{}', affected_resources={}", tag, affectedResources);
            return affectedResources;
        } finally {
            lock.unlock();
        }
    }

    public List<String> getTagResources(String tag) {
        lock.lock();
        try {
            return new ArrayList<>(tagToResource.getOrDefault(tag, Collections.emptySet()));
        } finally {
            lock.unlock();
        }
    }

    public List<String> findResourcesByTags(String tag, TagMatchStrategy tagMatchStrategy) {
        return findResourcesByTags(tag, tagMatchStrategy, true);
    }

    public List<String> findResourcesByTags(String tag, TagMatchStrategy tagMatchStrategy, boolean skipIfNotExists) {
        return findResourcesByTagsInternal(normalizeTags(tag), tag, tagMatchStrategy, skipIfNotExists);
    }

    public List<String> findResourcesByTags(Collection<String> tags, TagMatchStrategy tagMatchStrategy) {
        return findResourcesByTags(tags, tagMatchStrategy, true);
    }

    public List<String> findResourcesByTags(Collection<String> tags, TagMatchStrategy tagMatchStrategy,
                                            boolean skipIfNotExists) {
        return findResourcesByTagsInternal(normalizeTags(tags), tags, tagMatchStrategy, skipIfNotExists);
    }

    public boolean hasResourceTag(String resourceId, String tag) {
        lock.lock();
        try {
            return resourceTags.getOrDefault(resourceId, Collections.emptySet()).contains(tag);
        } finally {
            lock.unlock();
        }
    }

    public List<String> getResourcesTags(String resourceId) {
        lock.lock();
        try {
            return new ArrayList<>(resourceTags.getOrDefault(resourceId, Collections.emptySet()));
        } finally {
            lock.unlock();
        }
    }

    public String display() {
        return display(true);
    }

    public String display(boolean enableLog) {
        lock.lock();
        try {
            StringBuilder message = new StringBuilder();
            message.append('\n').append("Tag -> Resource IDs:").append('\n');
            tagToResource.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        if (!entry.getValue().isEmpty()) {
                            message.append("  tag['").append(entry.getKey()).append("']: [")
                                    .append(entry.getValue().stream().sorted().collect(Collectors.joining(", ")))
                                    .append("]").append('\n');
                        }
                    });

            message.append('\n').append("Resource -> Tags:").append('\n');
            resourceTags.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> message.append("  resource['").append(entry.getKey()).append("']: [")
                            .append(entry.getValue().stream().sorted().collect(Collectors.joining(", ")))
                            .append("]").append('\n'));

            message.append('\n').append("Statistics:").append('\n');
            message.append("  Total tags: ").append(tagToResource.size()).append('\n');
            message.append("  Total resources: ").append(resourceTags.size()).append('\n');
            message.append("  GLOBAL resources: ")
                    .append(tagToResource.getOrDefault(ResourceManagerBase.GLOBAL, Collections.emptySet()).size())
                    .append('\n');

            String result = message.toString();
            if (enableLog) {
                LOGGER.info("---- Tag Manager State ----\n{}", result);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    private List<String> tagResourceInternal(String resourceId, Set<String> tagsToAdd, Object originalTags) {
        lock.lock();
        try {
            resourceTags.computeIfAbsent(resourceId, ignored -> new LinkedHashSet<>());

            if (tagsToAdd.contains(ResourceManagerBase.GLOBAL)) {
                List<String> oldTags = setGlobalResource(resourceId);
                LOGGER.info("Added GLOBAL tag to resource. resource_id={}, changed from {} to [GLOBAL]",
                        resourceId, oldTags);
                return List.of(ResourceManagerBase.GLOBAL);
            }

            List<String> currentTags = addResourceTags(resourceId, tagsToAdd);
            LOGGER.info("Added tags to resource. resource_id={}, added_tags={}, current_tags={}",
                    resourceId, originalTags, currentTags);
            return currentTags;
        } finally {
            lock.unlock();
        }
    }

    private List<String> removeResourceTagsInternal(String resourceId, Set<String> tagsToRemove,
                                                    Object originalTags, boolean skipIfNotExists) {
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                throw buildError(StatusCode.RESOURCE_TAG_REMOVE_RESOURCE_TAG_ERROR,
                        "resource_id", resourceId,
                        "tags", originalTags,
                        "reason", "Resource does not exist");
            }

            Set<String> currentTags = resourceTags.get(resourceId);
            if (!skipIfNotExists) {
                Set<String> nonExistentTags = new LinkedHashSet<>(tagsToRemove);
                nonExistentTags.removeAll(currentTags);
                if (!nonExistentTags.isEmpty()) {
                    throw buildError(StatusCode.RESOURCE_TAG_REMOVE_RESOURCE_TAG_ERROR,
                            "resource_id", resourceId,
                            "tags", new ArrayList<>(nonExistentTags),
                            "reason", "Tag does not exist");
                }
            }

            List<String> remainingTags = removeResourceTagsInternal(resourceId, tagsToRemove);
            LOGGER.info("Removed tags from resource. resource_id={}, removed_tags={}, remaining_tags={}",
                    resourceId, tagsToRemove, remainingTags);
            return remainingTags;
        } finally {
            lock.unlock();
        }
    }

    private List<String> updateResourceTagsInternal(String resourceId, Set<String> newTags,
                                                    Object originalTags, TagUpdateStrategy tagUpdateStrategy) {
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                throw buildError(StatusCode.RESOURCE_TAG_REPLACE_RESOURCE_TAG_ERROR,
                        "resource_id", resourceId,
                        "tags", originalTags,
                        "tag", originalTags,
                        "reason", "Resource does not exist");
            }

            if (newTags.contains(ResourceManagerBase.GLOBAL)) {
                List<String> oldTags = setGlobalResource(resourceId);
                LOGGER.info("Updated resource to GLOBAL. resource_id={}, strategy={}, old_tags={}",
                        resourceId, tagUpdateStrategy, oldTags);
                return List.of(ResourceManagerBase.GLOBAL);
            }

            if (tagUpdateStrategy == TagUpdateStrategy.REPLACE) {
                List<String> currentTags = replaceResourceTags(resourceId, newTags);
                LOGGER.info("Replaced resource tags. resource_id={}, new_tags={}", resourceId, newTags);
                return currentTags;
            }

            if (tagUpdateStrategy == TagUpdateStrategy.MERGE) {
                List<String> currentTags = addResourceTags(resourceId, newTags);
                LOGGER.info("Merged resource tags. resource_id={}, added_tags={}, current_tags={}",
                        resourceId, newTags, currentTags);
                return currentTags;
            }

            throw buildError(StatusCode.RESOURCE_TAG_REPLACE_RESOURCE_TAG_ERROR,
                    "resource_id", resourceId,
                    "tags", originalTags,
                    "tag", originalTags,
                    "reason", "Unsupported strategy: " + tagUpdateStrategy);
        } finally {
            lock.unlock();
        }
    }

    private List<String> findResourcesByTagsInternal(Set<String> tagsToSearch, Object originalTags,
                                                     TagMatchStrategy tagMatchStrategy, boolean skipIfNotExists) {
        lock.lock();
        try {
            if (tagMatchStrategy == TagMatchStrategy.ANY) {
                Set<String> foundResources = new LinkedHashSet<>();
                for (String tag : tagsToSearch) {
                    Set<String> resources = tagToResource.get(tag);
                    if (resources == null || resources.isEmpty()) {
                        if (!isBuiltinTag(tag) && !skipIfNotExists) {
                            throw buildError(StatusCode.RESOURCE_TAG_FIND_RESOURCE_ERROR,
                                    "tags", originalTags,
                                    "tag", originalTags,
                                    "strategy", tagMatchStrategy,
                                    "reason", "Tag '" + tag + "' does not exist");
                        }
                    } else {
                        foundResources.addAll(resources);
                    }
                }
                return new ArrayList<>(foundResources);
            }

            if (tagMatchStrategy == TagMatchStrategy.ALL) {
                return findResourcesWithAllTags(tagsToSearch, skipIfNotExists);
            }

            throw buildError(StatusCode.RESOURCE_TAG_FIND_RESOURCE_ERROR,
                    "tags", originalTags,
                    "tag", originalTags,
                    "strategy", tagMatchStrategy,
                    "reason", "Unsupported tag match strategy");
        } finally {
            lock.unlock();
        }
    }

    private List<String> setGlobalResource(String resourceId) {
        List<String> oldTags = new ArrayList<>(resourceTags.getOrDefault(resourceId, Collections.emptySet()));
        for (String oldTag : oldTags) {
            Set<String> resources = tagToResource.get(oldTag);
            if (resources != null) {
                resources.remove(resourceId);
                if (resources.isEmpty() && !ResourceManagerBase.GLOBAL.equals(oldTag)) {
                    tagToResource.remove(oldTag);
                }
            }
        }

        Set<String> globalSet = new LinkedHashSet<>();
        globalSet.add(ResourceManagerBase.GLOBAL);
        resourceTags.put(resourceId, globalSet);
        tagToResource.computeIfAbsent(ResourceManagerBase.GLOBAL, ignored -> new LinkedHashSet<>()).add(resourceId);
        return oldTags;
    }

    private List<String> addResourceTags(String resourceId, Set<String> tagsToAdd) {
        if (!resourceTags.containsKey(resourceId)) {
            return Collections.emptyList();
        }

        Set<String> currentTags = resourceTags.get(resourceId);
        if (currentTags.contains(ResourceManagerBase.GLOBAL)) {
            return List.of(ResourceManagerBase.GLOBAL);
        }

        currentTags.addAll(tagsToAdd);
        for (String tag : tagsToAdd) {
            tagToResource.computeIfAbsent(tag, ignored -> new LinkedHashSet<>()).add(resourceId);
        }
        return new ArrayList<>(currentTags);
    }

    private List<String> removeResourceInternal(String resourceId) {
        Set<String> tags = resourceTags.get(resourceId);
        if (tags == null) {
            return Collections.emptyList();
        }

        List<String> removedTags = new ArrayList<>(tags);
        for (String tag : removedTags) {
            Set<String> resources = tagToResource.get(tag);
            if (resources != null) {
                resources.remove(resourceId);
                if (resources.isEmpty() && !ResourceManagerBase.GLOBAL.equals(tag)) {
                    tagToResource.remove(tag);
                }
            }
        }

        resourceTags.remove(resourceId);
        return removedTags;
    }

    private List<String> removeResourceTagsInternal(String resourceId, Set<String> tagsToRemove) {
        if (!resourceTags.containsKey(resourceId)) {
            return Collections.emptyList();
        }

        Set<String> currentTags = resourceTags.get(resourceId);
        for (String tag : tagsToRemove) {
            if (currentTags.contains(tag)) {
                currentTags.remove(tag);
                Set<String> resources = tagToResource.get(tag);
                if (resources != null) {
                    resources.remove(resourceId);
                    if (resources.isEmpty() && !ResourceManagerBase.GLOBAL.equals(tag)) {
                        tagToResource.remove(tag);
                    }
                }
            }
        }

        List<String> remainingTags = new ArrayList<>(currentTags);
        if (currentTags.isEmpty()) {
            resourceTags.remove(resourceId);
        }
        return remainingTags;
    }

    private List<String> replaceResourceTags(String resourceId, Set<String> newTags) {
        if (!resourceTags.containsKey(resourceId)) {
            return Collections.emptyList();
        }

        Set<String> oldTags = resourceTags.get(resourceId);
        for (String oldTag : new ArrayList<>(oldTags)) {
            Set<String> resources = tagToResource.get(oldTag);
            if (resources != null) {
                resources.remove(resourceId);
                if (resources.isEmpty() && !ResourceManagerBase.GLOBAL.equals(oldTag)) {
                    tagToResource.remove(oldTag);
                }
            }
        }

        Set<String> newTagSet = new LinkedHashSet<>(newTags);
        resourceTags.put(resourceId, newTagSet);
        for (String tag : newTags) {
            tagToResource.computeIfAbsent(tag, ignored -> new LinkedHashSet<>()).add(resourceId);
        }
        return new ArrayList<>(newTags);
    }

    private List<String> removeTagInternal(String tag) {
        if (!tagToResource.containsKey(tag)) {
            return Collections.emptyList();
        }

        List<String> affectedResources = new ArrayList<>(tagToResource.get(tag));
        for (String resourceId : affectedResources) {
            Set<String> tags = resourceTags.get(resourceId);
            if (tags != null) {
                tags.remove(tag);
                if (tags.isEmpty()) {
                    resourceTags.remove(resourceId);
                }
            }
        }

        tagToResource.remove(tag);
        return affectedResources;
    }

    private List<String> findResourcesWithAllTags(Set<String> requiredTags, boolean skipIfNotExists) {
        if (requiredTags.isEmpty()) {
            return Collections.emptyList();
        }

        for (String tag : requiredTags) {
            if (!tagToResource.containsKey(tag)) {
                if (!isBuiltinTag(tag) && !skipIfNotExists) {
                    throw buildError(StatusCode.RESOURCE_TAG_FIND_RESOURCE_ERROR,
                            "tags", requiredTags,
                            "tag", requiredTags,
                            "strategy", TagMatchStrategy.ALL,
                            "reason", "Tag '" + tag + "' does not exist");
                }
            }
        }

        String firstTag = requiredTags.iterator().next();
        Set<String> foundResources = new LinkedHashSet<>(tagToResource.getOrDefault(firstTag, Collections.emptySet()));
        for (String tag : requiredTags) {
            foundResources.retainAll(tagToResource.getOrDefault(tag, Collections.emptySet()));
        }
        return new ArrayList<>(foundResources);
    }

    static Set<String> normalizeTags(String tag) {
        Set<String> normalized = new LinkedHashSet<>();
        normalized.add(tag);
        return normalized;
    }

    static Set<String> normalizeTags(Collection<String> tags) {
        if (tags == null) {
            return normalizeTags((String) null);
        }
        return new LinkedHashSet<>(tags);
    }

    static boolean isBuiltinTag(String tag) {
        return ResourceManagerBase.GLOBAL.equals(tag);
    }

    private static BaseError buildError(StatusCode status, Object... kvPairs) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int index = 0; index + 1 < kvPairs.length; index += 2) {
            params.put(String.valueOf(kvPairs[index]), kvPairs[index + 1]);
        }
        return ErrorHelper.buildError(status, null, null, null, params);
    }

    Set<String> resourceTagsForTest(String resourceId) {
        lock.lock();
        try {
            return new HashSet<>(resourceTags.getOrDefault(resourceId, Collections.emptySet()));
        } finally {
            lock.unlock();
        }
    }
}
