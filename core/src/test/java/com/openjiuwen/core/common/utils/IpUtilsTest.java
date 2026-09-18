/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Mirrors Python's {@code openjiuwen.core.common.utils.ip_utils} in
 * {@code openjiuwen/core/common/utils/ip_utils.py}.
 */
class IpUtilsTest {

    @Test
    void getLocalIpNeverReturnsBlank() {
        String ip = IpUtils.getLocalIp();

        assertNotNull(ip);
        assertFalse(ip.isBlank());
    }
}
