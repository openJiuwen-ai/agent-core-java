/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.context_engine;

import static com.openjiuwen.unit_tests.support.JUnitBridgeAssertions.assertDelegatedClassPasses;

import com.openjiuwen.core.context.context.KVCacheManagerTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bridge tests for the canonical Java translation of KV-cache release tests.
 *
 * <p>Mirrors Python's test_kv_cache_manager in tests.unit_tests.core.context_engine.</p>
 * The real translated coverage lives in {@link KVCacheManagerTest} and
 * {@link TestInferenceAffinityKvCacheReleaseWithProcessors}; this bridge replaces
 * the old placeholder shell with executable delegated coverage.
 */
@DisplayName("TestKvCacheManager")
class TestKvCacheManager {
    @Test
    @DisplayName("delegates KV-cache coverage to canonical tests")
    void testDelegatedKvCacheCoverage() {
        assertDelegatedClassPasses(KVCacheManagerTest.class);
        assertDelegatedClassPasses(TestInferenceAffinityKvCacheReleaseWithProcessors.class);
    }
}
