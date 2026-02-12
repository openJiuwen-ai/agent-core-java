// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 标签管理器
 * 
 * 对应Python: resources_manager/tag_manager.py - TagMgr
 */
public class TagMgr {
    
    private static final Logger logger = LoggerFactory.getLogger(TagMgr.class);
    
    private final Map<String, Set<String>> resourceTags = new HashMap<>();
    private final Map<String, Set<String>> tagToResource = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    
    public TagMgr() {
        // 初始化GLOBAL标签
        tagToResource.put(Tag.GLOBAL, new HashSet<>());
    }
    
    /**
     * 检查标签是否存在
     */
    public boolean hasTag(String tag) {
        lock.lock();
        try {
            return tagToResource.containsKey(tag);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取所有标签（排除空标签）
     */
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
    
    /**
     * 检查资源是否存在
     */
    public boolean hasResource(String resourceId) {
        lock.lock();
        try {
            return resourceTags.containsKey(resourceId);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 为资源添加标签（原子操作）
     */
    public List<String> tagResource(String resourceId, Object tags) {
        Set<String> tagsToAdd = normalizeTags(tags);
        
        lock.lock();
        try {
            // 检查资源是否存在，不存在则创建
            resourceTags.computeIfAbsent(resourceId, k -> new HashSet<>());
            
            // 检查是否包含GLOBAL标签
            if (tagsToAdd.contains(Tag.GLOBAL)) {
                setGlobalResource(resourceId);
                logger.info("Added GLOBAL tag to resource. resource_id=" + resourceId);
                return Collections.singletonList(Tag.GLOBAL);
            }
            
            // 添加标签
            List<String> currentTags = addResourceTags(resourceId, tagsToAdd);
            logger.info("Added tags to resource. resource_id=" + resourceId + 
                ", added_tags=" + tagsToAdd + ", current_tags=" + currentTags);
            return currentTags;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 为资源添加单个标签
     */
    public List<String> tagResource(String resourceId, String tag) {
        return tagResource(resourceId, Collections.singletonList(tag));
    }
    
    /**
     * 完全移除资源及其所有标签
     */
    public List<String> removeResource(String resourceId) {
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                return Collections.emptyList();
            }
            
            List<String> removedTags = doRemoveResource(resourceId);
            logger.info("Removed resource. resource_id=" + resourceId + ", removed_tags=" + removedTags);
            return removedTags;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 从资源移除指定标签
     */
    public List<String> removeResourceTags(String resourceId, Object tags) {
        return removeResourceTags(resourceId, tags, false);
    }
    
    /**
     * 从资源移除指定标签
     */
    public List<String> removeResourceTags(String resourceId, Object tags, boolean skipIfNotExists) {
        Set<String> tagsToRemove = normalizeTags(tags);
        
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                throw ErrorBuilder.build(
                    StatusCode.RESOURCE_TAG_REMOVE_RESOURCE_TAG_ERROR,
                    "Resource does not exist: " + resourceId
                );
            }
            
            List<String> remainingTags = doRemoveResourceTags(resourceId, tagsToRemove);
            logger.info("Removed tags from resource. resource_id=" + resourceId + 
                ", removed_tags=" + tagsToRemove + ", remaining_tags=" + remainingTags);
            return remainingTags;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 更新资源标签
     */
    public List<String> updateResourceTags(String resourceId, Object tags, TagUpdateStrategy strategy) {
        Set<String> newTags = normalizeTags(tags);
        
        lock.lock();
        try {
            if (!resourceTags.containsKey(resourceId)) {
                throw ErrorBuilder.build(
                    StatusCode.RESOURCE_TAG_REPLACE_RESOURCE_TAG_ERROR,
                    "Resource does not exist: " + resourceId
                );
            }
            
            // 检查是否包含GLOBAL标签
            if (newTags.contains(Tag.GLOBAL)) {
                setGlobalResource(resourceId);
                logger.info("Updated resource to GLOBAL. resource_id=" + resourceId + ", strategy=" + strategy);
                return Collections.singletonList(Tag.GLOBAL);
            }
            
            List<String> currentTags;
            if (strategy == TagUpdateStrategy.REPLACE) {
                currentTags = replaceResourceTags(resourceId, newTags);
                logger.info("Replaced resource tags. resource_id=" + resourceId + ", new_tags=" + newTags);
            } else if (strategy == TagUpdateStrategy.MERGE) {
                currentTags = addResourceTags(resourceId, newTags);
                logger.info("Merged resource tags. resource_id=" + resourceId + 
                    ", added_tags=" + newTags + ", current_tags=" + currentTags);
            } else {
                throw ErrorBuilder.build(
                    StatusCode.RESOURCE_TAG_REPLACE_RESOURCE_TAG_ERROR,
                    "Unsupported strategy: " + strategy
                );
            }
            
            return currentTags;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 完全移除标签及其所有关联
     */
    public List<String> removeTag(String tag) {
        return removeTag(tag, false);
    }
    
    /**
     * 完全移除标签及其所有关联
     */
    public List<String> removeTag(String tag, boolean skipIfNotExists) {
        lock.lock();
        try {
            if (!tagToResource.containsKey(tag)) {
                if (skipIfNotExists) {
                    return Collections.emptyList();
                }
                throw ErrorBuilder.build(
                    StatusCode.RESOURCE_TAG_REMOVE_TAG_ERROR,
                    "Tag does not exist: " + tag
                );
            }
            
            List<String> affectedResources = doRemoveTag(tag);
            logger.info("Removed tag. tag='" + tag + "', affected_resources=" + affectedResources);
            return affectedResources;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取指定标签的所有资源
     */
    public List<String> getTagResources(String tag) {
        lock.lock();
        try {
            Set<String> resources = tagToResource.get(tag);
            return resources != null ? new ArrayList<>(resources) : Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 根据标签查找资源
     */
    public List<String> findResourcesByTags(Object tags, TagMatchStrategy strategy, boolean skipIfNotExists) {
        Set<String> tagsToSearch = normalizeTags(tags);
        
        lock.lock();
        try {
            if (strategy == TagMatchStrategy.ANY) {
                Set<String> foundResources = new HashSet<>();
                for (String tag : tagsToSearch) {
                    Set<String> resources = tagToResource.get(tag);
                    if (resources == null || resources.isEmpty()) {
                        if (!isBuiltinTag(tag) && !skipIfNotExists) {
                            throw ErrorBuilder.build(
                                StatusCode.RESOURCE_TAG_FIND_RESOURCE_ERROR,
                                "Tag '" + tag + "' does not exist"
                            );
                        }
                    } else {
                        foundResources.addAll(resources);
                    }
                }
                return new ArrayList<>(foundResources);
            } else if (strategy == TagMatchStrategy.ALL) {
                return findResourcesWithAllTags(tagsToSearch, skipIfNotExists);
            } else {
                throw ErrorBuilder.build(
                    StatusCode.RESOURCE_TAG_FIND_RESOURCE_ERROR,
                    "Unsupported tag match strategy: " + strategy
                );
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 检查资源是否有指定标签
     */
    public boolean hasResourceTag(String resourceId, String tag) {
        lock.lock();
        try {
            Set<String> tags = resourceTags.get(resourceId);
            return tags != null && tags.contains(tag);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取资源的所有标签
     */
    public List<String> getResourceTags(String resourceId) {
        lock.lock();
        try {
            Set<String> tags = resourceTags.get(resourceId);
            return tags != null ? new ArrayList<>(tags) : Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 显示当前状态
     */
    public String display(boolean enableLog) {
        lock.lock();
        try {
            StringBuilder msg = new StringBuilder("\nTag -> Resource IDs:\n");
            tagToResource.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(e -> !e.getValue().isEmpty())
                .forEach(e -> {
                    List<String> sortedIds = new ArrayList<>(e.getValue());
                    Collections.sort(sortedIds);
                    msg.append("  tag['").append(e.getKey()).append("']: [")
                        .append(String.join(", ", sortedIds)).append("]\n");
                });
            
            msg.append("\nResource -> Tags:\n");
            resourceTags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    List<String> sortedTags = new ArrayList<>(e.getValue());
                    Collections.sort(sortedTags);
                    msg.append("  resource['").append(e.getKey()).append("']: [")
                        .append(String.join(", ", sortedTags)).append("]\n");
                });
            
            msg.append("\nStatistics:\n");
            msg.append("  Total tags: ").append(tagToResource.size()).append("\n");
            msg.append("  Total resources: ").append(resourceTags.size()).append("\n");
            msg.append("  GLOBAL resources: ").append(
                tagToResource.getOrDefault(Tag.GLOBAL, Collections.emptySet()).size()).append("\n");
            
            String result = msg.toString();
            if (enableLog) {
                logger.info("---- Tag Manager State ----\n" + result);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }
    
    // ============================================================================
    // 私有辅助方法
    // ============================================================================
    
    private List<String> setGlobalResource(String resourceId) {
        // 获取旧标签
        Set<String> oldTags = resourceTags.getOrDefault(resourceId, Collections.emptySet());
        List<String> oldTagsList = new ArrayList<>(oldTags);
        
        // 从tagToResource中移除旧标签关联
        for (String oldTag : oldTags) {
            Set<String> resources = tagToResource.get(oldTag);
            if (resources != null) {
                resources.remove(resourceId);
                if (resources.isEmpty() && !oldTag.equals(Tag.GLOBAL)) {
                    tagToResource.remove(oldTag);
                }
            }
        }
        
        // 更新resourceTags
        resourceTags.put(resourceId, new HashSet<>(Collections.singleton(Tag.GLOBAL)));
        
        // 更新tagToResource
        tagToResource.computeIfAbsent(Tag.GLOBAL, k -> new HashSet<>()).add(resourceId);
        
        return oldTagsList;
    }
    
    private List<String> addResourceTags(String resourceId, Set<String> tagsToAdd) {
        Set<String> currentTags = resourceTags.get(resourceId);
        if (currentTags == null) {
            return Collections.emptyList();
        }
        
        // 如果已是GLOBAL资源，不能添加其他标签
        if (currentTags.contains(Tag.GLOBAL)) {
            return Collections.singletonList(Tag.GLOBAL);
        }
        
        // 添加新标签
        currentTags.addAll(tagsToAdd);
        
        // 更新tagToResource
        for (String tag : tagsToAdd) {
            tagToResource.computeIfAbsent(tag, k -> new HashSet<>()).add(resourceId);
        }
        
        return new ArrayList<>(currentTags);
    }
    
    private List<String> doRemoveResource(String resourceId) {
        Set<String> tags = resourceTags.get(resourceId);
        if (tags == null) {
            return Collections.emptyList();
        }
        
        List<String> tagsList = new ArrayList<>(tags);
        
        // 从tagToResource中移除关联
        for (String tag : tags) {
            Set<String> resources = tagToResource.get(tag);
            if (resources != null) {
                resources.remove(resourceId);
                if (resources.isEmpty() && !tag.equals(Tag.GLOBAL)) {
                    tagToResource.remove(tag);
                }
            }
        }
        
        // 从resourceTags中移除资源
        resourceTags.remove(resourceId);
        
        return tagsList;
    }
    
    private List<String> doRemoveResourceTags(String resourceId, Set<String> tagsToRemove) {
        Set<String> currentTags = resourceTags.get(resourceId);
        if (currentTags == null) {
            return Collections.emptyList();
        }
        
        // 移除指定标签
        for (String tag : tagsToRemove) {
            if (currentTags.contains(tag)) {
                currentTags.remove(tag);
                Set<String> resources = tagToResource.get(tag);
                if (resources != null) {
                    resources.remove(resourceId);
                    if (resources.isEmpty() && !tag.equals(Tag.GLOBAL)) {
                        tagToResource.remove(tag);
                    }
                }
            }
        }
        
        // 如果资源没有标签了，移除资源
        if (currentTags.isEmpty()) {
            resourceTags.remove(resourceId);
        }
        
        return new ArrayList<>(currentTags);
    }
    
    private List<String> replaceResourceTags(String resourceId, Set<String> newTags) {
        Set<String> oldTags = resourceTags.get(resourceId);
        if (oldTags == null) {
            return Collections.emptyList();
        }
        
        // 从tagToResource中移除旧标签关联
        for (String oldTag : oldTags) {
            Set<String> resources = tagToResource.get(oldTag);
            if (resources != null) {
                resources.remove(resourceId);
                if (resources.isEmpty() && !oldTag.equals(Tag.GLOBAL)) {
                    tagToResource.remove(oldTag);
                }
            }
        }
        
        // 设置新标签
        resourceTags.put(resourceId, new HashSet<>(newTags));
        
        // 更新tagToResource
        for (String tag : newTags) {
            tagToResource.computeIfAbsent(tag, k -> new HashSet<>()).add(resourceId);
        }
        
        return new ArrayList<>(newTags);
    }
    
    private List<String> doRemoveTag(String tag) {
        Set<String> affectedResourcesSet = tagToResource.get(tag);
        if (affectedResourcesSet == null) {
            return Collections.emptyList();
        }
        
        List<String> affectedResources = new ArrayList<>(affectedResourcesSet);
        
        // 从每个资源的标签集中移除此标签
        for (String resourceId : affectedResources) {
            Set<String> tags = resourceTags.get(resourceId);
            if (tags != null) {
                tags.remove(tag);
                if (tags.isEmpty()) {
                    resourceTags.remove(resourceId);
                }
            }
        }
        
        // 从tagToResource中移除标签
        tagToResource.remove(tag);
        
        return affectedResources;
    }
    
    private List<String> findResourcesWithAllTags(Set<String> requiredTags, boolean skipIfNotExists) {
        if (requiredTags.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 检查所有标签是否存在
        for (String tag : requiredTags) {
            if (!tagToResource.containsKey(tag)) {
                if (!isBuiltinTag(tag) && !skipIfNotExists) {
                    throw ErrorBuilder.build(
                        StatusCode.RESOURCE_TAG_FIND_RESOURCE_ERROR,
                        "Tag '" + tag + "' does not exist"
                    );
                }
            }
        }
        
        // 获取第一个标签的资源作为初始集合
        Iterator<String> iter = requiredTags.iterator();
        String firstTag = iter.next();
        Set<String> foundResources = new HashSet<>(
            tagToResource.getOrDefault(firstTag, Collections.emptySet()));
        
        // 取交集：必须有所有标签
        while (iter.hasNext()) {
            String tag = iter.next();
            Set<String> resources = tagToResource.getOrDefault(tag, Collections.emptySet());
            foundResources.retainAll(resources);
        }
        
        return new ArrayList<>(foundResources);
    }
    
    /**
     * 标准化标签输入为Set
     */
    @SuppressWarnings("unchecked")
    public static Set<String> normalizeTags(Object tags) {
        if (tags instanceof String) {
            return new HashSet<>(Collections.singleton((String) tags));
        } else if (tags instanceof Collection) {
            return new HashSet<>((Collection<String>) tags);
        }
        throw new IllegalArgumentException("Tags must be a String or Collection<String>");
    }
    
    /**
     * 检查是否为内置标签
     */
    public static boolean isBuiltinTag(String tag) {
        return Tag.GLOBAL.equals(tag);
    }
}

