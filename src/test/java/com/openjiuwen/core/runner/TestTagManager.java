/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner;

import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.base.TagUpdateStrategy;
import com.openjiuwen.core.runner.resourcemanager.TagMgr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TagMgr.
 * Mirrors Python's tests/unit_tests/core/runner/test_tag_manager.py
 */
class TestTagManager {

    private TagMgr tagMgr;

    @BeforeEach
    void setUp() {
        tagMgr = new TagMgr();
        tagMgr.tagResource("res1", Arrays.asList("tag1", "tag2"));
        tagMgr.tagResource("res2", Arrays.asList("tag2", "tag3"));
        tagMgr.tagResource("res3", Tag.GLOBAL);
        tagMgr.tagResource("res4", Arrays.asList("tag1", "tag3", "tag4"));
    }

    @Nested
    @DisplayName("hasTag tests")
    class HasTagTests {

        @Test
        @DisplayName("test has_tag returns true for existing tag")
        void testHasTagTrue() {
            assertTrue(tagMgr.hasTag("tag1"));
        }

        @Test
        @DisplayName("test has_tag returns false for non-existing tag")
        void testHasTagFalse() {
            assertFalse(tagMgr.hasTag("tag5"));
        }

        @Test
        @DisplayName("test has_tag returns true for GLOBAL tag")
        void testHasTagGlobal() {
            assertTrue(tagMgr.hasTag(Tag.GLOBAL));
        }
    }

    @Nested
    @DisplayName("listTags tests")
    class ListTagsTests {

        @Test
        @DisplayName("test list_tags returns all tags")
        void testListTags() {
            List<String> tags = tagMgr.listTags();
            assertTrue(tags.contains("tag1"));
            assertTrue(tags.contains("tag2"));
            assertTrue(tags.contains("tag3"));
            assertTrue(tags.contains("tag4"));
            assertTrue(tags.contains(Tag.GLOBAL));
            assertEquals(5, tags.size());
        }
    }

    @Nested
    @DisplayName("hasResource tests")
    class HasResourceTests {

        @Test
        @DisplayName("test has_resource returns true for existing resource")
        void testHasResourceTrue() {
            assertTrue(tagMgr.hasResource("res1"));
        }

        @Test
        @DisplayName("test has_resource returns false for non-existing resource")
        void testHasResourceFalse() {
            assertFalse(tagMgr.hasResource("res5"));
        }
    }

    @Nested
    @DisplayName("tagResource tests")
    class TagResourceTests {

        @Test
        @DisplayName("test tag_resource normal - add new tags")
        void testTagResourceNormal() {
            List<String> currentTags = tagMgr.tagResource("res1", Arrays.asList("tag5", "tag6"));
            assertTrue(currentTags.contains("tag5"));
            assertTrue(currentTags.contains("tag6"));
            assertTrue(currentTags.contains("tag1"));
            assertTrue(currentTags.contains("tag2"));

            List<String> tag5Resources = tagMgr.getTagResources("tag5");
            assertTrue(tag5Resources.contains("res1"));
            List<String> tag6Resources = tagMgr.getTagResources("tag6");
            assertTrue(tag6Resources.contains("res1"));
        }

        @Test
        @DisplayName("test tag_resource with GLOBAL tag")
        void testTagResourceWithGlobal() {
            List<String> currentTags = tagMgr.tagResource("res1", Tag.GLOBAL);
            assertEquals(List.of(Tag.GLOBAL), currentTags);
            List<String> res1Tags = tagMgr.getResourcesTags("res1");
            assertEquals(Set.of(Tag.GLOBAL), new HashSet<>(res1Tags));
            List<String> globalResources = tagMgr.getTagResources(Tag.GLOBAL);
            assertTrue(globalResources.contains("res1"));
        }
    }

    @Nested
    @DisplayName("removeResource tests")
    class RemoveResourceTests {

        @Test
        @DisplayName("test remove_resource removes resource and returns tags")
        void testRemoveResource() {
            List<String> removedTags = tagMgr.removeResource("res1");
            assertEquals(Set.of("tag1", "tag2"), new HashSet<>(removedTags));
            assertFalse(tagMgr.hasResource("res1"));

            List<String> tag1Resources = tagMgr.getTagResources("tag1");
            assertFalse(tag1Resources.contains("res1"));
            List<String> tag2Resources = tagMgr.getTagResources("tag2");
            assertFalse(tag2Resources.contains("res1"));
        }

