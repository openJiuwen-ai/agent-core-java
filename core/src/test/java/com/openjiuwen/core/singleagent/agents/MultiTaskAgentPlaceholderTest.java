/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiTaskAgentPlaceholderTest {

    @Test
    void placeholderIsInstantiable() {
        assertThat(new MultiTaskAgent()).isNotNull();
    }
}
