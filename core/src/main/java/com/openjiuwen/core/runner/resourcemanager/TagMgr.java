/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.base.TagUpdateStrategy;

import java.util.Collection;
import java.util.List;

/**
 * Backward-compatible 0.1.12 resource tag manager facade.
 *
 * <p>Mirrors Python's {@code TagMgr} in
 * {@code openjiuwen/core/runner/resources_manager/tag_manager.py}.</p>
 */
public class TagMgr extends TagManager {

    public List<String> tagResource(String resourceId, Object tags) {
        return super.tagResource(resourceId, normalizeTagsObject(tags));
    }

    public List<String> removeResourceTags(String resourceId, Object tags, boolean skipIfNotExists) {
        return super.removeResourceTags(resourceId, normalizeTagsObject(tags), skipIfNotExists);
    }

    public List<String> updateResourceTags(String resourceId, Object tags, TagUpdateStrategy strategy) {
        com.openjiuwen.core.runner.resourcemanager.TagUpdateStrategy targetStrategy = strategy == null
                ? com.openjiuwen.core.runner.resourcemanager.TagUpdateStrategy.MERGE
                : strategy.toResourceManagerStrategy();
        return super.updateResourceTags(resourceId, normalizeTagsObject(tags), targetStrategy);
    }

    public List<String> findResourcesByTags(Object tags, TagMatchStrategy strategy, boolean skipIfNotExists) {
        com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy targetStrategy = strategy == null
                ? com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy.ANY
                : strategy.toResourceManagerStrategy();
        return super.findResourcesByTags(normalizeTagsObject(tags), targetStrategy, skipIfNotExists);
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> normalizeTagsObject(Object tags) {
        if (tags == null) {
            return List.of();
        }
        if (tags instanceof String tag) {
            return List.of(tag);
        }
        if (tags instanceof Collection<?> collection) {
            return (Collection<String>) collection;
        }
        return List.of(String.valueOf(tags));
    }
}
