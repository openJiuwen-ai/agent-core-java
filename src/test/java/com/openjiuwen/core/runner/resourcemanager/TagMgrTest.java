// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.base.TagUpdateStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TagMgr: tag/untag, find by tags, match strategies.
 * Translated from Python test_tag_manager.py
 */
@DisplayName("TagMgr Tests")
class TagMgrTest {

    private TagMgr tagMgr;

    @BeforeEach
    void setup() {
        tagMgr = new TagMgr();
        // Initialize test resources via tagResource calls (mirrors Python's direct _resource_tags setup)
        tagMgr.tagResource("res1", List.of("tag1", "tag2"));
        tagMgr.tagResource("res2", List.of("tag2", "tag3"));
        tagMgr.tagResource("res3", Tag.GLOBAL);
        tagMgr.tagResource("res4", List.of("tag1", "tag3", "tag4"));
    }

    @Test
    @DisplayName("hasTag returns true for existing tag")
    void testHasTag() {
        assertTrue(tagMgr.hasTag("tag1"));
        assertFalse(tagMgr.hasTag("tag5"));
        assertTrue(tagMgr.hasTag(Tag.GLOBAL));
    }

    @Test
    @DisplayName("listTags returns all tags with resources")
    void testListTags() {
        List<String> tags = tagMgr.listTags();
        assertTrue(tags.contains("tag1"));
        assertTrue(tags.contains("tag2"));
        assertTrue(tags.contains("tag3"));
        assertTrue(tags.contains("tag4"));
        assertTrue(tags.contains(Tag.GLOBAL));
        assertEquals(5, tags.size());
    }

    @Test
    @DisplayName("hasResource returns true for existing resource")
    void testHasResource() {
        assertTrue(tagMgr.hasResource("res1"));
        assertFalse(tagMgr.hasResource("res5"));
    }

    @Test
    @DisplayName("tagResource adds new tags")
    void testTagResourceNormal() {
        List<String> currentTags = tagMgr.tagResource("res1", List.of("tag5", "tag6"));
        assertTrue(currentTags.contains("tag5"));
        assertTrue(currentTags.contains("tag6"));
        assertTrue(currentTags.contains("tag1"));
        assertTrue(currentTags.contains("tag2"));
    }

    @Test
    @DisplayName("tagResource with GLOBAL replaces all tags")
    void testTagResourceWithGlobal() {
        List<String> currentTags = tagMgr.tagResource("res1", Tag.GLOBAL);
        assertEquals(List.of(Tag.GLOBAL), currentTags);
        assertTrue(tagMgr.hasResourceTag("res1", Tag.GLOBAL));
    }

    @Test
    @DisplayName("removeResource removes resource and its tag mappings")
    void testRemoveResource() {
        List<String> removedTags = tagMgr.removeResource("res1");
        assertEquals(Set.of("tag1", "tag2"), new HashSet<>(removedTags));
        assertFalse(tagMgr.hasResource("res1"));

        // tag1 should no longer contain res1
        List<String> tag1Resources = tagMgr.getTagResources("tag1");
        assertFalse(tag1Resources.contains("res1"));
        List<String> tag2Resources = tagMgr.getTagResources("tag2");
        assertFalse(tag2Resources.contains("res1"));
    }

