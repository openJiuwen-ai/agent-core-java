// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 TagMgr 标签管理器
 * 
 * 对应Python: test_tag_manager.py
 */
class TagMgrTest {

    private TagMgr tagMgr;

    @BeforeEach
    void setUp() {
        tagMgr = new TagMgr();
        // 初始化测试数据
        tagMgr.tagResource("res1", Arrays.asList("tag1", "tag2"));
        tagMgr.tagResource("res2", Arrays.asList("tag2", "tag3"));
        tagMgr.tagResource("res3", Tag.GLOBAL);
        tagMgr.tagResource("res4", Arrays.asList("tag1", "tag3", "tag4"));
    }

    @Nested
    @DisplayName("基本查询测试")
    class BasicQueryTest {

        @Test
        @DisplayName("测试 hasTag 方法")
        void testHasTag() {
            assertTrue(tagMgr.hasTag("tag1"));
            assertFalse(tagMgr.hasTag("tag5"));
            assertTrue(tagMgr.hasTag(Tag.GLOBAL));
        }

        @Test
        @DisplayName("测试 listTags 方法")
        void testListTags() {
            List<String> tags = tagMgr.listTags();
            assertTrue(tags.contains("tag1"));
            assertTrue(tags.contains("tag2"));
            assertTrue(tags.contains("tag3"));
            assertTrue(tags.contains("tag4"));
            assertTrue(tags.contains(Tag.GLOBAL)); // GLOBAL包含在返回列表中
            assertEquals(5, tags.size());
        }

        @Test
        @DisplayName("测试 hasResource 方法")
        void testHasResource() {
            assertTrue(tagMgr.hasResource("res1"));
            assertFalse(tagMgr.hasResource("res5"));
        }
    }

    @Nested
    @DisplayName("标签资源操作测试")
    class TagResourceTest {

        @Test
        @DisplayName("测试正常添加标签")
        void testTagResourceNormal() {
            // 为 res1 添加新标签
            List<String> currentTags = tagMgr.tagResource("res1", Arrays.asList("tag5", "tag6"));
            assertTrue(currentTags.contains("tag5"));
            assertTrue(currentTags.contains("tag6"));
            assertTrue(currentTags.contains("tag1"));
            assertTrue(currentTags.contains("tag2"));
        }

        @Test
        @DisplayName("测试添加 GLOBAL 标签")
        void testTagResourceWithGlobal() {
            List<String> currentTags = tagMgr.tagResource("res1", Tag.GLOBAL);
            assertEquals(1, currentTags.size());
            assertEquals(Tag.GLOBAL, currentTags.get(0));
            assertTrue(tagMgr.hasResourceTag("res1", Tag.GLOBAL));
        }

        @Test
        @DisplayName("测试为新资源添加标签")
        void testTagNewResource() {
            List<String> tags = tagMgr.tagResource("new_res", Arrays.asList("new_tag1", "new_tag2"));
            assertEquals(2, tags.size());
            assertTrue(tags.contains("new_tag1"));
            assertTrue(tags.contains("new_tag2"));
            assertTrue(tagMgr.hasResource("new_res"));
        }
    }

    @Nested
    @DisplayName("资源移除测试")
    class RemoveResourceTest {

        @Test
        @DisplayName("测试删除资源")
        void testRemoveResource() {
            List<String> removedTags = tagMgr.removeResource("res1");
            assertTrue(removedTags.containsAll(Arrays.asList("tag1", "tag2")));
            assertFalse(tagMgr.hasResource("res1"));
        }

        @Test
        @DisplayName("测试删除不存在的资源")
        void testRemoveNonexistentResource() {
            List<String> result = tagMgr.removeResource("res99");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("测试删除指定标签")
        void testRemoveResourceTags() {
            List<String> remainingTags = tagMgr.removeResourceTags("res1", Arrays.asList("tag1", "tag3"));
            assertFalse(remainingTags.contains("tag1"));
            assertTrue(remainingTags.contains("tag2"));
        }
    }

    @Nested
    @DisplayName("标签更新测试")
    class UpdateTagTest {

        @Test
        @DisplayName("测试替换标签策略")
        void testUpdateResourceTagsReplace() {
            List<String> newTags = Arrays.asList("tag5", "tag6");
            List<String> currentTags = tagMgr.updateResourceTags("res1", newTags, TagUpdateStrategy.REPLACE);
            
            assertEquals(new HashSet<>(newTags), new HashSet<>(currentTags));
            assertFalse(currentTags.contains("tag1"));
            assertFalse(currentTags.contains("tag2"));
        }

        @Test
        @DisplayName("测试合并标签策略")
        void testUpdateResourceTagsMerge() {
            List<String> newTags = Arrays.asList("tag5", "tag6");
            List<String> currentTags = tagMgr.updateResourceTags("res1", newTags, TagUpdateStrategy.MERGE);
            
            assertTrue(currentTags.contains("tag1"));
            assertTrue(currentTags.contains("tag2"));
            assertTrue(currentTags.contains("tag5"));
            assertTrue(currentTags.contains("tag6"));
        }

