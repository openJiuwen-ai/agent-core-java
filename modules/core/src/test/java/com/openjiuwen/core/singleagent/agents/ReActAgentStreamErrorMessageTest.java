/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;

/**
 * Regression: stream failures with null {@code getMessage()} must not log/publish bare "null".
 */
class ReActAgentStreamErrorMessageTest {

    @Test
    void describeStreamThrowableUsesClassNameWhenMessageIsNull() {
        ConcurrentModificationException cme = new ConcurrentModificationException();
        assertThat(cme.getMessage()).isNull();
        assertThat(ReActAgent.describeStreamThrowable(cme)).isEqualTo(ConcurrentModificationException.class.getName());
    }

    @Test
    void describeStreamThrowableKeepsClassAndMessageWhenPresent() {
        assertThat(ReActAgent.describeStreamThrowable(new IllegalStateException("boom")))
                .isEqualTo("IllegalStateException: boom");
    }

    @Test
    void describeStreamThrowableHandlesNullThrowable() {
        assertThat(ReActAgent.describeStreamThrowable(null)).isEqualTo("unknown");
    }
}