        @Test
        @DisplayName("test remove_resource returns empty list for non-existing resource")
        void testRemoveNonexistentResource() {
            List<String> result = tagMgr.removeResource("res99");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("removeResourceTags tests")
    class RemoveResourceTagsTests {

        @Test
        @DisplayName("test remove_resource_tags removes specified tags")
        void testRemoveResourceTags() {
            List<String> remainingTags = tagMgr.removeResourceTags("res1", Arrays.asList("tag1", "tag3"), true);
            assertFalse(remainingTags.contains("tag1"));
            assertTrue(remainingTags.contains("tag2"));

            List<String> tag1Resources = tagMgr.getTagResources("tag1");
            assertFalse(tag1Resources.contains("res1"));
        }
    }

    @Nested
    @DisplayName("updateResourceTags tests")
    class UpdateResourceTagsTests {

        @Test
        @DisplayName("test update_resource_tags with REPLACE strategy")
        void testUpdateResourceTagsReplace() {
            List<String> newTags = Arrays.asList("tag5", "tag6");
            List<String> currentTags = tagMgr.updateResourceTags("res1", newTags, TagUpdateStrategy.REPLACE);
            assertEquals(new HashSet<>(newTags), new HashSet<>(currentTags));
            assertFalse(currentTags.contains("tag1"));
            assertFalse(currentTags.contains("tag2"));

            List<String> tag1Resources = tagMgr.getTagResources("tag1");
            assertFalse(tag1Resources.contains("res1"));
            List<String> tag2Resources = tagMgr.getTagResources("tag2");
            assertFalse(tag2Resources.contains("res1"));
            List<String> tag5Resources = tagMgr.getTagResources("tag5");
            assertTrue(tag5Resources.contains("res1"));
            List<String> tag6Resources = tagMgr.getTagResources("tag6");
            assertTrue(tag6Resources.contains("res1"));
        }

        @Test
        @DisplayName("test update_resource_tags with MERGE strategy")
        void testUpdateResourceTagsMerge() {
            List<String> newTags = Arrays.asList("tag5", "tag6");
            List<String> currentTags = tagMgr.updateResourceTags("res1", newTags, TagUpdateStrategy.MERGE);
            assertTrue(currentTags.contains("tag1"));
            assertTrue(currentTags.contains("tag2"));
            assertTrue(currentTags.contains("tag5"));
            assertTrue(currentTags.contains("tag6"));
        }

        @Test
        @DisplayName("test update_resource_tags to GLOBAL")
        void testUpdateToGlobal() {
            List<String> oldTags = tagMgr.updateResourceTags("res1", Tag.GLOBAL, TagUpdateStrategy.REPLACE);
            assertEquals(List.of(Tag.GLOBAL), oldTags);
            List<String> res1Tags = tagMgr.getResourcesTags("res1");
            assertEquals(Set.of(Tag.GLOBAL), new HashSet<>(res1Tags));
            List<String> globalResources = tagMgr.getTagResources(Tag.GLOBAL);
            assertTrue(globalResources.contains("res1"));
        }
    }

    @Nested
    @DisplayName("removeTag tests")
    class RemoveTagTests {

        @Test
        @DisplayName("test remove_tag removes tag and returns affected resources")
        void testRemoveTag() {
            List<String> affectedResources = tagMgr.removeTag("tag1", false);
            assertTrue(affectedResources.contains("res1"));
            assertTrue(affectedResources.contains("res4"));
            assertFalse(tagMgr.hasTag("tag1"));

            List<String> res1Tags = tagMgr.getResourcesTags("res1");
            assertFalse(res1Tags.contains("tag1"));
            List<String> res4Tags = tagMgr.getResourcesTags("res4");
            assertFalse(res4Tags.contains("tag1"));
        }
    }

    @Nested
    @DisplayName("getTagResources tests")
    class GetTagResourcesTests {

        @Test
        @DisplayName("test get_tag_resources returns resources for tag")
        void testGetTagResources() {
            List<String> resources = tagMgr.getTagResources("tag1");
            assertTrue(resources.contains("res1"));
            assertTrue(resources.contains("res4"));
            assertEquals(2, resources.size());
        }
    }

    @Nested
    @DisplayName("findResourcesByTags tests")
    class FindResourcesByTagsTests {

        @Test
        @DisplayName("test find_resources_by_tags with ANY strategy")
        void testFindResourcesByTagsAny() {
            List<String> resources = tagMgr.findResourcesByTags(
                    Arrays.asList("tag1", "tag3"), TagMatchStrategy.ANY, false);
            assertTrue(resources.contains("res1"));
            assertTrue(resources.contains("res2"));
            assertTrue(resources.contains("res4"));
            assertFalse(resources.contains("res3"));
        }

        @Test
        @DisplayName("test find_resources_by_tags with ALL strategy")
        void testFindResourcesByTagsAll() {
            List<String> resources = tagMgr.findResourcesByTags(
                    Arrays.asList("tag1", "tag3"), TagMatchStrategy.ALL, false);
            assertFalse(resources.contains("res1"));
            assertFalse(resources.contains("res2"));
            assertTrue(resources.contains("res4"));
            assertFalse(resources.contains("res3"));
        }

        @Test
        @DisplayName("test find_resources_by_tags with non-existent tag throws exception")
        void testFindResourcesWithNonexistentTag() {
            Exception exception = assertThrows(Exception.class, () -> {
                tagMgr.findResourcesByTags(Arrays.asList("tag99"), TagMatchStrategy.ANY, false);
            });
            assertTrue(exception.getMessage().contains("does not exist"));
        }

        @Test
        @DisplayName("test find_resources_by_tags skip non-existent tag")
        void testFindResourcesSkipNonexistentTag() {
            List<String> resources = tagMgr.findResourcesByTags(
                    Arrays.asList("tag1", "tag99"), TagMatchStrategy.ANY, true);
            assertTrue(resources.contains("res1"));
            assertTrue(resources.contains("res4"));
        }
    }

    @Nested
    @DisplayName("hasResourceTag tests")
    class HasResourceTagTests {

        @Test
        @DisplayName("test has_resource_tag returns correct result")
        void testHasResourceTag() {
            assertTrue(tagMgr.hasResourceTag("res1", "tag1"));
            assertFalse(tagMgr.hasResourceTag("res1", "tag3"));
            assertTrue(tagMgr.hasResourceTag("res3", Tag.GLOBAL));
        }
    }

    @Nested
    @DisplayName("getResourcesTags tests")
    class GetResourcesTagsTests {

        @Test
        @DisplayName("test get_resources_tags returns tags for resource")
        void testGetResourcesTags() {
            List<String> tags = tagMgr.getResourcesTags("res1");
            assertEquals(Set.of("tag1", "tag2"), new HashSet<>(tags));
        }
    }

    @Nested
    @DisplayName("display tests")
    class DisplayTests {

        @Test
        @DisplayName("test display returns formatted state")
        void testDisplay() {
            String result = tagMgr.display(false);
            assertTrue(result.contains("Tag -> Resource IDs:"));
            assertTrue(result.contains("Resource -> Tags:"));
            assertTrue(result.contains("Statistics:"));
        }
    }

    @Nested
    @DisplayName("concurrent operations tests")
    class ConcurrentTests {

        @Test
        @DisplayName("test concurrent operations")
        void testConcurrentOperations() throws InterruptedException {
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        tagMgr.tagResource("res" + index, Arrays.asList("tag" + index));
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("normalizeTags tests")
    class NormalizeTagsTests {

        @Test
        @DisplayName("test normalize_tags with single string via tagResource")
        void testNormalizeTagsSingleString() {
            tagMgr.clear();
            List<String> result = tagMgr.tagResource("res1", "tag1");
            assertTrue(result.contains("tag1"));
        }

        @Test
        @DisplayName("test normalize_tags with list via tagResource")
        void testNormalizeTagsList() {
            tagMgr.clear();
            List<String> result = tagMgr.tagResource("res1", Arrays.asList("tag1", "tag2"));
            assertTrue(result.contains("tag1"));
            assertTrue(result.contains("tag2"));
        }
    }

    @Nested
    @DisplayName("isBuiltinTag tests")
    class IsBuiltinTagTests {

        @Test
        @DisplayName("test GLOBAL is builtin tag")
        void testIsBuiltinTagGlobal() {
            assertTrue(isBuiltinTag(Tag.GLOBAL));
        }

        @Test
        @DisplayName("test normal tag is not builtin tag")
        void testIsBuiltinTagNormal() {
            assertFalse(isBuiltinTag("tag1"));
        }
    }

    private boolean isBuiltinTag(String tag) {
        return Tag.GLOBAL.equals(tag) || Tag.ALL.equals(tag)
                || Tag.ACTIVE.equals(tag) || Tag.INACTIVE.equals(tag);
    }
}