        @Test
        @DisplayName("测试更新为 GLOBAL 标签")
        void testUpdateToGlobal() {
            List<String> oldTags = tagMgr.updateResourceTags("res1", Tag.GLOBAL, TagUpdateStrategy.REPLACE);
            assertEquals(1, oldTags.size());
            assertEquals(Tag.GLOBAL, oldTags.get(0));
            assertTrue(tagMgr.hasResourceTag("res1", Tag.GLOBAL));
        }
    }

    @Nested
    @DisplayName("标签删除测试")
    class RemoveTagTest {

        @Test
        @DisplayName("测试删除标签")
        void testRemoveTag() {
            List<String> affectedResources = tagMgr.removeTag("tag1");
            assertTrue(affectedResources.contains("res1"));
            assertTrue(affectedResources.contains("res4"));
            assertFalse(tagMgr.hasTag("tag1"));
        }

        @Test
        @DisplayName("测试获取标签对应的资源")
        void testGetTagResources() {
            List<String> resources = tagMgr.getTagResources("tag1");
            assertTrue(resources.contains("res1"));
            assertTrue(resources.contains("res4"));
            assertEquals(2, resources.size());
        }
    }

    @Nested
    @DisplayName("标签查找测试")
    class FindResourcesTest {

        @Test
        @DisplayName("测试 ANY 匹配策略")
        void testFindResourcesByTagsAny() {
            List<String> resources = tagMgr.findResourcesByTags(
                Arrays.asList("tag1", "tag3"), TagMatchStrategy.ANY, false);
            assertTrue(resources.contains("res1"));  // 有 tag1
            assertTrue(resources.contains("res2"));  // 有 tag3
            assertTrue(resources.contains("res4"));  // 有 tag1 和 tag3
            assertFalse(resources.contains("res3")); // GLOBAL 不包含
        }

        @Test
        @DisplayName("测试 ALL 匹配策略")
        void testFindResourcesByTagsAll() {
            List<String> resources = tagMgr.findResourcesByTags(
                Arrays.asList("tag1", "tag3"), TagMatchStrategy.ALL, false);
            assertFalse(resources.contains("res1")); // 只有 tag1，没有 tag3
            assertFalse(resources.contains("res2")); // 只有 tag3，没有 tag1
            assertTrue(resources.contains("res4"));  // 既有 tag1 又有 tag3
            assertFalse(resources.contains("res3")); // GLOBAL 不包含
        }

        @Test
        @DisplayName("测试查找不存在的标签")
        void testFindResourcesWithNonexistentTag() {
            assertThrows(Exception.class, () -> 
                tagMgr.findResourcesByTags(Arrays.asList("tag99"), TagMatchStrategy.ANY, false));
        }

        @Test
        @DisplayName("测试跳过不存在的标签")
        void testFindResourcesSkipNonexistentTag() {
            List<String> resources = tagMgr.findResourcesByTags(
                Arrays.asList("tag1", "tag99"), TagMatchStrategy.ANY, true);
            assertTrue(resources.contains("res1"));
            assertTrue(resources.contains("res4"));
        }
    }

    @Nested
    @DisplayName("资源标签查询测试")
    class ResourceTagQueryTest {

        @Test
        @DisplayName("测试检查资源是否有指定标签")
        void testHasResourceTag() {
            assertTrue(tagMgr.hasResourceTag("res1", "tag1"));
            assertFalse(tagMgr.hasResourceTag("res1", "tag3"));
            assertTrue(tagMgr.hasResourceTag("res3", Tag.GLOBAL));
        }

        @Test
        @DisplayName("测试获取资源的标签")
        void testGetResourcesTags() {
            List<String> tags = tagMgr.getResourceTags("res1");
            assertEquals(new HashSet<>(Arrays.asList("tag1", "tag2")), new HashSet<>(tags));
        }
    }

    @Nested
    @DisplayName("显示和工具方法测试")
    class UtilityTest {

        @Test
        @DisplayName("测试显示状态")
        void testDisplay() {
            String result = tagMgr.display(false);
            assertTrue(result.contains("Tag -> Resource IDs:"));
            assertTrue(result.contains("Resource -> Tags:"));
            assertTrue(result.contains("Statistics:"));
        }

        @Test
        @DisplayName("测试标签标准化")
        void testNormalizeTags() {
            // 测试单个标签
            Set<String> result = TagMgr.normalizeTags("tag1");
            assertEquals(Set.of("tag1"), result);

            // 测试标签列表
            result = TagMgr.normalizeTags(Arrays.asList("tag1", "tag2"));
            assertEquals(Set.of("tag1", "tag2"), result);
        }

        @Test
        @DisplayName("测试内置标签检查")
        void testIsBuiltinTag() {
            assertTrue(TagMgr.isBuiltinTag(Tag.GLOBAL));
            assertFalse(TagMgr.isBuiltinTag("tag1"));
        }
    }

    @Nested
    @DisplayName("并发操作测试")
    class ConcurrencyTest {

        @Test
        @DisplayName("简单测试并发操作")
        void testConcurrentOperations() throws InterruptedException {
            TagMgr concurrentTagMgr = new TagMgr();
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        concurrentTagMgr.tagResource("res" + idx, Arrays.asList("tag" + idx));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // 验证所有资源都被添加
            assertEquals(threadCount, concurrentTagMgr.listTags().size());
        }
    }
}

