/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryManagePackageMarkerTest {

    @Test
    void memoryManageRemainsMarkerOnly() throws Exception {
        Constructor<MemoryManagePackageMarker> constructor =
                MemoryManagePackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryManagePackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
