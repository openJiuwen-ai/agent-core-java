/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ToolCard 并发安全声明便捷入口测试。
 *
 * @since 2026-11-09
 */
class ToolCardParallelSafetyTest {
    @Test
    void explicitDeclarationsOverrideDefault() {
        assertThat(ToolCard.builder().name("safe").parallelSafe().build().isParallelSafe()).isTrue();
        assertThat(ToolCard.builder().name("unsafe").notParallelSafe().build().isParallelSafe()).isFalse();
    }

    @Test
    void undeclaredCardKeepsLegacyDefault() {
        assertThat(ToolCard.builder().name("legacy").build().isParallelSafe()).isTrue();
    }
}
