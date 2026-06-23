/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestRedactUrlPassword} in
 * {@code tests/unit_tests/core/common/utils/test_url_utils.py}.
 */
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
    void leavesUrlUntouchedWhenNoCredentialsExist() {
        assertThat(UrlUtils.redactUrlPassword("redis://localhost:6379/1"))
                .isEqualTo("redis://localhost:6379/1");
    }

    @Test
    void returnsEmptyAndNullUrlsUnchanged() {
        assertThat(UrlUtils.redactUrlPassword("")).isEqualTo("");
        assertThat(UrlUtils.redactUrlPassword(null)).isNull();
    }

    @Test
    void redactsSpecialCharactersInPassword() {
        assertThat(UrlUtils.redactUrlPassword("redis://:My%23SecretPwd@127.0.0.1:6379/0"))
                .isEqualTo("redis://:***@127.0.0.1:6379/0");
    }

    @Test
    void redactsMysqlPassword() {
        assertThat(UrlUtils.redactUrlPassword("mysql://root:password123@localhost:3306/mydb"))
                .isEqualTo("mysql://root:***@localhost:3306/mydb");
    }

    @Test
    void redactsPostgresPassword() {
        assertThat(UrlUtils.redactUrlPassword("postgresql://admin:secretpass@db.example.com:5432/production"))
                .isEqualTo("postgresql://admin:***@db.example.com:5432/production");
    }

    @Test
    void redactsPasswordWhenUrlHasNoPort() {
        assertThat(UrlUtils.redactUrlPassword("redis://:password@localhost/0"))
                .isEqualTo("redis://:***@localhost/0");
    }

    @Test
    void returnsOriginalStringWhenParsingFails() {
        assertThat(UrlUtils.redactUrlPassword("not a valid url at all"))
                .isEqualTo("not a valid url at all");
    }

    @Test
    void redactsPasswordAndKeepsQueryParams() {
        String result = UrlUtils.redactUrlPassword("redis://:secret@host:6379/0?ssl=true");

        assertThat(result).doesNotContain("secret");
        assertThat(result).contains("ssl=true");
    }
}