    @Test
    @DisplayName("removeResource of nonexistent returns empty list")
    void testRemoveNonexistentResource() {
        List<String> result = tagMgr.removeResource("res99");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("removeResourceTags removes specified tags from resource")
    void testRemoveResourceTags() {
        List<String> remaining = tagMgr.removeResourceTags("res1", List.of("tag1", "tag3"), true);
        assertFalse(remaining.contains("tag1"));
        assertTrue(remaining.contains("tag2"));

        // tag1 should no longer map to res1
        List<String> tag1Resources = tagMgr.getTagResources("tag1");
        assertFalse(tag1Resources.contains("res1"));
    }

    @Test
    @DisplayName("updateResourceTags REPLACE strategy")
    void testUpdateResourceTagsReplace() {
        List<String> currentTags = tagMgr.updateResourceTags("res1",
                List.of("tag5", "tag6"), TagUpdateStrategy.REPLACE);
        assertEquals(Set.of("tag5", "tag6"), new HashSet<>(currentTags));
        assertFalse(currentTags.contains("tag1"));
        assertFalse(currentTags.contains("tag2"));

        // Old tag mappings cleaned up
        List<String> tag1Resources = tagMgr.getTagResources("tag1");
        assertFalse(tag1Resources.contains("res1"));
        List<String> tag2Resources = tagMgr.getTagResources("tag2");
        assertFalse(tag2Resources.contains("res1"));

        // New tag mappings added
        List<String> tag5Resources = tagMgr.getTagResources("tag5");
        assertTrue(tag5Resources.contains("res1"));
        List<String> tag6Resources = tagMgr.getTagResources("tag6");
        assertTrue(tag6Resources.contains("res1"));
    }

    @Test
    @DisplayName("updateResourceTags MERGE strategy")
    void testUpdateResourceTagsMerge() {
        List<String> currentTags = tagMgr.updateResourceTags("res1",
                List.of("tag5", "tag6"), TagUpdateStrategy.MERGE);
        assertTrue(currentTags.contains("tag1"));
        assertTrue(currentTags.contains("tag2"));
        assertTrue(currentTags.contains("tag5"));
        assertTrue(currentTags.contains("tag6"));
    }

    @Test
    @DisplayName("updateResourceTags to GLOBAL")
    void testUpdateToGlobal() {
        List<String> oldTags = tagMgr.updateResourceTags("res1",
                Tag.GLOBAL, TagUpdateStrategy.REPLACE);
        assertEquals(List.of(Tag.GLOBAL), oldTags);
        assertTrue(tagMgr.hasResourceTag("res1", Tag.GLOBAL));
    }

    @Test
    @DisplayName("removeTag removes tag and cleans up resources")
    void testRemoveTag() {
        List<String> affected = tagMgr.removeTag("tag1", false);
        assertTrue(affected.contains("res1"));
        assertTrue(affected.contains("res4"));
        assertFalse(tagMgr.hasTag("tag1"));

        // Resources should no longer have tag1
        List<String> res1Tags = tagMgr.getResourcesTags("res1");
        assertFalse(res1Tags.contains("tag1"));
        List<String> res4Tags = tagMgr.getResourcesTags("res4");
        assertFalse(res4Tags.contains("tag1"));
    }

    @Test
    @DisplayName("getTagResources returns correct resources")
    void testGetTagResources() {
        List<String> resources = tagMgr.getTagResources("tag1");
        assertTrue(resources.contains("res1"));
        assertTrue(resources.contains("res4"));
        assertEquals(2, resources.size());
    }

    @Test
    @DisplayName("findResourcesByTags ANY strategy")
    void testFindResourcesByTagsAny() {
        List<String> resources = tagMgr.findResourcesByTags(
                List.of("tag1", "tag3"), TagMatchStrategy.ANY, true);
        assertTrue(resources.contains("res1")); // has tag1
        assertTrue(resources.contains("res2")); // has tag3
        assertTrue(resources.contains("res4")); // has tag1 and tag3
        assertFalse(resources.contains("res3")); // GLOBAL
    }

    @Test
    @DisplayName("findResourcesByTags ALL strategy")
    void testFindResourcesByTagsAll() {
        List<String> resources = tagMgr.findResourcesByTags(
                List.of("tag1", "tag3"), TagMatchStrategy.ALL, true);
        assertFalse(resources.contains("res1")); // only tag1
        assertFalse(resources.contains("res2")); // only tag3
        assertTrue(resources.contains("res4"));   // both tag1 and tag3
        assertFalse(resources.contains("res3"));  // GLOBAL
    }

    @Test
    @DisplayName("findResourcesByTags with nonexistent tag throws exception")
    void testFindResourcesWithNonexistentTag() {
        assertThrows(Exception.class, () ->
                tagMgr.findResourcesByTags(List.of("tag99"), TagMatchStrategy.ANY, false));
    }

    @Test
    @DisplayName("findResourcesByTags skip nonexistent tag")
    void testFindResourcesSkipNonexistentTag() {
        List<String> resources = tagMgr.findResourcesByTags(
                List.of("tag1", "tag99"), TagMatchStrategy.ANY, true);
        assertTrue(resources.contains("res1"));
        assertTrue(resources.contains("res4"));
    }

    @Test
    @DisplayName("hasResourceTag checks specific tag on resource")
    void testHasResourceTag() {
        assertTrue(tagMgr.hasResourceTag("res1", "tag1"));
        assertFalse(tagMgr.hasResourceTag("res1", "tag3"));
        assertTrue(tagMgr.hasResourceTag("res3", Tag.GLOBAL));
    }

    @Test
    @DisplayName("getResourcesTags returns resource tags")
    void testGetResourcesTags() {
        List<String> tags = tagMgr.getResourcesTags("res1");
        assertEquals(Set.of("tag1", "tag2"), new HashSet<>(tags));
    }

    @Test
    @DisplayName("getResourcesTags for nonexistent resource returns null")
    void testGetResourcesTagsNonexistent() {
        List<String> tags = tagMgr.getResourcesTags("res99");
        assertNull(tags);
    }
}
