/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlUtilsTest {

    @Test
    void redactsPasswordWhenUsernameIsEmpty() {
        assertThat(UrlUtils.redactUrlPassword("redis://:secret@host:6379/0"))
                .isEqualTo("redis://:***@host:6379/0");
    }

    @Test
    void redactsPasswordWhenUsernameIsPresent() {
        assertThat(UrlUtils.redactUrlPassword("redis://user:secret@host:6379/0"))
                .isEqualTo("redis://user:***@host:6379/0");
    }

    @Test
    void leavesUrlUntouchedWhenNoPasswordExists() {
        assertThat(UrlUtils.redactUrlPassword("redis://host:6379/0"))
                .isEqualTo("redis://host:6379/0");
    }

    @Test
    void returnsOriginalStringWhenParsingFails() {
        assertThat(UrlUtils.redactUrlPassword("redis://bad host:6379/0"))
                .isEqualTo("redis://bad host:6379/0");
    }
}
