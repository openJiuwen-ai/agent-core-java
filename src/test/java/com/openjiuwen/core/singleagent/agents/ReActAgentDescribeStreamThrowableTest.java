/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

/**
 * Covers issue #66 / commit {@code 19c4f1fd} stream error description helper.
 */
class ReActAgentDescribeStreamThrowableTest {

    @Test
    void nullMessageUsesClassName() {
        assertThat(ReActAgent.describeStreamThrowable(new ConcurrentModificationException()))
                .isEqualTo(ConcurrentModificationException.class.getName());
    }

    @Test
    void nonBlankMessageIncludesSimpleNameAndText() {
        assertThat(ReActAgent.describeStreamThrowable(new IllegalStateException("boom")))
                .isEqualTo("IllegalStateException: boom");
    }

    @Test
    void nullThrowableReturnsUnknown() {
        assertThat(ReActAgent.describeStreamThrowable(null)).isEqualTo("unknown");
    }
}
